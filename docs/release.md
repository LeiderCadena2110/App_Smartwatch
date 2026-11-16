# Publicar una versión (despliegue)

El repositorio tiene dos flujos de GitHub Actions en `.github/workflows/`:

| Flujo | Cuándo corre | Qué hace |
| --- | --- | --- |
| `android-ci.yml` | En cada push a `main` y en cada pull request | Pruebas de `core`, lint, APK de depuración y App Bundles de release (verifica R8) |
| `release.yml` | Al subir una etiqueta `vX.Y.Z` | Pruebas, lint, App Bundles y APK firmados, `mapping.txt` de R8 y publicación en **Releases** |

## Publicar

1. En `gradle.properties` sube `appVersionCode` en 1 y cambia `appVersionName`. Cada app calcula su `versionCode` a partir de ahí: reloj `2000000 + appVersionCode`, celular `1000000 + appVersionCode`. Google Play exige un `versionCode` distinto para cada paquete del mismo listado.
2. Haz commit y push.
3. Crea la etiqueta:

   ```bash
   git tag v2.1.0
   git push origin v2.1.0
   ```

4. En unos minutos aparece el release con:
   - `HidraSmart-phone-v2.1.0.aab` y `HidraSmart-watch-v2.1.0.aab`: para subir a Google Play Console.
   - `HidraSmart-phone-v2.1.0.apk` y `HidraSmart-watch-v2.1.0.apk`: para instalar a mano.
   - En el run de Actions, el artefacto `r8-mapping-v2.1.0` con los `mapping.txt` para leer errores de la versión ofuscada.

## Firma con tu propio keystore (recomendado)

Sin keystore, los APK se firman con una clave de depuración distinta en cada compilación. Por eso una versión nueva no se puede instalar encima de la anterior. Para evitarlo:

1. Crea el keystore una sola vez (guárdalo bien y **no lo subas al repositorio**):

   ```bash
   keytool -genkeypair -v -keystore hidrasmart-release.jks -alias hidrasmart \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Conviértelo a base64:

   ```bash
   base64 -w 0 hidrasmart-release.jks > keystore.txt     # Linux / Git Bash
   ```

3. En GitHub: **Settings › Secrets and variables › Actions › New repository secret**:

   | Secreto | Valor |
   | --- | --- |
   | `KEYSTORE_BASE64` | El contenido de `keystore.txt` |
   | `KEYSTORE_PASSWORD` | La contraseña del keystore |
   | `KEY_ALIAS` | `hidrasmart` |
   | `KEY_PASSWORD` | La contraseña de la clave |

## Sin claves de API en la app

La app no incluye claves de Gemini ni de OpenWeatherMap: una clave dentro de un APK o App Bundle público se puede extraer. Las claves viven solo en el servidor `hidrasmart-api` (Google Cloud Run + Secret Manager).

La app solo necesita la URL del servidor. Créala como **variable** (no secreto) de repositorio: **Settings › Secrets and variables › Actions › Variables › New repository variable** › `API_BASE_URL`. Sin ella, el coach usa la IA local.

Si alguna versión anterior se publicó con una clave de Gemini dentro, **revócala** en Google AI Studio y borra los secretos `GEMINI_API_KEY` y `OPENWEATHER_API_KEY` y la variable `EMBED_GEMINI_KEY` del repositorio.
