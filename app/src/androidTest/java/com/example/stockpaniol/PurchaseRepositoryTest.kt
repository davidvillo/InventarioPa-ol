package com.example.stockpaniol

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PurchaseRepositoryTest {
    private val date = "2026-10-12"
    private fun entry(tools: Boolean, id: Long = 0, quantity: Double = 2.0, company: String = "Proveedor A", receipt: String = "R-1") =
        StockEntry(id, if (tools) "Martillo" else "Arena", quantity, if (tools) "unidades" else "kg", "Notas", receipt, date, "", "Marca", company)
    private fun withRepository(block: (LocalStockRepository) -> Unit) {
        val context = isolatedStockContext("purchase_test_")
        context.deleteDatabase("stock_paniol.db")
        try { LocalStockRepository(context).use(block) } finally { context.deleteDatabase("stock_paniol.db") }
    }
    private fun rejectsForeignKey(block: () -> Unit) {
        try { block(); fail("La relación inválida debe rechazarse") } catch (_: SQLiteConstraintException) { }
    }

    @Test fun everyNewAndExistingIncomeCreatesOneRelatedPurchaseWithSupplierSnapshot() = withRepository { r ->
        for (tools in listOf(false, true)) {
            r.receive(tools, entry(tools))
            val itemId = r.items(tools).single().id
            r.receive(tools, entry(tools, itemId, 3.0, "Proveedor B", "R-2"))
            assertEquals(5.0, r.items(tools).single().quantity, 0.0)
            val purchases = r.purchases(tools).sortedBy { it.id }
            assertEquals(listOf("Proveedor A", "Proveedor B"), purchases.map { it.company })
            assertEquals(listOf("R-1", "R-2"), purchases.map { it.receipt })
            assertEquals(listOf(2.0, 3.0), purchases.map { it.quantity })
            assertTrue(purchases.all { it.itemId == itemId && it.date == date })
            val movements = r.movements(tools)
            assertEquals(purchases.map { it.id }.toSet(), movements.map { it.purchaseId }.toSet())
            for (movement in movements) {
                val purchase = purchases.first { it.id == movement.purchaseId }
                assertEquals(purchase.quantity, movement.quantity, 0.0)
                assertEquals(purchase.unit, movement.unit)
            }
            r.deleteMovement(tools, movements.first { it.purchaseId == purchases.first().id }.id)
            assertEquals(3.0, r.items(tools).single().quantity, 0.0)
            assertEquals(listOf("R-2"), r.purchases(tools).map { it.receipt })
        }
        assertEquals(3.0, r.items(false).single().quantity, 0.0)
        assertEquals(3.0, r.items(true).single().quantity, 0.0)
        assertEquals(0, r.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
    }

    @Test fun sixPhysicalTablesExistAndForeignKeysRejectOrphansAndMismatchedArticles() = withRepository { r ->
        r.receive(false, entry(false))
        r.receive(false, entry(false).copy(name = "Cal"))
        val db = r.writableDatabase
        val names = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
        assertTrue(names.containsAll(listOf("materiales", "herramientas", "movimientos_materiales", "movimientos_herramientas", "compras_materiales", "compras_herramientas")))
        assertFalse("ingresos" in names)
        assertFalse("movimientos" in names)
        assertEquals(1, db.rawQuery("PRAGMA foreign_keys", null).use { it.moveToFirst(); it.getInt(0) })
        rejectsForeignKey {
            db.insertOrThrow("compras_materiales", null, ContentValues().apply {
                put("articulo_id", 999L); put("cantidad", 1.0); put("unidad", "kg"); put("fecha", date); put("empresa", "A"); put("remito", "R")
            })
        }
        val arena = r.items(false).first { it.name == "Arena" }
        val cal = r.items(false).first { it.name == "Cal" }
        val movement = r.movements(false).first { it.itemId == arena.id }
        rejectsForeignKey { db.update("movimientos_materiales", ContentValues().apply { put("articulo_id", cal.id) }, "id=?", arrayOf(movement.id.toString())) }
        rejectsForeignKey { db.delete("materiales", "id=?", arrayOf(arena.id.toString())) }
        rejectsForeignKey { db.delete("compras_materiales", "id=?", arrayOf(movement.purchaseId.toString())) }
        assertEquals(2, r.purchases(false).size)
        assertEquals(0, db.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
    }

    @Test fun failedPurchaseInsertRollsBackTheStockAndMovement() = withRepository { r ->
        r.receive(false, entry(false))
        val original = r.items(false).single()
        r.writableDatabase.execSQL("CREATE TRIGGER reject_purchase BEFORE INSERT ON compras_materiales BEGIN SELECT RAISE(ABORT, 'Compra rechazada'); END")
        assertTrue(runCatching { r.receive(false, entry(false, original.id, 1.0, "Otro proveedor")) }.isFailure)
        assertEquals(original, r.items(false).single())
        assertEquals(1, r.purchases(false).size)
        assertEquals(1, r.movements(false).size)
    }

    @Test fun versionTwoMigrationPreservesPurchasesPersonnelAndPartialReturns() {
        val context = isolatedStockContext("purchase_migration_")
        context.deleteDatabase("stock_paniol.db")
        try {
            context.openOrCreateDatabase("stock_paniol.db", 0, null).use { db ->
                for (table in listOf("materiales", "herramientas")) {
                    db.execSQL("CREATE TABLE $table (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL, cantidad REAL NOT NULL, unidad TEXT NOT NULL, observaciones TEXT NOT NULL, imagen TEXT NOT NULL, marca TEXT NOT NULL, empresa TEXT NOT NULL, tipo_material TEXT NOT NULL)")
                    db.execSQL("INSERT INTO $table VALUES (1, '${if (table == "materiales") "Arena" else "Martillo"}', ${if (table == "materiales") "7.5" else "3"}, '${if (table == "materiales") "kg" else "unidades"}', 'Notas', 'content://foto', 'Marca', 'Actual', '${if (table == "materiales") MaterialTypes.ELECTRICAL else ""}')")
                }
                db.execSQL("CREATE TABLE personal (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL, apellido TEXT NOT NULL, obra TEXT NOT NULL, apodo TEXT NOT NULL)")
                db.execSQL("INSERT INTO personal VALUES (1, 'Ana', 'Ruiz', 'Obra norte', 'Anita')")
                db.execSQL("CREATE TABLE ingresos (id INTEGER PRIMARY KEY AUTOINCREMENT, tipo TEXT NOT NULL, articulo_id INTEGER NOT NULL, cantidad REAL NOT NULL, remito TEXT NOT NULL, fecha TEXT NOT NULL, observaciones TEXT NOT NULL, marca TEXT NOT NULL, empresa TEXT NOT NULL)")
                db.execSQL("INSERT INTO ingresos VALUES (1, 'materiales', 1, 10, 'R-M', '$date', 'Notas', 'Marca', 'Proveedor histórico')")
                db.execSQL("INSERT INTO ingresos VALUES (2, 'herramientas', 1, 4, 'R-H', '$date', 'Notas', 'Marca', 'Proveedor de herramientas')")
                db.execSQL("CREATE TABLE movimientos (id INTEGER PRIMARY KEY AUTOINCREMENT, stock TEXT NOT NULL, articulo_id INTEGER NOT NULL, cantidad REAL NOT NULL, unidad TEXT NOT NULL, fecha TEXT NOT NULL, persona TEXT NOT NULL, personal_id INTEGER, clase TEXT NOT NULL, tipo_material TEXT NOT NULL, observaciones TEXT NOT NULL, padre_id INTEGER, devuelto REAL NOT NULL DEFAULT 0, fecha_devolucion TEXT NOT NULL DEFAULT '', ingreso_id INTEGER)")
                db.execSQL("INSERT INTO movimientos VALUES (1, 'materiales', 1, 10, 'kg', '$date', '', NULL, 'Ingreso', '${MaterialTypes.ELECTRICAL}', 'Notas', NULL, 0, '', 1)")
                db.execSQL("INSERT INTO movimientos VALUES (2, 'herramientas', 1, 4, 'unidades', '$date', '', NULL, 'Ingreso', '', 'Notas', NULL, 0, '', 2)")
                db.execSQL("INSERT INTO movimientos VALUES (3, 'materiales', 1, 3, 'kg', '$date', 'Ana Ruiz (Anita)', 1, 'Egreso', '${MaterialTypes.ELECTRICAL}', 'Entrega', NULL, 0.5, '$date', NULL)")
                db.execSQL("INSERT INTO movimientos VALUES (4, 'herramientas', 1, 2, 'unidades', '$date', 'Ana Ruiz (Anita)', 1, 'Egreso', '', 'Préstamo', NULL, 1, '$date', NULL)")
                db.execSQL("INSERT INTO movimientos VALUES (5, 'materiales', 1, 0.5, 'kg', '$date', 'Ana Ruiz (Anita)', 1, 'Devolución', '${MaterialTypes.ELECTRICAL}', 'Sobrante', 3, 0, '', NULL)")
                db.execSQL("CREATE TABLE configuracion (clave TEXT PRIMARY KEY, valor TEXT NOT NULL)")
                db.execSQL("INSERT INTO configuracion VALUES ('nombre', 'Mi obra')")
                db.version = 2
            }
            LocalStockRepository(context).use { r ->
                assertEquals(14, r.readableDatabase.version)
                assertEquals("Mi obra", r.stockName)
                assertEquals(7.5, r.items(false).single().quantity, 0.0)
                assertEquals(3.0, r.items(true).single().quantity, 0.0)
                assertEquals("Proveedor histórico", r.purchases(false).single().company)
                assertEquals("R-H", r.purchases(true).single().receipt)
                assertEquals(1L, r.movements(false).first { it.kind == "Ingreso" }.purchaseId)
                assertEquals(2.5, r.movements(false).first { it.id == 3L }.pending, 0.0)
                assertEquals(3L, r.movements(false).first { it.id == 5L }.parentId)
                assertEquals("Anita", r.personnel().single().nickname)
                r.deleteMovement(false, 5L)
                r.deleteMovement(false, 3L)
                assertEquals(10.0, r.items(false).single().quantity, 0.0)
                r.returnStock(true, 4L, 1.0, "2026-10-13", null, "", r.movements(true).first { it.id == 4L }.units.filter { !it.returned }.map { it.unit.id })
                assertEquals(4.0, r.items(true).single().quantity, 0.0)
                assertEquals(0, r.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
            }
        } finally { context.deleteDatabase("stock_paniol.db") }
    }
}
