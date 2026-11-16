# Servidor `hidrasmart-api`

Servicio pequeño en **Google Cloud Run** que guarda las claves de Gemini y OpenWeatherMap y habla con esos servicios en nombre de la app. La app solo conoce la URL del servidor (`API_BASE_URL`); nunca lleva claves.

- Código: [`server/`](../server) — Node.js 24 + TypeScript + Fastify.
- Pruebas: `server/test/` (45 pruebas con Gemini, App Check y cuotas simulados).
- Flujos: `server-ci.yml` (pruebas en cada push) y `server-deploy.yml` (despliegue con la etiqueta `api-vX.Y.Z`).

## Qué hace

| Ruta | Para qué | Protecciones |
| --- | --- | --- |
| `POST /v1/coach` | Recibe el contexto agregado del atleta y la pregunta; arma el prompt y llama a Gemini | App Check, id de instalación, esquema estricto, pregunta ≤ 300 caracteres, cuota diaria, 8 s máximo con 1 reintento, corte de circuito |
| `GET /v1/weather?city=` | Temperatura y humedad actuales | App Check, ciudad validada, caché de 30 min por ciudad |
| `GET /healthz` | Comprobar que el servicio está vivo | Sin datos |

### Respuestas de `POST /v1/coach`

| Código | `error` | Qué hace la app |
| --- | --- | --- |
| 200 | — | Muestra `text` (fuente Gemini) |
| 400 | `invalid_request` | IA local |
| 401 | `app_check_missing` / `app_check_invalid` | IA local |
| 413 | `payload_too_large` | IA local |
| 422 | `blocked` | Muestra que no puede responder eso |
| 429 | `quota_exceeded` (+ `Retry-After` en segundos) | IA local hasta el día siguiente |
| 503 | `upstream_unavailable` | IA local |

Encabezados que envía la app: `X-Firebase-AppCheck` (token de App Check) y `X-Install-Id` (UUID aleatorio generado al instalar).

Ejemplo de cuerpo:

```json
{
  "question": "¿Cuánta agua llevo al entreno?",
  "compact": true,
  "context": {
    "localTime": "14:30", "age": 24, "weightKg": 72.4, "dailyGoalMl": 2500,
    "todayMl": 900, "expectedByNowMl": 1400, "weeklyAverageMl": 2100, "goalStreakDays": 3,
    "sweatRateLh": 0.85, "conditionsFactor": 1.1, "inWorkout": false, "adherencePct": 72,
    "recentSessions": [{ "type": "Correr", "minutes": 45, "avgHeartRate": 152, "drankMl": 400, "recommendedMl": 600 }],
    "plan": { "totalMl": 650, "perHourMl": 650, "source": "Modelo IA personal" },
    "insights": [{ "level": "MEDIUM", "title": "Vas atrasado", "message": "Llevas 500 ml menos de lo esperado." }]
  }
}
```

### Privacidad y seguridad

- **Datos mínimos:** la app envía datos agregados, no el historial. El peso llega a Gemini como rango de 5 kg.
- **Prompt en el servidor:** versionado (`PROMPT_VERSION` en `server/src/services/prompt.ts`); cada respuesta y cada log lo incluyen. Cambiarlo no exige publicar la app.
- **Inyección de prompt:** la pregunta va en una parte aparte, delimitada y marcada como texto no confiable; el prompt de sistema limita el tema y Gemini filtra con `safetySettings`.
- **Logs sin datos personales:** se registran evento, modelo, versión del prompt, tokens, latencia y código de error. Nunca el cuerpo, la pregunta, el token de App Check ni el id de instalación.
- **Sin CORS:** lo llaman apps nativas, no páginas web.
- **Producción bloqueada sin protecciones:** con `NODE_ENV=production` el servidor no arranca si App Check está apagado, si las cuotas están en memoria o si falta la clave de Gemini.

## Desarrollo local

Requisitos: Node.js 22 o superior.

```bash
cd server
npm ci
cp .env.example .env        # en Windows: copy .env.example .env
# Pon tu clave de Gemini en .env (APP_CHECK_MODE=off y QUOTA_BACKEND=memory solo sirven en local)
npm run dev                  # http://localhost:8080
npm test                     # 45 pruebas
```

Probar:

```bash
curl http://localhost:8080/healthz
curl -X POST http://localhost:8080/v1/coach \
  -H "content-type: application/json" \
  -H "x-install-id: 6f1c2b8e-1d2a-4c7e-9b1f-0a2b3c4d5e6f" \
  -d @ejemplo.json
```

