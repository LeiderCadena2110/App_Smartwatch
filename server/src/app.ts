import Fastify, { type FastifyError, type FastifyInstance } from 'fastify';
import type { Config } from './config.js';
import { appCheckGuard, type AppCheckVerifier } from './plugins/appCheck.js';
import { loggerOptions, requestId } from './plugins/logging.js';
import { coachRoutes } from './routes/coach.js';
import { healthRoutes } from './routes/health.js';
import { weatherRoutes } from './routes/weather.js';
import type { CoachService } from './services/coach.js';
import type { QuotaStore } from './services/quota.js';
import type { WeatherService } from './services/weather.js';

export interface AppDependencies {
  config: Config;
  coach: CoachService;
  weather: WeatherService;
  quota: QuotaStore;
  appCheck: AppCheckVerifier;
}

/**
 * Arma el servidor con sus dependencias. Las pruebas pasan versiones simuladas
 * (Gemini, App Check y cuotas en memoria) y llaman a las rutas con app.inject().
 */
export async function buildApp(deps: AppDependencies): Promise<FastifyInstance> {
  const { config } = deps;

  const app = Fastify({
    logger: loggerOptions(config),
    genReqId: requestId,
    trustProxy: true, // Cloud Run está detrás de un balanceador
    bodyLimit: 16 * 1024, // el contexto del coach pesa ~2 KB
    ajv: {
      // Estricto: los campos desconocidos se rechazan (no se eliminan en silencio) y no se convierten tipos
      customOptions: { removeAdditional: false, coerceTypes: false, allErrors: false },
    },
  });

  // Sin CORS a propósito: lo llaman apps nativas, no páginas web.
  app.addHook('onSend', async (_request, reply) => {
    reply.header('cache-control', 'no-store');
    reply.header('x-content-type-options', 'nosniff');
  });

  app.setErrorHandler((error: FastifyError, request, reply) => {
    if (error.validation) {
      return reply.code(400).send({ error: 'invalid_request', message: error.message });
    }
    const status = error.statusCode ?? 500;
    if (status < 500) {
      const code = status === 413 ? 'payload_too_large' : status === 415 ? 'unsupported_media_type' : 'invalid_request';
      return reply.code(status).send({ error: code, message: error.message });
    }
    request.log.error({ err: error }, 'Error no controlado');
    return reply.code(500).send({ error: 'internal', message: 'Error interno' });
  });

  app.setNotFoundHandler((_request, reply) => reply.code(404).send({ error: 'not_found', message: 'Ruta no encontrada' }));

  await app.register(healthRoutes, { version: config.version });

  // Todo lo que está bajo /v1 exige App Check
  await app.register(async (v1) => {
    v1.addHook('onRequest', appCheckGuard(deps.appCheck, config.appCheckMode === 'enforce'));
    await v1.register(coachRoutes, { config, coach: deps.coach, quota: deps.quota });
    await v1.register(weatherRoutes, { weather: deps.weather });
  });

  return app;
}
