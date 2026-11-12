# Día Verde y Panel de Control: correcciones de consistencia

## Hallazgos y cambios

| Problema observado | Cambio preparado |
| --- | --- |
| La cola GPS borraba puntos tanto al cargar como al fallar una imagen usada para enviar el lote. | Solicitud JSON con confirmación explícita; se conserva el lote ante error, desconexión o respuesta incompleta. |
| Reintentar un lote podía duplicarlo en Sheets. | El Apps Script identifica cada punto y confirma reintentos sin insertar otra fila. Requiere implementar el TXT nuevo. |
| El Panel agrupaba por nombre, aunque dos cuentas pueden compartirlo. | Agrupación por usuario; separación por recorrido, finalización, zona, día y discontinuidades. |
| El estado `Activo` no participaba de la firma de actualización. | La firma incluye usuario, recorrido, estado, precisión y coordenadas; una finalización se refleja aunque el vehículo no se mueva. |
| Cada navegador geocodificaba direcciones y guardaba resultados independientes. | Día Verde consulta las mismas ubicaciones corregidas del Backend Maestro que el Panel; se eliminó la geocodificación automática en ambos navegadores. |
| Una corrección de calle/altura podía aplicarse a otro CGM. | El Backend Maestro exige clave o dirección con localidad y zona. Requiere implementar sus reemplazos. |
| Coordenadas vacías podían convertirse en cero y valores con coma decimal podían descartarse. | Conversión compartida y estricta, con soporte de coma decimal y valores ausentes. |
| Los reclamos/evidencias del Panel se cargaban una sola vez. | Reclamos y evidencias se consultan cada 30 segundos; GPS cada 15. Una falla conserva los datos anteriores y muestra que faltan actualizar. |
| Una foto no obtenía el GPS, aunque el formulario decía que lo haría. | Captura de ubicación real del teléfono, precisión y hora; si no se consigue GPS se informa y se guarda ausente, sin usar el punto del domicilio como posición del teléfono. |
| El histórico del Panel descartaba foto e ID de evidencia. | Se conservan foto, ID, usuario, CGM, recorrido y captura en los reemplazos del Backend Maestro. |
| Había criterios diferentes para considerar realizada una dirección. | Ambos clientes comparten el mismo criterio de clave/dirección/CGM y día de Argentina. Las evidencias antiguas ambiguas no se atribuyen a otro CGM. |
| Dos observadores de ubicación podían producir referencias distintas al centrar. | Un solo observador por recorrido; centrar usa la misma lectura validada que se envía al Panel. |
| La selección de CGM podía cambiar mientras el recorrido seguía activo. | Se conservan CGM/ID de recorrido y se bloquean ambos selectores hasta finalizar. |

Los polígonos de `zonas.geojson` eran idénticos en ambos repositorios. Durante la consulta de revisión, la fuente compartida tenía 254 direcciones y 241 con coordenadas. Es una fotografía de esa consulta; las cantidades cambiarán al actualizar los datos. Las direcciones sin ubicación confirmada permanecen en la lista.

## Mejoras visuales de Día Verde

Se corrigió un cierre de contenedor que separaba la lista del mapa en escritorio. En celular se compactaron los filtros y se redujo el desplazamiento necesario para llegar al mapa. Se agregaron avance de zona, cantidades realizadas/pendientes y estados de confirmación de GPS/evidencias. Los colores de los reclamos coinciden con el Panel y el centro de cada marcador representa su coordenada. Las horas operativas usan Argentina.

## Validación

Se ejecutaron once pruebas de lógica para las colas, el servidor y el render del mapa. La prueba de navegador conjunta usó datos simulados: ingreso, recorrido, GPS aceptado, falla/reintento, cierre, foto con GPS y lectura de la misma coordenada final en el Panel. No generó evidencias ni posiciones ficticias en producción. Se verificó la vista de Día Verde a 360, 390, 768 y 1440 píxeles.

Las consultas reales a ambos endpoints del Backend Maestro respondieron correctamente. Los Apps Scripts propuestos todavía requieren implementación desde Google; las pruebas locales no sustituyen esa comprobación en producción.

## Implementación y límites

Instalar primero los cambios de Apps Script siguiendo `backend/GUIA-SINCRONIZACION.md` y la guía equivalente de Panel-de-Control. Después publicar los dos clientes y comprobar un recorrido real. Las lecturas del Panel son periódicas; no se presentan como transmisión instantánea.

Las filas antiguas que no guardaron CGM, usuario o ID de recorrido no pueden reconstruirse con certeza. El nuevo protocolo conserva esos campos para los registros nuevos. Esta revisión no cambia los permisos de los endpoints legados: el ingreso por servidor existente no equivale a autorización por token de todas las escrituras de GPS/evidencias. Ese control requiere una migración coordinada de clientes y servidor por separado.
