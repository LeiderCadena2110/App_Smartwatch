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
| | `data` | `HydrationRepository` (SharedPreferences + JSON) y `WeatherService` (OpenWeatherMap) |
| | `ai` | `HydrationPredictor`, `RidgeRegression`, `HydrationInsights`, `LocalCoach`, `PromptBuilder`, `GeminiClient`, `AiCoach` |
| | `format` | `Formatters`: fechas, miles, decimales y duraciones en español |
| `wear` | raíz | `MainActivity`, `HidraSmartApp` (navegación), `AppViewModel` |
| | `alerts` | `SessionEngine` (reglas del entrenamiento), `AlarmScheduler`, `AlertReceiver`, `AlertActivity`, `AlertDiagnostics` |
| | `data` | `Sensors`: frecuencia cardíaca y barómetro |
| | `ui` | `Components`, `theme`, `screens` (Home, Intake, Workout, Coach, History, Profile, Routes) |
| `mobile` | raíz | `MainActivity`, `MobileViewModel` |
| | `ui` | `components`, `navigation` (barra inferior), `screens` (Home, Coach, History, Profile), `theme` |

## Flujo de una alerta en el reloj (con la app cerrada)

1. `SessionEngine.start()` guarda la sesión y `AlarmScheduler.schedule()` programa una alarma exacta.
2. A la hora indicada, `AlertReceiver` recibe la alarma y llama a `SessionEngine.fireAlert()`.
3. `HydrationCalculator` calcula los ml con la tasa de sudoración y el factor de condiciones.
4. `AlarmScheduler.showAlert()` muestra la notificación con los botones **Ya tomé** y **En 5 min**, y `AlertActivity` aparece sobre la esfera.

## Convenciones

- Carpetas, paquetes, archivos e identificadores en **inglés**.
- Textos que ve el usuario en **español**.
- Un archivo por responsabilidad. Las pantallas se agrupan por flujo (`WorkoutScreens.kt`, `ProfileScreens.kt`...).
