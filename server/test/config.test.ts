import { describe, expect, it } from 'vitest';
import { loadConfig } from '../src/config.js';
import { quotaDay, secondsUntilReset } from '../src/services/quota.js';

describe('loadConfig', () => {
  const prod = { NODE_ENV: 'production', GEMINI_API_KEY: 'k' };

  it('en producción exige App Check, cuotas en Firestore y clave de Gemini', () => {
    expect(() => loadConfig({ ...prod, APP_CHECK_MODE: 'off' })).toThrow('APP_CHECK_MODE=off');
    expect(() => loadConfig({ ...prod, QUOTA_BACKEND: 'memory' })).toThrow('QUOTA_BACKEND=memory');
    expect(() => loadConfig({ NODE_ENV: 'production' })).toThrow('GEMINI_API_KEY');
    expect(loadConfig(prod)).toMatchObject({ appCheckMode: 'enforce', quotaBackend: 'firestore', port: 8080 });
  });

  it('valida números y zona horaria', () => {
    expect(() => loadConfig({ COACH_DAILY_LIMIT: '-1' })).toThrow('COACH_DAILY_LIMIT');
    expect(() => loadConfig({ QUOTA_TIME_ZONE: 'Marte/Olympus' })).toThrow('QUOTA_TIME_ZONE');
  });
});

describe('cuota diaria', () => {
  it('el día cambia a medianoche de Colombia (UTC−5)', () => {
    // 2026-11-16 04:59 UTC = 2026-11-15 23:59 en Bogotá
    expect(quotaDay('America/Bogota', new Date('2026-11-16T04:59:00Z'))).toBe('2026-11-15');
    expect(quotaDay('America/Bogota', new Date('2026-11-16T05:00:00Z'))).toBe('2026-11-16');
  });

  it('calcula los segundos hasta el reinicio', () => {
    expect(secondsUntilReset('America/Bogota', new Date('2026-11-16T04:59:00Z'))).toBe(60);
  });
});
