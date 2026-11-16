/**
 * Cliente de la API REST de Gemini (generateContent) con fetch nativo de Node.
 * - Clave en el encabezado x-goog-api-key (nunca en la URL ni en los logs).
 * - Tiempo máximo por intento con AbortSignal.
 * - Errores clasificados para decidir si se reintenta y qué código recibe la app.
 * - Devuelve los tokens usados (usageMetadata) para medir el costo.
 */

export type GeminiErrorCode = 'timeout' | 'rate_limited' | 'unavailable' | 'blocked' | 'auth' | 'bad_response';

export class GeminiError extends Error {
  constructor(
    readonly code: GeminiErrorCode,
    message: string,
    readonly status?: number,
  ) {
    super(message);
    this.name = 'GeminiError';
  }

  /** Errores pasajeros: vale la pena un reintento. */
  get retryable(): boolean {
    return this.code === 'rate_limited' || this.code === 'unavailable' || this.code === 'timeout';
  }
}

export interface GenerateRequest {
  system: string;
  parts: string[];
  maxOutputTokens: number;
  temperature: number;
  timeoutMs: number;
}

export interface GenerateResult {
  text: string;
  model: string;
  inputTokens: number;
  outputTokens: number;
}

export interface TextGenerator {
  readonly model: string;
  generate(request: GenerateRequest): Promise<GenerateResult>;
}

const BASE_URL = 'https://generativelanguage.googleapis.com/v1beta/models';

const SAFETY_SETTINGS = [
  'HARM_CATEGORY_HARASSMENT',
  'HARM_CATEGORY_HATE_SPEECH',
  'HARM_CATEGORY_SEXUALLY_EXPLICIT',
  'HARM_CATEGORY_DANGEROUS_CONTENT',
].map((category) => ({ category, threshold: 'BLOCK_MEDIUM_AND_ABOVE' }));

interface GeminiPart {
  text?: string;
  thought?: boolean;
}
interface GeminiResponseBody {
  candidates?: Array<{ content?: { parts?: GeminiPart[] }; finishReason?: string }>;
  promptFeedback?: { blockReason?: string };
  usageMetadata?: { promptTokenCount?: number; candidatesTokenCount?: number };
  error?: { message?: string; status?: string };
}

export class GeminiClient implements TextGenerator {
  constructor(
    private readonly apiKey: string,
    readonly model: string,
    private readonly fetchImpl: typeof fetch = fetch,
  ) {}

  async generate(req: GenerateRequest): Promise<GenerateResult> {
    if (!this.apiKey) throw new GeminiError('auth', 'Falta la clave de Gemini en el servidor');

    const body = {
      systemInstruction: { parts: [{ text: req.system }] },
      contents: [{ role: 'user', parts: req.parts.map((text) => ({ text })) }],
      generationConfig: { maxOutputTokens: req.maxOutputTokens, temperature: req.temperature },
      safetySettings: SAFETY_SETTINGS,
    };

    let response: Response;
    try {
      response = await this.fetchImpl(`${BASE_URL}/${encodeURIComponent(this.model)}:generateContent`, {
        method: 'POST',
        headers: { 'content-type': 'application/json; charset=utf-8', 'x-goog-api-key': this.apiKey },
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(req.timeoutMs),
      });
    } catch (err) {
      if (err instanceof Error && (err.name === 'TimeoutError' || err.name === 'AbortError')) {
        throw new GeminiError('timeout', `Gemini no respondió en ${req.timeoutMs} ms`);
      }
      throw new GeminiError('unavailable', `Error de red con Gemini: ${(err as Error).message}`);
    }

    let json: GeminiResponseBody;
    try {
      json = (await response.json()) as GeminiResponseBody;
    } catch {
      throw new GeminiError(response.ok ? 'bad_response' : statusToCode(response.status), 'Respuesta de Gemini no es JSON', response.status);
    }

    if (!response.ok) {
      const detail = json.error?.message ?? `HTTP ${response.status}`;
      throw new GeminiError(statusToCode(response.status), `Gemini respondió ${response.status}: ${detail.slice(0, 200)}`, response.status);
    }
    return parseResponse(json, this.model);
  }
}

function statusToCode(status: number): GeminiErrorCode {
  if (status === 429) return 'rate_limited';
  if (status === 401 || status === 403) return 'auth';
  if (status >= 500) return 'unavailable';
  return 'bad_response';
}

export function parseResponse(json: GeminiResponseBody, model: string): GenerateResult {
  const blockReason = json.promptFeedback?.blockReason;
  if (blockReason) throw new GeminiError('blocked', `Pregunta bloqueada (${blockReason})`);

  const candidate = json.candidates?.[0];
  if (!candidate) throw new GeminiError('bad_response', 'Respuesta sin candidatos');
  if (candidate.finishReason === 'SAFETY' || candidate.finishReason === 'PROHIBITED_CONTENT') {
    throw new GeminiError('blocked', `Respuesta bloqueada (${candidate.finishReason})`);
  }

  // Algunos modelos devuelven partes de "pensamiento": no se muestran
  const text = (candidate.content?.parts ?? [])
    .filter((p) => !p.thought && typeof p.text === 'string')
    .map((p) => p.text)
    .join('')
    .trim();
  if (!text) throw new GeminiError('bad_response', 'Respuesta vacía');

  return {
    text,
    model,
    inputTokens: json.usageMetadata?.promptTokenCount ?? 0,
    outputTokens: json.usageMetadata?.candidatesTokenCount ?? 0,
  };
}
