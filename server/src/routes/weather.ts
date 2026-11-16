import type { FastifyInstance } from 'fastify';
import {
  errorSchema,
  weatherQuerySchema,
  weatherResponseSchema,
  type ErrorResponse,
  type WeatherResponse,
} from '../schemas.js';
import { WeatherError, type WeatherService } from '../services/weather.js';

/**
 * GET /v1/weather?city=Pasto,CO — temperatura y humedad actuales.
 *   200 { city, temperatureC, humidityPct, fetchedAt } · 404 city_not_found · 503 weather_unavailable
 */
export async function weatherRoutes(app: FastifyInstance, deps: { weather: WeatherService }) {
  app.get<{ Querystring: { city: string }; Reply: WeatherResponse | ErrorResponse }>(
    '/v1/weather',
    {
      schema: {
        querystring: weatherQuerySchema,
        response: { 200: weatherResponseSchema, '4xx': errorSchema, '5xx': errorSchema },
      },
    },
    async (request, reply) => {
      try {
        const { cached, ...value } = await deps.weather.current(request.query.city);
        request.log.info({ event: 'weather', outcome: 'ok', cached }, 'Clima');
        return reply.send(value);
      } catch (err) {
        if (err instanceof WeatherError && err.code === 'not_found') {
          return reply.code(404).send({ error: 'city_not_found', message: 'No se encontró la ciudad' });
        }
        request.log.error({ event: 'weather', outcome: 'error', errorCode: (err as WeatherError).code }, (err as Error).message);
        return reply.code(503).send({ error: 'weather_unavailable', message: 'El clima no está disponible' });
      }
    },
  );
}
