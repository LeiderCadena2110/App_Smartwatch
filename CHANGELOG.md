# Changelog

## 2.1.0 (sin publicar)

### Seguridad
- La app ya no incluye claves de API: se quitaron `GEMINI_API_KEY`, `OPENWEATHER_API_KEY` y `EMBED_GEMINI_KEY` de Gradle, de `local.properties.example` y de GitHub Actions. La app solo recibe `API_BASE_URL` (servidor `hidrasmart-api`).
- Se quitó la opción de pegar una clave de Gemini en el perfil del celular; la clave guardada por versiones anteriores se borra al abrir la app.
- Mientras se integra el servidor, el coach responde con la IA local y el clima usa los valores manuales.

### App conectada al servidor
- Coach de reloj y celular vuelve a usar Gemini, ahora a través de `hidrasmart-api` (`HidraSmartApi`, `CloudServices`). Si la nube falla, responde la IA local y la pantalla dice por qué: sin internet, límite diario, pregunta bloqueada, app no verificada o servicio caído.
- `CoachContextBuilder` envía datos agregados y recortados a los rangos del servidor.
- Firebase App Check (BoM 35.1.0): Play Integrity en release y proveedor de depuración en debug, iniciado con `FIREBASE_APP_ID`, `FIREBASE_API_KEY` y `FIREBASE_PROJECT_ID` (sin `google-services.json`).
- Id aleatorio de instalación para la cuota diaria.
- Reloj: el clima vuelve a funcionar a través del servidor.
- Versión de depuración: permite HTTP hacia `10.0.2.2` para probar con el servidor local.
- Se eliminaron `GeminiClient`, `PromptBuilder` y `WeatherService` de la app: el prompt y las claves viven en el servidor.
- GitHub Actions actualizadas a versiones con Node 24 (`checkout@v5`, `setup-java@v5`, `setup-gradle@v6`, `upload-artifact@v6`, `setup-node@v5`).

### Servidor `hidrasmart-api` (nuevo, carpeta `server/`)
- Node.js 24 + TypeScript + Fastify, listo para Google Cloud Run.
- `POST /v1/coach`: prompt versionado en el servidor (portado de `PromptBuilder`), peso enviado como rango, pregunta separada y marcada como no confiable, `safetySettings`, 8 s máximo con 1 reintento, corte de circuito, caché de 15 min para el consejo sin pregunta.
- `GET /v1/weather`: clima de OpenWeatherMap con caché de 30 min por ciudad.
- Firebase App Check obligatorio, cuota diaria por instalación y global (Firestore), validación estricta de entradas, sin CORS.
- Logs JSON para Cloud Logging con tokens, latencia y versión del prompt, sin datos personales.
- 45 pruebas automáticas, Dockerfile, flujos `server-ci.yml` y `server-deploy.yml` (Workload Identity Federation) y guía `docs/server.md`.

### Google Play
- Celular con `targetSdk 36`; reloj en `35`.
- `versionCode` separados: reloj `2000000 + appVersionCode`, celular `1000000 + appVersionCode` (`gradle.properties`).
- R8 activado (`isMinifyEnabled` e `isShrinkResources`) con `proguard-rules.pro`; lint bloqueante.
- GitHub Actions genera App Bundles (`.aab`), además de APK, y guarda el `mapping.txt`.
- Reloj: se quitó `USE_EXACT_ALARM` (Play lo reserva a apps de alarma y calendario); se usa `SCHEDULE_EXACT_ALARM`, que concede el usuario.
- Celular: permiso `INTERNET` declarado explícitamente.

### Agregado
- Reloj: botones de **toma rápida** (+150, +250, +500 ml) en el inicio, que registran con un solo toque.
- Reloj: confirmación animada al registrar una toma (círculo con ✓, vibración y anillo que avanza hasta el total del día). Se cierra sola o al tocarla.
- Reloj: íconos propios para Registrar toma (gota), Coach IA (destellos), Entrenar (cronómetro) e Historial.
- Reloj: **Tile** "Hidratación de hoy" con anillo de progreso y botón **+250 ml** que registra sin abrir la app.
- Reloj: **complicación** "Agua de hoy" para la esfera (mini anillo frente a la meta o texto corto).
- Reloj: **actividad en curso** durante el entrenamiento: cronómetro en la esfera y en Recientes para volver a la sesión con un toque.

### Cambiado
- Reloj: la carpeta `wear/.../data` pasa a llamarse `sensors` (solo tiene `Sensors.kt` y ya no comparte paquete con `core/data`).
- Valores internos en inglés: rutas de navegación (`home`, `history`, `profile`...), acciones de las alarmas (`ALARM`, `DRANK`, `SNOOZE`) y constantes de la Tile y de la actividad en curso. Las claves donde se guardan los datos no cambian, para no perder el historial.

### Corregido
- Reloj: el manifiesto apuntaba a `AlertaActivity` y `AlertaReceiver`, que ya no existen tras pasar los nombres a inglés. Las alertas con la app cerrada cerraban la app con un error y los botones "Ya tomé" / "En 5 min" no hacían nada.
- Las fechas y horas usan la zona horaria actual del dispositivo aunque cambie con la app abierta.

## 2.0.0

### Agregado
- **App para celular** (`mobile`) con Material 3: inicio, coach de IA, historial y perfil.
- **IA en el dispositivo:** `HydrationPredictor` (regresión ridge) predice cuánta agua llevar según tus entrenamientos.
- **Sistema experto:** `HydrationInsights` analiza el ritmo diario, la racha y el cumplimiento, con nivel de riesgo.
- **Coach generativo** con Google Gemini y respuesta local sin internet.
- Reloj: pantallas **Coach IA** (con preguntas por voz) y **Planear**, análisis de IA al terminar cada entrenamiento.
- Reloj: arco de cuenta regresiva en el borde y anillo de meta que se pone verde al cumplirla.
- GitHub Actions: pruebas y APK en cada push, y publicación de APK firmados en Releases con cada etiqueta.
- Documentación en `docs/`.

### Cambiado
- Carpetas, paquetes, archivos e identificadores en inglés (`dominio` → `domain`, `datos` → `data`, `alertas` → `alerts`...).
- Código compartido movido al módulo `core`.

## 1.2
- Alerta sobre la esfera con la app cerrada y diagnóstico de permisos.

## 1.0
- App Wear OS con alertas de hidratación.
