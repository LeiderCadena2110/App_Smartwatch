/**
 * Contrato del API. Fastify valida cada petición contra estos esquemas JSON antes de llegar a la ruta:
 * campos desconocidos se rechazan, los textos tienen largo máximo y los números rangos fisiológicos.
 *
 * La app envía datos ya agregados (no el historial crudo) y el servidor arma el prompt con ellos.
 */

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export interface RecentSession {
  type: string;
  minutes: number;
  avgHeartRate: number | null;
  drankMl: number;
  recommendedMl: number;
}

export interface CoachContext {
  /** Hora local del usuario, "HH:mm". */
  localTime: string;
  age: number;
  weightKg: number;
  dailyGoalMl: number;
  todayMl: number;
  expectedByNowMl: number;
  weeklyAverageMl: number;
  goalStreakDays: number;
  sweatRateLh: number | null;
  conditionsFactor: number | null;
  inWorkout: boolean;
  /** Tomado / recomendado en los entrenamientos, en %. */
  adherencePct: number | null;
  recentSessions: RecentSession[];
  plan: { totalMl: number; perHourMl: number; source: string } | null;
  insights: Array<{ level: RiskLevel; title: string; message: string }>;
}

export interface CoachRequest {
  /** Pregunta libre; null o ausente = "dame el consejo más útil para este momento". */
  question?: string | null;
  /** true = la respuesta se lee en el reloj (corta). */
  compact: boolean;
  context: CoachContext;
}

export interface CoachResponse {
  text: string;
  source: 'gemini';
  model: string;
  promptVersion: string;
  cached: boolean;
}

export interface WeatherResponse {
  city: string;
  temperatureC: number;
  humidityPct: number;
  fetchedAt: string;
}

export interface ErrorResponse {
  error: string;
  message: string;
}

const nullableInt = (minimum: number, maximum: number) => ({
  anyOf: [{ type: 'integer', minimum, maximum }, { type: 'null' }],
});
const nullableNumber = (minimum: number, maximum: number) => ({
  anyOf: [{ type: 'number', minimum, maximum }, { type: 'null' }],
});
const shortText = (maxLength: number) => ({ type: 'string', minLength: 1, maxLength });

export const errorSchema = {
  type: 'object',
  required: ['error', 'message'],
  properties: { error: { type: 'string' }, message: { type: 'string' } },
} as const;

export const coachBodySchema = {
  type: 'object',
  additionalProperties: false,
  required: ['compact', 'context'],
  properties: {
    question: { anyOf: [{ type: 'string', maxLength: 300 }, { type: 'null' }] },
    compact: { type: 'boolean' },
    context: {
      type: 'object',
      additionalProperties: false,
      required: [
        'localTime', 'age', 'weightKg', 'dailyGoalMl', 'todayMl', 'expectedByNowMl', 'weeklyAverageMl',
        'goalStreakDays', 'sweatRateLh', 'conditionsFactor', 'inWorkout', 'adherencePct', 'recentSessions',
        'plan', 'insights',
      ],
      properties: {
        localTime: { type: 'string', pattern: '^([01][0-9]|2[0-3]):[0-5][0-9]$' },
        age: { type: 'integer', minimum: 10, maximum: 100 },
        weightKg: { type: 'number', minimum: 30, maximum: 250 },
        dailyGoalMl: { type: 'integer', minimum: 500, maximum: 8000 },
        todayMl: { type: 'integer', minimum: 0, maximum: 20000 },
        expectedByNowMl: { type: 'integer', minimum: 0, maximum: 20000 },
        weeklyAverageMl: { type: 'integer', minimum: 0, maximum: 20000 },
        goalStreakDays: { type: 'integer', minimum: 0, maximum: 3650 },
        sweatRateLh: nullableNumber(0.05, 4),
        conditionsFactor: nullableNumber(0.5, 2),
        inWorkout: { type: 'boolean' },
        adherencePct: nullableInt(0, 1000),
        recentSessions: {
          type: 'array',
          maxItems: 5,
          items: {
            type: 'object',
            additionalProperties: false,
            required: ['type', 'minutes', 'avgHeartRate', 'drankMl', 'recommendedMl'],
            properties: {
              type: shortText(30),
              minutes: { type: 'integer', minimum: 0, maximum: 600 },
              avgHeartRate: nullableInt(30, 230),
              drankMl: { type: 'integer', minimum: 0, maximum: 10000 },
              recommendedMl: { type: 'integer', minimum: 0, maximum: 10000 },
            },
          },
        },
        plan: {
          anyOf: [
            {
              type: 'object',
              additionalProperties: false,
              required: ['totalMl', 'perHourMl', 'source'],
              properties: {
                totalMl: { type: 'integer', minimum: 0, maximum: 10000 },
                perHourMl: { type: 'integer', minimum: 0, maximum: 5000 },
                source: shortText(60),
              },
            },
            { type: 'null' },
          ],
        },
        insights: {
          type: 'array',
          maxItems: 6,
          items: {
            type: 'object',
            additionalProperties: false,
            required: ['level', 'title', 'message'],
            properties: {
              level: { type: 'string', enum: ['LOW', 'MEDIUM', 'HIGH'] },
              title: shortText(80),
              message: shortText(240),
            },
          },
        },
      },
    },
  },
} as const;

export const coachResponseSchema = {
  type: 'object',
  required: ['text', 'source', 'model', 'promptVersion', 'cached'],
  properties: {
    text: { type: 'string' },
    source: { type: 'string', enum: ['gemini'] },
    model: { type: 'string' },
    promptVersion: { type: 'string' },
    cached: { type: 'boolean' },
  },
} as const;

export const weatherQuerySchema = {
  type: 'object',
  additionalProperties: false,
  required: ['city'],
  properties: {
    // Letras (con tildes), espacios, comas, puntos y guiones: "Pasto,CO", "San Juan de Pasto"
    city: { type: 'string', minLength: 2, maxLength: 80, pattern: "^[\\p{L} ,.'-]+$" },
  },
} as const;

export const weatherResponseSchema = {
  type: 'object',
  required: ['city', 'temperatureC', 'humidityPct', 'fetchedAt'],
  properties: {
    city: { type: 'string' },
    temperatureC: { type: 'number' },
    humidityPct: { type: 'number' },
    fetchedAt: { type: 'string' },
  },
} as const;

/** Encabezado con un identificador aleatorio de la instalación (UUID), para la cuota diaria. */
export const INSTALL_ID_HEADER = 'x-install-id';
export const INSTALL_ID_PATTERN = /^[A-Za-z0-9-]{16,64}$/;
/** Encabezado con el token de Firebase App Check. */
export const APP_CHECK_HEADER = 'x-firebase-appcheck';
