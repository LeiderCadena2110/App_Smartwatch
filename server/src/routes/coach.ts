import type { FastifyInstance } from 'fastify';
import type { Config } from '../config.js';
import {
  INSTALL_ID_HEADER,
  INSTALL_ID_PATTERN,
  coachBodySchema,
  coachResponseSchema,
  errorSchema,
  type CoachRequest,
  type CoachResponse,
  type ErrorResponse,
} from '../schemas.js';
import { CircuitOpenError, type CoachService } from '../services/coach.js';
import { GeminiError } from '../services/gemini.js';
import { quotaDay, secondsUntilReset, type QuotaStore } from '../services/quota.js';

const GLOBAL_KEY = 'global';

/**
 * POST /v1/coach — pregunta al coach de IA.
 *
 * Respuestas:
 *   200 { text, source, model, promptVersion, cached }
 *   400 invalid_request · 401 app_check_* · 429 quota_exceeded (con Retry-After)
 *   422 blocked (contenido bloqueado por seguridad) · 503 upstream_unavailable
 * Ante cualquier error la app responde con su IA local.
 */
export async function coachRoutes(
  app: FastifyInstance,
  deps: { config: Config; coach: CoachService; quota: QuotaStore },
) {
  const { config, coach, quota } = deps;

  app.post<{ Body: CoachRequest; Reply: CoachResponse | ErrorResponse }>(
    '/v1/coach',
    {
      schema: {
        body: coachBodySchema,
        response: { 200: coachResponseSchema, '4xx': errorSchema, '5xx': errorSchema },
      },
    },
    async (request, reply) => {
      const installId = request.headers[INSTALL_ID_HEADER];
      if (typeof installId !== 'string' || !INSTALL_ID_PATTERN.test(installId)) {
        return reply.code(400).send({ error: 'invalid_request', message: `Falta o es inválido el encabezado ${INSTALL_ID_HEADER}` });
      }

      const day = quotaDay(config.quotaTimeZone);
      const perInstall = await quota.consume(`install:${installId}`, day, config.coachDailyLimitPerInstall);
      if (!perInstall.allowed) {
        request.log.info({ event: 'coach', outcome: 'quota_install' }, 'Cuota diaria de la instalación agotada');
        return reply
          .code(429)
          .header('retry-after', String(secondsUntilReset(config.quotaTimeZone)))
          .send({ error: 'quota_exceeded', message: 'Llegaste al límite de preguntas de hoy. Mañana se reinicia.' });
      }
      const global = await quota.consume(GLOBAL_KEY, day, config.coachDailyLimitGlobal);
      if (!global.allowed) {
        await quota.release(`install:${installId}`, day);
        request.log.warn({ event: 'coach', outcome: 'quota_global' }, 'Tope diario global alcanzado');
        return reply
          .code(429)
          .header('retry-after', String(secondsUntilReset(config.quotaTimeZone)))
          .send({ error: 'quota_exceeded', message: 'El coach en la nube llegó a su límite de hoy.' });
      }

      try {
        const answer = await coach.answer(installId, request.body);
        if (answer.cached) {
          // Una respuesta guardada no gasta Gemini: no cuenta para la cuota
          await Promise.all([quota.release(`install:${installId}`, day), quota.release(GLOBAL_KEY, day)]);
        }
        request.log.info(
          {
            event: 'coach',
            outcome: 'ok',
            model: answer.model,
            promptVersion: answer.promptVersion,
            cached: answer.cached,
            attempts: answer.attempts,
            latencyMs: answer.latencyMs,
            inputTokens: answer.cached ? 0 : answer.inputTokens,
            outputTokens: answer.cached ? 0 : answer.outputTokens,
            compact: request.body.compact,
            hasQuestion: Boolean(request.body.question?.trim()),
          },
          'Respuesta del coach',
        );
        return reply.send({
          text: answer.text,
          source: 'gemini',
          model: answer.model,
          promptVersion: answer.promptVersion,
          cached: answer.cached,
        });
      } catch (err) {
        // La pregunta no se respondió: no cuenta para la cuota
        await Promise.all([quota.release(`install:${installId}`, day), quota.release(GLOBAL_KEY, day)]);

        if (err instanceof GeminiError && err.code === 'blocked') {
          request.log.info({ event: 'coach', outcome: 'blocked' }, err.message);
          return reply.code(422).send({ error: 'blocked', message: 'No puedo responder eso. Pregunta sobre hidratación o ejercicio.' });
        }
        const code = err instanceof GeminiError ? err.code : err instanceof CircuitOpenError ? 'circuit_open' : 'internal';
        request.log.error(
          { event: 'coach', outcome: 'error', errorCode: code, status: err instanceof GeminiError ? err.status : undefined },
          (err as Error).message,
        );
        return reply.code(503).send({ error: 'upstream_unavailable', message: 'El coach en la nube no está disponible.' });
      }
    },
  );
}
