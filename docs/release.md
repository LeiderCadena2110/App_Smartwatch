# Publicar una versión (despliegue)

El repositorio tiene dos flujos de GitHub Actions en `.github/workflows/`:

| Flujo | Cuándo corre | Qué hace |
| --- | --- | --- |
| `android-ci.yml` | En cada push a `main` y en cada pull request | Pruebas de `core` y APK de depuración como artefactos |
| `release.yml` | Al subir una etiqueta `vX.Y.Z` | Pruebas, APK firmados y publicación en **Releases** |

## Publicar

1. Sube `versionCode` y `versionName` en `wear/build.gradle.kts` y `mobile/build.gradle.kts`.
2. Haz commit y push.
3. Crea la etiqueta:

   ```bash
   git tag v2.0.0
   git push origin v2.0.0
   ```

4. En unos minutos aparece el release con `HidraSmart-phone-v2.0.0.apk` y `HidraSmart-watch-v2.0.0.apk`.

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

## Claves de API en los APK publicados

- `OPENWEATHER_API_KEY` (secreto opcional): clima real.
- `GEMINI_API_KEY` (secreto) **solo se incluye** si creas la variable de repositorio `EMBED_GEMINI_KEY` con el valor `true`.

  ⚠ Una clave dentro de un APK público se puede extraer. Si la incluyes, usa una clave del plan gratuito y fíjale un límite de uso en Google AI Studio. Lo más seguro es no incluirla: en el celular cada usuario puede pegar su propia clave en **Perfil**, y sin clave el coach usa la IA local.
