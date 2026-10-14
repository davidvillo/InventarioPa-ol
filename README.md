# Stock Pañol

Aplicación Android para registrar materiales de construcción vial y herramientas.

- Solapas Materiales y Herramientas, búsqueda y vistas sencilla/completa.
- El menú muestra movimientos y compras correspondientes a la solapa activa: materiales o herramientas.
- Nombre editable desde el encabezado o el menú. Identifica lógicamente la base local; el archivo interno conserva un nombre estable para evitar pérdida de datos al renombrar.
- Ingresos de artículos nuevos o existentes. Los existentes suman cantidad y conservan su unidad. Las herramientas con el mismo nombre comparten artículo de catálogo, pero cada ejemplar tiene marca y código propios.
- Materiales: unidades, mts, kg, litros, pares y otras unidades configuradas. Litros, mts y kg aceptan decimales; unidades y pares requieren enteros. Herramientas: unidad de conteo, marca y empresa proveedora.
- Remito libre, fecha mediante calendario, observaciones e imagen opcional.
- Todas las fechas de formularios, filtros, compras, movimientos y devoluciones se muestran en español: «Lunes 12 de octubre de 2026».
- Vista sencilla: muestra nombre, identificación y cantidad, sin el número de ítem del catálogo. Tocar la foto para ampliarla; los iconos para cambiarla o quitarla aparecen debajo de la imagen ampliada. Marcador «Sin imagen» cuando no hay foto disponible.
- Agregar o cambiar una imagen permite elegir entre «Sacar foto con la cámara» y «Elegir de la galería». La cámara guarda la foto completa en la carpeta privada y persistente de la aplicación mediante FileProvider; las capturas canceladas se descartan.
- Ordenamiento por ítem, nombre, cantidad, unidad y observaciones, ascendente o descendente. Disponible en ambas vistas, con selección independiente para materiales y herramientas. En vista completa también se puede tocar cada encabezado para ordenar.
- Cantidades positivas y unidades enteras. El nombre identifica el artículo de catálogo; el identificador distingue cada unidad física, incluso entre ejemplares de la misma marca. Si no se escribe identificador, se genera uno automáticamente (H-1, H-2 o M-1). No se pueden repetir identificadores dentro del mismo stock.
- Herramientas: el formulario agrega un ejemplar por ingreso, con marca e identificador opcional. Para una segunda amoladora con otra marca o etiqueta, se registra otro ingreso con el mismo nombre o se selecciona el artículo existente.
- Materiales: por defecto se controlan por cantidad (cemento, cal, arena, etc.). Al crear un material reutilizable se puede marcar «Identificar cada unidad». Esta elección se conserva en sus siguientes ingresos y exige la unidad «unidades».
- El detalle de un artículo muestra qué ejemplares están en pañol y quién tiene los prestados. Permite editar marca e identificador sin cambiar la identificación histórica de los movimientos. La búsqueda también encuentra marca, identificador y persona.
- Al tocar un artículo se abre una ventana del 80% de la pantalla con detalle y solapa Egreso: artículo, cantidad, fecha (hoy por defecto), personal y observaciones. Para artículos individuales se marcan los ejemplares disponibles; la cantidad se calcula a partir de la selección. La devolución permite marcar únicamente ejemplares pendientes de ese préstamo. Para stock por cantidad, kg/mts aceptan decimales y unidades exige enteros.
- Personal de obra desde el menú: nombre, apellido, obra desplegable y apodo. Cada persona es un objeto Worker con identidad propia y una obra Worksite asociada; se puede editar o eliminar. Editar conserva su identidad y no reescribe el nombre registrado en movimientos anteriores. Eliminar retira a la persona de nuevas entregas; si tiene historial, se conserva su registro como inactivo, manteniendo préstamos y permitiendo sus devoluciones pendientes. Las personas sin historial se eliminan físicamente.
- La lista Obra comienza con 513 Vereda, 513 Movimiento de suelo, 519, Camionero, Maquinista, Administrativa, Globalcia y Otros. Desde «Ajustes → Obras» en el menú o «Administrar opciones de obra» en el formulario se pueden agregar, modificar y quitar opciones. Renombrar actualiza las asignaciones del personal; quitar una opción impide nuevas asignaciones, pero conserva las existentes. No se aceptan opciones vacías o duplicadas. Volver a agregar una opción retirada que tiene personal asignado la reactiva.
- «Ajustes → Unidades» reúne el listado y las operaciones para agregar, modificar o quitar unidades de medida. Para las nuevas unidades se elige si las cantidades deben ser enteras. Renombrar actualiza el nombre en el stock sin convertir cantidades; los movimientos y compras anteriores conservan su unidad original. No se puede cambiar la regla de enteros/decimales cuando una unidad ya tiene artículos. Quitar una unidad la retira de materiales nuevos, conservando los artículos existentes y sus operaciones. La unidad de conteo de herramientas sigue disponible para ejemplares identificados aunque se quite de la lista de materiales.
- Materiales nuevos: material de construcción (predeterminado), EPP o material eléctrico.
- Menú: historiales separados de materiales y herramientas, con filtros por columna, texto, rango de fechas, tipo de movimiento y tipo de material; ordenamiento ascendente/descendente y encabezados seleccionables.
- Compras de materiales y compras de herramientas desde el menú: fecha, artículo, cantidad, unidad, empresa y remito. Cada ingreso confirmado (nuevo o existente) genera una compra y su movimiento de ingreso en la misma operación, con vistas sencilla/completa, filtros por columna y fechas, y ordenamiento. La empresa y el remito se conservan por compra.
- Materiales: las devoluciones parciales o totales crean movimientos vinculados al egreso. Herramientas: actualizan el egreso original, con cantidades devueltas/pendientes y fecha de la última devolución; no generan filas adicionales. La vista sencilla de herramientas muestra por defecto egresos pendientes de ayer y hoy; la completa incluye el historial completo. Los filtros de fecha se pueden quitar para revisar préstamos anteriores.
- Eliminar un movimiento revierte su efecto sobre el stock. Eliminar un egreso de materiales también elimina sus devoluciones vinculadas y repone solo lo pendiente. Se rechazan operaciones que dejarían stock negativo, entregas que superen lo disponible y devoluciones que superen lo pendiente.

