package com.example.stockpaniol

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class StockItem(val id: Long, val name: String, val quantity: Double, val unit: String, val notes: String, val image: String, val brand: String, val company: String, val materialType: String = MaterialTypes.CONSTRUCTION)
data class StockEntry(val itemId: Long, val name: String, val quantity: Double, val unit: String, val notes: String, val receipt: String, val date: String, val image: String, val brand: String, val company: String, val materialType: String = MaterialTypes.CONSTRUCTION)

/** Local repository boundary: a future remote implementation can preserve this contract. */
interface StockRepository {
    fun items(tools: Boolean): List<StockItem>
    fun receive(tools: Boolean, entry: StockEntry)
    fun updateImage(tools: Boolean, itemId: Long, image: String)
    fun personnel(): List<Worker>
    fun addWorker(name: String, surname: String, worksite: String, nickname: String)
    fun movements(tools: Boolean): List<StockMovement>
    fun purchases(tools: Boolean): List<StockPurchase>
    fun issue(tools: Boolean, itemId: Long, quantity: Double, date: String, workerId: Long, notes: String)
    fun returnStock(tools: Boolean, movementId: Long, quantity: Double, date: String, workerId: Long?, notes: String)
    fun deleteMovement(tools: Boolean, movementId: Long)
    var stockName: String
}

