# Actualizar el Apps Script de evidencias de Día Verde

`Codigo-Completo-Dia-Verde.txt` contiene **todo el código** del proyecto de evidencias, GPS e ingreso. Está basado en la versión restaurada e implementada en esta conversación.

1. Guardar una copia privada de la versión actual.
2. Reemplazar todo `Código.gs` por el TXT completo. Si existe un `Auth.gs` separado con las mismas funciones, quitar ese duplicado: el módulo de ingreso ya está incorporado en el TXT.
3. Conservar las propiedades existentes `CARPETA_DRIVE_ID`, `ID_HOJA_DE_DISPERSION`, `DV_AUTH_SECRET` y `DV_AUTH_USERS`. **No volver a importar las cuentas ni borrar las propiedades.**
4. Ejecutar `diagnosticoConexionDiaVerde`. Es una lectura y no agrega registros de prueba. Debe devolver `ok:true`, `carpetaAccesible:true`, `cuentas:true` y `sincronizacion:2`.
5. Implementar → Administrar implementaciones → editar la actual → Nueva versión. Conservar su URL `/exec` y sus permisos actuales.

Se mantienen las columnas anteriores. Se agregan metadatos al final de GPS (J:M) y del histórico (J:Q). Si esas columnas ya tienen otra información, el servidor avisa y no las sobrescribe.

El servidor confirma lotes GPS y reconoce `ID_PUNTO`, de modo que repetir un envío después de perder la respuesta no duplica el registro. Se conservan `ID_RECORRIDO`, usuario, CGM, captura y fuente. La ubicación de una foto es la del teléfono al tomarla, o queda ausente si el teléfono no la entregó: no se sustituye por la dirección del reclamo.

Para completar la integración también hay que instalar los reemplazos documentados en el repositorio Panel-de-Control, `backend/GUIA-SINCRONIZACION.md`.

## Límite de los datos anteriores

Las filas históricas que nunca guardaron usuario, CGM o ID de recorrido no permiten recuperar esa información con certeza. Los nuevos campos se completan en los registros nuevos. Las ubicaciones de reclamos salen del Backend Maestro compartido; las que todavía no tienen coordenadas se identifican como pendientes de ubicación y siguen en la lista de direcciones.

## Verificación local

`node --test tests/*.test.cjs`

Incluye confirmación/reintento de GPS, persistencia de nuevos puntos durante un envío, deduplicación del servidor, metadatos de evidencias y coincidencia de CGM/dirección. La prueba de navegador conjunta también simuló ingreso, recorrido, falla y reintento de red, cierre, foto con GPS y lectura de la misma posición final desde el Panel.