## Datos

La versión 6 agrega el catálogo de unidades de medida con referencias desde los artículos y conserva la regla de cantidades enteras en cada movimiento. La migración desde versiones anteriores mantiene las unidades y cantidades de los artículos existentes y agrega litros y pares. MeasurementRepository encapsula las operaciones de este catálogo.

La versión 5 incorpora un catálogo de obras, referencias desde personal e indicadores de actividad para conservar el historial al eliminar personas u opciones. La migración conserva las obras escritas anteriormente, agregándolas a las opciones iniciales sin cambiar identidades ni movimientos. PersonnelRepository separa estas operaciones del inventario y LocalPersonnelRepository encapsula su persistencia.

La base versión 4 conserva las tablas de stock, movimientos, compras, personal y configuración, y agrega unidades físicas y vínculos entre unidades y movimientos para cada stock. La migración desde versiones 1, 2 y 3 conserva artículos, fotos, cantidades, personal, devoluciones y nombre del stock; los ingresos anteriores pasan a compras, manteniendo empresa y remito originales. Las herramientas anteriores reciben códigos provisionales: se crea una unidad por ejemplar disponible y por ejemplar pendiente de devolución. Los préstamos históricos quedan señalados porque la base anterior no permite reconstruir la identidad física real; las devoluciones históricas ya completadas conservan sus cantidades sin inventar unidades. Los materiales anteriores conservan su control por cantidad. Ingresos, egresos, devoluciones y eliminación actualizan sus registros y cantidades dentro de una misma transacción.

| Stock | Movimientos | Compras |
| --- | --- | --- |
| materiales | movimientos_materiales | compras_materiales |
| herramientas | movimientos_herramientas | compras_herramientas |

Las compras y movimientos referencian al artículo de su propio stock mediante claves foráneas. Cada movimiento de ingreso se vincula a una sola compra; las devoluciones de materiales se vinculan al egreso del mismo artículo y los egresos al personal. SQLite valida estas relaciones, impidiendo referencias inexistentes o a artículos incorrectos. Eliminar un ingreso desde movimientos elimina su compra asociada y revierte la cantidad dentro de la misma transacción; si el stock quedaría negativo se rechaza toda la operación.

Las unidades y sus movimientos también tienen claves foráneas que exigen pertenecer al mismo artículo, y un índice único impide dos préstamos pendientes de una misma unidad. Eliminar un ingreso individual solo retira sus propios ejemplares y se rechaza si tienen movimientos posteriores. Los ingresos históricos sin identificación de ejemplares se conservan. Eliminar una devolución individual se rechaza si esos ejemplares tienen préstamos posteriores.

Las compras conservan cantidad, unidad, remito, fecha, observaciones, marca y empresa de cada ingreso. Los movimientos conservan artículo, unidad, fecha, persona, tipo, observaciones y devolución vinculada o cantidad devuelta. La base guarda la URI de cada imagen, no sus bytes. Las imágenes de galería mantienen referencias con permiso persistente a su ubicación original; si se elimina el archivo original, se muestra «Sin imagen». Las fotos tomadas con la cámara se guardan como archivos JPG en files/stock_images dentro del almacenamiento privado de la app, y no dependen de archivos de galería ni de la caché. Cambiar o quitar la foto actualiza su referencia, sin alterar cantidades ni crear ingresos.

La interfaz StockRepository separa la pantalla del almacenamiento. StockItem representa el artículo; InventoryUnit, el ejemplar; StockMovement y MovementUnit, su movimiento e identificación histórica. InventoryPolicy encapsula, mediante dos implementaciones polimórficas, las reglas de control por cantidad y por ejemplar. La sincronización, autenticación y autorización de administradores todavía no están implementadas. Una futura versión debe validar los permisos también en el servidor.

## Verificación

Compilar: gradlew.bat :app:assembleDebug
Pruebas locales: gradlew.bat :app:testDebugUnitTest
Pruebas en dispositivo/emulador (persistencia, migración desde versiones 1/2/3, claves foráneas, compras, egresos, devoluciones individuales, identificación, eliminación, personal y formularios): gradlew.bat :app:connectedDebugAndroidTest
