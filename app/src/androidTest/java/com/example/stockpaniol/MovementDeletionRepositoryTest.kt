package com.example.stockpaniol

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovementDeletionRepositoryTest {
    private val date = "2026-10-22"
    private fun withStock(block: (LocalStockRepository, android.content.Context) -> Unit) {
        val context = isolatedStockContext("movement_deletion_")
        try { LocalStockRepository(context).use { block(it, context) } }
        finally { context.deleteDatabase("stock_paniol.db") }
    }
    private fun add(r: LocalStockRepository, tools: Boolean, individual: Boolean = tools, name: String = if (tools) "Taladro" else "Guantes moteados"): StockItem {
        r.receive(tools, StockEntry(0, name, 4.0, if (tools) "unidades" else "pares", "", "", date, "", "", "",
            control = if (individual) StockControl.INDIVIDUAL else StockControl.QUANTITY))
        return r.items(tools).first { it.name == name }
    }
    private fun person(r: LocalStockRepository): Long { r.addWorker("Ana", "Ruiz", "Obra", ""); return r.personnel().single().id }
    private fun check(r: LocalStockRepository, tools: Boolean, quantity: Double) {
        val item = r.items(tools).single()
        assertEquals(quantity, item.quantity, 0.0)
        if (item.individuallyTracked) assertEquals(quantity, item.availableUnits.size.toDouble(), 0.0)
        assertEquals(0, r.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
    }

    @Test fun toolDamageRepairAndReturnDeletionReverseStockAndPersist() = withStock { r, context ->
        val item = add(r, true)
        val id = item.units.first().id
        r.changeToolState(id, ToolAction.DAMAGE, date, "Rotura")
        val damage = r.movements(true).first { it.kind == ToolAction.DAMAGE.movement }
        check(r, true, 3.0)
        r.changeToolState(id, ToolAction.SEND_REPAIR, date, "Revisar", "Taller")
        r.deleteMovement(true, r.movements(true).first { it.kind == ToolAction.SEND_REPAIR.movement }.id)
        assertEquals(ToolCondition.DAMAGED, r.items(true).single().units.first().condition)
        r.deleteMovement(true, damage.id)
        check(r, true, 4.0)
        val worker = person(r)
        r.issue(true, item.id, 1.0, date, worker, "", listOf(id))
        val loan = r.movements(true).first { it.kind == "Egreso" }
        r.returnStock(true, loan.id, 1.0, date, null, "", listOf(id), mapOf(id to "Dañada"))
        check(r, true, 3.0)
        r.deleteMovement(true, r.movements(true).first { it.kind == "Devolución dañada" }.id)
        assertEquals(1.0, r.movements(true).first { it.id == loan.id }.pending, 0.0)
        check(r, true, 3.0)
        r.deleteMovement(true, loan.id)
        check(r, true, 4.0)
        LocalStockRepository(context).use { reopened ->
            check(reopened, true, 4.0)
            assertEquals(listOf("Ingreso"), reopened.movements(true).map { it.kind })
        }
    }

    @Test fun deletingReturnedLoanRemovesDependentEventsAndThenItsPurchaseCanBeDeleted() = withStock { r, _ ->
        val item = add(r, true)
        r.issue(true, item.id, 1.0, date, person(r), "", listOf(item.units.first().id))
        val loan = r.movements(true).first { it.kind == "Egreso" }
        r.returnStock(true, loan.id, 1.0, date, null, "", listOf(item.units.first().id))
        r.deleteMovement(true, loan.id)
        assertEquals(1, r.movements(true).size)
        r.deleteMovement(true, r.purchases(true).single().movementId!!)
        check(r, true, 0.0)
        assertTrue(r.purchases(true).isEmpty())
        assertTrue(r.items(true).single().units.isEmpty())
    }

    @Test fun deletingOneBundleLoanDeletesAllLinkedLoansAndReturnsAtomically() = withStock { r, _ ->
        val main = add(r, true)
        val accessory = add(r, true, name = "Batería")
        r.saveToolBundle(null, "Equipo", listOf(main.units.first().id, accessory.units.first().id))
        r.issue(true, main.id, 1.0, date, person(r), "", listOf(main.units.first().id))
        val loan = r.movements(true).first { it.kind == "Egreso" }
        r.returnStock(true, loan.id, 1.0, date, null, "", loan.units.map { it.unit.id })
        r.deleteMovement(true, loan.id)
        assertTrue(r.movements(true).all { it.kind == "Ingreso" })
        assertTrue(r.items(true).all { it.quantity == 4.0 && it.availableUnits.size == 4 })
        assertEquals(0, r.readableDatabase.rawQuery("SELECT * FROM prestamos_conjuntos", null).use { it.count })
        assertEquals(0, r.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
    }

    @Test fun rentalReturnAndIncomeCanBeDeleted() = withStock { r, _ ->
        r.receive(true, StockEntry(0, "Alquiler", 1.0, "unidades", "", "", date, "", "", "", rental = RentalEntry("Proveedor")))
        val unit = r.items(true).single().units.single()
        r.returnRental(unit.id, date, "Devuelta")
        r.deleteMovement(true, r.movements(true).first { it.kind == ToolRentals.RETURN }.id)
        check(r, true, 1.0)
        assertFalse(r.items(true).single().units.single().rental!!.closed)
        r.deleteMovement(true, r.movements(true).single().id)
        check(r, true, 0.0)
        assertTrue(r.items(true).single().units.isEmpty())
    }

    @Test fun materialDamageUseAndCorrectionsAreReversedForBothControlTypes() = withStock { r, _ ->
        for (individual in listOf(false, true)) {
            val item = add(r, false, individual, if (individual) "Arnés" else "Guantes moteados")
            val ids = item.units.take(1).map { it.id }
            r.changeMaterialState(item.id, MaterialDamageAction.DAMAGE, 1.0, date, "Rotura", ids)
            val damage = r.damagedMaterials().first { it.itemId == item.id }
            r.changeMaterialState(item.id, MaterialDamageAction.USE, 1.0, date, "Utilizar", damageId = damage.id)
            val use = r.movements(false).first { it.itemId == item.id && it.kind == MaterialDamageAction.USE.movement }
            r.deleteMovement(false, use.id)
            assertEquals(3.0, r.items(false).first { it.id == item.id }.quantity, 0.0)
            r.deleteMovement(false, r.movements(false).first { it.itemId == item.id && it.kind == MaterialDamageAction.DAMAGE.movement }.id)
            val restored = r.items(false).first { it.id == item.id }
            assertEquals(4.0, restored.quantity, 0.0)
            r.updateMaterial(item.id, MaterialEdit(restored.name, 3.0, 4.0, "", "", "", restored.materialType,
                removedUnitIds = if (individual) ids else emptyList(), date = date))
            r.deleteMovement(false, r.movements(false).first { it.itemId == item.id && it.kind == MaterialDamage.CORRECTION }.id)
            assertEquals(4.0, r.items(false).first { it.id == item.id }.quantity, 0.0)
        }
        assertTrue(r.damagedMaterials().isEmpty())
        assertEquals(0, r.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
    }

    @Test fun consumedQuantityReturnAndReferencedIncomeRejectWithoutPartialChanges() = withStock { r, _ ->
        val item = add(r, false)
        val worker = person(r)
        r.issue(false, item.id, 4.0, date, worker, "")
        val loan = r.movements(false).first { it.kind == "Egreso" }
        r.returnStock(false, loan.id, 1.0, date, worker, "")
        val returned = r.movements(false).first { it.kind == "Devolución" }
        r.issue(false, item.id, 1.0, date, worker, "")
        val before = r.movements(false)
        assertTrue(runCatching { r.deleteMovement(false, returned.id) }.isFailure)
        assertEquals(before, r.movements(false))
        check(r, false, 0.0)
        assertTrue(runCatching { r.deleteMovement(false, r.purchases(false).single().movementId!!) }.isFailure)
        assertEquals(before, r.movements(false))
    }
}
