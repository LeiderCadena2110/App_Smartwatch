import type { WeatherResponse } from '../schemas.js';
import { TtlCache } from './coach.js';

export class WeatherError extends Error {
  constructor(
    readonly code: 'not_configured' | 'not_found' | 'unavailable',
    message: string,
  ) {
    super(message);
    this.name = 'WeatherError';
  }
}

/**
 * Clima actual de OpenWeatherMap (plan gratuito). La clave queda en el servidor y cada ciudad
 * se guarda 30 minutos: muchos usuarios de la misma ciudad cuestan una sola consulta.
 */
export class WeatherService {
  private readonly cache: TtlCache<WeatherResponse>;

  constructor(
    private readonly apiKey: string,
    private readonly fetchImpl: typeof fetch = fetch,
    now: () => number = Date.now,
  ) {
    this.cache = new TtlCache(30 * 60_000, 500, now);
  }

  async current(city: string): Promise<WeatherResponse & { cached: boolean }> {
    if (!this.apiKey) throw new WeatherError('not_configured', 'El clima no está configurado en el servidor');
    const key = city.trim().toLowerCase();
    const hit = this.cache.get(key);
    if (hit) return { ...hit, cached: true };

    const url = new URL('https://api.openweathermap.org/data/2.5/weather');
    url.searchParams.set('q', city.trim());
    url.searchParams.set('units', 'metric');
    url.searchParams.set('appid', this.apiKey);

    let response: Response;
    try {
      response = await this.fetchImpl(url, { signal: AbortSignal.timeout(5000) });
    } catch (err) {
      throw new WeatherError('unavailable', `Error de red con el servicio de clima: ${(err as Error).name}`);
    }
    if (response.status === 404) throw new WeatherError('not_found', 'Ciudad no encontrada');
    if (!response.ok) throw new WeatherError('unavailable', `El servicio de clima respondió ${response.status}`);

    const json = (await response.json().catch(() => null)) as { main?: { temp?: unknown; humidity?: unknown } } | null;
    const temp = json?.main?.temp;
    const humidity = json?.main?.humidity;
    if (typeof temp !== 'number' || typeof humidity !== 'number') {
      throw new WeatherError('unavailable', 'Respuesta de clima incompleta');
    }

    const value: WeatherResponse = {
      city: city.trim(),
      temperatureC: Math.round(temp * 10) / 10,
      humidityPct: Math.round(humidity),
      fetchedAt: new Date().toISOString(),
    };
    this.cache.set(key, value);
    return { ...value, cached: false };
  }
}
