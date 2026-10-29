# HidraSmart — Hidratación inteligente para reloj y celular

App de hidratación deportiva para **Wear OS** y **Android** que avisa al atleta **cuándo y cuánto** tomar agua durante el entrenamiento y usa **inteligencia artificial** para personalizar las recomendaciones.

Hecha en Kotlin con Jetpack Compose (Wear Compose en el reloj, Material 3 en el celular).

[![Android CI](https://github.com/LeiderCadena2110/App_Smartwatch/actions/workflows/android-ci.yml/badge.svg)](https://github.com/LeiderCadena2110/App_Smartwatch/actions/workflows/android-ci.yml)

## Descargar

Los APK del reloj y del celular están en **[Releases](https://github.com/LeiderCadena2110/App_Smartwatch/releases)**:

| Archivo | Dispositivo |
| --- | --- |
| `HidraSmart-phone-vX.Y.Z.apk` | Celular Android 8.0 o superior |
| `HidraSmart-watch-vX.Y.Z.apk` | Reloj Wear OS 3 o superior |

Cómo instalarlos (incluido el reloj): [docs/installation.md](docs/installation.md).

## Funciones

**Reloj (Wear OS)**
- Alertas de hidratación durante el entrenamiento, con sonido y vibración, aunque la app esté cerrada.
- Mililitros por alerta calculados con la guía NATA 2017, ajustados por altitud, calor, humedad y frecuencia cardíaca.
- Arco de cuenta regresiva en el borde de la pantalla hasta la próxima toma.
- **Coach IA**: consejos, preguntas por voz y análisis de cada entrenamiento.
- **Planear**: el modelo de IA del reloj predice cuánta agua llevar.

**Celular (Android)**
- Registro rápido de tomas y anillo de progreso diario.
- Chat con el **coach de IA** (Gemini) y planificador de entrenamientos.
- Historial de 30 días y registro manual de entrenamientos.
- Perfil, prueba de sudoración y clave de Gemini propia.

## Inteligencia artificial

| Componente | Tipo de IA | Funciona sin internet |
| --- | --- | --- |
| `HydrationPredictor` | Aprendizaje automático: regresión ridge entrenada en el dispositivo con tus entrenamientos | Sí |
| `HydrationInsights` + `LocalCoach` | Sistema experto basado en reglas (ritmo del día, racha, cumplimiento) | Sí |
| `AiCoach` + `GeminiClient` | IA generativa (Google Gemini) con el análisis local como contexto | No (usa el coach local si falla) |

Detalles y fórmulas: [docs/ai.md](docs/ai.md).

## Estructura del proyecto

```
App_Smartwatch/
├── core/                         Módulo compartido (biblioteca Android)
│   └── src/main/java/co/edu/ucc/hidrasmart/
│       ├── ai/                   Predictor, insights, coach local, Gemini
│       ├── data/                 HydrationRepository, WeatherService
│       ├── domain/               Models, HydrationCalculator (NATA 2017)
│       └── format/               Formatters (fechas, ml, duraciones)
├── wear/                         App del reloj (Wear OS)
│   └── src/main/java/co/edu/ucc/hidrasmart/
│       ├── alerts/               AlarmScheduler, SessionEngine, AlertActivity...
│       ├── data/                 Sensors (frecuencia cardíaca y barómetro)
│       └── ui/                   components, theme, screens
├── mobile/                       App del celular (Android)
│   └── src/main/java/co/edu/ucc/hidrasmart/mobile/
│       └── ui/                   components, navigation, screens, theme
├── docs/                         Documentación
└── .github/workflows/            Compilación, pruebas y publicación automáticas
```

Más detalle: [docs/architecture.md](docs/architecture.md).

## Compilar en Android Studio

1. Android Studio › **Open** › la carpeta del proyecto › **Trust Project**. La ruta no debe tener tildes, ñ ni espacios.
2. Si pide la JVM de Gradle, elige **JVM 21**.
3. Copia `local.properties.example` como `local.properties` y agrega tus claves (opcionales):
   - `OPENWEATHER_API_KEY`: clima real.
   - `GEMINI_API_KEY`: coach generativo (sin ella funciona la IA local).
4. Arriba elige la configuración **wear** (emulador *Wear OS Large Round*) o **mobile** (emulador de teléfono) y pulsa **Run ▶**.

Pruebas unitarias: clic derecho en `core/src/test` › **Run 'Tests in ...'**, o en la terminal:

```bash
./gradlew :core:testDebugUnitTest
```

## Publicar una versión

```bash
git tag v2.0.0
git push origin v2.0.0
```

GitHub Actions compila, prueba, firma y publica los dos APK en Releases. Ver [docs/release.md](docs/release.md).

## Cálculo de hidratación (guía NATA 2017 + ajuste propio)

- **Tasa de sudoración** = (peso inicial − peso final + líquido tomado − orina) / duración (h)
- **ml por alerta** = tasa × f × 1000 × (intervalo / 60), redondeado a 10 ml
- **Factor f** (1,0 a 1,3, ajuste propio): +0,1 por cada condición más exigente que la de la prueba (más de 500 m de altitud, humedad > 70 %, más de 5 °C, o FC > 85 % de la FC máxima).
- Sin prueba de sudoración la app no da mililitros: solo recuerda "toma según tu sed".

> HidraSmart da orientación general y no reemplaza a un profesional de la salud.
