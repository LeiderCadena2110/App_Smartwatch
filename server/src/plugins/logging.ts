import { randomUUID } from 'node:crypto';
import type { IncomingMessage } from 'node:http';
import type { FastifyServerOptions } from 'fastify';
import type { Config } from '../config.js';

/** Niveles de pino → severidad de Google Cloud Logging. */
const SEVERITY: Record<string, string> = {
  trace: 'DEBUG',
  debug: 'DEBUG',
  info: 'INFO',
  warn: 'WARNING',
  error: 'ERROR',
  fatal: 'CRITICAL',
};

/**
 * Logs JSON de una línea que Cloud Logging entiende (campo "severity" y "message").
 * Nunca se registran el cuerpo de las peticiones (datos de salud), las preguntas, los tokens
 * de App Check ni las claves.
 */
export function loggerOptions(config: Config): FastifyServerOptions['logger'] {
  if (config.env === 'test') return false;
  const base = {
    level: config.logLevel,
    messageKey: 'message',
    redact: {
      paths: [
        'req.headers["x-firebase-appcheck"]',
        'req.headers["x-install-id"]',
        'req.headers.authorization',
        'req.body',
      ],
      remove: true,
    },
  };
  if (config.env === 'development') {
    return { ...base, transport: { target: 'pino-pretty', options: { messageKey: 'message' } } };
  }
  return {
    ...base,
    formatters: {
      level: (label: string) => ({ severity: SEVERITY[label] ?? 'DEFAULT' }),
    },
  };
}

/** Id de petición: el de Cloud Run (X-Cloud-Trace-Context) o uno nuevo. */
export function requestId(req: IncomingMessage): string {
  const trace = req.headers['x-cloud-trace-context'];
  if (typeof trace === 'string' && trace.length > 0) return trace.split('/')[0] ?? randomUUID();
  return randomUUID();
}
