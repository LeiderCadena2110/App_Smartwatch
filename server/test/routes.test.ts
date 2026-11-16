import { afterEach, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import { GeminiError } from '../src/services/gemini.js';
import { FakeGenerator, authHeaders, buildTestApp, sampleRequest } from './helpers.js';

let app: FastifyInstance | undefined;
afterEach(async () => {
  await app?.close();
  app = undefined;
});

async function start(options: Parameters<typeof buildTestApp>[0] = {}) {
  const built = await buildTestApp(options);
  app = built.app;
  return built;
}

const post = (body: unknown, headers: Record<string, string> = authHeaders) =>
  app!.inject({ method: 'POST', url: '/v1/coach', headers, payload: body as object });

describe('GET /healthz', () => {
  it('responde sin autenticación', async () => {
    await start();
    const res = await app!.inject({ method: 'GET', url: '/healthz' });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toMatchObject({ status: 'ok' });
  });
});

describe('POST /v1/coach', () => {
  it('responde con el texto de Gemini y metadatos', async () => {
    const { generator } = await start({ generator: new FakeGenerator().willReturn('Toma 500 ml antes de entrenar.') });
    const res = await post(sampleRequest());
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({
      text: 'Toma 500 ml antes de entrenar.',
      source: 'gemini',
      model: 'gemini-prueba',
      promptVersion: expect.any(String),
      cached: false,
    });
    expect(res.headers['cache-control']).toBe('no-store');
    expect(res.headers['access-control-allow-origin']).toBeUndefined();
    expect(generator.calls[0]?.parts[1]).toContain('¿Cuánta agua llevo al entreno?');
  });

  it('401 sin token de App Check', async () => {
    const { generator } = await start();
    const res = await post(sampleRequest(), { 'x-install-id': authHeaders['x-install-id'] });
    expect(res.statusCode).toBe(401);
    expect(res.json().error).toBe('app_check_missing');
    expect(generator.calls).toHaveLength(0);
  });

  it('401 con token inválido', async () => {
    await start();
    const res = await post(sampleRequest(), { ...authHeaders, 'x-firebase-appcheck': 'x'.repeat(40) });
    expect(res.statusCode).toBe(401);
    expect(res.json().error).toBe('app_check_invalid');
  });

  it('400 sin id de instalación', async () => {
    await start();
    const res = await post(sampleRequest(), { 'x-firebase-appcheck': authHeaders['x-firebase-appcheck'] });
    expect(res.statusCode).toBe(400);
    expect(res.json().error).toBe('invalid_request');
  });

  it('400 con campos desconocidos (no se eliminan en silencio)', async () => {
    await start();
    const res = await post({ ...sampleRequest(), apiKey: 'algo' });
    expect(res.statusCode).toBe(400);
  });

  it('400 con una pregunta de más de 300 caracteres', async () => {
    await start();
    const res = await post(sampleRequest({ question: 'a'.repeat(301) }));
    expect(res.statusCode).toBe(400);
  });

  it('400 con valores fuera de rango', async () => {
    await start();
    const req = sampleRequest();
    const res = await post({ ...req, context: { ...req.context, age: 7 } });
    expect(res.statusCode).toBe(400);
  });

  it('413 si el cuerpo es demasiado grande', async () => {
    await start();
    const req = sampleRequest();
    const res = await post({ ...req, context: { ...req.context, padding: 'x'.repeat(20_000) } });
    expect(res.statusCode).toBe(413);
  });

  it('429 al pasar la cuota diaria, con Retry-After', async () => {
    await start({ config: { coachDailyLimitPerInstall: 2 } });
    expect((await post(sampleRequest())).statusCode).toBe(200);
    expect((await post(sampleRequest())).statusCode).toBe(200);
    const res = await post(sampleRequest());
    expect(res.statusCode).toBe(429);
    expect(res.json().error).toBe('quota_exceeded');
    expect(Number(res.headers['retry-after'])).toBeGreaterThan(0);
  });

  it('el tope global también aplica', async () => {
    await start({ config: { coachDailyLimitGlobal: 1 } });
    expect((await post(sampleRequest())).statusCode).toBe(200);
    const res = await post(sampleRequest(), { ...authHeaders, 'x-install-id': 'otra-instalacion-1234567' });
    expect(res.statusCode).toBe(429);
  });

  it('503 si Gemini falla, y esa pregunta no cuenta para la cuota', async () => {
    const generator = new FakeGenerator().willReturn(
      new GeminiError('auth', 'clave inválida'),
      'Ahora sí.',
    );
    await start({ generator, config: { coachDailyLimitPerInstall: 1 } });
    const failed = await post(sampleRequest());
    expect(failed.statusCode).toBe(503);
    expect(failed.json().error).toBe('upstream_unavailable');
    expect(failed.body).not.toContain('clave');
    const ok = await post(sampleRequest());
    expect(ok.statusCode).toBe(200);
  });

  it('422 si la seguridad de Gemini bloquea la respuesta', async () => {
    await start({ generator: new FakeGenerator().willReturn(new GeminiError('blocked', 'SAFETY')) });
    const res = await post(sampleRequest());
    expect(res.statusCode).toBe(422);
    expect(res.json().error).toBe('blocked');
  });
});

describe('GET /v1/weather', () => {
  const okFetch = (async () =>
    new Response(JSON.stringify({ main: { temp: 13.46, humidity: 81 } }), { status: 200 })) as unknown as typeof fetch;

  it('devuelve el clima y lo guarda en caché', async () => {
    let calls = 0;
    const counting = (async (...args: Parameters<typeof fetch>) => {
      calls += 1;
      const url = String(args[0]);
      expect(url).toContain('q=Pasto%2CCO');
      return okFetch(...args);
    }) as typeof fetch;
    await start({ weatherFetch: counting });
    const headers = { 'x-firebase-appcheck': authHeaders['x-firebase-appcheck'] };
    const res = await app!.inject({ method: 'GET', url: '/v1/weather?city=Pasto,CO', headers });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toMatchObject({ city: 'Pasto,CO', temperatureC: 13.5, humidityPct: 81 });
    await app!.inject({ method: 'GET', url: '/v1/weather?city=pasto,co', headers });
    expect(calls).toBe(1);
  });

  it('acepta tildes y rechaza caracteres raros', async () => {
    await start({ weatherFetch: okFetch });
    const headers = { 'x-firebase-appcheck': authHeaders['x-firebase-appcheck'] };
    const ok = await app!.inject({ method: 'GET', url: `/v1/weather?city=${encodeURIComponent('Bogotá,CO')}`, headers });
    expect(ok.statusCode).toBe(200);
    const bad = await app!.inject({ method: 'GET', url: `/v1/weather?city=${encodeURIComponent('Pasto&appid=x')}`, headers });
    expect(bad.statusCode).toBe(400);
  });

  it('404 si la ciudad no existe', async () => {
    const notFound = (async () => new Response('{}', { status: 404 })) as unknown as typeof fetch;
    await start({ weatherFetch: notFound });
    const res = await app!.inject({
      method: 'GET',
      url: '/v1/weather?city=Ciudadinventada',
      headers: { 'x-firebase-appcheck': authHeaders['x-firebase-appcheck'] },
    });
    expect(res.statusCode).toBe(404);
  });

  it('401 sin App Check', async () => {
    await start({ weatherFetch: okFetch });
    const res = await app!.inject({ method: 'GET', url: '/v1/weather?city=Pasto' });
    expect(res.statusCode).toBe(401);
  });
});

describe('otras rutas', () => {
  it('404 en JSON para rutas desconocidas', async () => {
    await start();
    const res = await app!.inject({ method: 'GET', url: '/admin' });
    expect(res.statusCode).toBe(404);
    expect(res.json().error).toBe('not_found');
  });
});
