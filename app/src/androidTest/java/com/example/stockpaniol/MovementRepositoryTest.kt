package com.example.stockpaniol

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

internal fun isolatedStockContext(prefix: String): Context {
    val base = InstrumentationRegistry.getInstrumentation().targetContext
    return object : ContextWrapper(base) {
        override fun getDatabasePath(name: String) = base.getDatabasePath(prefix + name)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?) = base.openOrCreateDatabase(prefix + name, mode, factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?) = base.openOrCreateDatabase(prefix + name, mode, factory, handler)
        override fun deleteDatabase(name: String) = base.deleteDatabase(prefix + name)
    }
}

@RunWith(AndroidJUnit4::class)
class MovementRepositoryTest {
    private val date = "2026-10-12"
    private fun entry(tools: Boolean, quantity: Double = 10.5) = StockEntry(0, if (tools) "Martillo" else "Arena", quantity,
        if (tools) "unidades" else "kg", "Notas", "R-1", date, "", "Marca", "Proveedor", MaterialTypes.ELECTRICAL)
    private fun withRepository(block: (LocalStockRepository, Context) -> Unit) {
        val context = isolatedStockContext("movement_test_")
        context.deleteDatabase("stock_paniol.db")
        val repository = LocalStockRepository(context)
        try { block(repository, context) } finally { repository.close(); context.deleteDatabase("stock_paniol.db") }
    }
    private fun assertRejected(block: () -> Unit) {
        try { block(); fail("La operación inválida debe rechazarse") } catch (_: IllegalArgumentException) { } catch (_: IllegalStateException) { }
    }
    private fun worker(repository: LocalStockRepository): Long {
        repository.addWorker("Ana", "Ruiz", "Obra norte", "Anita")
        return repository.personnel().single().id
    }

    @Test fun materialPartialReturnsCreateRowsAndDeletionReversesStock() = withRepository { r, _ ->
        r.receive(false, entry(false))
        val item = r.items(false).single()
        val person = worker(r)
        r.issue(false, item.id, 6.25, date, person, "Entrega")
        val outgoing = r.movements(false).first { it.kind == "Egreso" }
        assertEquals(4.25, r.items(false).single().quantity, 0.0)
        r.returnStock(false, outgoing.id, 2.0, date, person, "Sobrante")
        assertEquals(6.25, r.items(false).single().quantity, 0.0)
        assertEquals(4.25, r.movements(false).first { it.id == outgoing.id }.pending, 0.0)
        val returned = r.movements(false).first { it.kind == "Devolución" }
        assertEquals("Ana Ruiz (Anita)", returned.person)
        assertEquals(MaterialTypes.ELECTRICAL, returned.materialType)
        r.deleteMovement(false, returned.id)
        assertEquals(4.25, r.items(false).single().quantity, 0.0)
        assertEquals(0.0, r.movements(false).first { it.id == outgoing.id }.returned, 0.0)
        r.returnStock(false, outgoing.id, 1.25, date, person, "")
        r.deleteMovement(false, outgoing.id)
        assertEquals(10.5, r.items(false).single().quantity, 0.0)
        assertEquals(listOf("Ingreso"), r.movements(false).map { it.kind })
        r.deleteMovement(false, r.movements(false).single().id)
        assertEquals(0.0, r.items(false).single().quantity, 0.0)
        assertTrue(r.movements(false).isEmpty())
        assertTrue(r.purchases(false).isEmpty())
    }

    @Test fun toolPartialReturnsUpdateLoanAppendLedgerAndPersist() = withRepository { r, context ->
        r.receive(true, entry(true, 4.0))
        val person = worker(r)
        r.issue(true, r.items(true).single().id, 3.0, date, person, "Préstamo", r.items(true).single().availableUnits.take(3).map { it.id })
        val outgoing = r.movements(true).first { it.kind == "Egreso" }
        r.returnStock(true, outgoing.id, 1.0, date, null, "", outgoing.units.take(1).map { it.unit.id })
        assertEquals(3, r.movements(true).size)
        assertEquals(2.0, r.items(true).single().quantity, 0.0)
        assertEquals(2.0, r.movements(true).first { it.id == outgoing.id }.pending, 0.0)
        r.returnStock(true, outgoing.id, 2.0, "2026-10-13", null, "", outgoing.units.drop(1).map { it.unit.id })
        r.close()
        LocalStockRepository(context).use { reopened ->
            assertEquals(4.0, reopened.items(true).single().quantity, 0.0)
            val loan = reopened.movements(true).first { it.id == outgoing.id }
            assertEquals(0.0, loan.pending, 0.0)
            assertEquals("2026-10-13", loan.returnDate)
            assertEquals(5, reopened.movements(true).size)
            assertEquals("Obra norte", reopened.personnel().single().worksite)
            assertRejected { reopened.deleteMovement(true, outgoing.id) }
            assertEquals(4.0, reopened.items(true).single().quantity, 0.0)
        }
    }

