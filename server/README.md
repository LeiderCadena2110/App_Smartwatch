# hidrasmart-api

Servidor intermediario de HidraSmart en Google Cloud Run: guarda las claves de Gemini y OpenWeatherMap y responde a la app con el coach de IA y el clima. La app nunca lleva claves.

```bash
npm ci
cp .env.example .env   # solo para desarrollo local
npm run dev            # http://localhost:8080
npm test               # pruebas
npm run build          # compila a dist/
```

| Carpeta | Contenido |
| --- | --- |
| `src/routes/` | `coach.ts` (`POST /v1/coach`), `weather.ts` (`GET /v1/weather`), `health.ts` (`GET /healthz`) |
| `src/services/` | `gemini.ts` (cliente REST), `coach.ts` (reintento, corte de circuito, caché), `prompt.ts` (prompt versionado), `quota.ts` (cuotas diarias), `weather.ts` |
| `src/plugins/` | `appCheck.ts` (Firebase App Check), `logging.ts` (logs para Cloud Logging) |
| `src/schemas.ts` | Contrato del API y validación |
| `test/` | Pruebas con Gemini, App Check y cuotas simulados |

Contrato, seguridad y despliegue paso a paso: [docs/server.md](../docs/server.md).
