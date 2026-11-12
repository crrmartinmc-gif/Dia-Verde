# Ingreso de Día Verde en Apps Script

Instalar el servidor antes de publicar el nuevo index.html para evitar interrumpir el ingreso.

1. En el proyecto actual de Google Apps Script, agregar un archivo `Auth.gs` y copiar el contenido de este directorio.
2. Reemplazar únicamente `doPost(e)` de Code.gs por:

```js
function doPost(e) {
  const auth = dvProcesarAuth_(e);
  if (auth !== null) return auth;
  return hacerPost(e);
}
```

3. En Configuración del proyecto → Propiedades de la secuencia de comandos, crear `DV_AUTH_IMPORT`. Su valor debe ser el JSON de las cuentas originales (usuario, password y nombre). No subir ese valor a GitHub ni dejarlo en un archivo servido por la web.
4. Ejecutar `importarUsuariosDiaVerde` desde el editor. Confirmar en el registro la cantidad esperada de cuentas. La función guarda verificadores HMAC con sal y una clave privada del servidor; elimina la propiedad de importación con las claves originales. No eliminar `DV_AUTH_SECRET` ni `DV_AUTH_USERS`.
5. Implementar → Administrar implementaciones → editar la implementación actual → Nueva versión. Mantener la misma URL `/exec` y el acceso necesario para que la página consulte el servidor. Ejecutar como propietario. No crear otra implementación salvo que también se actualice la URL del cliente.
6. Probar el nuevo index con una cuenta existente, una clave incorrecta, recarga y salida. Publicar main solamente después de verificar el backend.

Las cuentas y contraseñas siguen siendo las mismas. Las sesiones antiguas requieren ingresar nuevamente; las nuevas duran hasta seis horas y pueden expirar antes si Google descarta la caché. La validación necesita conexión. Las claves nunca se guardan en el navegador ni se envían en la URL.

## Alcance

Este cambio traslada la validación del ingreso al servidor. Las operaciones existentes de evidencias y GPS conservan su comportamiento: todavía no exigen un token en cada solicitud. Esta pantalla de ingreso no constituye por sí sola un control de acceso completo a esas operaciones. No se publica como una protección integral de los datos.

Las contraseñas que estuvieron en commits anteriores siguen en el historial público. Retirarlas del index actual no las vuelve secretas; para recuperar su confidencialidad hay que cambiarlas y revisar el historial por separado.
