package com.example.stockpaniol

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

enum class MaterialDamageAction(val label: String, val movement: String) {
    DAMAGE("Registrar daño", "Daño de material"),
    USE("Utilizar dañado", "Material habilitado para uso dañado"),
    TRASH("Enviar a papelera", "Baja de material"),
    RESTORE("Restaurar a materiales dañados", "Restauración de material")
}

data class DamagedMaterial(val id: Long, val itemId: Long, val quantity: Double,
    val unitId: Long?, val state: String, val notes: String, val date: String)

internal object MaterialDamage {
    const val CORRECTION = "Corrección de cantidad"
    val movementKinds = MaterialDamageAction.entries.map { it.movement } + CORRECTION

    fun migrate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS danos_materiales (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            articulo_id INTEGER NOT NULL REFERENCES materiales(id) ON DELETE RESTRICT,
            cantidad REAL NOT NULL CHECK(cantidad > 0),
            unidad_id INTEGER REFERENCES unidades_materiales(id) ON DELETE RESTRICT,
            estado TEXT NOT NULL CHECK(estado IN ('DAMAGED','USABLE','TRASH')),
            observaciones TEXT NOT NULL, fecha TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS eventos_materiales (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            articulo_id INTEGER NOT NULL REFERENCES materiales(id) ON DELETE RESTRICT,
            cantidad REAL NOT NULL, unidad TEXT NOT NULL, fecha TEXT NOT NULL,
            tipo TEXT NOT NULL, tipo_material TEXT NOT NULL, observaciones TEXT NOT NULL,
            identificador TEXT NOT NULL, marca TEXT NOT NULL, unidad_id INTEGER,
            cantidad_entera INTEGER NOT NULL)""")
    }

    fun all(db: SQLiteDatabase): List<DamagedMaterial> = db.rawQuery("SELECT * FROM danos_materiales ORDER BY id", null).use { c ->
        buildList { while (c.moveToNext()) add(DamagedMaterial(c.getLong(0), c.getLong(1), c.getDouble(2),
            if (c.isNull(3)) null else c.getLong(3), c.getString(4), c.getString(5), c.getString(6))) }
    }

    fun register(db: SQLiteDatabase, item: StockItem, quantity: Double, unit: InventoryUnit?, date: String, notes: String) {
        // Keep immutable events when a previously usable damaged unit is damaged again.
        unit?.let { db.delete("danos_materiales", "unidad_id=? AND estado='USABLE'", arrayOf(it.id.toString())) }
        val damageId = db.insertOrThrow("danos_materiales", null, ContentValues().apply {
            put("articulo_id", item.id); put("cantidad", quantity); put("unidad_id", unit?.id)
            put("estado", "DAMAGED"); put("observaciones", notes.trim()); put("fecha", date)
        })
        record(db, item, quantity, unit, MaterialDamageAction.DAMAGE, date, notes, damageId)
    }

    fun record(db: SQLiteDatabase, item: StockItem, quantity: Double, unit: InventoryUnit?, action: MaterialDamageAction, date: String, notes: String, damageId: Long? = null) {
        val id = recordEvent(db, item, quantity, unit, action.movement, date, notes)
        damageId?.let { db.execSQL("INSERT OR REPLACE INTO configuracion VALUES (?, ?)", arrayOf("dano_evento_$id", it.toString())) }
    }

    fun recordEvent(db: SQLiteDatabase, item: StockItem, quantity: Double, unit: InventoryUnit?, kind: String, date: String, notes: String): Long {
        require(kind in movementKinds)
        return db.insertOrThrow("eventos_materiales", null, ContentValues().apply {
            put("articulo_id", item.id); put("cantidad", quantity); put("unidad", item.unit)
            put("fecha", date); put("tipo", kind); put("tipo_material", item.materialType)
            put("observaciones", notes.trim()); put("identificador", unit?.identifier.orEmpty())
            put("marca", unit?.brand.orEmpty()); put("unidad_id", unit?.id)
            put("cantidad_entera", if (item.wholeNumbers) 1 else 0)
        })
    }

    fun movements(db: SQLiteDatabase): List<StockMovement> = db.rawQuery("""SELECT e.*, a.nombre, a.control
        FROM eventos_materiales e JOIN materiales a ON a.id=e.articulo_id""", null).use { c ->
        buildList { while (c.moveToNext()) add(StockMovement(-c.getLong(0), c.getLong(1), c.getString(12), c.getDouble(2),
            c.getString(3), c.getString(4), "", null, c.getString(5), c.getString(6), c.getString(7), null,
            0.0, "", null, StockControl.valueOf(c.getString(13)),
            if (c.isNull(10)) emptyList() else listOf(MovementUnit(InventoryUnit(c.getLong(10), c.getLong(1), c.getString(8), c.getString(9)), true)),
            c.getInt(11) == 1, auditProtected = true)) }
    }
}
