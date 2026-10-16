package com.example.stockpaniol

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

enum class StockCatalogueKind(val title: String, val singular: String) {
    BRAND("Marcas", "marca"), COMPANY("Empresas", "empresa")
}
data class StockCatalogueOption(val id: Long, val name: String, val active: Boolean = true)

interface StockCatalogueRepository {
    fun stockOptions(kind: StockCatalogueKind, includeInactive: Boolean = false): List<StockCatalogueOption>
    fun addStockOption(kind: StockCatalogueKind, name: String)
    fun updateStockOption(kind: StockCatalogueKind, id: Long, name: String)
    fun deleteStockOption(kind: StockCatalogueKind, id: Long)
}

internal fun matchesStockOption(name: String, query: String): Boolean {
    fun normalize(value: String) = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    return normalize(name).contains(normalize(query))
}

class LocalStockCatalogueRepository(private val database: () -> SQLiteDatabase) : StockCatalogueRepository {
    companion object {
        fun migrate(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE opciones_stock (id INTEGER PRIMARY KEY AUTOINCREMENT,
                tipo TEXT NOT NULL CHECK(tipo IN ('BRAND','COMPANY')), nombre TEXT NOT NULL COLLATE NOCASE,
                activo INTEGER NOT NULL DEFAULT 1 CHECK(activo IN (0,1)), UNIQUE(tipo,nombre))""")
            val catalogue = LocalStockCatalogueRepository { db }
            for (stock in listOf("materiales", "herramientas")) {
                for (table in listOf(stock, "compras_$stock", "unidades_$stock")) {
                    db.rawQuery("SELECT DISTINCT marca FROM $table", null).use { c ->
                        while (c.moveToNext()) catalogue.remember(StockCatalogueKind.BRAND, c.getString(0))
                    }
                }
                for (table in listOf(stock, "compras_$stock")) {
                    db.rawQuery("SELECT DISTINCT empresa FROM $table", null).use { c ->
                        while (c.moveToNext()) catalogue.remember(StockCatalogueKind.COMPANY, c.getString(0))
                    }
                }
            }
            db.rawQuery("SELECT DISTINCT proveedor FROM alquileres_herramientas", null).use { c ->
                while (c.moveToNext()) catalogue.remember(StockCatalogueKind.COMPANY, c.getString(0))
            }
        }
    }
    private fun transaction(action: (SQLiteDatabase) -> Unit) {
        val db = database()
        db.beginTransaction()
        try { action(db); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
    override fun stockOptions(kind: StockCatalogueKind, includeInactive: Boolean): List<StockCatalogueOption> = database().rawQuery(
        "SELECT id,nombre,activo FROM opciones_stock WHERE tipo=? ${if (includeInactive) "" else "AND activo=1"}", arrayOf(kind.name)).use { c ->
        val collator = Collator.getInstance(Locale.forLanguageTag("es-AR")).apply { strength = Collator.PRIMARY }
        buildList { while (c.moveToNext()) add(StockCatalogueOption(c.getLong(0), c.getString(1), c.getInt(2) == 1)) }
            .sortedWith(compareBy(collator) { option: StockCatalogueOption -> option.name }.thenBy { it.id })
    }
    /** New manually entered values become suggestions; removed options stay removed. */
    internal fun remember(kind: StockCatalogueKind, name: String) {
        val clean = name.trim()
        if (clean.isBlank() || stockOptions(kind, true).any { it.name.equals(clean, true) }) return
        database().insertOrThrow("opciones_stock", null, ContentValues().apply { put("tipo", kind.name); put("nombre", clean) })
    }
    override fun addStockOption(kind: StockCatalogueKind, name: String) = transaction { db ->
        require(name.isNotBlank()) { "Ingresá el nombre de la ${kind.singular}." }
        val previous = stockOptions(kind, true).firstOrNull { it.name.equals(name.trim(), true) }
        require(previous?.active != true) { "Esa ${kind.singular} ya existe." }
        if (previous == null) remember(kind, name)
        else db.update("opciones_stock", ContentValues().apply { put("nombre", name.trim()); put("activo", 1) }, "id=?", arrayOf(previous.id.toString()))
    }
    override fun updateStockOption(kind: StockCatalogueKind, id: Long, name: String) = transaction { db ->
        require(name.isNotBlank()) { "Ingresá el nombre de la ${kind.singular}." }
        val old = stockOptions(kind).firstOrNull { it.id == id } ?: error("La opción ya no está disponible.")
        val duplicate = stockOptions(kind, true).firstOrNull { it.id != id && it.name.equals(name.trim(), true) }
        require(duplicate?.active != true) { "Esa ${kind.singular} ya existe." }
        if (duplicate != null) db.delete("opciones_stock", "id=?", arrayOf(duplicate.id.toString()))
        db.update("opciones_stock", ContentValues().apply { put("nombre", name.trim()) }, "id=?", arrayOf(id.toString()))
        if (!old.name.equals(name.trim(), true)) {
            db.insertOrThrow("opciones_stock", null, ContentValues().apply { put("tipo", kind.name); put("nombre", old.name); put("activo", 0) })
        }
    }
    override fun deleteStockOption(kind: StockCatalogueKind, id: Long) = transaction { db ->
        require(stockOptions(kind).any { it.id == id }) { "La opción ya no está disponible." }
        db.update("opciones_stock", ContentValues().apply { put("activo", 0) }, "id=?", arrayOf(id.toString()))
    }
}
