import { describe, expect, it } from 'vitest';
import { GeminiClient, GeminiError, parseResponse } from '../src/services/gemini.js';

const request = { system: 's', parts: ['ctx', 'q'], maxOutputTokens: 100, temperature: 0.5, timeoutMs: 2000 };

function fakeFetch(status: number, body: unknown, capture?: (url: string, init: RequestInit) => void): typeof fetch {
  return (async (url: string | URL, init?: RequestInit) => {
    capture?.(String(url), init ?? {});
    return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
  }) as typeof fetch;
}

describe('parseResponse', () => {
  it('une las partes de texto y omite el "pensamiento"', () => {
    const r = parseResponse(
      {
        candidates: [{ content: { parts: [{ text: 'pensando…', thought: true }, { text: 'Toma ' }, { text: '250 ml.' }] } }],
        usageMetadata: { promptTokenCount: 300, candidatesTokenCount: 12 },
      },
      'm',
    );
    expect(r).toEqual({ text: 'Toma 250 ml.', model: 'm', inputTokens: 300, outputTokens: 12 });
  });

  it('detecta bloqueos de seguridad', () => {
    expect(() => parseResponse({ promptFeedback: { blockReason: 'SAFETY' } }, 'm')).toThrow(GeminiError);
    try {
      parseResponse({ candidates: [{ finishReason: 'SAFETY' }] }, 'm');
    } catch (e) {
      expect((e as GeminiError).code).toBe('blocked');
    }
  });

  it('rechaza respuestas vacías', () => {
    expect(() => parseResponse({ candidates: [{ content: { parts: [] } }] }, 'm')).toThrow('Respuesta vacía');
  });
});

describe('GeminiClient', () => {
  it('envía la clave en el encabezado, no en la URL', async () => {
    let seenUrl = '';
    let seenInit: RequestInit = {};
    const client = new GeminiClient(
      'clave-secreta',
      'gemini-x',
      fakeFetch(200, { candidates: [{ content: { parts: [{ text: 'ok' }] } }] }, (u, i) => {
        seenUrl = u;
        seenInit = i;
      }),
    );
    await client.generate(request);
    expect(seenUrl).toBe('https://generativelanguage.googleapis.com/v1beta/models/gemini-x:generateContent');
    expect(seenUrl).not.toContain('clave-secreta');
    expect((seenInit.headers as Record<string, string>)['x-goog-api-key']).toBe('clave-secreta');
    const body = JSON.parse(String(seenInit.body));
    expect(body.contents[0].parts).toEqual([{ text: 'ctx' }, { text: 'q' }]);
    expect(body.safetySettings).toHaveLength(4);
  });

  it.each([
    [429, 'rate_limited', true],
    [503, 'unavailable', true],
    [403, 'auth', false],
    [400, 'bad_response', false],
  ])('HTTP %i → %s (reintentable: %s)', async (status, code, retryable) => {
    const client = new GeminiClient('k', 'm', fakeFetch(status, { error: { message: 'x' } }));
    const err = await client.generate(request).catch((e) => e);
    expect(err).toBeInstanceOf(GeminiError);
    expect(err.code).toBe(code);
    expect(err.retryable).toBe(retryable);
  });

  it('corta por tiempo', async () => {
    const slow = ((_url: string, init?: RequestInit) =>
      new Promise((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(init.signal?.reason));
      })) as typeof fetch;
    const client = new GeminiClient('k', 'm', slow);
    const err = await client.generate({ ...request, timeoutMs: 50 }).catch((e) => e);
    expect(err.code).toBe('timeout');
  });

  it('sin clave no llama a la red', async () => {
    const client = new GeminiClient('', 'm', () => {
      throw new Error('no debería llamar');
    });
    const err = await client.generate(request).catch((e) => e);
    expect(err.code).toBe('auth');
  });
});
