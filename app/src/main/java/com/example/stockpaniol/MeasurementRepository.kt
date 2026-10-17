package com.example.stockpaniol

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

data class MeasurementUnit(val id: Long, val name: String, val wholeNumbers: Boolean,
    val active: Boolean = true, val counting: Boolean = false, val pairs: Boolean = false) {
    val identifiesMaterial: Boolean get() = wholeNumbers && (counting || pairs)
}

interface MeasurementRepository {
    fun measurements(includeInactive: Boolean = false): List<MeasurementUnit>
    fun addMeasurement(name: String, wholeNumbers: Boolean)
    fun updateMeasurement(id: Long, name: String, wholeNumbers: Boolean)
    fun deleteMeasurement(id: Long)
}

class LocalMeasurementRepository(private val database: () -> SQLiteDatabase) : MeasurementRepository {
    companion object {
        fun migrate(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE unidades_medida (id INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre TEXT NOT NULL COLLATE NOCASE UNIQUE, entera INTEGER NOT NULL CHECK(entera IN (0,1)),
                activo INTEGER NOT NULL DEFAULT 1 CHECK(activo IN (0,1)), conteo INTEGER NOT NULL DEFAULT 0 CHECK(conteo IN (0,1)),
                pareado INTEGER NOT NULL DEFAULT 0 CHECK(pareado IN (0,1)))""")
            listOf("unidades" to true, "mts" to false, "kg" to false, "litros" to false, "pares" to true).forEach { (name, whole) ->
                db.execSQL("INSERT INTO unidades_medida(nombre,entera,conteo,pareado) VALUES (?,?,?,?)", arrayOf<Any>(name, if (whole) 1 else 0, if (name == "unidades") 1 else 0, if (name == "pares") 1 else 0))
            }
            for (stock in listOf("materiales", "herramientas")) {
                db.execSQL("ALTER TABLE $stock ADD COLUMN unidad_medida_id INTEGER REFERENCES unidades_medida(id) ON DELETE RESTRICT")
                db.rawQuery("SELECT DISTINCT unidad FROM $stock", null).use { c ->
                    while (c.moveToNext()) {
                        val name = c.getString(0)
                        db.execSQL("INSERT OR IGNORE INTO unidades_medida(nombre,entera) VALUES (?,?)", arrayOf<Any>(name, if (name in listOf("unidades", "pares")) 1 else 0))
                    }
                }
                db.execSQL("UPDATE $stock SET unidad_medida_id=(SELECT id FROM unidades_medida WHERE nombre=$stock.unidad COLLATE NOCASE)")
                db.execSQL("ALTER TABLE movimientos_$stock ADD COLUMN cantidad_entera INTEGER NOT NULL DEFAULT 0 CHECK(cantidad_entera IN (0,1))")
                db.execSQL("UPDATE movimientos_$stock SET cantidad_entera=1 WHERE unidad IN ('unidades','pares')")
            }
        }

        fun migratePairCounting(db: SQLiteDatabase) {
            val hasColumn = db.rawQuery("PRAGMA table_info(unidades_medida)", null).use { c ->
                var found = false
                while (c.moveToNext()) if (c.getString(1) == "pareado") found = true
                found
            }
            if (!hasColumn) {
                db.execSQL("ALTER TABLE unidades_medida ADD COLUMN pareado INTEGER NOT NULL DEFAULT 0 CHECK(pareado IN (0,1))")
                // The original catalogue always seeded pairs as ID 5, even if renamed later.
                db.execSQL("UPDATE unidades_medida SET pareado=1 WHERE id=5 OR nombre='pares' COLLATE NOCASE")
            }
        }
    }
    private fun transaction(action: (SQLiteDatabase) -> Unit) {
        val db = database()
        db.beginTransaction()
        try { action(db); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
    override fun measurements(includeInactive: Boolean): List<MeasurementUnit> = database().rawQuery(
        "SELECT id,nombre,entera,activo,conteo,pareado FROM unidades_medida ${if (includeInactive) "" else "WHERE activo=1"} ORDER BY id", null).use { c ->
        buildList { while (c.moveToNext()) add(MeasurementUnit(c.getLong(0), c.getString(1), c.getInt(2)==1, c.getInt(3)==1, c.getInt(4)==1, c.getInt(5)==1)) }
    }
    private fun inUse(db: SQLiteDatabase, id: Long): Boolean = listOf("materiales", "herramientas").any { stock ->
        db.rawQuery("SELECT 1 FROM $stock WHERE unidad_medida_id=? LIMIT 1", arrayOf(id.toString())).use { it.moveToFirst() }
    }
    override fun addMeasurement(name: String, wholeNumbers: Boolean) = transaction { db ->
        require(name.isNotBlank()) { "Ingresá el nombre de la unidad." }
        val old = measurements(true).firstOrNull { it.name.equals(name.trim(), true) }
        require(old == null || !old.active) { "Esa unidad ya existe." }
        if (old != null) {
            require(old.wholeNumbers == wholeNumbers) { "La unidad retirada conserva su forma de contar. Reactivala con la misma opción de cantidades enteras." }
            db.update("unidades_medida", ContentValues().apply { put("activo",1) }, "id=?", arrayOf(old.id.toString()))
        } else db.insertOrThrow("unidades_medida", null, ContentValues().apply { put("nombre",name.trim()); put("entera",if(wholeNumbers)1 else 0) })
    }
    override fun updateMeasurement(id: Long, name: String, wholeNumbers: Boolean) = transaction { db ->
        require(name.isNotBlank()) { "Ingresá el nombre de la unidad." }
        val old = measurements().firstOrNull { it.id==id } ?: error("La unidad ya no está disponible.")
        require(measurements(true).none { it.id!=id && it.name.equals(name.trim(),true) }) { "Esa unidad ya existe." }
        require((!old.counting && !old.pairs) || wholeNumbers) { "Las unidades y los pares identificados se cuentan en números enteros." }
        require(old.wholeNumbers == wholeNumbers || !inUse(db,id)) { "No se puede cambiar la forma de contar de una unidad que ya tiene artículos. Creá otra unidad." }
        db.update("unidades_medida",ContentValues().apply { put("nombre",name.trim()); put("entera",if(wholeNumbers)1 else 0) },"id=?",arrayOf(id.toString()))
        for(stock in listOf("materiales","herramientas")) db.update(stock,ContentValues().apply { put("unidad",name.trim()) },"unidad_medida_id=?",arrayOf(id.toString()))
    }
    override fun deleteMeasurement(id: Long) = transaction { db ->
        val old=measurements().firstOrNull { it.id==id } ?: error("La unidad ya no está disponible.")
        if(old.counting || old.pairs || inUse(db,id)) db.update("unidades_medida",ContentValues().apply { put("activo",0) },"id=?",arrayOf(id.toString()))
        else db.delete("unidades_medida","id=?",arrayOf(id.toString()))
    }
}
