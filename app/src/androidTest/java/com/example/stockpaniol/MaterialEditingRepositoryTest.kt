package com.example.stockpaniol

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaterialEditingRepositoryTest {
    private val date = "2026-10-16"
    private fun withStock(block: (LocalStockRepository, android.content.Context) -> Unit) {
        val context = isolatedStockContext("material_editing_")
        try { LocalStockRepository(context).use { block(it, context) } }
        finally { context.deleteDatabase("stock_paniol.db") }
    }
    private fun draft(item: StockItem, quantity: Double = item.quantity, removed: List<Long> = emptyList()) =
        MaterialEdit(item.name, quantity, item.quantity, item.notes, item.brand, item.company, item.materialType, removed, date)
    private fun add(r: LocalStockRepository, individual: Boolean = false): StockItem {
        r.receive(false, StockEntry(0, if (individual) "Guantess" else "Arenaa", if (individual) 4.0 else 10.5,
            if (individual) "pares" else "kg", "", "R1", date, "", "Marca", "Empresa",
            control = if (individual) StockControl.INDIVIDUAL else StockControl.QUANTITY))
        return r.items(false).single()
    }
    private fun worker(r: LocalStockRepository): Long {
        r.addWorker("Ana", "Ruiz", "Obra", "")
        return r.personnel().single().id
    }

    @Test fun quantityAndTextEditsPersistWithoutRewritingPurchasesOrPendingLoans() = withStock { r, context ->
        val original = add(r)
        val person = worker(r)
        r.issue(false, original.id, 2.5, date, person, "", emptyList())
        val loan = r.movements(false).first { it.kind == "Egreso" }
        val item = r.items(false).single()
        r.updateMaterial(item.id, draft(item, 7.25).copy(name = "Arena", brand = "Nueva marca", notes = "Acopio sur", company = "Otra empresa"))
        val saved = r.items(false).single()
        assertEquals("Arena", saved.name)
        assertEquals(7.25, saved.quantity, 0.0)
        assertEquals("Acopio sur", saved.notes)
        assertEquals(10.5, r.purchases(false).single().quantity, 0.0)
        assertEquals("Empresa", r.purchases(false).single().company)
        assertEquals(loan.copy(itemName = "Arena", auditProtected = true), r.movements(false).first { it.id == loan.id })
        assertEquals(1, r.movements(false).count { it.kind == MaterialDamage.CORRECTION })
        LocalStockRepository(context).use { reopened ->
            assertEquals(saved, reopened.items(false).single())
            reopened.returnStock(false, loan.id, 1.0, date, person, "")
            assertEquals(8.25, reopened.items(false).single().quantity, 0.0)
            assertEquals(1.5, reopened.movements(false).first { it.id == loan.id }.pending, 0.0)
        }
    }

    @Test fun reusableCorrectionsKeepExactUnitsLoansDamageAndHistory() = withStock { r, context ->
        val original = add(r, true)
        val person = worker(r)
        r.issue(false, original.id, 1.0, date, person, "", listOf(original.units.first().id))
        r.changeMaterialState(original.id, MaterialDamageAction.DAMAGE, 1.0, date, "Rotura", listOf(original.units[1].id))
        val item = r.items(false).single()
        val removed = original.units.last().id
        assertTrue(runCatching { r.updateMaterial(item.id, draft(item, 1.0, listOf(original.units.first().id))) }.isFailure)
        assertEquals(item, r.items(false).single())
        r.updateMaterial(item.id, draft(item, 1.0, listOf(removed)).copy(name = "Guantes"))
        val corrected = r.items(false).single()
        assertEquals(1, corrected.availableUnits.size)
        assertTrue(corrected.units.first { it.id == removed }.excludedByAdjustment)
        assertTrue(corrected.units.first { it.id == original.units.first().id }.loanId != null)
        assertEquals(ToolCondition.DAMAGED, corrected.units[1].condition)
        r.updateMaterial(item.id, draft(corrected, 3.0))
        assertEquals(3, r.items(false).single().availableUnits.size)
        assertEquals(6, r.items(false).single().units.size)
        assertEquals(6, r.items(false).single().units.map { it.identifier }.distinct().size)
        assertEquals(4.0, r.purchases(false).single().quantity, 0.0)
        LocalStockRepository(context).use { reopened ->
            assertTrue(reopened.items(false).single().units.first { it.id == removed }.excludedByAdjustment)
            assertEquals(3.0, reopened.items(false).single().quantity, 0.0)
            val loan = reopened.movements(false).first { it.kind == "Egreso" }
            reopened.returnStock(false, loan.id, 1.0, date, person, "", listOf(original.units.first().id))
            assertEquals(4, reopened.items(false).single().availableUnits.size)
            assertEquals(4.0, reopened.items(false).single().quantity, 0.0)
            assertEquals(0, reopened.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
        }
    }

    @Test fun invalidAndStaleEditsLeaveTheDatabaseUnchanged() = withStock { r, _ ->
        val item = add(r, true)
        val history = r.movements(false)
        for (edit in listOf(draft(item).copy(name = ""), draft(item, -1.0), draft(item, 1.5), draft(item, Double.NaN),
            draft(item).copy(expectedQuantity = 99.0), draft(item, 3.0), draft(item, 3.0, listOf(item.units.first().id, item.units.first().id)))) {
            assertTrue(runCatching { r.updateMaterial(item.id, edit) }.isFailure)
            assertEquals(item, r.items(false).single())
            assertEquals(history, r.movements(false))
        }
        r.receive(false, StockEntry(0, "Otro material", 1.0, "unidades", "", "", date, "", "", ""))
        assertTrue(runCatching { r.updateMaterial(item.id, draft(item).copy(name = "Otro material")) }.isFailure)
    }

    @Test fun upgradingAnExistingStockPreservesAllUnitsAsActive() = withStock { r, context ->
        val item = add(r, true)
        r.writableDatabase.execSQL("ALTER TABLE unidades_materiales DROP COLUMN descartada_ajuste")
        r.writableDatabase.version = 12
        r.close()
        LocalStockRepository(context).use { upgraded ->
            assertEquals(14, upgraded.readableDatabase.version)
            assertEquals(item, upgraded.items(false).single())
        }
    }
}
