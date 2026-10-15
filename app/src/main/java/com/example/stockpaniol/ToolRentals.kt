package com.example.stockpaniol

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

data class RentalEntry(val supplier: String, val reference: String = "")

data class ToolRental(val supplier: String, val startDate: String, val reference: String,
    val returnedDate: String = "", val returnNotes: String = "") {
    val closed: Boolean get() = returnedDate.isNotBlank()
}

internal object ToolRentals {
    const val INCOME = "Ingreso por alquiler"
    const val RETURN = "Devolución de alquiler"

    fun migrate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS alquileres_herramientas (
            unidad_id INTEGER PRIMARY KEY REFERENCES unidades_herramientas(id) ON DELETE RESTRICT,
            proveedor TEXT NOT NULL, fecha_inicio TEXT NOT NULL, referencia TEXT NOT NULL,
            fecha_devolucion TEXT NOT NULL DEFAULT '', observaciones_devolucion TEXT NOT NULL DEFAULT '')""")
    }

    fun all(db: SQLiteDatabase): Map<Long, ToolRental> = db.rawQuery("SELECT * FROM alquileres_herramientas", null).use { c ->
        buildMap { while (c.moveToNext()) put(c.getLong(0), ToolRental(c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5))) }
    }

    fun add(db: SQLiteDatabase, unitId: Long, entry: RentalEntry, date: String) {
        db.insertOrThrow("alquileres_herramientas", null, ContentValues().apply {
            put("unidad_id", unitId); put("proveedor", entry.supplier.trim()); put("fecha_inicio", date)
            put("referencia", entry.reference.trim())
        })
    }

    fun close(db: SQLiteDatabase, unitId: Long, date: String, notes: String) {
        check(db.update("alquileres_herramientas", ContentValues().apply {
            put("fecha_devolucion", date); put("observaciones_devolucion", notes.trim())
        }, "unidad_id=? AND fecha_devolucion=''", arrayOf(unitId.toString())) == 1) { "Este alquiler ya está cerrado." }
    }

    fun description(rental: ToolRental): String = listOf("Proveedor: ${rental.supplier}",
        rental.reference.takeIf { it.isNotBlank() }?.let { "Remito / contrato: $it" }.orEmpty()).filter { it.isNotBlank() }.joinToString("\n")
}
