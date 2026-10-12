# HidraSmart — App para smartwatch (Wear OS)

App nativa de Wear OS que avisa al atleta **cuándo y cuánto** hidratarse durante el entrenamiento.
Hecha en Android Studio con Kotlin y Jetpack Compose for Wear OS. Funciona sola en el reloj, sin teléfono.

## Cálculo (guía NATA 2017 + ajuste propio)

- **Tasa de sudoración** = (peso inicial − peso final + líquido tomado − orina) / duración (h)
- **ml por alerta** = tasa × f × 1000 × (intervalo / 60), redondeado a 10 ml
- **Intervalo** por defecto: 15 min (NATA sugiere ~200 ml cada 15–20 min)
- **Factor f** (1,0 a 1,3, ajuste propio, no viene de NATA): +0,1 por cada condición más exigente que la de la prueba:
  más de 500 m de altitud, humedad > 70 %, más de 5 °C, o FC > 85 % de la FC máxima (220 − edad).
- Sin prueba de sudoración la app no da mililitros: solo recuerda "toma según tu sed".

La lógica está en `wear/src/main/java/.../dominio/CalculadoraHidratacion.kt` y tiene 12 pruebas en `wear/src/test`.

## Requisitos

- Android Studio (probado con la misma configuración de Mercado Campesino: Gradle 8.14.3, AGP 8.13, **JVM 21**)
- Un emulador **Wear OS** (o un reloj Wear OS 3 o superior)
- La carpeta del proyecto **sin tildes, ñ ni espacios** en la ruta, por ejemplo `C:\Proyecto_Movil\hidrasmart`

## Cómo correrla

1. Android Studio › **Open** › carpeta `hidrasmart` › **Trust Project**.
2. Si aparece "Please Select Gradle JVM", pulsa **Use JVM 21**.
3. Si Android Studio sugiere **actualizar AGP o Gradle**, responde que no por ahora.
4. Espera el **Gradle sync**.
5. Crea el reloj: **Device Manager › + › Wear OS › Wear OS Large Round** › imagen recomendada › **Finish**.
6. Arriba elige el reloj y la configuración **wear**, y pulsa **Run ▶**.

### Clima (opcional)

Crea una cuenta gratis en OpenWeatherMap y agrega tu clave al archivo `local.properties`
(ver `local.properties.example`). Ese archivo no se sube a GitHub.

### Frecuencia cardíaca en el emulador

Si no hay sensor o no se da permiso, la app muestra una frecuencia **simulada** (lo indica en pantalla).
En un reloj real usa el sensor de frecuencia cardíaca.

### Probar las alertas con la app cerrada

1. Perfil › Intervalo de alertas: bájalo a **1 min** (solo para pruebas).
2. Entrenar › elige un tipo y **acepta los permisos** (sensores y notificaciones).
3. Sal a la esfera con el botón lateral (en el emulador: botón de encendido de la barra lateral).
4. Al minuto el reloj suena y vibra, y aparece "¡Hora de hidratarte!" con los botones **Ya tomé** y **En 5 min**.
5. Al terminar, vuelve a poner el intervalo en **15 min**.

### Pruebas unitarias

En Android Studio: clic derecho en `wear/src/test` › **Run 'Tests in ...'**.

## Estructura

```
wear/src/main/java/co/edu/ucc/hidrasmart/
├── MainActivity.kt          punto de entrada
├── HidraApp.kt              navegación (SwipeDismissableNavHost)
├── AppViewModel.kt          estado: perfil, tomas, sesión, alertas
├── dominio/                 modelos y CalculadoraHidratacion (Kotlin puro)
├── datos/                   Repositorio (guardado local), ClimaServicio, Sensores
└── ui/
    ├── tema/                colores por acción
    ├── Componentes.kt       chips, botones redondos, filas − / +
    └── pantallas/           Inicio, Tomas, Entrenamiento, Historial, Perfil
```

## Diferencias con la documentación (versión 1)

Para que la primera versión sea simple y estable:

| Documentación | Versión 1 | Siguiente paso |
| --- | --- | --- |
| Room | SharedPreferences con JSON | Migrar a Room si crece el historial |
| Health Services API | SensorManager (FC y barómetro) | ExerciseClient de Health Services |
| Retrofit | HttpURLConnection | Retrofit si se agregan más servicios |
| Servicio en primer plano + Ongoing Activity | Alarma del sistema (AlarmManager) + notificación con sonido, vibración y botones "Ya tomé" / "En 5 min": funciona con la app cerrada y la pantalla apagada. La FC se lee mientras la app está abierta | Servicio en primer plano para leer la FC con la pantalla apagada |
| Tile y complicación | — | Tile de progreso |
