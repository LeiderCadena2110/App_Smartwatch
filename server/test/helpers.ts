import { buildApp } from '../src/app.js';
import { loadConfig, type Config } from '../src/config.js';
import type { AppCheckVerifier } from '../src/plugins/appCheck.js';
import type { CoachContext, CoachRequest } from '../src/schemas.js';
import { CoachService } from '../src/services/coach.js';
import type { GenerateRequest, GenerateResult, TextGenerator } from '../src/services/gemini.js';
import { MemoryQuotaStore } from '../src/services/quota.js';
import { WeatherService } from '../src/services/weather.js';

export const VALID_TOKEN = 'token-valido-de-app-check-para-pruebas';
export const INSTALL_ID = '6f1c2b8e-1d2a-4c7e-9b1f-0a2b3c4d5e6f';

export function sampleContext(overrides: Partial<CoachContext> = {}): CoachContext {
  return {
    localTime: '14:30',
    age: 24,
    weightKg: 72.4,
    dailyGoalMl: 2500,
    todayMl: 900,
    expectedByNowMl: 1400,
    weeklyAverageMl: 2100,
    goalStreakDays: 3,
    sweatRateLh: 0.85,
    conditionsFactor: 1.1,
    inWorkout: false,
    adherencePct: 72,
    recentSessions: [{ type: 'Correr', minutes: 45, avgHeartRate: 152, drankMl: 400, recommendedMl: 600 }],
    plan: { totalMl: 650, perHourMl: 650, source: 'Modelo IA personal' },
    insights: [{ level: 'MEDIUM', title: 'Vas atrasado', message: 'Llevas 500 ml menos de lo esperado a esta hora.' }],
    ...overrides,
  };
}

export function sampleRequest(overrides: Partial<CoachRequest> = {}): CoachRequest {
  return { question: '¿Cuánta agua llevo al entreno?', compact: false, context: sampleContext(), ...overrides };
}

/** Gemini simulado: devuelve lo que se le programe y guarda las peticiones recibidas. */
export class FakeGenerator implements TextGenerator {
  readonly model = 'gemini-prueba';
  readonly calls: GenerateRequest[] = [];
  private queue: Array<GenerateResult | Error> = [];

  willReturn(...items: Array<string | Error>): this {
    this.queue.push(
      ...items.map((i) => (typeof i === 'string' ? { text: i, model: this.model, inputTokens: 120, outputTokens: 30 } : i)),
    );
    return this;
  }

  async generate(request: GenerateRequest): Promise<GenerateResult> {
    this.calls.push(request);
    const next = this.queue.shift() ?? { text: 'Respuesta por defecto.', model: this.model, inputTokens: 100, outputTokens: 20 };
    if (next instanceof Error) throw next;
    return next;
  }
}

export class FakeAppCheck implements AppCheckVerifier {
  async verify(token: string) {
    if (token !== VALID_TOKEN) throw new Error('invalid');
    return { appId: '1:123:android:abc' };
  }
}

export function testConfig(overrides: Partial<Config> = {}): Config {
  return {
    ...loadConfig({ NODE_ENV: 'test', QUOTA_BACKEND: 'memory', GEMINI_API_KEY: 'x', OPENWEATHER_API_KEY: 'y' }),
    ...overrides,
  };
}

export async function buildTestApp(
  options: {
    generator?: FakeGenerator;
    config?: Partial<Config>;
    weatherFetch?: typeof fetch;
  } = {},
) {
  const generator = options.generator ?? new FakeGenerator();
  const config = testConfig(options.config);
  const app = await buildApp({
    config,
    coach: new CoachService(generator, { budgetMs: 8000, sleep: async () => {} }),
    weather: new WeatherService(config.openWeatherApiKey, options.weatherFetch ?? (fetch as typeof fetch)),
    quota: new MemoryQuotaStore(),
    appCheck: new FakeAppCheck(),
  });
  return { app, generator, config };
}

export const authHeaders = {
  'x-firebase-appcheck': VALID_TOKEN,
  'x-install-id': INSTALL_ID,
};
