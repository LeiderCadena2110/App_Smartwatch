import { buildApp } from './app.js';
import { loadConfig } from './config.js';
import { DisabledAppCheckVerifier, FirebaseAppCheckVerifier } from './plugins/appCheck.js';
import { CoachService } from './services/coach.js';
import { GeminiClient } from './services/gemini.js';
import { FirestoreQuotaStore, MemoryQuotaStore, type QuotaStore } from './services/quota.js';
import { WeatherService } from './services/weather.js';

const config = loadConfig();

// Firebase Admin solo se inicia si hace falta (App Check o cuotas en Firestore).
// En Cloud Run usa la cuenta de servicio del servicio: no hay archivos de credenciales.
const needsFirebase = config.appCheckMode === 'enforce' || config.quotaBackend === 'firestore';
let quota: QuotaStore = new MemoryQuotaStore();
if (needsFirebase) {
  const { initializeApp, getApps } = await import('firebase-admin/app');
  if (getApps().length === 0) initializeApp();
  if (config.quotaBackend === 'firestore') {
    const { getFirestore } = await import('firebase-admin/firestore');
    quota = new FirestoreQuotaStore(getFirestore());
  }
}

const app = await buildApp({
  config,
  coach: new CoachService(new GeminiClient(config.geminiApiKey, config.geminiModel), { budgetMs: config.geminiBudgetMs }),
  weather: new WeatherService(config.openWeatherApiKey),
  quota,
  appCheck: config.appCheckMode === 'enforce' ? new FirebaseAppCheckVerifier() : new DisabledAppCheckVerifier(),
});

// Cloud Run envía SIGTERM antes de apagar una instancia: se terminan las peticiones en curso
for (const signal of ['SIGTERM', 'SIGINT'] as const) {
  process.once(signal, () => {
    app.log.info({ signal }, 'Apagando');
    app.close().then(
      () => process.exit(0),
      () => process.exit(1),
    );
  });
}

try {
  await app.listen({ host: '0.0.0.0', port: config.port });
  app.log.info(
    { model: config.geminiModel, appCheck: config.appCheckMode, quota: config.quotaBackend, version: config.version },
    'hidrasmart-api listo',
  );
} catch (err) {
  app.log.error({ err }, 'No se pudo iniciar el servidor');
  process.exit(1);
}
