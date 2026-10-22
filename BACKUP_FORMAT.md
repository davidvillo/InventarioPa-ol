# Copias de Stock Pañol: formato 1

Archivo ZIP UTF-8, con `manifest.json` como primera entrada. La carpeta de destino se elige mediante Storage Access Framework y debe estar fuera de los directorios privados de la aplicación. La desinstalación conserva el archivo compartido, pero elimina los permisos concedidos a la instalación anterior: hay que volver a elegir la carpeta o abrir el archivo para recuperarlo.

El manifiesto contiene `application: StockPanol`, `formatVersion: 1`, `databaseVersion`, `appVersion`, `createdAt` (milisegundos Unix), `stockName`, `counts`, `photos` y `files`.

- `database/stock_paniol.db` es una instantánea SQLite autocontenida, incluso cuando la base activa usa WAL. Se copian tablas, datos, secuencias, índices, vistas y disparadores dentro de una transacción consistente.
- `files` asocia cada nombre del ZIP con `size` y `sha256`.
- `photos` asocia cada URI original con `images/<sha256>.img`. Se incluyen las fotos referenciadas, también las seleccionadas desde proveedores externos. Restaurar vuelve a generar sus URI con el FileProvider de la instalación actual; no depende de permisos de galería de una instalación desinstalada.

## Compatibilidad entre versiones

Las versiones futuras deben mantener el lector del formato 1 y las migraciones no destructivas de SQLiteOpenHelper. Al modificar el esquema, incrementar `LocalStockRepository.DATABASE_VERSION` y agregar la migración correspondiente y sus pruebas. Una copia de una versión antigua se migra sobre una base temporal antes de reemplazar la activa.

Una versión antigua no debe reducir automáticamente el esquema de una copia más nueva: se rechaza antes de modificar el inventario. Un formato desconocido, una suma incorrecta, referencias a fotos ausentes o una base con errores también se rechazan.

## Restauración

Primero se revisa completamente el ZIP, se migra en una carpeta temporal y se comprueban integridad y claves foráneas. El usuario confirma qué copia restaurar. Las fotos se copian con nombres basados en su contenido y se conservan las existentes. El reemplazo de SQLite mantiene una base anterior y un marcador AtomicFile que permite recuperar el estado anterior al reiniciar si el proceso se interrumpe antes de confirmar el reemplazo.

Las tareas de exportación/restauración pertenecen a la aplicación, sobreviven a cambios de orientación y bloquean la edición mientras trabajan. No se elimina automáticamente ninguna copia compartida. Las copias son manuales y no están cifradas; deben guardarse en un lugar que el usuario controle.
