# Instalación

## En el celular

1. Abre **[Releases](https://github.com/LeiderCadena2110/App_Smartwatch/releases)** desde el celular.
2. Descarga `HidraSmart-phone-vX.Y.Z.apk`.
3. Ábrelo. Si Android lo pide, permite **Instalar apps desconocidas** para el navegador o el gestor de archivos.
4. Pulsa **Instalar**.

## En el reloj (Wear OS)

Un reloj no puede abrir un APK descargado, así que se instala desde el computador con `adb` por Wi‑Fi.

1. En el reloj: **Ajustes › Sistema › Acerca de › Información de software** y toca **Número de compilación** 7 veces. Así se activan las opciones de desarrollador.
2. **Ajustes › Opciones de desarrollador**: activa **Depuración por ADB** y **Depurar por Wi‑Fi**. Anota la IP y el puerto que aparecen.
3. En Wear OS 3 o superior, entra a **Depurar por Wi‑Fi › Vincular dispositivo nuevo** para obtener el código de vinculación.
4. En el computador, con [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools) (incluidas en Android Studio):

   ```bash
   adb pair IP:PUERTO_DE_VINCULACION      # escribe el código que muestra el reloj
   adb connect IP:PUERTO
   adb install HidraSmart-watch-vX.Y.Z.apk
   ```

5. Abre **HidraSmart** en el reloj y acepta los permisos de sensores y notificaciones.

También puedes instalarla directamente desde Android Studio con el reloj conectado: elige la configuración **wear** y pulsa **Run ▶**.

## Actualizar

Instala el APK nuevo encima del anterior. Si aparece *"La app no está instalada porque el paquete entra en conflicto"*, la versión anterior se firmó con otra clave. Desinstálala primero. Para que no vuelva a pasar, configura el keystore de [release.md](release.md).
