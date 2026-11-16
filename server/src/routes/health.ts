import type { FastifyInstance } from 'fastify';

/** GET /healthz — sin datos ni autenticación; lo usan Cloud Run y los monitores de disponibilidad. */
export async function healthRoutes(app: FastifyInstance, deps: { version: string }) {
  app.get('/healthz', { logLevel: 'warn' }, async () => ({ status: 'ok', version: deps.version }));
}
