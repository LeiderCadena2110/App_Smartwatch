/**
 * Configuración del servidor, leída de variables de entorno.
 * En Cloud Run las claves llegan desde Secret Manager (--set-secrets); nunca se escriben en el código.
 */

export type AppCheckMode = 'enforce' | 'off';
export type QuotaBackend = 'firestore' | 'memory';

export interface Config {
  env: 'production' | 'development' | 'test';
  port: number;
  /** Versión del servicio (la pone Cloud Run en K_REVISION). */
  version: string;

  geminiApiKey: string;
  geminiModel: string;
  /** Tiempo total máximo por pregunta, reintento incluido. */
  geminiBudgetMs: number;

  openWeatherApiKey: string;

  /** enforce = toda llamada a /v1 necesita un token válido de Firebase App Check. */
  appCheckMode: AppCheckMode;

  quotaBackend: QuotaBackend;
  /** Preguntas al coach por instalación y por día. */
  coachDailyLimitPerInstall: number;
  /** Tope de preguntas de todos los usuarios por día: protege el presupuesto. */
  coachDailyLimitGlobal: number;
  /** Zona horaria en la que se reinician las cuotas diarias. */
  quotaTimeZone: string;

  logLevel: string;
}

function int(value: string | undefined, fallback: number, name: string): number {
  if (value === undefined || value === '') return fallback;
  const n = Number(value);
  if (!Number.isInteger(n) || n < 0) throw new Error(`${name} debe ser un entero positivo`);
  return n;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const nodeEnv = env.NODE_ENV === 'production' || env.NODE_ENV === 'test' ? env.NODE_ENV : 'development';
  const appCheckMode: AppCheckMode = env.APP_CHECK_MODE === 'off' ? 'off' : 'enforce';
  const quotaBackend: QuotaBackend = env.QUOTA_BACKEND === 'memory' ? 'memory' : 'firestore';

  const config: Config = {
    env: nodeEnv,
    port: int(env.PORT, 8080, 'PORT'),
    version: env.K_REVISION ?? env.APP_VERSION ?? 'local',
    geminiApiKey: env.GEMINI_API_KEY ?? '',
    geminiModel: env.GEMINI_MODEL || 'gemini-3.5-flash-lite',
    geminiBudgetMs: int(env.GEMINI_BUDGET_MS, 8000, 'GEMINI_BUDGET_MS'),
    openWeatherApiKey: env.OPENWEATHER_API_KEY ?? '',
    appCheckMode,
    quotaBackend,
    coachDailyLimitPerInstall: int(env.COACH_DAILY_LIMIT, 20, 'COACH_DAILY_LIMIT'),
    coachDailyLimitGlobal: int(env.COACH_GLOBAL_DAILY_LIMIT, 2000, 'COACH_GLOBAL_DAILY_LIMIT'),
    quotaTimeZone: env.QUOTA_TIME_ZONE || 'America/Bogota',
    logLevel: env.LOG_LEVEL || (nodeEnv === 'production' ? 'info' : 'debug'),
  };

  if (config.env === 'production') {
    // En producción no se permite arrancar sin protecciones
    if (config.appCheckMode !== 'enforce') throw new Error('APP_CHECK_MODE=off no está permitido en producción');
    if (config.quotaBackend !== 'firestore') throw new Error('QUOTA_BACKEND=memory no está permitido en producción');
    if (!config.geminiApiKey) throw new Error('Falta GEMINI_API_KEY (Secret Manager)');
  }
  try {
    new Intl.DateTimeFormat('en-CA', { timeZone: config.quotaTimeZone });
  } catch {
    throw new Error(`QUOTA_TIME_ZONE inválida: ${config.quotaTimeZone}`);
  }
  return config;
}
