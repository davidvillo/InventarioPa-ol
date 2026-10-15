package com.example.stockpaniol

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

enum class ToolCondition(val label: String) {
    OPERATIVE("Disponible en pañol"), DAMAGED("Dañada en pañol"), IN_REPAIR("En reparación"), RETIRED("Baja · Papelera")
}

enum class ToolAction(val label: String, val movement: String, val destination: ToolCondition) {
    DAMAGE("Registrar daño", "Daño en pañol", ToolCondition.DAMAGED),
    SEND_REPAIR("Enviar a reparar", "Envío a reparación", ToolCondition.IN_REPAIR),
    REPAIRED("Recibir reparada", "Retorno reparada", ToolCondition.OPERATIVE),
    UNREPAIRED("Recibir sin reparar", "Retorno sin reparar", ToolCondition.DAMAGED),
    RETIRE("Dar de baja", "Baja", ToolCondition.RETIRED),
    RESTORE("Restaurar de papelera", "Restauración", ToolCondition.DAMAGED);

    fun allows(condition: ToolCondition): Boolean = when (this) {
        DAMAGE -> condition == ToolCondition.OPERATIVE
        SEND_REPAIR -> condition == ToolCondition.DAMAGED
        REPAIRED, UNREPAIRED -> condition == ToolCondition.IN_REPAIR
        RETIRE -> condition in listOf(ToolCondition.DAMAGED, ToolCondition.IN_REPAIR)
        RESTORE -> condition == ToolCondition.RETIRED
    }
}

internal data class ToolState(val condition: ToolCondition, val damage: String, val date: String,
    val workshop: String, val repairDate: String)

/** Physical events share the history view while retaining immutable per-unit snapshots. */
internal object ToolLifecycle {
    val movementKinds = listOf("Devolución", "Devolución dañada", ToolRentals.INCOME, ToolRentals.RETURN) + ToolAction.entries.map { it.movement }

    fun migrate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS estado_herramientas (
            unidad_id INTEGER PRIMARY KEY REFERENCES unidades_herramientas(id) ON DELETE RESTRICT,
            estado TEXT NOT NULL CHECK(estado IN ('OPERATIVE','DAMAGED','IN_REPAIR','RETIRED')),
            dano TEXT NOT NULL, fecha TEXT NOT NULL, taller TEXT NOT NULL, fecha_reparacion TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS eventos_herramientas (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            unidad_id INTEGER NOT NULL REFERENCES unidades_herramientas(id) ON DELETE RESTRICT,
            articulo_id INTEGER NOT NULL REFERENCES herramientas(id) ON DELETE RESTRICT,
            tipo TEXT NOT NULL, fecha TEXT NOT NULL, observaciones TEXT NOT NULL,
            identificador TEXT NOT NULL, marca TEXT NOT NULL, taller TEXT NOT NULL,
            prestamo_id INTEGER REFERENCES movimientos_herramientas(id) ON DELETE RESTRICT,
            persona TEXT NOT NULL)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS eventos_herramientas_unidad ON eventos_herramientas(unidad_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS eventos_herramientas_prestamo ON eventos_herramientas(prestamo_id)")
    }

    fun states(db: SQLiteDatabase): Map<Long, ToolState> = db.rawQuery("SELECT * FROM estado_herramientas", null).use { c ->
        buildMap { while (c.moveToNext()) put(c.getLong(0), ToolState(ToolCondition.valueOf(c.getString(1)), c.getString(2), c.getString(3), c.getString(4), c.getString(5))) }
    }

    fun setState(db: SQLiteDatabase, unit: InventoryUnit, condition: ToolCondition, date: String,
        damage: String = unit.damageNotes, workshop: String = unit.repairPlace, repairDate: String = unit.repairDate) {
        db.execSQL("INSERT OR REPLACE INTO estado_herramientas VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(unit.id, condition.name, damage.trim(), date, workshop.trim(), repairDate))
    }

    fun record(db: SQLiteDatabase, unit: InventoryUnit, kind: String, date: String, notes: String,
        workshop: String = "", loanId: Long? = null, person: String = "") {
        require(kind in movementKinds)
        db.insertOrThrow("eventos_herramientas", null, ContentValues().apply {
            put("unidad_id", unit.id); put("articulo_id", unit.itemId); put("tipo", kind); put("fecha", date)
            put("observaciones", notes.trim()); put("identificador", unit.identifier); put("marca", unit.brand)
            put("taller", workshop.trim()); put("prestamo_id", loanId); put("persona", person)
        })
    }

    fun movements(db: SQLiteDatabase): List<StockMovement> {
        val rentals = ToolRentals.all(db)
        return db.rawQuery("""SELECT e.id, e.articulo_id, a.nombre,
        a.unidad, e.fecha, e.persona, e.tipo, e.observaciones, e.prestamo_id, e.unidad_id,
        e.identificador, e.marca, e.taller FROM eventos_herramientas e JOIN herramientas a ON a.id=e.articulo_id""", null).use { c ->
        buildList { while (c.moveToNext()) {
            val notes = listOf(c.getString(7), c.getString(12).takeIf { it.isNotBlank() }?.let { "Taller: $it" }.orEmpty()).filter { it.isNotBlank() }.joinToString("\n")
            add(StockMovement(-c.getLong(0), c.getLong(1), c.getString(2), 1.0, c.getString(3), c.getString(4),
                c.getString(5), null, c.getString(6), "", notes, if (c.isNull(8)) null else c.getLong(8),
                0.0, "", null, StockControl.INDIVIDUAL,
                listOf(MovementUnit(InventoryUnit(c.getLong(9), c.getLong(1), c.getString(10), c.getString(11), rental = rentals[c.getLong(9)]), true)),
                true, auditProtected = true))
        } }
    }
    }

    fun latestDate(db: SQLiteDatabase, unitId: Long): String = db.rawQuery("""SELECT MAX(fecha) FROM (
        SELECT e.fecha FROM eventos_herramientas e WHERE e.unidad_id=?
        UNION ALL SELECT m.fecha FROM unidades_movimientos_herramientas u JOIN movimientos_herramientas m ON m.id=u.movimiento_id WHERE u.unidad_id=?)""",
        arrayOf(unitId.toString(), unitId.toString())).use { it.moveToFirst(); if (it.isNull(0)) "" else it.getString(0) }
}
