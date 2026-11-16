# Inteligencia artificial en HidraSmart

La IA vive en el módulo compartido `core/src/main/java/co/edu/ucc/hidrasmart/ai/`, así que el reloj y el celular usan exactamente la misma.

## 1. Modelo predictivo en el dispositivo — `HydrationPredictor`

**Pregunta que responde:** ¿cuánta agua debo llevar a un entrenamiento de X minutos?

**Modelo:** agua = horas × (a + b · intensidad), donde intensidad = FC media / FC máxima (220 − edad).

- Variables por sesión: `[horas, horas × intensidad]`, sin intercepto (0 minutos = 0 ml).
- Valor a predecir: los ml recomendados de esa sesión (tasa de sudoración × factor de condiciones) o, si no hubo prueba de sudoración, lo que el atleta realmente tomó.
- Entrenamiento: **regresión ridge** (`RidgeRegression`), que resuelve (XᵀX + λI)·w = Xᵀy con eliminación de Gauss. λ = 0,05 evita pesos extremos con pocos datos.
- Calidad: se reporta el **R²** del ajuste.
- Se activa con **3 o más entrenamientos** válidos (de 10 minutos o más). Usa las sesiones del mismo tipo si hay suficientes; si no, todas.
- Respaldo: sin datos suficientes usa la tasa de la prueba de sudoración; sin ella, la guía general de 400–800 ml/h (600 ml/h).

Corre en milisegundos, sin internet y sin librerías externas de ML.

## 2. Sistema experto — `HydrationInsights` y `LocalCoach`

Reglas que analizan los datos guardados y producen hallazgos con nivel de riesgo (bajo, medio o alto):

| Regla | Ejemplo de hallazgo |
| --- | --- |
| Ritmo del día: la meta se reparte de 7:00 a 21:00 | "Vas atrasado: toma 500 ml pronto" (alto si faltan 500 ml o más) |
| Factor de condiciones ≥ 1,2 | "Condiciones exigentes: toma antes de tener sed" |
| Cumplimiento en entrenamientos (tomado / recomendado) | "En tus últimos entrenos tomaste el 55 %" (alto si es menor al 60 %) |
| Racha de días cumpliendo la meta | "Racha de 4 días" |
| Sin prueba de sudoración | "Personaliza tus alertas" |

`LocalCoach` responde preguntas frecuentes ("¿Cómo voy hoy?", "¿Cuánta agua llevo…?") con esos datos. Es la respuesta de respaldo cuando no hay internet.

## 3. Coach generativo — `AiCoach` + servidor `hidrasmart-api`

- La app **no habla con Gemini**: envía al servidor `hidrasmart-api` (Cloud Run) un contexto con datos agregados y la pregunta. El servidor guarda la clave, arma el prompt (versionado) y llama a **Google Gemini** (modelo por defecto `gemini-3.5-flash-lite`). Ver [server.md](server.md).
- `CoachContextBuilder` arma el contexto: hora, meta, lo tomado hoy y lo esperado, promedio de 7 días, racha, tasa de sudoración, últimos 5 entrenamientos, la predicción del modelo y los hallazgos del sistema experto. **Solo datos de hidratación**: ni nombre ni ubicación; el servidor convierte el peso en un rango de 5 kg.
- `HidraSmartApi` hace la petición con el token de **Firebase App Check** y un id aleatorio de la instalación (para la cuota diaria).
- Si no hay servidor, no hay internet, se acabó la cuota diaria, la pregunta se bloquea o la nube falla, responde `LocalCoach` y la pantalla dice por qué (`AiCoach.noteFor`). La app nunca se queda sin respuesta.

### Configuración

`API_BASE_URL` y los datos de Firebase (`FIREBASE_APP_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID`) van en `local.properties` o como variables de repositorio de GitHub. No son secretos.

## Pruebas

`core/src/test/java/co/edu/ucc/hidrasmart/ai/` tiene 28 pruebas:

- El ridge recupera una recta exacta y la regularización reduce los pesos.
- El modelo aprende la tasa según la intensidad (R² > 0,99 con datos sintéticos).
- El orden de respaldo funciona: modelo → prueba de sudoración → guía general.
- Ritmo diario, racha, cumplimiento y riesgo.
- Respuestas del coach local.
- Contrato con el servidor: campos obligatorios, sin campos de más, valores recortados a los rangos que acepta, códigos de error y respaldo local con su motivo.
