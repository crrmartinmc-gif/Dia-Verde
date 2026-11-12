# Día Verde para Android Studio

Proyecto Android nativo en Java, Android 8.0 o superior (API 26), para la interfaz existente de Día Verde. El GPS y la transmisión pertenecen a un servicio de ubicación en primer plano, con notificación permanente y almacenamiento SQLite. No dependen de temporizadores de Chrome ni de que el WebView esté visible.

## Abrir y generar la APK

1. Descargar el proyecto y abrir **esta carpeta `android-studio`** desde Android Studio, no la raíz del repositorio web.
2. Usar Android Studio Meerkat o posterior, con un JDK 17 o 21 compatible con Gradle 8.11.1. Instalar Android SDK Platform 35 y Build Tools 35.0.0 desde SDK Manager.
3. Esperar Gradle Sync. En Windows se incluye `gradlew.bat`; en Linux/macOS, `./gradlew`.
4. Ejecutar **Build → Build App Bundle(s) / APK(s) → Build APK(s)**, o `./gradlew :app:assembleDebug`.
5. La APK de prueba se genera en `app/build/outputs/apk/debug/app-debug.apk`. Se firma con la clave de desarrollo que genera Android en el equipo; no es una publicación firmada para Play Store. Para distribución definitiva, generar y conservar una clave propia con **Generate Signed App Bundle / APK**.

Los endpoints están en `app/build.gradle` (`WEB_URL` y `BACKEND_URL`). Son las URLs públicas actuales de la aplicación y del Apps Script de Día Verde. No hay contraseñas de usuarios ni claves privadas en el proyecto. Si cambia `/exec`, actualizar `BACKEND_URL` y recompilar. El Backend Maestro sigue consultando la misma planilla histórica; la APK no crea otra fuente GPS.

## Primer uso

1. Abrir Día Verde e ingresar con la cuenta habitual.
2. Elegir CGM/zona y tocar **Iniciar recorrido**.
3. Permitir **ubicación precisa** y **notificaciones**. El servicio se inicia desde la actividad visible, por lo que no solicita acceso permanente a ubicación para iniciar recorridos ocultos.
4. Esperar la notificación **Día Verde · Recorrido activo** y la confirmación del servicio antes de bloquear la pantalla.
5. Desde **Ajustes → Batería**, permitir actividad sin restricciones si el fabricante interrumpe el servicio. La APK abre el ajuste a pedido del operador, no cambia esa preferencia por su cuenta.
6. Finalizar desde el botón de la página o desde la notificación. Cerrar la sesión también finaliza el seguimiento. Las fotos se toman mediante la cámara del teléfono y mantienen el flujo de evidencias existente.

El WebView y Chrome del teléfono tienen almacenamiento de sesión independiente: es normal tener que ingresar otra vez en la APK. Mantener Android System WebView/Chrome actualizado; se usa un puente con origen permitido y mensajes del marco principal, sin `addJavascriptInterface` abierto a todos los marcos.

## Pantalla encendida y seguimiento en segundo plano

Durante un recorrido activo, la ventana de Día Verde mantiene la pantalla encendida mientras la aplicación está visible. Al finalizar o salir a otra aplicación se libera esa condición. El botón físico de bloqueo sigue funcionando; al bloquear manualmente o cambiar de aplicación, el servicio GPS continúa con su notificación. La pantalla no se enciende por la fuerza desde el segundo plano.

## Qué hace con pantalla bloqueada

- El servicio de Android recibe las ubicaciones y aplica precisión máxima de 50 m, rechazo de lecturas viejas/desordenadas y saltos imposibles. Los latidos estacionarios conservan la misma coordenada filtrada.
- Cada punto recibe un UUID y se guarda antes de transmitirse. El envío se intenta cada 10 segundos y solo se confirma cuando el Apps Script devuelve los IDs exactos del lote. No se borra información por un fallo HTTP.
- El historial nativo conserva usuario, CGM, zona, ID de recorrido, precisión, captura y origen. Al volver a la pantalla, se recupera de SQLite y se dibuja la misma trayectoria que se envió al Panel.
- Al finalizar se guarda un punto inactivo. Los envíos pendientes se reintentan con WorkManager; el rescate periódico respeta las restricciones de Android y puede demorarse. No toma GPS nuevo fuera del recorrido.
- Una detención forzada desde Ajustes, revocar permisos, apagar el dispositivo o ciertas restricciones del fabricante pueden detener el servicio. No se promete sobrevivir a **Forzar detención**. Tras reiniciar el teléfono hay que abrir la app y reanudar el recorrido. Desinstalar/borrar almacenamiento elimina los puntos locales todavía pendientes.

La notificación es visible durante el seguimiento y permite finalizarlo sin abrir el mapa. No se inicia seguimiento automáticamente al arrancar el teléfono. La base y la sesión se guardan en el almacenamiento privado y no se incluyen en copias automáticas.

## Validación

`./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`

Las pruebas JVM verifican filtros GPS y confirmaciones por ID. Antes de utilizarla en la flota, probar en el modelo real: empezar un recorrido al aire libre, bloquear la pantalla 5–10 minutos, verificar avance en el Panel, activar/desactivar datos, volver a abrir y finalizar desde la notificación. Las pruebas locales no sustituyen esa prueba física con el GPS y la batería del fabricante.

El servicio usa los endpoints existentes de Día Verde. La validación de sesión se realiza al iniciar; la autorización por token de todas las escrituras del servidor legado no se cambia en este proyecto.
