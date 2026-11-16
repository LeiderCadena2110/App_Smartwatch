import type { FastifyReply, FastifyRequest } from 'fastify';
import { APP_CHECK_HEADER } from '../schemas.js';

/**
 * Firebase App Check: solo la app HidraSmart original, instalada desde Google Play en un
 * dispositivo íntegro (Play Integrity), obtiene tokens válidos. Sin token válido → 401.
 */
export interface AppCheckVerifier {
  verify(token: string): Promise<{ appId: string }>;
}

/** Verificador real con el Admin SDK de Firebase. */
export class FirebaseAppCheckVerifier implements AppCheckVerifier {
  async verify(token: string) {
    const { getAppCheck } = await import('firebase-admin/app-check');
    const result = await getAppCheck().verifyToken(token);
    return { appId: result.appId };
  }
}

/** Solo para desarrollo local (APP_CHECK_MODE=off). loadConfig lo prohíbe en producción. */
export class DisabledAppCheckVerifier implements AppCheckVerifier {
  async verify() {
    return { appId: 'local-development' };
  }
}

declare module 'fastify' {
  interface FastifyRequest {
    appId?: string;
  }
}

export function appCheckGuard(verifier: AppCheckVerifier, enforce: boolean) {
  return async function guard(request: FastifyRequest, reply: FastifyReply) {
    if (!enforce) {
      request.appId = 'local-development';
      return;
    }
    const token = request.headers[APP_CHECK_HEADER];
    if (typeof token !== 'string' || token.length < 20) {
      return reply.code(401).send({ error: 'app_check_missing', message: 'Falta el token de App Check' });
    }
    try {
      const { appId } = await verifier.verify(token);
      request.appId = appId;
    } catch {
      request.log.warn({ event: 'app_check_rejected' }, 'Token de App Check inválido');
      return reply.code(401).send({ error: 'app_check_invalid', message: 'Token de App Check inválido o vencido' });
    }
  };
}
