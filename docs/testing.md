# Pruebas

## Pruebas unitarias

```bash
./gradlew :core:testDebugUnitTest
```

Hay 31 pruebas en `core/src/test`: 12 de la calculadora NATA y 19 de la IA. También corren solas en GitHub Actions en cada push.

## Probar las alertas con la app cerrada (reloj)

1. Perfil › Intervalo de alertas: bájalo a **1 min** (solo para pruebas).
2. Entrenar › elige un tipo y **acepta los permisos** (sensores y notificaciones).
3. Sal a la esfera con el botón lateral (en el emulador: botón de encendido de la barra lateral).
4. Al minuto el reloj suena y vibra, y aparece "¡Hora de hidratarte!" con los botones **Ya tomé** y **En 5 min**.
5. Al terminar, vuelve a poner el intervalo en **15 min**.

Durante el entrenamiento, **Probar alarma (10 s)** programa la alarma real para dentro de 10 segundos.

## Frecuencia cardíaca en el emulador

Si no hay sensor o no se da el permiso, la app muestra una frecuencia **simulada** y lo indica en pantalla. En un reloj real usa el sensor de frecuencia cardíaca.

## Probar la IA

- **Sin clave de Gemini:** el Coach IA responde con la IA local. La pantalla dice "IA en el reloj · sin internet".
- **Modelo predictivo:** en el celular, Historial › **Entrenamiento**, registra 3 entrenamientos con distinta duración. En Coach IA › Planear, la fuente cambia a "Modelo IA personal".
- **Con clave:** pégala en Perfil (celular) o en `local.properties`. Las respuestas dirán "Gemini".
