# Arquitectura

## Módulos

```
:core    Biblioteca Android compartida (sin interfaz)
:wear    App del reloj  ──depende de──▶ :core
:mobile  App del celular ──depende de──▶ :core
```

| Módulo | Paquetes | Responsabilidad |
| --- | --- | --- |
| `core` | `domain` | Modelos (`Profile`, `SweatTest`, `SavedSession`...) y `HydrationCalculator` (Kotlin puro, con pruebas) |
| | `data` | `HydrationRepository` (SharedPreferences + JSON) |
| | `ai` | `HydrationPredictor`, `RidgeRegression`, `HydrationInsights`, `LocalCoach`, `AiCoach`, `CloudCoach`, `CoachContextBuilder` |
| | `remote` | `HidraSmartApi` (cliente del servidor), `CloudServices`, `FirebaseAppCheckProvider` (App Check; proveedor de depuración en `src/debug`, Play Integrity en `src/release`), `InstallId` |
| | `format` | `Formatters`: fechas, miles, decimales y duraciones en español |
| `wear` | raíz | `MainActivity`, `AppViewModel` |
| | `alerts` | `SessionEngine` (reglas del entrenamiento), `AlarmScheduler`, `AlertReceiver`, `AlertActivity`, `AlertDiagnostics` |
| | `surfaces` | `HydrationTileService` (Tile), `HydrationComplicationService` (complicación), `WorkoutOngoing` (actividad en curso), `WatchSurfaces` (las actualiza al registrar una toma), `DailyProgress` |
| | `sensors` | `Sensors`: frecuencia cardíaca y barómetro |
| | `ui` | `components`, `navigation` (`HidraSmartApp`, `Routes`), `screens` (Home, Intake, Workout, Coach, History, Profile), `theme` |
| `mobile` | raíz | `MainActivity`, `MobileViewModel` |
| | `ui` | `components`, `navigation` (barra inferior), `screens` (Home, Coach, History, Profile), `theme` |
| `server` | `routes`, `services`, `plugins` | Servidor `hidrasmart-api` en Cloud Run: coach con Gemini (prompt versionado, cuotas, reintento) y clima. Guarda las claves; ver [server.md](server.md) |

## Flujo de una alerta en el reloj (con la app cerrada)

1. `SessionEngine.start()` guarda la sesión y `AlarmScheduler.schedule()` programa una alarma exacta.
2. A la hora indicada, `AlertReceiver` recibe la alarma y llama a `SessionEngine.fireAlert()`.
3. `HydrationCalculator` calcula los ml con la tasa de sudoración y el factor de condiciones.
4. `AlarmScheduler.showAlert()` muestra la notificación con los botones **Ya tomé** y **En 5 min**, y `AlertActivity` aparece sobre la esfera.

## Convenciones

- Carpetas, paquetes, archivos e identificadores en **inglés**.
- Textos que ve el usuario en **español**.
- Un archivo por responsabilidad. Las pantallas se agrupan por flujo (`WorkoutScreens.kt`, `ProfileScreens.kt`...).
