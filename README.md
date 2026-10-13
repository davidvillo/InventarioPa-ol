# Stock Pañol

Aplicación Android para registrar materiales de construcción vial y herramientas.

- Solapas Materiales y Herramientas, búsqueda y vistas sencilla/completa.
- El menú muestra movimientos y compras correspondientes a la solapa activa: materiales o herramientas.
- Nombre editable desde el encabezado o el menú. Identifica lógicamente la base local; el archivo interno conserva un nombre estable para evitar pérdida de datos al renombrar.
- Ingresos de artículos nuevos o existentes. Los existentes suman cantidad y conservan su unidad.
- Materiales: mts, kg o unidades. Herramientas: unidades, marca y empresa proveedora.
- Remito libre, fecha mediante calendario, observaciones e imagen opcional.
- Todas las fechas de formularios, filtros, compras, movimientos y devoluciones se muestran en español: «Lunes 12 de octubre de 2026».
- Vista sencilla: tocar la foto para ampliarla; los iconos para cambiarla o quitarla aparecen debajo de la imagen ampliada. Marcador «Sin imagen» cuando no hay foto disponible.
- Ordenamiento por ítem, nombre, cantidad, unidad y observaciones, ascendente o descendente. Disponible en ambas vistas, con selección independiente para materiales y herramientas. En vista completa también se puede tocar cada encabezado para ordenar.
- Cantidades positivas; unidades enteras; nombres únicos por tipo de stock.
- Al tocar un artículo se abre una ventana del 80% de la pantalla con detalle y solapa Egreso: artículo, cantidad, fecha (hoy por defecto), personal y observaciones. La unidad se conserva; kg/mts aceptan decimales y unidades exige enteros.
- Personal de obra desde el menú: nombre, apellido, obra y apodo; disponible para seleccionar en egresos y devoluciones de materiales.
- Materiales nuevos: material de construcción (predeterminado), EPP o material eléctrico.
- Menú: historiales separados de materiales y herramientas, con filtros por columna, texto, rango de fechas, tipo de movimiento y tipo de material; ordenamiento ascendente/descendente y encabezados seleccionables.
- Compras de materiales y compras de herramientas desde el menú: fecha, artículo, cantidad, unidad, empresa y remito. Cada ingreso confirmado (nuevo o existente) genera una compra y su movimiento de ingreso en la misma operación, con vistas sencilla/completa, filtros por columna y fechas, y ordenamiento. La empresa y el remito se conservan por compra.
- Materiales: las devoluciones parciales o totales crean movimientos vinculados al egreso. Herramientas: actualizan el egreso original, con cantidades devueltas/pendientes y fecha de la última devolución; no generan filas adicionales. La vista sencilla de herramientas muestra por defecto egresos pendientes de ayer y hoy; la completa incluye el historial completo. Los filtros de fecha se pueden quitar para revisar préstamos anteriores.
- Eliminar un movimiento revierte su efecto sobre el stock. Eliminar un egreso de materiales también elimina sus devoluciones vinculadas y repone solo lo pendiente. Se rechazan operaciones que dejarían stock negativo, entregas que superen lo disponible y devoluciones que superen lo pendiente.

## Datos

La base versión 3 usa seis tablas físicas para stock, movimientos y compras separados por materiales y herramientas, más personal y configuración. La migración desde versiones 1 y 2 conserva artículos, fotos, cantidades, personal, devoluciones y nombre del stock; los ingresos anteriores pasan a compras, manteniendo empresa y remito originales. Ingresos, egresos, devoluciones y eliminación actualizan sus registros y cantidades dentro de una misma transacción.

| Stock | Movimientos | Compras |
| --- | --- | --- |
| materiales | movimientos_materiales | compras_materiales |
| herramientas | movimientos_herramientas | compras_herramientas |

Las compras y movimientos referencian al artículo de su propio stock mediante claves foráneas. Cada movimiento de ingreso se vincula a una sola compra; las devoluciones de materiales se vinculan al egreso del mismo artículo y los egresos al personal. SQLite valida estas relaciones, impidiendo referencias inexistentes o a artículos incorrectos. Eliminar un ingreso desde movimientos elimina su compra asociada y revierte la cantidad dentro de la misma transacción; si el stock quedaría negativo se rechaza toda la operación.

Las compras conservan cantidad, unidad, remito, fecha, observaciones, marca y empresa de cada ingreso. Los movimientos conservan artículo, unidad, fecha, persona, tipo, observaciones y devolución vinculada o cantidad devuelta. Las fotos se seleccionan con el selector de Android y se conservan referencias con permiso persistente. Si se elimina el archivo original, se muestra «Sin imagen». Cambiar o quitar la foto guarda solamente la imagen del artículo, sin alterar cantidades ni crear ingresos.

La interfaz StockRepository separa la pantalla del almacenamiento. La sincronización, autenticación y autorización de administradores todavía no están implementadas. Una futura versión debe validar los permisos también en el servidor.

## Verificación

Compilar: gradlew.bat :app:assembleDebug
Pruebas locales: gradlew.bat :app:testDebugUnitTest
Pruebas en dispositivo/emulador (persistencia, migración desde versiones 1/2, claves foráneas, compras, egresos, devoluciones, eliminación, personal y formularios): gradlew.bat :app:connectedDebugAndroidTest
