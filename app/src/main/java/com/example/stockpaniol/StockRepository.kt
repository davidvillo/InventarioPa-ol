package com.example.stockpaniol

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Local repository boundary: a future remote implementation can preserve this contract. */
interface StockRepository : PersonnelRepository, MeasurementRepository {
    fun items(tools: Boolean): List<StockItem>
    fun receive(tools: Boolean, entry: StockEntry)
    fun updateImage(tools: Boolean, itemId: Long, image: String)
    fun movements(tools: Boolean): List<StockMovement>
    fun purchases(tools: Boolean): List<StockPurchase>
    fun issue(tools: Boolean, itemId: Long, quantity: Double, date: String, workerId: Long, notes: String, unitIds: List<Long> = emptyList())
    fun returnStock(tools: Boolean, movementId: Long, quantity: Double, date: String, workerId: Long?, notes: String, unitIds: List<Long> = emptyList())
    fun identifyUnit(tools: Boolean, unitId: Long, brand: String, identifier: String)
    fun deleteMovement(tools: Boolean, movementId: Long)
    var stockName: String
}

class LocalStockRepository(context: Context) : SQLiteOpenHelper(context, "stock_paniol.db", null, 6), StockRepository {
    private val people = LocalPersonnelRepository { writableDatabase }
    private val unitCatalogue = LocalMeasurementRepository { writableDatabase }
    override fun measurements(includeInactive: Boolean) = unitCatalogue.measurements(includeInactive)
    override fun addMeasurement(name: String, wholeNumbers: Boolean) = unitCatalogue.addMeasurement(name, wholeNumbers)
    override fun updateMeasurement(id: Long, name: String, wholeNumbers: Boolean) = unitCatalogue.updateMeasurement(id, name, wholeNumbers)
    override fun deleteMeasurement(id: Long) = unitCatalogue.deleteMeasurement(id)
    override fun personnel(includeInactive: Boolean) = people.personnel(includeInactive)
    override fun worksites(includeInactive: Boolean) = people.worksites(includeInactive)
    override fun addWorker(draft: WorkerDraft) = people.addWorker(draft)
    override fun addWorker(name: String, surname: String, worksite: String, nickname: String) = people.addWorker(name, surname, worksite, nickname)
    override fun updateWorker(workerId: Long, draft: WorkerDraft) = people.updateWorker(workerId, draft)
    override fun deleteWorker(workerId: Long) = people.deleteWorker(workerId)
    override fun addWorksite(name: String) = people.addWorksite(name)
    override fun updateWorksite(worksiteId: Long, name: String) = people.updateWorksite(worksiteId, name)
    override fun deleteWorksite(worksiteId: Long) = people.deleteWorksite(worksiteId)
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
        createUnitSchema(db)
        LocalPersonnelRepository.migrate(db)
        LocalMeasurementRepository.migrate(db)
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
        if (oldVersion < 4) {
            createUnitSchema(db)
            migrateToolUnits(db)
        }
        if (oldVersion < 5) LocalPersonnelRepository.migrate(db)
        if (oldVersion < 6) LocalMeasurementRepository.migrate(db)
    }
    private fun unitTable(tools: Boolean) = "unidades_${table(tools)}"
    private fun unitMovementTable(tools: Boolean) = "unidades_movimientos_${table(tools)}"

    private fun createUnitSchema(db: SQLiteDatabase) {
        for (tools in listOf(false, true)) {
            db.execSQL("ALTER TABLE ${table(tools)} ADD COLUMN control TEXT NOT NULL DEFAULT '${if (tools) "INDIVIDUAL" else "QUANTITY"}' CHECK(control IN ('QUANTITY','INDIVIDUAL'))")
            db.execSQL("""CREATE TABLE ${unitTable(tools)} (id INTEGER PRIMARY KEY AUTOINCREMENT,
                articulo_id INTEGER NOT NULL REFERENCES ${table(tools)}(id),
                identificador TEXT NOT NULL COLLATE NOCASE UNIQUE, marca TEXT NOT NULL,
                compra_id INTEGER REFERENCES ${purchaseTable(tools)}(id), legado INTEGER NOT NULL DEFAULT 0,
                UNIQUE(id, articulo_id))""")
            db.execSQL("""CREATE TABLE ${unitMovementTable(tools)} (movimiento_id INTEGER NOT NULL,
                unidad_id INTEGER NOT NULL, articulo_id INTEGER NOT NULL,
                devuelta INTEGER NOT NULL DEFAULT 0 CHECK(devuelta IN (0,1)),
                identificador TEXT NOT NULL, marca TEXT NOT NULL, legado INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(movimiento_id, unidad_id),
                FOREIGN KEY(movimiento_id, articulo_id) REFERENCES ${movementTable(tools)}(id, articulo_id) ON DELETE CASCADE,
                FOREIGN KEY(unidad_id, articulo_id) REFERENCES ${unitTable(tools)}(id, articulo_id) ON DELETE RESTRICT)""")
            db.execSQL("CREATE INDEX ${unitTable(tools)}_articulo ON ${unitTable(tools)}(articulo_id)")
            db.execSQL("CREATE UNIQUE INDEX ${unitMovementTable(tools)}_prestamo ON ${unitMovementTable(tools)}(unidad_id) WHERE devuelta=0")
        }
    }

    /** Old stock only knew totals: preserve them and label inferred loans explicitly. */
    private fun migrateToolUnits(db: SQLiteDatabase) {
        db.rawQuery("SELECT id, cantidad, marca FROM herramientas", null).use { c ->
            while (c.moveToNext()) {
                val itemId = c.getLong(0)
                repeat(c.getInt(1)) { createUnit(db, true, itemId, c.getString(2), "", null, true) }
                db.rawQuery("SELECT id, cantidad-devuelto FROM movimientos_herramientas WHERE articulo_id=? AND clase='Egreso' AND cantidad>devuelto ORDER BY id", arrayOf(itemId.toString())).use { loans ->
                    while (loans.moveToNext()) repeat(loans.getInt(1)) {
                        val unitId = createUnit(db, true, itemId, c.getString(2), "", null, true)
                        linkUnits(db, true, loans.getLong(0), itemId, listOf(unitId), false)
                    }
                }
            }
        }
    }

    private fun createUnit(db: SQLiteDatabase, tools: Boolean, itemId: Long, brand: String, identifier: String, purchaseId: Long?, legacy: Boolean = false): Long {
        val id = db.insertOrThrow(unitTable(tools), null, ContentValues().apply {
            put("articulo_id", itemId); put("identificador", identifier.ifBlank { "TEMP-${java.util.UUID.randomUUID()}" })
            put("marca", brand.trim()); put("compra_id", purchaseId); put("legado", if (legacy) 1 else 0)
        })
        if (identifier.isBlank()) {
            var code = "${if (tools) "H" else "M"}-$id"
            while (db.rawQuery("SELECT 1 FROM ${unitTable(tools)} WHERE identificador=?", arrayOf(code)).use { it.moveToFirst() }) code += "-A"
            db.update(unitTable(tools), ContentValues().apply { put("identificador", code) }, "id=?", arrayOf(id.toString()))
        }
        return id
    }

    private fun inventoryUnits(tools: Boolean, db: SQLiteDatabase = readableDatabase): List<InventoryUnit> = db.rawQuery("""SELECT u.id, u.articulo_id, u.identificador, u.marca,
        CASE WHEN m.id IS NOT NULL THEN COALESCE(NULLIF(m.persona, ''), 'Préstamo histórico sin personal') ELSE '' END,
        u.legado, m.id FROM ${unitTable(tools)} u
        LEFT JOIN ${unitMovementTable(tools)} l ON l.unidad_id=u.id AND l.devuelta=0
        LEFT JOIN ${movementTable(tools)} m ON m.id=l.movimiento_id ORDER BY u.id""", null).use { c ->
        buildList { while (c.moveToNext()) add(InventoryUnit(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4), c.getInt(5) == 1, if (c.isNull(6)) null else c.getLong(6))) }
    }

    private fun movementUnits(tools: Boolean): Map<Long, List<MovementUnit>> = readableDatabase.rawQuery("""SELECT l.movimiento_id,
        u.id, u.articulo_id, l.identificador, l.marca, l.legado, l.devuelta FROM ${unitMovementTable(tools)} l
        JOIN ${unitTable(tools)} u ON u.id=l.unidad_id ORDER BY u.id""", null).use { c ->
        buildList { while (c.moveToNext()) add(c.getLong(0) to MovementUnit(InventoryUnit(c.getLong(1), c.getLong(2), c.getString(3), c.getString(4), legacy = c.getInt(5) == 1), c.getInt(6) == 1)) }.groupBy({ it.first }, { it.second })
    }

    private fun linkUnits(db: SQLiteDatabase, tools: Boolean, movementId: Long, itemId: Long, ids: List<Long>, returned: Boolean) {
        val units = inventoryUnits(tools, db).associateBy { it.id }
        ids.forEach { id ->
            val unit = units.getValue(id)
            db.insertOrThrow(unitMovementTable(tools), null, ContentValues().apply {
                put("movimiento_id", movementId); put("unidad_id", id); put("articulo_id", itemId)
                put("devuelta", if (returned) 1 else 0); put("identificador", unit.identifier); put("marca", unit.brand); put("legado", if (unit.legacy) 1 else 0)
            })
        }
    }

    override fun identifyUnit(tools: Boolean, unitId: Long, brand: String, identifier: String) = transaction { db ->
        require(identifier.isNotBlank()) { "Ingresá un identificador." }
        require(inventoryUnits(tools).none { it.id != unitId && it.identifier.equals(identifier.trim(), true) }) { "Ese identificador ya existe." }
        check(db.update(unitTable(tools), ContentValues().apply { put("marca", brand.trim()); put("identificador", identifier.trim()); put("legado", 0) }, "id=?", arrayOf(unitId.toString())) == 1) { "Unidad inexistente." }
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
    override fun items(tools: Boolean): List<StockItem> {
        val units = inventoryUnits(tools).groupBy { it.itemId }
        return readableDatabase.rawQuery("SELECT a.*, u.entera FROM ${table(tools)} a JOIN unidades_medida u ON u.id=a.unidad_medida_id ORDER BY a.nombre COLLATE NOCASE", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(StockItem(cursor.getLong(0), cursor.getString(1), cursor.getDouble(2), cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getString(6), cursor.getString(7), cursor.getString(8), StockControl.valueOf(cursor.getString(9)), units[cursor.getLong(0)].orEmpty(), cursor.getInt(11) == 1)) }
        }
    }
    override fun updateImage(tools: Boolean, itemId: Long, image: String) {
        check(writableDatabase.update(table(tools), ContentValues().apply {
            put("imagen", image)
        }, "id=?", arrayOf(itemId.toString())) == 1) { "Artículo inexistente" }
    }
    override fun receive(tools: Boolean, entry: StockEntry) {
        require(entry.quantity.isFinite() && entry.quantity > 0)
        require(entry.name.isNotBlank())
        validateMovementDate(entry.date)
        require(tools || entry.materialType in MaterialTypes.all) { "Seleccioná un tipo de material válido." }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val current = if (entry.itemId != 0L) items(tools).firstOrNull { it.id == entry.itemId }
                ?: error("El artículo ya no existe.") else items(tools).firstOrNull { it.name.equals(entry.name.trim(), true) }
            val control = current?.control ?: if (tools) StockControl.INDIVIDUAL else entry.control
            val measure = measurements(true).firstOrNull { it.name.equals(entry.unit, true) } ?: error("Seleccioná una unidad válida.")
            require(measure.active || current != null || (control == StockControl.INDIVIDUAL && measure.counting)) { "Esa unidad fue quitada de la lista." }
            val policy = if (control == StockControl.INDIVIDUAL) InventoryPolicy.ByUnit else InventoryPolicy.ByQuantity
            policy.validateReceipt(entry.quantity, entry.unit, entry.identifier, measure.wholeNumbers, measure.counting)
            if (current != null) {
                require(current.unit == entry.unit && (tools || current.materialType == entry.materialType)) { "El nombre ya existe con otra unidad o tipo de material." }
                require(tools || current.control == entry.control) { "Ese material ya existe con otra forma de control." }
            }
            require(entry.identifier.isBlank() || inventoryUnits(tools).none { it.identifier.equals(entry.identifier.trim(), true) }) { "Ese identificador ya existe." }
            val id = if (current == null) {
                db.insertOrThrow(table(tools), null, ContentValues().apply {
                    put("nombre", entry.name.trim()); put("cantidad", entry.quantity); put("unidad", entry.unit)
                    put("observaciones", entry.notes); put("imagen", entry.image); put("marca", entry.brand); put("empresa", entry.company)
                    put("tipo_material", if (tools) "" else entry.materialType)
                    put("control", control.name)
                    put("unidad_medida_id", measure.id)
                })
            } else {
                require(current.unit == entry.unit)
                val updated = stockSum(current.quantity, entry.quantity)
                require(updated.isFinite())
                db.update(table(tools), ContentValues().apply {
                    put("cantidad", updated)
                    if (entry.notes.isNotBlank()) put("observaciones", entry.notes)
                    if (entry.image.isNotBlank()) put("imagen", entry.image)
                    if (control == StockControl.QUANTITY && entry.brand.isNotBlank()) put("marca", entry.brand)
                    if (entry.company.isNotBlank()) put("empresa", entry.company)
                }, "id=?", arrayOf(current.id.toString()))
                current.id
            }
            val purchaseId = db.insertOrThrow(purchaseTable(tools), null, ContentValues().apply {
                put("articulo_id", id); put("cantidad", entry.quantity); put("unidad", entry.unit)
                put("remito", entry.receipt.ifBlank { "no hay" }); put("fecha", entry.date)
                put("observaciones", entry.notes); put("marca", entry.brand); put("empresa", entry.company)
            })
            val item = items(tools).first { it.id == id }
            val movementId = insertMovement(db, tools, item, entry.quantity, entry.date, null, "Ingreso", entry.notes, purchaseId = purchaseId)
            if (item.individuallyTracked) {
                val unitIds = List(entry.quantity.toInt()) { createUnit(db, tools, id, entry.brand, entry.identifier.trim(), purchaseId) }
                linkUnits(db, tools, movementId, id, unitIds, true)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    override fun movements(tools: Boolean): List<StockMovement> {
        val units = movementUnits(tools)
        return readableDatabase.rawQuery("""SELECT m.id, m.articulo_id, a.nombre,
        m.cantidad, m.unidad, m.fecha, m.persona, m.personal_id, m.clase, m.tipo_material, m.observaciones,
        m.padre_id, m.devuelto, m.fecha_devolucion, m.compra_id, a.control, m.cantidad_entera FROM ${movementTable(tools)} m
        JOIN ${table(tools)} a ON a.id=m.articulo_id ORDER BY m.fecha DESC, m.id DESC""", null).use { c ->
        buildList { while (c.moveToNext()) add(StockMovement(c.getLong(0), c.getLong(1), c.getString(2), c.getDouble(3), c.getString(4), c.getString(5), c.getString(6), if (c.isNull(7)) null else c.getLong(7), c.getString(8), c.getString(9), c.getString(10), if (c.isNull(11)) null else c.getLong(11), c.getDouble(12), c.getString(13), if (c.isNull(14)) null else c.getLong(14), StockControl.valueOf(c.getString(15)), units[c.getLong(0)].orEmpty(), c.getInt(16)==1)) }
        }
    }
    override fun purchases(tools: Boolean): List<StockPurchase> {
        val units = movementUnits(tools)
        return readableDatabase.rawQuery("""SELECT c.id, c.articulo_id, a.nombre,
            c.cantidad, c.unidad, c.fecha, c.empresa, c.remito, m.id FROM ${purchaseTable(tools)} c
            JOIN ${table(tools)} a ON a.id=c.articulo_id LEFT JOIN ${movementTable(tools)} m ON m.compra_id=c.id ORDER BY c.fecha DESC, c.id DESC""", null).use { c ->
            buildList { while (c.moveToNext()) add(StockPurchase(c.getLong(0), c.getLong(1), c.getString(2), c.getDouble(3), c.getString(4), c.getString(5), c.getString(6), c.getString(7), units[c.getLong(8)].orEmpty().joinToString("; ") { it.unit.label })) }
        }
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
    private fun insertMovement(db: SQLiteDatabase, tools: Boolean, item: StockItem, quantity: Double, date: String, worker: Worker?, kind: String, notes: String, parentId: Long? = null, purchaseId: Long? = null): Long {
        return db.insertOrThrow(movementTable(tools), null, ContentValues().apply {
            put("articulo_id", item.id); put("cantidad", quantity); put("unidad", item.unit)
            put("fecha", date); put("persona", worker?.displayName.orEmpty()); put("personal_id", worker?.id)
            put("clase", kind); put("tipo_material", item.materialType); put("observaciones", notes.trim())
            put("padre_id", parentId); put("compra_id", purchaseId)
            put("cantidad_entera", if(item.wholeNumbers) 1 else 0)
        })
    }
    override fun issue(tools: Boolean, itemId: Long, quantity: Double, date: String, workerId: Long, notes: String, unitIds: List<Long>) = transaction { db ->
        val item = items(tools).firstOrNull { it.id == itemId } ?: error("El artículo ya no existe.")
        validateMovementQuantity(quantity, item.unit, item.wholeNumbers)
        validateMovementDate(date)
        item.policy.validateSelection(quantity, unitIds)
        require(unitIds.all { id -> item.availableUnits.any { it.id == id } }) { "Alguna unidad ya está prestada o pertenece a otro artículo." }
        require(quantity <= item.quantity) { "La cantidad supera el stock disponible (${formatStockQuantity(item.quantity)} ${item.unit})." }
        val worker = personnel().firstOrNull { it.id == workerId } ?: error("Seleccioná personal de obra.")
        changeQuantity(db, tools, item, -quantity)
        val movementId = insertMovement(db, tools, item, quantity, date, worker, "Egreso", notes)
        linkUnits(db, tools, movementId, item.id, unitIds, false)
    }
    override fun returnStock(tools: Boolean, movementId: Long, quantity: Double, date: String, workerId: Long?, notes: String, unitIds: List<Long>) = transaction { db ->
        val movement = movements(tools).firstOrNull { it.id == movementId } ?: error("El movimiento ya no existe.")
        require(movement.kind == "Egreso") { "Solo se pueden devolver egresos." }
        validateMovementQuantity(quantity, movement.unit, movement.wholeNumbers)
        validateMovementDate(date)
        require(date >= movement.date && (movement.returnDate.isBlank() || date >= movement.returnDate)) { "La devolución no puede ser anterior al egreso ni a la última devolución." }
        require(quantity <= movement.pending) { "La cantidad supera lo pendiente de devolución (${formatStockQuantity(movement.pending)} ${movement.unit})." }
        val worker = if (tools) null else (personnel(true).firstOrNull { it.id == workerId && (it.active || it.id == movement.workerId) } ?: error("Seleccioná la persona que devuelve el material."))
        val item = items(tools).first { it.id == movement.itemId }
        item.policy.validateSelection(quantity, unitIds)
        require(unitIds.all { id -> movement.units.any { it.unit.id == id && !it.returned } }) { "Seleccioná una unidad pendiente de este préstamo." }
        changeQuantity(db, tools, item, quantity)
        unitIds.forEach { id -> db.update(unitMovementTable(tools), ContentValues().apply { put("devuelta", 1) }, "movimiento_id=? AND unidad_id=?", arrayOf(movementId.toString(), id.toString())) }
        db.update(movementTable(tools), ContentValues().apply {
            put("devuelto", stockSum(movement.returned, quantity)); put("fecha_devolucion", date)
        }, "id=?", arrayOf(movement.id.toString()))
        if (!tools) {
            val returnId = insertMovement(db, false, item, quantity, date, worker, "Devolución", notes, parentId = movement.id)
            linkUnits(db, false, returnId, item.id, unitIds, true)
        }
    }
    override fun deleteMovement(tools: Boolean, movementId: Long) = transaction { db ->
        val movement = movements(tools).firstOrNull { it.id == movementId } ?: error("El movimiento ya no existe.")
        val item = items(tools).first { it.id == movement.itemId }
        if (item.individuallyTracked && movement.kind == "Ingreso") {
            require(movement.units.size.toDouble() == movement.quantity) { "Este ingreso histórico no identifica sus unidades y debe conservarse." }
            val ids = movement.units.map { it.unit.id }
            require(movements(tools).none { it.id != movement.id && it.units.any { u -> u.unit.id in ids } }) { "No se puede eliminar el ingreso: sus unidades tienen movimientos posteriores." }
            db.delete(unitMovementTable(tools), "movimiento_id=?", arrayOf(movement.id.toString()))
            ids.forEach { db.delete(unitTable(tools), "id=?", arrayOf(it.toString())) }
        }
        val delta = if (movement.kind == "Egreso") movement.pending else -movement.quantity
        changeQuantity(db, tools, item, delta)
        if (movement.kind == "Devolución") {
            val parent = movements(tools).first { it.id == movement.parentId }
            if (item.individuallyTracked) {
                val ids = movement.units.map { it.unit.id }
                require(ids.all { id -> item.availableUnits.any { it.id == id } }) { "No se puede eliminar la devolución: alguna unidad volvió a prestarse." }
                require(movements(tools).none { it.kind == "Egreso" && it.id > parent.id && it.units.any { u -> u.unit.id in ids } }) { "No se puede eliminar la devolución: alguna unidad tiene préstamos posteriores." }
                ids.forEach { id -> db.update(unitMovementTable(tools), ContentValues().apply { put("devuelta", 0) }, "movimiento_id=? AND unidad_id=?", arrayOf(parent.id.toString(), id.toString())) }
            }
            val latest = db.rawQuery("SELECT MAX(fecha) FROM ${movementTable(tools)} WHERE padre_id=? AND id<>?", arrayOf(parent.id.toString(), movement.id.toString())).use { it.moveToFirst(); if (it.isNull(0)) "" else it.getString(0) }
            db.update(movementTable(tools), ContentValues().apply { put("devuelto", stockSum(parent.returned, -movement.quantity)); put("fecha_devolucion", latest) }, "id=?", arrayOf(parent.id.toString()))
        }
        if (movement.kind == "Egreso") db.delete(movementTable(tools), "padre_id=?", arrayOf(movement.id.toString()))
        db.delete(movementTable(tools), "id=?", arrayOf(movement.id.toString()))
        movement.purchaseId?.let { db.delete(purchaseTable(tools), "id=?", arrayOf(it.toString())) }
    }
}

