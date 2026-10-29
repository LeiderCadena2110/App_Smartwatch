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

## 3. Coach generativo — `AiCoach` y `GeminiClient`

- Llama a la API `generateContent` de **Google Gemini** (modelo por defecto `gemini-3.5-flash-lite`, configurable con `GEMINI_MODEL`).
- `PromptBuilder` envía como contexto el análisis del sistema experto, la predicción del modelo y el resumen de los últimos entrenamientos. **Solo datos de hidratación**: ni nombre ni ubicación exacta.
- Instrucciones del sistema: español de Colombia, sin diagnósticos médicos, y en el reloj máximo 2 oraciones.
- Si no hay clave, no hay conexión o la API falla, responde `LocalCoach`. La app nunca se queda sin respuesta.

### Configurar la clave

- **Celular:** Perfil › Coach de IA › pega la clave (se guarda solo en el teléfono).
- **Reloj y compilación:** `GEMINI_API_KEY=...` en `local.properties`.

La clave es gratuita en [Google AI Studio](https://aistudio.google.com/apikey).

## Pruebas

`core/src/test/java/co/edu/ucc/hidrasmart/ai/` tiene 19 pruebas:

- El ridge recupera una recta exacta y la regularización reduce los pesos.
- El modelo aprende la tasa según la intensidad (R² > 0,99 con datos sintéticos).
- El orden de respaldo funciona: modelo → prueba de sudoración → guía general.
- Ritmo diario, racha, cumplimiento y riesgo.
- Respuestas del coach local y armado del JSON para Gemini.