class LocalStockRepository(context: Context) : SQLiteOpenHelper(context, "stock_paniol.db", null, 3), StockRepository {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        for (table in listOf("materiales", "herramientas")) {
            db.execSQL("CREATE TABLE $table (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL COLLATE NOCASE UNIQUE, cantidad REAL NOT NULL CHECK(cantidad >= 0), unidad TEXT NOT NULL, observaciones TEXT NOT NULL, imagen TEXT NOT NULL, marca TEXT NOT NULL, empresa TEXT NOT NULL)")
        }
        db.execSQL("CREATE TABLE ingresos (id INTEGER PRIMARY KEY AUTOINCREMENT, tipo TEXT NOT NULL, articulo_id INTEGER NOT NULL, cantidad REAL NOT NULL, remito TEXT NOT NULL, fecha TEXT NOT NULL, observaciones TEXT NOT NULL, marca TEXT NOT NULL, empresa TEXT NOT NULL)")
        db.execSQL("CREATE TABLE configuracion (clave TEXT PRIMARY KEY, valor TEXT NOT NULL)")
        db.execSQL("INSERT INTO configuracion VALUES ('nombre', 'Stock Pañol')")
        createMovementSchema(db)
        splitMovementSchema(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createMovementSchema(db)
            db.execSQL("""INSERT INTO movimientos (stock, articulo_id, cantidad, unidad, fecha, persona, clase, tipo_material, observaciones, ingreso_id)
                SELECT i.tipo, i.articulo_id, i.cantidad, a.unidad, i.fecha, '', 'Ingreso', a.tipo_material, i.observaciones, i.id
                FROM ingresos i JOIN materiales a ON i.articulo_id=a.id WHERE i.tipo='materiales'""")
            db.execSQL("""INSERT INTO movimientos (stock, articulo_id, cantidad, unidad, fecha, persona, clase, tipo_material, observaciones, ingreso_id)
                SELECT i.tipo, i.articulo_id, i.cantidad, a.unidad, i.fecha, '', 'Ingreso', '', i.observaciones, i.id
                FROM ingresos i JOIN herramientas a ON i.articulo_id=a.id WHERE i.tipo='herramientas'""")
        }
        if (oldVersion < 3) splitMovementSchema(db)
    }
    /** Preserve legacy IDs so purchases, returns and personnel keep their relationships. */
    private fun splitMovementSchema(db: SQLiteDatabase) {
        for (tools in listOf(false, true)) {
            val stock = table(tools)
            val purchases = purchaseTable(tools)
            val movements = movementTable(tools)
            db.execSQL("""CREATE TABLE $purchases (id INTEGER PRIMARY KEY AUTOINCREMENT,
                articulo_id INTEGER NOT NULL REFERENCES $stock(id) ON DELETE RESTRICT,
                cantidad REAL NOT NULL CHECK(cantidad > 0), unidad TEXT NOT NULL, fecha TEXT NOT NULL,
                empresa TEXT NOT NULL, remito TEXT NOT NULL, observaciones TEXT NOT NULL DEFAULT '', marca TEXT NOT NULL DEFAULT '',
                UNIQUE(id, articulo_id))""")
            db.execSQL("""CREATE TABLE $movements (id INTEGER PRIMARY KEY AUTOINCREMENT,
                articulo_id INTEGER NOT NULL REFERENCES $stock(id) ON DELETE RESTRICT,
                cantidad REAL NOT NULL CHECK(cantidad > 0), unidad TEXT NOT NULL, fecha TEXT NOT NULL, persona TEXT NOT NULL,
                personal_id INTEGER REFERENCES personal(id) ON DELETE RESTRICT,
                clase TEXT NOT NULL CHECK(clase IN ('Ingreso', 'Egreso'${if (tools) "" else ", 'Devolución'"})),
                tipo_material TEXT NOT NULL, observaciones TEXT NOT NULL, padre_id INTEGER,
                devuelto REAL NOT NULL DEFAULT 0 CHECK(devuelto >= 0 AND devuelto <= cantidad), fecha_devolucion TEXT NOT NULL DEFAULT '',
                compra_id INTEGER UNIQUE, UNIQUE(id, articulo_id),
                CHECK((clase='Ingreso' AND compra_id IS NOT NULL) OR (clase<>'Ingreso' AND compra_id IS NULL)),
                CHECK((clase='Devolución' AND padre_id IS NOT NULL) OR (clase<>'Devolución' AND padre_id IS NULL)),
                FOREIGN KEY(compra_id, articulo_id) REFERENCES $purchases(id, articulo_id) ON DELETE RESTRICT,
                FOREIGN KEY(padre_id, articulo_id) REFERENCES $movements(id, articulo_id) ON DELETE RESTRICT)""")
            db.execSQL("CREATE INDEX ${purchases}_fecha ON $purchases(fecha)")
            db.execSQL("CREATE INDEX ${purchases}_articulo ON $purchases(articulo_id)")
            db.execSQL("CREATE INDEX ${movements}_fecha ON $movements(fecha)")
            db.execSQL("CREATE INDEX ${movements}_articulo ON $movements(articulo_id)")
            db.execSQL("CREATE INDEX ${movements}_personal ON $movements(personal_id)")
            db.execSQL("CREATE INDEX ${movements}_padre ON $movements(padre_id)")
            db.execSQL("""INSERT INTO $purchases (id, articulo_id, cantidad, unidad, fecha, empresa, remito, observaciones, marca)
                SELECT i.id, i.articulo_id, i.cantidad, a.unidad, i.fecha, i.empresa, i.remito, i.observaciones, i.marca
                FROM ingresos i JOIN $stock a ON a.id=i.articulo_id WHERE i.tipo='$stock'""")
            db.execSQL("""INSERT INTO $movements (id, articulo_id, cantidad, unidad, fecha, persona, personal_id, clase, tipo_material,
                observaciones, padre_id, devuelto, fecha_devolucion, compra_id)
                SELECT id, articulo_id, cantidad, unidad, fecha, persona, personal_id, clase, tipo_material,
                observaciones, padre_id, devuelto, fecha_devolucion, ingreso_id FROM movimientos WHERE stock='$stock' ORDER BY id""")
        }
        db.execSQL("DROP TABLE movimientos")
        db.execSQL("DROP TABLE ingresos")
    }
    private fun createMovementSchema(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE materiales ADD COLUMN tipo_material TEXT NOT NULL DEFAULT '${MaterialTypes.CONSTRUCTION}'")
        db.execSQL("ALTER TABLE herramientas ADD COLUMN tipo_material TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE TABLE personal (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL, apellido TEXT NOT NULL, obra TEXT NOT NULL, apodo TEXT NOT NULL)")
        db.execSQL("""CREATE TABLE movimientos (id INTEGER PRIMARY KEY AUTOINCREMENT, stock TEXT NOT NULL, articulo_id INTEGER NOT NULL,
            cantidad REAL NOT NULL CHECK(cantidad > 0), unidad TEXT NOT NULL, fecha TEXT NOT NULL, persona TEXT NOT NULL,
            personal_id INTEGER, clase TEXT NOT NULL, tipo_material TEXT NOT NULL, observaciones TEXT NOT NULL,
            padre_id INTEGER, devuelto REAL NOT NULL DEFAULT 0 CHECK(devuelto >= 0 AND devuelto <= cantidad), fecha_devolucion TEXT NOT NULL DEFAULT '', ingreso_id INTEGER)""")
        db.execSQL("CREATE INDEX movimientos_stock_fecha ON movimientos(stock, fecha)")
        db.execSQL("CREATE INDEX movimientos_padre ON movimientos(padre_id)")
    }
    private fun table(tools: Boolean) = if (tools) "herramientas" else "materiales"
    private fun movementTable(tools: Boolean) = if (tools) "movimientos_herramientas" else "movimientos_materiales"
    private fun purchaseTable(tools: Boolean) = if (tools) "compras_herramientas" else "compras_materiales"
    override var stockName: String
        get() = readableDatabase.rawQuery("SELECT valor FROM configuracion WHERE clave='nombre'", null).use { it.moveToFirst(); it.getString(0) }
        set(value) { require(value.trim().isNotEmpty()); writableDatabase.execSQL("UPDATE configuracion SET valor=? WHERE clave='nombre'", arrayOf(value.trim())) }
    override fun items(tools: Boolean): List<StockItem> = readableDatabase.rawQuery("SELECT * FROM ${table(tools)} ORDER BY nombre COLLATE NOCASE", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(StockItem(cursor.getLong(0), cursor.getString(1), cursor.getDouble(2), cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getString(6), cursor.getString(7), cursor.getString(8))) }
    }
    override fun updateImage(tools: Boolean, itemId: Long, image: String) {
        check(writableDatabase.update(table(tools), ContentValues().apply {
            put("imagen", image)
        }, "id=?", arrayOf(itemId.toString())) == 1) { "Artículo inexistente" }
    }
    override fun receive(tools: Boolean, entry: StockEntry) {
        require(entry.quantity.isFinite() && entry.quantity > 0)
        require(entry.name.isNotBlank())
        require(entry.unit != "unidades" || entry.quantity % 1.0 == 0.0)
        require(entry.unit in if (tools) listOf("unidades") else listOf("mts", "kg", "unidades"))
        validateMovementDate(entry.date)
        require(tools || entry.materialType in MaterialTypes.all) { "Seleccioná un tipo de material válido." }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = if (entry.itemId == 0L) {
                db.insertOrThrow(table(tools), null, ContentValues().apply {
                    put("nombre", entry.name.trim()); put("cantidad", entry.quantity); put("unidad", entry.unit)
                    put("observaciones", entry.notes); put("imagen", entry.image); put("marca", entry.brand); put("empresa", entry.company)
                    put("tipo_material", if (tools) "" else entry.materialType)
                })
            } else {
                val current = items(tools).first { it.id == entry.itemId }
                require(current.unit == entry.unit)
                val updated = stockSum(current.quantity, entry.quantity)
                require(updated.isFinite())
                db.update(table(tools), ContentValues().apply {
                    put("cantidad", updated)
                    if (entry.notes.isNotBlank()) put("observaciones", entry.notes)
                    if (entry.image.isNotBlank()) put("imagen", entry.image)
                    if (entry.brand.isNotBlank()) put("marca", entry.brand)
                    if (entry.company.isNotBlank()) put("empresa", entry.company)
                }, "id=?", arrayOf(entry.itemId.toString()))
                current.id
            }
            val purchaseId = db.insertOrThrow(purchaseTable(tools), null, ContentValues().apply {
                put("articulo_id", id); put("cantidad", entry.quantity); put("unidad", entry.unit)
                put("remito", entry.receipt.ifBlank { "no hay" }); put("fecha", entry.date)
                put("observaciones", entry.notes); put("marca", entry.brand); put("empresa", entry.company)
            })
            val item = items(tools).first { it.id == id }
            insertMovement(db, tools, item, entry.quantity, entry.date, null, "Ingreso", entry.notes, purchaseId = purchaseId)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    override fun personnel(): List<Worker> = readableDatabase.rawQuery("SELECT * FROM personal ORDER BY apellido COLLATE NOCASE, nombre COLLATE NOCASE", null).use { c ->
        buildList { while (c.moveToNext()) add(Worker(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4))) }
    }
    override fun addWorker(name: String, surname: String, worksite: String, nickname: String) {
        require(name.isNotBlank() && surname.isNotBlank() && worksite.isNotBlank()) { "Completá nombre, apellido y obra." }
        writableDatabase.insertOrThrow("personal", null, ContentValues().apply {
            put("nombre", name.trim()); put("apellido", surname.trim()); put("obra", worksite.trim()); put("apodo", nickname.trim())
        })
    }
    override fun movements(tools: Boolean): List<StockMovement> = readableDatabase.rawQuery("""SELECT m.id, m.articulo_id, a.nombre,
        m.cantidad, m.unidad, m.fecha, m.persona, m.personal_id, m.clase, m.tipo_material, m.observaciones,
        m.padre_id, m.devuelto, m.fecha_devolucion, m.compra_id FROM ${movementTable(tools)} m
        JOIN ${table(tools)} a ON a.id=m.articulo_id ORDER BY m.fecha DESC, m.id DESC""", null).use { c ->
        buildList { while (c.moveToNext()) add(StockMovement(c.getLong(0), c.getLong(1), c.getString(2), c.getDouble(3), c.getString(4), c.getString(5), c.getString(6), if (c.isNull(7)) null else c.getLong(7), c.getString(8), c.getString(9), c.getString(10), if (c.isNull(11)) null else c.getLong(11), c.getDouble(12), c.getString(13), if (c.isNull(14)) null else c.getLong(14))) }
    }
    override fun purchases(tools: Boolean): List<StockPurchase> = readableDatabase.rawQuery("""SELECT c.id, c.articulo_id, a.nombre,
        c.cantidad, c.unidad, c.fecha, c.empresa, c.remito FROM ${purchaseTable(tools)} c
        JOIN ${table(tools)} a ON a.id=c.articulo_id ORDER BY c.fecha DESC, c.id DESC""", null).use { c ->
        buildList { while (c.moveToNext()) add(StockPurchase(c.getLong(0), c.getLong(1), c.getString(2), c.getDouble(3), c.getString(4), c.getString(5), c.getString(6), c.getString(7))) }
    }
    private fun transaction(action: (SQLiteDatabase) -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try { action(db); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
    private fun changeQuantity(db: SQLiteDatabase, tools: Boolean, item: StockItem, delta: Double) {
        val total = stockSum(item.quantity, delta)
        require(total.isFinite() && total >= 0) { "No se puede realizar la operación: el stock disponible es insuficiente." }
        db.update(table(tools), ContentValues().apply { put("cantidad", total) }, "id=?", arrayOf(item.id.toString()))
    }
    private fun insertMovement(db: SQLiteDatabase, tools: Boolean, item: StockItem, quantity: Double, date: String, worker: Worker?, kind: String, notes: String, parentId: Long? = null, purchaseId: Long? = null) {
        db.insertOrThrow(movementTable(tools), null, ContentValues().apply {
            put("articulo_id", item.id); put("cantidad", quantity); put("unidad", item.unit)
            put("fecha", date); put("persona", worker?.displayName.orEmpty()); put("personal_id", worker?.id)
            put("clase", kind); put("tipo_material", item.materialType); put("observaciones", notes.trim())
            put("padre_id", parentId); put("compra_id", purchaseId)
        })
    }
    override fun issue(tools: Boolean, itemId: Long, quantity: Double, date: String, workerId: Long, notes: String) = transaction { db ->
        val item = items(tools).firstOrNull { it.id == itemId } ?: error("El artículo ya no existe.")
        validateMovementQuantity(quantity, item.unit)
        validateMovementDate(date)
        require(quantity <= item.quantity) { "La cantidad supera el stock disponible (${formatStockQuantity(item.quantity)} ${item.unit})." }
        val worker = personnel().firstOrNull { it.id == workerId } ?: error("Seleccioná personal de obra.")
        changeQuantity(db, tools, item, -quantity)
        insertMovement(db, tools, item, quantity, date, worker, "Egreso", notes)
    }
    override fun returnStock(tools: Boolean, movementId: Long, quantity: Double, date: String, workerId: Long?, notes: String) = transaction { db ->
        val movement = movements(tools).firstOrNull { it.id == movementId } ?: error("El movimiento ya no existe.")
        require(movement.kind == "Egreso") { "Solo se pueden devolver egresos." }
        validateMovementQuantity(quantity, movement.unit)
        validateMovementDate(date)
        require(date >= movement.date && (movement.returnDate.isBlank() || date >= movement.returnDate)) { "La devolución no puede ser anterior al egreso ni a la última devolución." }
        require(quantity <= movement.pending) { "La cantidad supera lo pendiente de devolución (${formatStockQuantity(movement.pending)} ${movement.unit})." }
        val worker = if (tools) null else (personnel().firstOrNull { it.id == workerId } ?: error("Seleccioná la persona que devuelve el material."))
        val item = items(tools).first { it.id == movement.itemId }
        changeQuantity(db, tools, item, quantity)
        db.update(movementTable(tools), ContentValues().apply {
            put("devuelto", stockSum(movement.returned, quantity)); put("fecha_devolucion", date)
        }, "id=?", arrayOf(movement.id.toString()))
        if (!tools) insertMovement(db, false, item, quantity, date, worker, "Devolución", notes, parentId = movement.id)
    }
    override fun deleteMovement(tools: Boolean, movementId: Long) = transaction { db ->
        val movement = movements(tools).firstOrNull { it.id == movementId } ?: error("El movimiento ya no existe.")
        val item = items(tools).first { it.id == movement.itemId }
        val delta = if (movement.kind == "Egreso") movement.pending else -movement.quantity
        changeQuantity(db, tools, item, delta)
        if (movement.kind == "Devolución") {
            val parent = movements(tools).first { it.id == movement.parentId }
            val latest = db.rawQuery("SELECT MAX(fecha) FROM ${movementTable(tools)} WHERE padre_id=? AND id<>?", arrayOf(parent.id.toString(), movement.id.toString())).use { it.moveToFirst(); if (it.isNull(0)) "" else it.getString(0) }
            db.update(movementTable(tools), ContentValues().apply { put("devuelto", stockSum(parent.returned, -movement.quantity)); put("fecha_devolucion", latest) }, "id=?", arrayOf(parent.id.toString()))
        }
        if (movement.kind == "Egreso") db.delete(movementTable(tools), "padre_id=?", arrayOf(movement.id.toString()))
        db.delete(movementTable(tools), "id=?", arrayOf(movement.id.toString()))
        movement.purchaseId?.let { db.delete(purchaseTable(tools), "id=?", arrayOf(it.toString())) }
    }
}

