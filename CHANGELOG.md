# Changelog

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