    @Test fun invalidIssuesAndReturnsLeaveStockAndLedgerUnchanged() = withRepository { r, _ ->
        r.receive(true, entry(true, 3.0))
        r.receive(false, entry(false))
        val person = worker(r)
        val tool = r.items(true).single()
        for (quantity in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 1.5, 4.0)) {
            assertRejected { r.issue(true, tool.id, quantity, date, person, "") }
        }
        assertRejected { r.issue(true, tool.id, 1.0, date, 999L, "") }
        assertRejected { r.issue(true, tool.id, 1.0, "2026-02-30", person, "") }
        assertEquals(3.0, r.items(true).single().quantity, 0.0)
        assertEquals(1, r.movements(true).size)
        r.issue(true, tool.id, 2.0, date, person, "", tool.availableUnits.take(2).map { it.id })
        val outgoing = r.movements(true).first { it.kind == "Egreso" }
        assertRejected { r.returnStock(true, outgoing.id, 3.0, date, null, "") }
        assertRejected { r.returnStock(true, outgoing.id, 0.5, date, null, "") }
        assertRejected { r.returnStock(true, outgoing.id, 1.0, "2026-10-11", null, "") }
        assertRejected { r.returnStock(false, outgoing.id, 1.0, date, person, "") }
        val income = r.movements(true).first { it.kind == "Ingreso" }
        assertRejected { r.returnStock(true, income.id, 1.0, date, null, "") }
        assertRejected { r.deleteMovement(true, income.id) }
        assertEquals(1.0, r.items(true).single().quantity, 0.0)
        assertEquals(10.5, r.items(false).single().quantity, 0.0)
        r.returnStock(true, outgoing.id, 1.0, date, null, "", outgoing.units.take(1).map { it.unit.id })
        assertRejected { r.returnStock(true, outgoing.id, 2.0, date, null, "") }
        assertEquals(1.0, r.movements(true).first { it.id == outgoing.id }.pending, 0.0)
    }

    @Test fun deletingConsumedReturnIsRejectedWithoutChangingParent() = withRepository { r, _ ->
        r.receive(false, entry(false, 10.0))
        val person = worker(r)
        val itemId = r.items(false).single().id
        r.issue(false, itemId, 6.0, date, person, "")
        val outgoing = r.movements(false).first { it.kind == "Egreso" }
        r.returnStock(false, outgoing.id, 2.0, date, person, "")
        val returned = r.movements(false).first { it.kind == "Devolución" }
        r.issue(false, itemId, 6.0, date, person, "")
        assertRejected { r.deleteMovement(false, returned.id) }
        assertEquals(0.0, r.items(false).single().quantity, 0.0)
        assertEquals(2.0, r.movements(false).first { it.id == outgoing.id }.returned, 0.0)
        assertTrue(r.movements(false).any { it.id == returned.id })
    }

    @Test fun decimalQuantitiesDoNotLeaveRoundingRemainders() = withRepository { r, _ ->
        r.receive(false, entry(false, 0.3))
        val person = worker(r)
        val id = r.items(false).single().id
        r.issue(false, id, 0.3, date, person, "")
        val outgoing = r.movements(false).first { it.kind == "Egreso" }
        r.returnStock(false, outgoing.id, 0.1, date, person, "")
        r.returnStock(false, outgoing.id, 0.2, date, person, "")
        assertEquals(0.3, r.items(false).single().quantity, 0.0)
        assertEquals(0.0, r.movements(false).first { it.id == outgoing.id }.pending, 0.0)
    }

    @Test fun upgradingVersionOnePreservesStockAndMigratesIncomeHistory() {
        val context = isolatedStockContext("migration_test_")
        context.deleteDatabase("stock_paniol.db")
        try {
            context.openOrCreateDatabase("stock_paniol.db", 0, null).use { db ->
                for (table in listOf("materiales", "herramientas")) {
                    db.execSQL("CREATE TABLE $table (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL COLLATE NOCASE UNIQUE, cantidad REAL NOT NULL CHECK(cantidad>=0), unidad TEXT NOT NULL, observaciones TEXT NOT NULL, imagen TEXT NOT NULL, marca TEXT NOT NULL, empresa TEXT NOT NULL)")
                    db.execSQL("INSERT INTO $table VALUES (1, 'Anterior', 5, '${if (table == "materiales") "kg" else "unidades"}', 'Notas', 'content://foto', 'Marca', 'Proveedor')")
                }
                db.execSQL("CREATE TABLE ingresos (id INTEGER PRIMARY KEY AUTOINCREMENT, tipo TEXT NOT NULL, articulo_id INTEGER NOT NULL, cantidad REAL NOT NULL, remito TEXT NOT NULL, fecha TEXT NOT NULL, observaciones TEXT NOT NULL, marca TEXT NOT NULL, empresa TEXT NOT NULL)")
                db.execSQL("INSERT INTO ingresos VALUES (1, 'materiales', 1, 5, 'Remito', '$date', 'Notas', '', '')")
                db.execSQL("INSERT INTO ingresos VALUES (2, 'herramientas', 1, 5, 'Remito', '$date', 'Notas', '', '')")
                db.execSQL("CREATE TABLE configuracion (clave TEXT PRIMARY KEY, valor TEXT NOT NULL)")
                db.execSQL("INSERT INTO configuracion VALUES ('nombre', 'Mi obra')")
                db.version = 1
            }
            LocalStockRepository(context).use { r ->
                assertEquals("Mi obra", r.stockName)
                assertEquals(5.0, r.items(false).single().quantity, 0.0)
                assertEquals("content://foto", r.items(true).single().image)
                assertEquals(MaterialTypes.CONSTRUCTION, r.items(false).single().materialType)
                assertEquals("Ingreso", r.movements(false).single().kind)
                assertEquals("Ingreso", r.movements(true).single().kind)
                assertEquals(12, r.readableDatabase.version)
                assertTrue(r.personnel().isEmpty())
                r.deleteMovement(false, r.movements(false).single().id)
                assertEquals(0.0, r.items(false).single().quantity, 0.0)
                assertEquals(5.0, r.items(true).single().quantity, 0.0)
            }
        } finally { context.deleteDatabase("stock_paniol.db") }
    }
}
