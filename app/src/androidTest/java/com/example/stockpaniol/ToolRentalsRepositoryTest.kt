package com.example.stockpaniol

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolRentalsRepositoryTest {
    private val date = "2026-10-14"
    private fun entry(count: Double = 1.0, rented: Boolean = true) = StockEntry(0, "Amoladora", count, "unidades", "", "A-25", date, "", "", "",
        rental = if (rented) RentalEntry("Alquileres Norte", "A-25") else null)
    private fun withRepository(block: (LocalStockRepository, android.content.Context) -> Unit) {
        val context = isolatedStockContext("tool_rentals_")
        context.deleteDatabase("stock_paniol.db")
        try { LocalStockRepository(context).use { block(it, context) } }
        finally { context.deleteDatabase("stock_paniol.db") }
    }
    private fun rejects(action: () -> Unit) { assertTrue(runCatching(action).isFailure) }
    private fun worker(r: LocalStockRepository): Long {
        r.addWorker("Ana", "Ruiz", "Obra", "")
        return r.personnel().single().id
    }
    private fun stock(r: LocalStockRepository, count: Double) {
        val item = r.items(true).single()
        assertEquals(count, item.quantity, 0.0)
        assertEquals(count, item.availableUnits.size.toDouble(), 0.0)
        assertEquals(0, r.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
    }

    @Test fun ownedAndRentedUnitsShareCatalogueButRentalsHaveTheirOwnFullHistoryAndSupplierReturn() = withRepository { r, context ->
        r.receive(true, entry(rented = false))
        r.receive(true, entry(2.0))
        val item = r.items(true).single()
        val owned = item.units.single { it.rental == null }
        val rented = item.units.filter { it.rental != null }
        stock(r, 3.0)
        assertEquals(1, r.purchases(true).size)
        assertEquals(2, r.movements(true).count { it.kind == ToolRentals.INCOME })
        assertTrue(rented.all { it.rental!!.supplier == "Alquileres Norte" && it.rental!!.reference == "A-25" && it.rental!!.startDate == date })
        r.issue(true, item.id, 3.0, date, worker(r), "Entrega a obra", item.units.map { it.id })
        val loan = r.movements(true).first { it.kind == "Egreso" }
        assertTrue(loan.identitySummary.contains("Alquilada: Alquileres Norte"))
        r.returnStock(true, loan.id, 3.0, date, null, "", item.units.map { it.id }, mapOf(rented[1].id to "Cable cortado"))
        stock(r, 2.0)
        r.returnRental(rented[0].id, date, "Entregada completa")
        stock(r, 1.0)
        r.returnRental(rented[1].id, date, "Proveedor recibe con cable cortado")
        stock(r, 1.0)
        assertEquals(listOf(owned.id), r.items(true).single().availableUnits.map { it.id })
        assertFalse(r.items(true).single().identitySummary.contains(rented[0].identifier))
        assertEquals(9, r.movements(true).size)
        assertEquals(2, r.movements(true).count { it.kind == ToolRentals.RETURN })
        assertEquals(loan.identitySummary, r.movements(true).first { it.id == loan.id }.identitySummary.replace(" · Devuelta", ""))
        r.close()
        LocalStockRepository(context).use { reopened ->
            stock(reopened, 1.0)
            val saved = reopened.items(true).single().units.filter { it.rental != null }
            assertTrue(saved.all { it.rental!!.closed && it.rental!!.returnedDate == date })
            assertEquals("Proveedor recibe con cable cortado", saved.first { it.id == rented[1].id }.rental!!.returnNotes)
            assertEquals(1, reopened.purchases(true).size)
            assertEquals(9, reopened.movements(true).size)
        }
    }

    @Test fun rentalsUseDamageAndRepairFlowAndCannotBeReturnedWhileLentOrUnderRepairOrClosedTwice() = withRepository { r, _ ->
        r.receive(true, entry())
        val item = r.items(true).single()
        val unit = item.units.single()
        val person = worker(r)
        rejects { r.returnRental(unit.id, "2026-10-13", "") }
        r.issue(true, item.id, 1.0, date, person, "", listOf(unit.id))
        rejects { r.returnRental(unit.id, date, "") }
        val loan = r.movements(true).first { it.kind == "Egreso" }
        r.returnStock(true, loan.id, 1.0, date, null, "", listOf(unit.id), mapOf(unit.id to "Motor quemado"))
        stock(r, 0.0)
        r.changeToolState(unit.id, ToolAction.SEND_REPAIR, date, "", "Taller Sur")
        rejects { r.returnRental(unit.id, date, "Retirar del taller") }
        r.changeToolState(unit.id, ToolAction.UNREPAIRED, date, "Irreparable")
        rejects { r.returnRental(unit.id, date, "") }
        r.changeToolState(unit.id, ToolAction.RETIRE, date, "Sin reparación")
        r.returnRental(unit.id, date, "Proveedor recibe irreparable")
        stock(r, 0.0)
        val before = r.movements(true)
        rejects { r.returnRental(unit.id, date, "Otra vez") }
        rejects { r.issue(true, item.id, 1.0, date, person, "", listOf(unit.id)) }
        rejects { r.changeToolState(unit.id, ToolAction.RESTORE, date, "Recuperar") }
        rejects { r.deleteMovement(true, before.first { it.kind == ToolRentals.INCOME }.id) }
        rejects { r.deleteMovement(true, before.first { it.kind == ToolRentals.RETURN }.id) }
        assertEquals(before, r.movements(true))
        assertTrue(r.purchases(true).isEmpty())
    }

    @Test fun invalidRentalReceiptsAndReturningOwnedToolLeaveStockAndHistoryUntouched() = withRepository { r, _ ->
        rejects { r.receive(true, entry().copy(rental = RentalEntry("   "))) }
        rejects { r.receive(false, entry()) }
        assertTrue(r.items(true).isEmpty())
        assertTrue(r.movements(true).isEmpty())
        r.receive(true, entry(rented = false))
        val before = r.items(true).single()
        rejects { r.returnRental(before.units.single().id, date, "") }
        assertEquals(before, r.items(true).single())
        assertEquals(1, r.movements(true).size)
    }

    @Test fun versionSevenMigrationKeepsOwnedToolDamageLoanPhotosAndHistory() = withRepository { r, context ->
        r.receive(true, entry(2.0, rented = false))
        val item = r.items(true).single()
        val unit = item.units.first()
        r.issue(true, item.id, 1.0, date, worker(r), "", listOf(unit.id))
        val loan = r.movements(true).first { it.kind == "Egreso" }
        r.returnStock(true, loan.id, 1.0, date, null, "", listOf(unit.id), mapOf(unit.id to "Rota"))
        r.updateImages(true, item.id, listOf("content://photos/1", "content://photos/2"))
        val before = r.items(true).single()
        val history = r.movements(true)
        removeToolRentalSchema(r.writableDatabase)
        r.writableDatabase.version = 7
        r.close()
        LocalStockRepository(context).use { upgraded ->
            assertEquals(12, upgraded.readableDatabase.version)
            assertEquals(before, upgraded.items(true).single())
            assertEquals(history, upgraded.movements(true))
            upgraded.receive(true, entry())
            stock(upgraded, 2.0)
            assertEquals(1, upgraded.items(true).single().units.count { it.rental != null })
            assertEquals(1, upgraded.purchases(true).size)
        }
    }
}
