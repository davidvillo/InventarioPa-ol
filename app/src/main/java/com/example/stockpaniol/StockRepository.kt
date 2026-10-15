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
    fun updateImages(tools: Boolean, itemId: Long, images: List<String>)
    fun movements(tools: Boolean): List<StockMovement>
    fun purchases(tools: Boolean): List<StockPurchase>
    fun issue(tools: Boolean, itemId: Long, quantity: Double, date: String, workerId: Long, notes: String, unitIds: List<Long> = emptyList())
    fun returnStock(tools: Boolean, movementId: Long, quantity: Double, date: String, workerId: Long?, notes: String, unitIds: List<Long> = emptyList(), damagedUnits: Map<Long, String> = emptyMap())
    fun changeToolState(unitId: Long, action: ToolAction, date: String, notes: String, workshop: String = "")
    fun returnRental(unitId: Long, date: String, notes: String)
    fun identifyUnit(tools: Boolean, unitId: Long, brand: String, identifier: String)
    fun deleteMovement(tools: Boolean, movementId: Long)
    var stockName: String
}

class LocalStockRepository(context: Context) : SQLiteOpenHelper(context, "stock_paniol.db", null, 8), StockRepository {
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
        ToolLifecycle.migrate(db)
        ToolRentals.migrate(db)
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
        if (oldVersion < 7) ToolLifecycle.migrate(db)
        if (oldVersion < 8) ToolRentals.migrate(db)
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
        val code = identifier.ifBlank { nextUnitIdentifier(db, tools, itemId) }
        val id = db.insertOrThrow(unitTable(tools), null, ContentValues().apply {
            put("articulo_id", itemId); put("identificador", code)
            put("marca", brand.trim()); put("compra_id", purchaseId); put("legado", if (legacy) 1 else 0)
        })
        return id
    }

    private fun identifierSetting(db: SQLiteDatabase, key: String): String? =
        db.rawQuery("SELECT valor FROM configuracion WHERE clave=?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun saveIdentifierSetting(db: SQLiteDatabase, key: String, value: String) {
        db.execSQL("INSERT OR REPLACE INTO configuracion (clave, valor) VALUES (?, ?)", arrayOf(key, value))
    }

    private fun reserveIdentifierPrefixes(db: SQLiteDatabase, tools: Boolean): Map<Long, String> {
        val keyBase = "codigo.${table(tools)}."
        val saved = db.rawQuery("SELECT clave, valor FROM configuracion WHERE clave LIKE ?", arrayOf("$keyBase%.prefijo")).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) }
        }
        val used = saved.values.toMutableSet()
        return db.rawQuery("SELECT id, nombre FROM ${table(tools)} WHERE control='INDIVIDUAL' ORDER BY id", null).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val key = "$keyBase$id.prefijo"
                    val prefix = saved[key] ?: chooseStockPrefix(cursor.getString(1), used).also {
                        saveIdentifierSetting(db, key, it)
                        used.add(it)
                    }
                    put(id, prefix)
                }
            }
        }
    }

    private fun nextUnitIdentifier(db: SQLiteDatabase, tools: Boolean, itemId: Long): String {
        val prefix = identifierSetting(db, "codigo.${table(tools)}.$itemId.prefijo")
            ?: reserveIdentifierPrefixes(db, tools).getValue(itemId)
        val key = "codigo.${table(tools)}.$itemId.ultimo"
        var number = identifierSetting(db, key)?.toLong() ?: 0L
        var code: String
        do {
            check(number < Long.MAX_VALUE) { "Se agotó la numeración de este artículo." }
            number++
            code = formatStockIdentifier(prefix, number)
        } while (db.rawQuery("SELECT 1 FROM ${unitTable(tools)} WHERE identificador=?", arrayOf(code)).use { it.moveToFirst() })
        saveIdentifierSetting(db, key, number.toString())
        return code
    }

    private fun inventoryUnits(tools: Boolean, db: SQLiteDatabase = readableDatabase): List<InventoryUnit> {
        val states = if (tools && db.version >= 7) ToolLifecycle.states(db) else emptyMap()
        val rentals = if (tools && db.version >= 8) ToolRentals.all(db) else emptyMap()
        return db.rawQuery("""SELECT u.id, u.articulo_id, u.identificador, u.marca,
        CASE WHEN m.id IS NOT NULL THEN COALESCE(NULLIF(m.persona, ''), 'Préstamo histórico sin personal') ELSE '' END,
        u.legado, m.id FROM ${unitTable(tools)} u
        LEFT JOIN ${unitMovementTable(tools)} l ON l.unidad_id=u.id AND l.devuelta=0
        LEFT JOIN ${movementTable(tools)} m ON m.id=l.movimiento_id ORDER BY u.id""", null).use { c ->
        buildList { while (c.moveToNext()) {
            val state = states[c.getLong(0)]
            add(InventoryUnit(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4), c.getInt(5) == 1,
                if (c.isNull(6)) null else c.getLong(6), state?.condition ?: ToolCondition.OPERATIVE,
                state?.damage.orEmpty(), state?.date.orEmpty(), state?.workshop.orEmpty(), state?.repairDate.orEmpty(), rentals[c.getLong(0)]))
        } }
    }
    }

    private fun movementUnits(tools: Boolean): Map<Long, List<MovementUnit>> {
        val rentals = if (tools) ToolRentals.all(readableDatabase) else emptyMap()
        return readableDatabase.rawQuery("""SELECT l.movimiento_id,
        u.id, u.articulo_id, l.identificador, l.marca, l.legado, l.devuelta FROM ${unitMovementTable(tools)} l
        JOIN ${unitTable(tools)} u ON u.id=l.unidad_id ORDER BY u.id""", null).use { c ->
        buildList { while (c.moveToNext()) add(c.getLong(0) to MovementUnit(InventoryUnit(c.getLong(1), c.getLong(2), c.getString(3), c.getString(4), legacy = c.getInt(5) == 1, rental = rentals[c.getLong(1)]), c.getInt(6) == 1)) }.groupBy({ it.first }, { it.second })
    }
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
    override fun updateImage(tools: Boolean, itemId: Long, image: String) =
        updateImages(tools, itemId, StockPhotos.decode(image))

    override fun updateImages(tools: Boolean, itemId: Long, images: List<String>) {
        val encoded = StockPhotos.encode(images)
        check(writableDatabase.update(table(tools), ContentValues().apply {
            put("imagen", encoded)
        }, "id=?", arrayOf(itemId.toString())) == 1) { "Artículo inexistente" }
    }
    override fun receive(tools: Boolean, entry: StockEntry) {
        require(entry.quantity.isFinite() && entry.quantity >= 0) { "Ingresá una cantidad igual o mayor a cero." }
        require(entry.name.isNotBlank())
        validateMovementDate(entry.date)
        require(tools || entry.materialType in MaterialTypes.all) { "Seleccioná un tipo de material válido." }
        require(entry.rental == null || tools) { "El alquiler se registra en herramientas." }
        require(entry.rental == null || entry.rental.supplier.isNotBlank()) { "Ingresá el proveedor del alquiler." }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val current = if (entry.itemId != 0L) items(tools).firstOrNull { it.id == entry.itemId }
                ?: error("El artículo ya no existe.") else items(tools).firstOrNull { it.name.equals(entry.name.trim(), true) }
            val control = current?.control ?: if (tools) StockControl.INDIVIDUAL else entry.control
            require(entry.rental == null || control == StockControl.INDIVIDUAL) { "El alquiler debe identificar cada unidad." }
            val photos = StockPhotos.encode((current?.images.orEmpty() + StockPhotos.decode(entry.image)).distinct())
            val measure = measurements(true).firstOrNull { it.name.equals(entry.unit, true) } ?: error("Seleccioná una unidad válida.")
            require(measure.active || current != null || (control == StockControl.INDIVIDUAL && measure.counting)) { "Esa unidad fue quitada de la lista." }
            val policy = if (control == StockControl.INDIVIDUAL) InventoryPolicy.ByUnit else InventoryPolicy.ByQuantity
            policy.validateReceipt(entry.quantity, entry.unit, entry.identifier, measure.wholeNumbers, measure.counting)
            require(entry.unitDetails.isEmpty() || (control == StockControl.INDIVIDUAL && entry.unitDetails.size.toDouble() == entry.quantity)) {
                "Completá los datos correspondientes a la cantidad de unidades."
            }
            val unitDetails = if (control == StockControl.INDIVIDUAL) {
                entry.unitDetails.ifEmpty { List(entry.quantity.toInt()) { StockUnitEntry(entry.brand, entry.identifier) } }
            } else emptyList()
            if (current != null) {
                require(current.unit == entry.unit && (tools || current.materialType == entry.materialType)) { "El nombre ya existe con otra unidad o tipo de material." }
                require(tools || current.control == entry.control) { "Ese material ya existe con otra forma de control." }
            }
            val identifiers = unitDetails.map { it.identifier.trim() }.filter { it.isNotEmpty() }
            require(identifiers.distinctBy { it.lowercase(java.util.Locale.ROOT) }.size == identifiers.size) { "No se pueden repetir identificadores." }
            val existingIdentifiers = inventoryUnits(tools).map { it.identifier.lowercase(java.util.Locale.ROOT) }.toSet()
            require(identifiers.none { it.lowercase(java.util.Locale.ROOT) in existingIdentifiers }) { "Ese identificador ya existe." }
            val id = if (current == null) {
                db.insertOrThrow(table(tools), null, ContentValues().apply {
                    put("nombre", entry.name.trim()); put("cantidad", entry.quantity); put("unidad", entry.unit)
                    put("observaciones", entry.notes); put("imagen", photos); put("marca", entry.brand); put("empresa", entry.company)
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
                    if (entry.image.isNotBlank()) put("imagen", photos)
                    if (control == StockControl.QUANTITY && entry.brand.isNotBlank()) put("marca", entry.brand)
                    if (entry.company.isNotBlank()) put("empresa", entry.company)
                }, "id=?", arrayOf(current.id.toString()))
                current.id
            }
            if (control == StockControl.INDIVIDUAL) reserveIdentifierPrefixes(db, tools)
            if (entry.quantity == 0.0) {
                db.setTransactionSuccessful()
                return
            }
            val purchaseId = if (entry.rental != null) null else db.insertOrThrow(purchaseTable(tools), null, ContentValues().apply {
                put("articulo_id", id); put("cantidad", entry.quantity); put("unidad", entry.unit)
                put("remito", entry.receipt.ifBlank { "no hay" }); put("fecha", entry.date)
                put("observaciones", entry.notes); put("marca", entry.brand); put("empresa", entry.company)
            })
            val item = items(tools).first { it.id == id }
            val movementId = if (entry.rental == null) insertMovement(db, tools, item, entry.quantity, entry.date, null, "Ingreso", entry.notes, purchaseId = purchaseId) else null
            if (item.individuallyTracked) {
                // Reserve custom identifiers before generating automatic codes for blank ones.
                val created = arrayOfNulls<Long>(unitDetails.size)
                unitDetails.indices.sortedBy { unitDetails[it].identifier.isBlank() }.forEach { index ->
                    val detail = unitDetails[index]
                    created[index] = createUnit(db, tools, id, detail.brand, detail.identifier.trim(), purchaseId)
                }
                val unitIds = created.map { requireNotNull(it) }
                if (entry.rental != null) {
                    unitIds.forEach { unitId -> ToolRentals.add(db, unitId, entry.rental, entry.date) }
                    inventoryUnits(true, db).filter { it.id in unitIds }.forEach { unit ->
                        ToolLifecycle.record(db, unit, ToolRentals.INCOME, entry.date,
                            listOf(ToolRentals.description(requireNotNull(unit.rental)), entry.notes).filter { it.isNotBlank() }.joinToString("\n"))
                    }
                } else linkUnits(db, tools, requireNotNull(movementId), id, unitIds, true)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    override fun movements(tools: Boolean): List<StockMovement> {
        val units = movementUnits(tools)
        val regular = readableDatabase.rawQuery("""SELECT m.id, m.articulo_id, a.nombre,
        m.cantidad, m.unidad, m.fecha, m.persona, m.personal_id, m.clase, m.tipo_material, m.observaciones,
        m.padre_id, m.devuelto, m.fecha_devolucion, m.compra_id, a.control, m.cantidad_entera FROM ${movementTable(tools)} m
        JOIN ${table(tools)} a ON a.id=m.articulo_id ORDER BY m.fecha DESC, m.id DESC""", null).use { c ->
        buildList { while (c.moveToNext()) add(StockMovement(c.getLong(0), c.getLong(1), c.getString(2), c.getDouble(3), c.getString(4), c.getString(5), c.getString(6), if (c.isNull(7)) null else c.getLong(7), c.getString(8), c.getString(9), c.getString(10), if (c.isNull(11)) null else c.getLong(11), c.getDouble(12), c.getString(13), if (c.isNull(14)) null else c.getLong(14), StockControl.valueOf(c.getString(15)), units[c.getLong(0)].orEmpty(), c.getInt(16)==1)) }
        }
        if (!tools) return regular
        val events = ToolLifecycle.movements(readableDatabase)
        val protectedLoans = events.mapNotNull { it.parentId }.toSet()
        return regular.map { if (it.id in protectedLoans) it.copy(auditProtected = true) else it } + events
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
        require(unitIds.all { id -> item.availableUnits.any { it.id == id } }) { "Alguna unidad no está disponible o pertenece a otro artículo." }
        require(quantity <= item.quantity) { "La cantidad supera el stock disponible (${formatStockQuantity(item.quantity)} ${item.unit})." }
        val worker = personnel().firstOrNull { it.id == workerId } ?: error("Seleccioná personal de obra.")
        changeQuantity(db, tools, item, -quantity)
        val movementId = insertMovement(db, tools, item, quantity, date, worker, "Egreso", notes)
        linkUnits(db, tools, movementId, item.id, unitIds, false)
    }
    override fun returnStock(tools: Boolean, movementId: Long, quantity: Double, date: String, workerId: Long?, notes: String, unitIds: List<Long>, damagedUnits: Map<Long, String>) = transaction { db ->
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
        require(damagedUnits.isEmpty() || tools) { "El estado de daño se registra en herramientas." }
        require(damagedUnits.keys.all { it in unitIds } && damagedUnits.values.all { it.isNotBlank() }) { "Seleccioná la herramienta dañada y describí qué le pasó." }
        changeQuantity(db, tools, item, stockSum(quantity, -damagedUnits.size.toDouble()))
        unitIds.forEach { id -> db.update(unitMovementTable(tools), ContentValues().apply { put("devuelta", 1) }, "movimiento_id=? AND unidad_id=?", arrayOf(movementId.toString(), id.toString())) }
        db.update(movementTable(tools), ContentValues().apply {
            put("devuelto", stockSum(movement.returned, quantity)); put("fecha_devolucion", date)
        }, "id=?", arrayOf(movement.id.toString()))
        if (tools) {
            unitIds.forEach { id ->
                val unit = item.units.first { it.id == id }
                val damage = damagedUnits[id]
                if (damage != null) ToolLifecycle.setState(db, unit, ToolCondition.DAMAGED, date, damage)
                ToolLifecycle.record(db, unit, if (damage == null) "Devolución" else "Devolución dañada", date,
                    listOfNotNull(notes.takeIf { it.isNotBlank() }, damage?.let { "Daño: ${it.trim()}" }).joinToString("\n"), loanId = movement.id, person = movement.person)
            }
        } else {
            val returnId = insertMovement(db, false, item, quantity, date, worker, "Devolución", notes, parentId = movement.id)
            linkUnits(db, false, returnId, item.id, unitIds, true)
        }
    }
    override fun changeToolState(unitId: Long, action: ToolAction, date: String, notes: String, workshop: String) = transaction { db ->
        val unit = inventoryUnits(true, db).firstOrNull { it.id == unitId } ?: error("La herramienta ya no existe.")
        require(unit.rental?.closed != true) { "El alquiler ya fue devuelto al proveedor." }
        require(unit.loanId == null) { "Registrá primero la devolución de la herramienta." }
        require(action.allows(unit.condition)) { "El estado de la herramienta cambió. Actualizá la lista." }
        validateMovementDate(date)
        require(date >= ToolLifecycle.latestDate(db, unitId)) { "La fecha no puede ser anterior al último movimiento de esta herramienta." }
        require(action !in listOf(ToolAction.DAMAGE, ToolAction.RETIRE, ToolAction.RESTORE) || notes.isNotBlank()) { "Describí el daño o el motivo de este cambio." }
        require(action != ToolAction.SEND_REPAIR || workshop.isNotBlank()) { "Ingresá el lugar donde se repara." }
        val item = items(true).first { it.id == unit.itemId }
        if (unit.condition == ToolCondition.OPERATIVE) changeQuantity(db, true, item, -1.0)
        if (action.destination == ToolCondition.OPERATIVE) changeQuantity(db, true, item, 1.0)
        val repairPlace = if (action == ToolAction.SEND_REPAIR) workshop.trim() else unit.repairPlace
        ToolLifecycle.setState(db, unit, action.destination, date,
            damage = if (action == ToolAction.DAMAGE) notes else unit.damageNotes,
            workshop = repairPlace, repairDate = if (action == ToolAction.SEND_REPAIR) date else unit.repairDate)
        ToolLifecycle.record(db, unit, action.movement, date, notes, workshop = repairPlace)
    }
    override fun returnRental(unitId: Long, date: String, notes: String) = transaction { db ->
        val unit = inventoryUnits(true, db).firstOrNull { it.id == unitId } ?: error("La herramienta ya no existe.")
        val rental = unit.rental ?: error("Esta herramienta no es alquilada.")
        require(!rental.closed) { "El alquiler ya fue devuelto al proveedor." }
        require(unit.loanId == null) { "Registrá primero la devolución del personal al pañol." }
        require(unit.condition != ToolCondition.IN_REPAIR) { "Registrá primero el regreso de la reparación." }
        validateMovementDate(date)
        require(date >= rental.startDate && date >= ToolLifecycle.latestDate(db, unitId)) { "La devolución no puede ser anterior al último movimiento del alquiler." }
        require(unit.condition == ToolCondition.OPERATIVE || notes.isNotBlank()) { "Describí el estado en que se devuelve la herramienta dañada o dada de baja." }
        if (unit.available) changeQuantity(db, true, items(true).first { it.id == unit.itemId }, -1.0)
        ToolRentals.close(db, unitId, date, notes)
        ToolLifecycle.record(db, unit, ToolRentals.RETURN, date,
            listOf(ToolRentals.description(rental), "Estado de entrega: ${unit.condition.label}", notes).filter { it.isNotBlank() }.joinToString("\n"))
    }
    override fun deleteMovement(tools: Boolean, movementId: Long) = transaction { db ->
        val movement = movements(tools).firstOrNull { it.id == movementId } ?: error("El movimiento ya no existe.")
        require(!movement.auditProtected) { "Este movimiento conserva el historial de la herramienta y no se puede eliminar." }
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

