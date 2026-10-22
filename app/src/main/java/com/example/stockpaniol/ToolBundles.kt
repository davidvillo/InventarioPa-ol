package com.example.stockpaniol

import android.database.sqlite.SQLiteDatabase

data class ToolBundle(val id: Long, val name: String, val unitIds: List<Long>, val principalUnitId: Long? = null)
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

    fun migratePrincipal(db: SQLiteDatabase) {
        val exists = db.rawQuery("PRAGMA table_info(conjuntos_herramientas)", null).use { c ->
            var found = false
            while (c.moveToNext()) if (c.getString(1) == "principal_id") found = true
            found
        }
        if (exists) return
        db.execSQL("ALTER TABLE conjuntos_herramientas ADD COLUMN principal_id INTEGER REFERENCES unidades_herramientas(id) ON DELETE RESTRICT")
        db.execSQL("""UPDATE conjuntos_herramientas SET principal_id =
            (SELECT MIN(unidad_id) FROM miembros_conjuntos WHERE conjunto_id=conjuntos_herramientas.id)""")
    }

    fun all(db: SQLiteDatabase): List<ToolBundle> = db.rawQuery("""SELECT c.id, c.nombre, m.unidad_id, c.principal_id FROM conjuntos_herramientas c
        JOIN miembros_conjuntos m ON m.conjunto_id=c.id ORDER BY c.nombre COLLATE NOCASE, m.unidad_id""", null).use { c ->
        val names = mutableMapOf<Long, String>()
        val principals = mutableMapOf<Long, Long?>()
        val members = linkedMapOf<Long, MutableList<Long>>()
        while (c.moveToNext()) {
            names[c.getLong(0)] = c.getString(1)
            principals[c.getLong(0)] = if (c.isNull(3)) null else c.getLong(3)
            members.getOrPut(c.getLong(0)) { mutableListOf() }.add(c.getLong(2))
        }
        members.map { (id, units) -> ToolBundle(id, names.getValue(id), units, principals[id]) }
    }

    fun loanTokens(db: SQLiteDatabase): Map<Pair<Long, Long>, String> = db.rawQuery("SELECT movimiento_id, unidad_id, lote FROM prestamos_conjuntos", null).use { c ->
        buildMap { while (c.moveToNext()) put(c.getLong(0) to c.getLong(1), c.getString(2)) }
    }
}

/** Build links from the complete stock so search filters never hide a related tool's identity. */
fun toolBundleSummary(items: List<StockItem>, bundles: List<ToolBundle>): Map<Long, List<String>> {
    data class Member(val item: StockItem, val unit: InventoryUnit) {
        val label: String get() = "${item.name} · ${unit.identifier.ifBlank { "sin código" }}"
    }
    val members = items.flatMap { item -> item.units.map { Member(item, it) } }.associateBy { it.unit.id }
    val result = linkedMapOf<Long, MutableList<String>>()
    bundles.forEach { bundle ->
        val principalId = bundle.principalUnitId?.takeIf { it in bundle.unitIds } ?: bundle.unitIds.firstOrNull()
        val principal = members[principalId] ?: return@forEach
        val accessories = bundle.unitIds.filter { it != principalId }.mapNotNull { members[it] }
        bundle.unitIds.mapNotNull { members[it] }.forEach { member ->
            val prefix = if (member.item.units.size > 1) "${member.unit.identifier}: " else ""
            val lines = if (member.unit.id == principalId) accessories.map { "${prefix}con ${it.label}" }
                else listOf("${prefix}en ${principal.label}")
            result.getOrPut(member.item.id) { mutableListOf() }.addAll(lines)
        }
    }
    return result
}