## Variables de entorno

| Variable | Por defecto | Para qué |
| --- | --- | --- |
| `GEMINI_API_KEY` | — | Clave de Gemini (Secret Manager en producción) |
| `GEMINI_MODEL` | `gemini-3.5-flash-lite` | Modelo |
| `GEMINI_BUDGET_MS` | `8000` | Tiempo máximo por pregunta, reintento incluido |
| `OPENWEATHER_API_KEY` | — | Clave del clima (Secret Manager) |
| `APP_CHECK_MODE` | `enforce` | `off` solo en local |
| `QUOTA_BACKEND` | `firestore` | `memory` solo en local |
| `COACH_DAILY_LIMIT` | `20` | Preguntas por instalación y día |
| `COACH_GLOBAL_DAILY_LIMIT` | `2000` | Tope diario de todo el servicio |
| `QUOTA_TIME_ZONE` | `America/Bogota` | Las cuotas se reinician a medianoche de esta zona |
| `LOG_LEVEL` | `info` | Nivel de logs |

## Despliegue en Cloud Run (primera vez)

Todo se hace en **Cloud Shell**, la terminal del navegador de Google Cloud: así no instalas nada en Windows. Entra a [console.cloud.google.com](https://console.cloud.google.com) y pulsa el ícono **>_** (Activar Cloud Shell) arriba a la derecha.

### 1. Variables de la sesión

Cambia `PROJECT_ID` por un nombre único tuyo (solo minúsculas, números y guiones):

```bash
export PROJECT_ID=hidrasmart-prod-leider
export REGION=us-east1
export RUNTIME_SA=hidrasmart-api@${PROJECT_ID}.iam.gserviceaccount.com
```

Si Cloud Shell se reinicia, vuelve a ejecutar este bloque.

### 2. Proyecto, facturación y presupuesto

```bash
gcloud projects create $PROJECT_ID --name="HidraSmart"
gcloud config set project $PROJECT_ID
export PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')
```

Luego en la consola:

1. **Facturación › Vincular una cuenta de facturación** al proyecto. Sin facturación, Google puede usar los datos enviados a Gemini para mejorar sus productos; con facturación no.
2. **Facturación › Presupuestos y alertas › Crear presupuesto** de, por ejemplo, USD 10 al mes, con alertas al 50 %, 90 % y 100 %.

### 3. Activar las APIs

```bash
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com \
  secretmanager.googleapis.com firestore.googleapis.com iamcredentials.googleapis.com
```

### 4. Guardar las claves en Secret Manager

1. Crea una clave de Gemini **dentro de este proyecto** en [Google AI Studio](https://aistudio.google.com/apikey) (elige el proyecto `hidrasmart-prod-…` al crearla).
2. Revoca cualquier clave de Gemini que haya estado alguna vez dentro de la app.
3. Guárdalas (el `read -s` no muestra lo que pegas):

```bash
read -s -p "Clave de Gemini: " K && printf '%s' "$K" | gcloud secrets create gemini-api-key --data-file=- ; unset K; echo
read -s -p "Clave de OpenWeatherMap: " K && printf '%s' "$K" | gcloud secrets create openweather-api-key --data-file=- ; unset K; echo
```

### 5. Cuenta de servicio del servidor

Solo puede leer esos dos secretos y usar Firestore:

```bash
gcloud iam service-accounts create hidrasmart-api --display-name="hidrasmart-api (Cloud Run)"
for s in gemini-api-key openweather-api-key; do
  gcloud secrets add-iam-policy-binding $s --member=serviceAccount:$RUNTIME_SA --role=roles/secretmanager.secretAccessor
done
gcloud projects add-iam-policy-binding $PROJECT_ID --member=serviceAccount:$RUNTIME_SA --role=roles/datastore.user
```

### 6. Firestore para las cuotas

```bash
gcloud firestore databases create --location=$REGION --type=firestore-native
# Borra solos los contadores viejos (campo expireAt)
gcloud firestore fields ttls update expireAt --collection-group=coachQuotas --enable-ttl
```

### 7. Permiso para compilar

Cloud Build compila el código con la cuenta de servicio predeterminada de Compute Engine:

```bash
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member=serviceAccount:${PROJECT_NUMBER}-compute@developer.gserviceaccount.com \
  --role=roles/run.builder
```

### 8. Primer despliegue

```bash
git clone https://github.com/LeiderCadena2110/App_Smartwatch.git
cd App_Smartwatch
gcloud run deploy hidrasmart-api --source server --region $REGION \
  --service-account $RUNTIME_SA \
  --set-secrets GEMINI_API_KEY=gemini-api-key:latest,OPENWEATHER_API_KEY=openweather-api-key:latest \
  --set-env-vars GOOGLE_CLOUD_PROJECT=$PROJECT_ID \
  --min-instances 0 --max-instances 5 --concurrency 40 --timeout 15 --memory 512Mi \
  --allow-unauthenticated
```

- `--allow-unauthenticated` es correcto: la app no tiene cuentas de Google Cloud. La autenticación la hace App Check dentro del servicio.
- `--max-instances 5` pone un techo al gasto si alguien ataca el servicio.

### 9. Probar el despliegue

```bash
URL=$(gcloud run services describe hidrasmart-api --region $REGION --format='value(status.url)')
echo $URL
curl $URL/healthz                                            # {"status":"ok",...}
curl -s -o /dev/null -w '%{http_code}\n' -X POST $URL/v1/coach  # 401: sin App Check no entra nadie
```

Guarda esa URL: es la `API_BASE_URL` de la app (variable de repositorio en GitHub y `local.properties`).

### 10. Firebase App Check

1. En [console.firebase.google.com](https://console.firebase.google.com) pulsa **Agregar proyecto** y elige el proyecto existente `hidrasmart-prod-…`.
2. **Configuración del proyecto › Agregar app › Android** con el paquete `co.edu.ucc.hidrasmart`. Agrega la huella **SHA-256** de la clave de firma (Play Console › Integridad de la app; mientras no estés en Play, la de tu keystore de release).
3. **App Check › Apps › Play Integrity › Registrar**.
4. Para el emulador se usa el **proveedor de depuración**: la app imprime un token de depuración en Logcat que se registra en **App Check › Administrar tokens de depuración**. Nunca lo actives en la versión de release.

La integración de App Check en la app (Kotlin) es el siguiente bloque.

## Despliegues automáticos desde GitHub

Tras el primer despliegue, cada etiqueta `api-vX.Y.Z` publica una versión nueva. GitHub se autentica con **Workload Identity Federation**: no se guarda ninguna llave de Google Cloud en GitHub.

```bash
# Cuenta de servicio que usa GitHub para desplegar
gcloud iam service-accounts create github-deployer --display-name="GitHub Actions deployer"
export DEPLOY_SA=github-deployer@${PROJECT_ID}.iam.gserviceaccount.com
for role in roles/run.sourceDeveloper roles/serviceusage.serviceUsageConsumer roles/logging.viewer; do
  gcloud projects add-iam-policy-binding $PROJECT_ID --member=serviceAccount:$DEPLOY_SA --role=$role
done
gcloud iam service-accounts add-iam-policy-binding $RUNTIME_SA \
  --member=serviceAccount:$DEPLOY_SA --role=roles/iam.serviceAccountUser

# Confianza solo en tu repositorio
gcloud iam workload-identity-pools create github --location=global --display-name="GitHub"
gcloud iam workload-identity-pools providers create-oidc app-smartwatch \
  --location=global --workload-identity-pool=github \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository=='LeiderCadena2110/App_Smartwatch'"
gcloud iam service-accounts add-iam-policy-binding $DEPLOY_SA --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github/attribute.repository/LeiderCadena2110/App_Smartwatch"

# Valores para GitHub
echo "GCP_PROJECT_ID=$PROJECT_ID"
echo "GCP_REGION=$REGION"
echo "GCP_DEPLOY_SA=$DEPLOY_SA"
echo "GCP_WIF_PROVIDER=projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github/providers/app-smartwatch"
```

En GitHub: **Settings › Secrets and variables › Actions › Variables** › crea esas cuatro variables. Luego:

```bash
git tag api-v1.0.0
git push origin api-v1.0.0
```

## Operación

- **Logs:** Cloud Run › `hidrasmart-api` › Registros. Filtro útil: `jsonPayload.event="coach"`. Cada línea trae `outcome`, `model`, `promptVersion`, `inputTokens`, `outputTokens` y `latencyMs`.
- **Alertas recomendadas** (Cloud Monitoring): tasa de `outcome="error"` mayor al 5 % en 10 min, y el presupuesto de facturación.
- **Rotar la clave de Gemini:** crea una versión nueva del secreto (`gcloud secrets versions add gemini-api-key --data-file=-`) y despliega de nuevo; luego revoca la anterior en AI Studio.
- **Cambiar el prompt:** edita `server/src/services/prompt.ts`, sube `PROMPT_VERSION`, corre `npm test` y publica una etiqueta `api-v*`.
