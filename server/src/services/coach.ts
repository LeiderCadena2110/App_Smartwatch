import { createHash } from 'node:crypto';
import type { CoachRequest } from '../schemas.js';
import { GeminiError, type GenerateResult, type TextGenerator } from './gemini.js';
import { PROMPT_VERSION, buildContext, fitForWatch, questionPart, systemInstruction } from './prompt.js';

export interface CoachAnswer extends GenerateResult {
  promptVersion: string;
  cached: boolean;
  attempts: number;
  latencyMs: number;
}

/**
 * Corte de circuito: tras varios fallos seguidos de Gemini deja de llamarlo por un rato.
 * Así un corte de Gemini no hace esperar 8 s a cada usuario: la app recibe 503 de inmediato
 * y responde con su IA local.
 */
export class CircuitBreaker {
  private failures = 0;
  private openUntil = 0;

  constructor(
    private readonly threshold = 3,
    private readonly cooldownMs = 60_000,
    private readonly now: () => number = Date.now,
  ) {}

  get isOpen(): boolean {
    return this.now() < this.openUntil;
  }

  success(): void {
    this.failures = 0;
    this.openUntil = 0;
  }

  failure(): void {
    this.failures += 1;
    if (this.failures >= this.threshold) {
      this.openUntil = this.now() + this.cooldownMs;
      this.failures = 0;
    }
  }
}

/** Caché pequeña en memoria con vencimiento (por instancia de Cloud Run). */
export class TtlCache<V> {
  private readonly items = new Map<string, { value: V; expires: number }>();

  constructor(
    private readonly ttlMs: number,
    private readonly maxItems: number,
    private readonly now: () => number = Date.now,
  ) {}

  get(key: string): V | undefined {
    const item = this.items.get(key);
    if (!item) return undefined;
    if (item.expires <= this.now()) {
      this.items.delete(key);
      return undefined;
    }
    return item.value;
  }

  set(key: string, value: V): void {
    if (this.items.size >= this.maxItems) {
      // Se descarta el más antiguo (los Map conservan el orden de inserción)
      const oldest = this.items.keys().next().value;
      if (oldest !== undefined) this.items.delete(oldest);
    }
    this.items.set(key, { value, expires: this.now() + this.ttlMs });
  }
}

export class CircuitOpenError extends Error {
  constructor() {
    super('Gemini en pausa tras varios fallos seguidos');
    this.name = 'CircuitOpenError';
  }
}

export interface CoachServiceOptions {
  budgetMs: number;
  /** Espera antes del reintento (ms). */
  retryDelayMs?: number;
  now?: () => number;
  sleep?: (ms: number) => Promise<void>;
}

export class CoachService {
  private readonly breaker: CircuitBreaker;
  /** El "consejo para este momento" (sin pregunta) se repite: se guarda 15 min. */
  private readonly adviceCache: TtlCache<GenerateResult>;
  private readonly now: () => number;
  private readonly sleep: (ms: number) => Promise<void>;
  private readonly retryDelayMs: number;

  constructor(
    private readonly generator: TextGenerator,
    private readonly options: CoachServiceOptions,
  ) {
    this.now = options.now ?? Date.now;
    this.sleep = options.sleep ?? ((ms) => new Promise((resolve) => setTimeout(resolve, ms)));
    this.retryDelayMs = options.retryDelayMs ?? 400;
    this.breaker = new CircuitBreaker(3, 60_000, this.now);
    this.adviceCache = new TtlCache(15 * 60_000, 5000, this.now);
  }

  get model(): string {
    return this.generator.model;
  }

  async answer(installId: string, req: CoachRequest): Promise<CoachAnswer> {
    const started = this.now();
    const hasQuestion = Boolean(req.question?.trim());
    const cacheKey = hasQuestion ? null : adviceKey(installId, req);

    if (cacheKey) {
      const hit = this.adviceCache.get(cacheKey);
      if (hit) return { ...hit, promptVersion: PROMPT_VERSION, cached: true, attempts: 0, latencyMs: this.now() - started };
    }
    if (this.breaker.isOpen) throw new CircuitOpenError();

    const request = {
      system: systemInstruction(req.compact),
      parts: [buildContext(req.context), questionPart(req.question)],
      maxOutputTokens: req.compact ? 160 : 512,
      temperature: 0.6,
    };

    let attempts = 0;
    for (;;) {
      attempts += 1;
      const remaining = this.options.budgetMs - (this.now() - started);
      try {
        if (remaining < 500) throw new GeminiError('timeout', 'Sin tiempo para otro intento');
        const result = await this.generator.generate({ ...request, timeoutMs: remaining });
        this.breaker.success();
        const text = req.compact ? fitForWatch(result.text) : result.text;
        const final = { ...result, text };
        if (cacheKey) this.adviceCache.set(cacheKey, final);
        return { ...final, promptVersion: PROMPT_VERSION, cached: false, attempts, latencyMs: this.now() - started };
      } catch (err) {
        const retryable = err instanceof GeminiError && err.retryable;
        const timeLeft = this.options.budgetMs - (this.now() - started) - this.retryDelayMs;
        if (retryable && attempts < 2 && timeLeft >= 1500) {
          await this.sleep(this.retryDelayMs);
          continue;
        }
        // Los bloqueos de seguridad no cuentan como caída de Gemini
        if (!(err instanceof GeminiError && err.code === 'blocked')) this.breaker.failure();
        throw err;
      }
    }
  }
}

/** Misma instalación y mismos datos = mismo consejo. La hora se ignora para que la caché sirva. */
function adviceKey(installId: string, req: CoachRequest): string {
  const { localTime: _ignored, ...rest } = req.context;
  const hash = createHash('sha256').update(JSON.stringify({ c: req.compact, ctx: rest })).digest('hex').slice(0, 32);
  return `${installId}:${hash}`;
}
