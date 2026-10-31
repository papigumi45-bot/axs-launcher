AXS Apple TV Launcher v2

COMO COMPILAR SIN INSTALAR NADA (GitHub)
1. Crea una cuenta gratis en https://github.com
2. Arriba a la derecha: + > New repository. Nombre: axs-launcher. Marca "Public" o "Private" (da igual). Create repository.
3. En la pagina del repo nuevo, pulsa "uploading an existing file".
4. Abre esta carpeta en tu PC y arrastra TODO lo que hay dentro (incluida la carpeta .github) a esa pagina. Pulsa "Commit changes".
   Si .github no se sube: Add file > Create new file, escribe como nombre  .github/workflows/build.yml  y pega el contenido del archivo build.yml.
5. Ve a la pestana "Actions". Veras "Build APK" corriendo (tarda 3 a 6 minutos). Espera el check verde.
6. Entra a esa ejecucion, abajo en "Artifacts" descarga AXS-AppleTV-APK, extrae el ZIP: dentro esta app-debug.apk.
   Si sale una X roja, abre la ejecucion, copia el error y pegaselo a Claude.

INSTALAR (CMD, un comando a la vez, dentro de platform-tools)
   adb install -r "C:\ruta\a\app-debug.apk"
   adb shell am start -n com.axs.appletv/.MainActivity

PROBAR
Navega con las flechas y OK. Pulsa Home para volver al inicio del dock.
Si algo no va bien, el launcher original sigue activo: no se desactivo nada.

FONDO CINEMATOGRAFICO (opcional)
Sube una imagen JPG 1280x720 (menos de 500 KB) con el nombre exacto bg_cinema.jpg a la carpeta
app/src/main/res/drawable-nodpi/  del repo (Add file > Upload files). GitHub recompila solo.

CAMBIAR LAS APPS DEL DOCK
Edita app/src/main/res/values/config.xml en GitHub (icono del lapiz). Un paquete por linea.
Para ver los paquetes instalados:  adb shell pm list packages

ESTABLECERLO COMO PREDETERMINADO (solo despues de probarlo)
   adb shell cmd package set-home-activity com.axs.appletv/.MainActivity
   adb shell dumpsys activity activities | findstr mFocusedApp
Para volver al original:
   adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
