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

## Tile, complicación y actividad en curso (reloj)

**Tile**
1. En la esfera, desliza hacia la izquierda hasta el final de las Tiles.
2. Toca **+ Agregar Tile** (o mantén presionada una Tile y elige **+**) y escoge **Hidratación de hoy**.
3. Toca **+250 ml**: el total y el anillo se actualizan sin abrir la app. Tocar el número abre la app.

En Android Studio también se puede instalar sola: arriba, en la lista de configuraciones, elige **Edit Configurations… › + › Wear OS Tile**, módulo `wear`, servicio `HydrationTileService`, y pulsa **Run**.

**Complicación**
1. Mantén presionada la esfera › **Personalizar** (o el lápiz).
2. Desliza hasta las complicaciones, toca un espacio libre y elige **HidraSmart › Agua de hoy**.
3. Registra una toma en la app: la complicación cambia al instante.

**Actividad en curso**
1. Inicia un entrenamiento y acepta el permiso de notificaciones.
2. Sal a la esfera con el botón lateral: abajo aparece el ícono del cronómetro. Tócalo para volver a la sesión.
3. En la lista de apps, la sección **Recientes** muestra el tipo de entrenamiento y el tiempo. Al pausar dice "en pausa".

## Pantallas pequeñas

Crea también un emulador **Wear OS Small Round** y revisa que el inicio, la confirmación y la Tile no se corten.

## Frecuencia cardíaca en el emulador

Si no hay sensor o no se da el permiso, la app muestra una frecuencia **simulada** y lo indica en pantalla. En un reloj real usa el sensor de frecuencia cardíaca.

## Probar la IA

- **Sin clave de Gemini:** el Coach IA responde con la IA local. La pantalla dice "IA en el reloj · sin internet".
- **Modelo predictivo:** en el celular, Historial › **Entrenamiento**, registra 3 entrenamientos con distinta duración. En Coach IA › Planear, la fuente cambia a "Modelo IA personal".
- **Con clave:** pégala en Perfil (celular) o en `local.properties`. Las respuestas dirán "Gemini".
