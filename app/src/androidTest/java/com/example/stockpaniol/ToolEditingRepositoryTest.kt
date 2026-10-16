package com.example.stockpaniol

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolEditingRepositoryTest {
    private fun withStock(block: (LocalStockRepository, android.content.Context) -> Unit) {
        val context = isolatedStockContext("tool_editing_")
        try { LocalStockRepository(context).use { block(it, context) } }
        finally { context.deleteDatabase("stock_paniol.db") }
    }
    private fun prepare(r: LocalStockRepository): StockItem {
        r.receive(true, StockEntry(0, "Taladro", 2.0, "unidades", "Original", "R1", "2026-10-10", "", "", "Empresa"))
        return r.items(true).single()
    }
    private fun draft(r: LocalStockRepository, item: StockItem) = ToolEdit(item.name, item.notes, item.company, item.images,
        item.units.map { ToolUnitEdit(it.id, it.brand, it.identifier) },
        r.purchases(true).filter { it.itemId == item.id }.map { ToolPurchaseEdit(it.id, it.date, it.receipt, it.company) })

    @Test fun editingPersistsWithoutAddingStockAndAllowsSwappingIdentifiers() = withStock { r, context ->
        val item = prepare(r)
        val edit = draft(r, item)
        r.updateTool(item.id, edit.copy(name = "Taladro percutor", notes = "Corregida", company = "Otra empresa",
            units = edit.units.mapIndexed { index, unit -> unit.copy(brand = "Marca", identifier = edit.units[1 - index].identifier) },
            purchases = edit.purchases.map { it.copy(date = "2026-10-09", receipt = "R2", company = "Proveedor") }))
        LocalStockRepository(context).use { reopened ->
            val saved = reopened.items(true).single()
            assertEquals(item.id, saved.id)
            assertEquals("Taladro percutor", saved.name)
            assertEquals("Corregida", saved.notes)
            assertEquals(2.0, saved.quantity, 0.0)
            assertEquals(item.units.map { it.identifier }.reversed(), saved.units.map { it.identifier })
            assertEquals("R2", reopened.purchases(true).single().receipt)
            assertEquals("2026-10-09", reopened.movements(true).single().date)
        }
    }

    @Test fun invalidEditRollsBackAndDeletionRemovesRelatedRecordsOnly() = withStock { r, _ ->
        val item = prepare(r)
        val edit = draft(r, item)
        assertTrue(runCatching { r.updateTool(item.id, edit.copy(name = "Cambio", units = edit.units.map { it.copy(identifier = "DUP") })) }.isFailure)
        assertEquals(item, r.items(true).single())
        r.receive(false, StockEntry(0, "Arena", 1.0, "unidades", "", "", "2026-10-10", "", "", ""))
        r.deleteTool(item.id)
        assertTrue(r.items(true).isEmpty())
        assertTrue(r.movements(true).isEmpty())
        assertTrue(r.purchases(true).isEmpty())
        assertEquals(1, r.items(false).size)
        assertEquals(0, r.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
    }

    @Test fun loansProtectDeletionAndAcquisitionCannotMoveAfterLoan() = withStock { r, _ ->
        val item = prepare(r)
        r.addWorker("Ana", "Ruiz", "Obra", "")
        r.issue(true, item.id, 1.0, "2026-10-11", r.personnel().single().id, "", listOf(item.units.first().id))
        assertTrue(runCatching { r.deleteTool(item.id) }.isFailure)
        val edit = draft(r, item)
        assertTrue(runCatching { r.updateTool(item.id, edit.copy(purchases = edit.purchases.map { it.copy(date = "2026-10-12") })) }.isFailure)
        assertEquals(1.0, r.items(true).single().quantity, 0.0)
        assertEquals(2, r.movements(true).size)
    }

    @Test fun accidentalRentalCanBeDeletedWithoutLeavingReferences() = withStock { r, _ ->
        r.receive(true, StockEntry(0, "Alquilada", 1.0, "unidades", "", "", "2026-10-10", "", "", "",
            rental = RentalEntry("Proveedor")))
        r.deleteTool(r.items(true).single().id)
        assertTrue(r.items(true).isEmpty())
        assertTrue(ToolRentals.all(r.readableDatabase).isEmpty())
        assertEquals(0, r.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
    }
}
