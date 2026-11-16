import { describe, expect, it } from 'vitest';
import { CircuitOpenError, CoachService } from '../src/services/coach.js';
import { GeminiError } from '../src/services/gemini.js';
import { FakeGenerator, INSTALL_ID, sampleRequest } from './helpers.js';

function service(generator: FakeGenerator, now: () => number = Date.now) {
  return new CoachService(generator, { budgetMs: 8000, sleep: async () => {}, now });
}

describe('CoachService', () => {
  it('reintenta una vez ante un error pasajero', async () => {
    const gen = new FakeGenerator().willReturn(new GeminiError('unavailable', '503'), 'Toma agua.');
    const answer = await service(gen).answer(INSTALL_ID, sampleRequest());
    expect(answer.text).toBe('Toma agua.');
    expect(answer.attempts).toBe(2);
    expect(gen.calls).toHaveLength(2);
  });

  it('no reintenta un bloqueo de seguridad', async () => {
    const gen = new FakeGenerator().willReturn(new GeminiError('blocked', 'SAFETY'));
    await expect(service(gen).answer(INSTALL_ID, sampleRequest())).rejects.toMatchObject({ code: 'blocked' });
    expect(gen.calls).toHaveLength(1);
  });

  it('no reintenta un error de clave', async () => {
    const gen = new FakeGenerator().willReturn(new GeminiError('auth', '403'));
    await expect(service(gen).answer(INSTALL_ID, sampleRequest())).rejects.toMatchObject({ code: 'auth' });
    expect(gen.calls).toHaveLength(1);
  });

  it('abre el circuito tras 3 fallos seguidos y lo cierra a los 60 s', async () => {
    let clock = 1_000_000;
    const gen = new FakeGenerator().willReturn(
      new GeminiError('auth', '1'),
      new GeminiError('auth', '2'),
      new GeminiError('auth', '3'),
      'De vuelta.',
    );
    const coach = service(gen, () => clock);
    for (let i = 0; i < 3; i++) await coach.answer(INSTALL_ID, sampleRequest()).catch(() => undefined);
    await expect(coach.answer(INSTALL_ID, sampleRequest())).rejects.toBeInstanceOf(CircuitOpenError);
    expect(gen.calls).toHaveLength(3);
    clock += 60_001;
    await expect(coach.answer(INSTALL_ID, sampleRequest())).resolves.toMatchObject({ text: 'De vuelta.' });
  });

  it('guarda 15 min el consejo sin pregunta, no las preguntas', async () => {
    let clock = 5_000_000;
    const gen = new FakeGenerator().willReturn('Consejo 1', 'Consejo 2', 'Respuesta 1', 'Respuesta 2', 'Consejo 3');
    const coach = service(gen, () => clock);
    const advice = sampleRequest({ question: null });

    expect((await coach.answer(INSTALL_ID, advice)).text).toBe('Consejo 1');
    const again = await coach.answer(INSTALL_ID, { ...advice, context: { ...advice.context, localTime: '14:31' } });
    expect(again).toMatchObject({ text: 'Consejo 1', cached: true });

    expect((await coach.answer(INSTALL_ID, sampleRequest())).text).toBe('Consejo 2');
    expect((await coach.answer(INSTALL_ID, sampleRequest())).text).toBe('Respuesta 1');

    clock += 15 * 60_000 + 1;
    expect((await coach.answer(INSTALL_ID, advice)).text).toBe('Respuesta 2');
  });

  it('en el reloj limita tokens y recorta la respuesta', async () => {
    const gen = new FakeGenerator().willReturn(`**Toma** ${'agua '.repeat(80)}`);
    const answer = await service(gen).answer(INSTALL_ID, sampleRequest({ compact: true }));
    expect(gen.calls[0]?.maxOutputTokens).toBe(160);
    expect(answer.text.length).toBeLessThanOrEqual(241);
    expect(answer.text).not.toContain('*');
  });
});
