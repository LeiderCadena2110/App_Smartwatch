# Changelog

## Sin publicar

### Agregado
- Reloj: botones de **toma rápida** (+150, +250, +500 ml) en el inicio, que registran con un solo toque.
- Reloj: confirmación animada al registrar una toma (círculo con ✓, vibración y anillo que avanza hasta el total del día). Se cierra sola o al tocarla.
- Reloj: íconos propios para Registrar toma (gota), Coach IA (destellos), Entrenar (cronómetro) e Historial.
- Reloj: **Tile** "Hidratación de hoy" con anillo de progreso y botón **+250 ml** que registra sin abrir la app.
- Reloj: **complicación** "Agua de hoy" para la esfera (mini anillo frente a la meta o texto corto).
- Reloj: **actividad en curso** durante el entrenamiento: cronómetro en la esfera y en Recientes para volver a la sesión con un toque.

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
