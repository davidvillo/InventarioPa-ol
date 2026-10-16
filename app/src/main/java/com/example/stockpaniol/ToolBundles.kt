package com.example.stockpaniol

import android.database.sqlite.SQLiteDatabase

data class ToolBundle(val id: Long, val name: String, val unitIds: List<Long>)
data class ToolLoanUnit(val movementId: Long, val itemName: String, val unit: InventoryUnit, val bundleLoan: String?)

internal object ToolBundles {
    fun migrate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE conjuntos_herramientas (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL COLLATE NOCASE UNIQUE)")
        db.execSQL("""CREATE TABLE miembros_conjuntos (unidad_id INTEGER PRIMARY KEY REFERENCES unidades_herramientas(id) ON DELETE RESTRICT,
            conjunto_id INTEGER NOT NULL REFERENCES conjuntos_herramientas(id) ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX miembros_conjuntos_grupo ON miembros_conjuntos(conjunto_id)")
        // Each loan keeps its own membership, independent of later changes to the set.
        db.execSQL("""CREATE TABLE prestamos_conjuntos (movimiento_id INTEGER NOT NULL REFERENCES movimientos_herramientas(id) ON DELETE RESTRICT,
            unidad_id INTEGER NOT NULL REFERENCES unidades_herramientas(id) ON DELETE RESTRICT,
            lote TEXT NOT NULL, PRIMARY KEY(movimiento_id, unidad_id))""")
        db.execSQL("CREATE INDEX prestamos_conjuntos_lote ON prestamos_conjuntos(lote)")
    }

    fun all(db: SQLiteDatabase): List<ToolBundle> = db.rawQuery("""SELECT c.id, c.nombre, m.unidad_id FROM conjuntos_herramientas c
        JOIN miembros_conjuntos m ON m.conjunto_id=c.id ORDER BY c.nombre COLLATE NOCASE, m.unidad_id""", null).use { c ->
        val names = mutableMapOf<Long, String>()
        val members = linkedMapOf<Long, MutableList<Long>>()
        while (c.moveToNext()) {
            names[c.getLong(0)] = c.getString(1)
            members.getOrPut(c.getLong(0)) { mutableListOf() }.add(c.getLong(2))
        }
        members.map { (id, units) -> ToolBundle(id, names.getValue(id), units) }
    }

    fun loanTokens(db: SQLiteDatabase): Map<Pair<Long, Long>, String> = db.rawQuery("SELECT movimiento_id, unidad_id, lote FROM prestamos_conjuntos", null).use { c ->
        buildMap { while (c.moveToNext()) put(c.getLong(0) to c.getLong(1), c.getString(2)) }
    }
}
