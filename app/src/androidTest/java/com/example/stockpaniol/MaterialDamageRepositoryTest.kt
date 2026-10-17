package com.example.stockpaniol

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaterialDamageRepositoryTest {
    private val date = "2026-10-16"
    private fun withStock(block: (LocalStockRepository, android.content.Context) -> Unit) {
        val context = isolatedStockContext("material_damage_")
        try { LocalStockRepository(context).use { block(it, context) } }
        finally { context.deleteDatabase("stock_paniol.db") }
    }
    private fun entry(individual: Boolean = false) = StockEntry(0, if (individual) "Arnés" else "Arena",
        if (individual) 2.0 else 10.5, if (individual) "unidades" else "kg", "", "", date, "", "", "",
        control = if (individual) StockControl.INDIVIDUAL else StockControl.QUANTITY)

    @Test fun quantitiesCanBeUsedDamagedOrDiscardedWithoutLosingHistory() = withStock { r, context ->
        r.receive(false, entry())
        val id = r.items(false).single().id
        r.changeMaterialState(id, MaterialDamageAction.DAMAGE, 2.5, date, "Contaminada")
        assertEquals(8.0, r.items(false).single().quantity, 0.0)
        val damage = r.damagedMaterials().single()
        r.changeMaterialState(id, MaterialDamageAction.TRASH, 2.5, date, "No se recupera", damageId = damage.id)
        assertEquals(8.0, r.items(false).single().quantity, 0.0)
        assertTrue(runCatching { r.changeMaterialState(id, MaterialDamageAction.USE, 2.5, date, "Usar", damageId = damage.id) }.isFailure)
        r.changeMaterialState(id, MaterialDamageAction.RESTORE, 2.5, date, "Revisar", damageId = damage.id)
        r.changeMaterialState(id, MaterialDamageAction.USE, 2.5, date, "Apta para relleno", damageId = damage.id)
        assertEquals(10.5, r.items(false).single().quantity, 0.0)
        assertTrue(runCatching { r.changeMaterialState(id, MaterialDamageAction.USE, 2.5, date, "Otra vez", damageId = damage.id) }.isFailure)
        assertEquals(4, r.movements(false).count { it.id < 0 })
        assertTrue(runCatching { r.deleteMovement(false, r.movements(false).first().id) }.isFailure)
        assertTrue(runCatching { r.deleteMaterial(id) }.isFailure)
        LocalStockRepository(context).use { reopened ->
            assertEquals("USABLE", reopened.damagedMaterials().single().state)
            assertEquals(10.5, reopened.items(false).single().quantity, 0.0)
            assertEquals(0, reopened.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
        }
    }

    @Test fun identifiedUnitsAreBlockedUntilEnabledAndKeepDamageOnLoans() = withStock { r, _ ->
        r.receive(false, entry(true))
        val item = r.items(false).single()
        val ids = listOf(item.units.first().id)
        r.addWorker("Ana", "Ruiz", "Obra", "")
        val worker = r.personnel().single().id
        r.changeMaterialState(item.id, MaterialDamageAction.DAMAGE, 1.0, date, "Rotura", ids)
        assertFalse(r.items(false).single().units.first().available)
        assertTrue(runCatching { r.issue(false, item.id, 1.0, date, worker, "", ids) }.isFailure)
        val damage = r.damagedMaterials().single()
        r.changeMaterialState(item.id, MaterialDamageAction.USE, 1.0, date, "Daño superficial", damageId = damage.id)
        assertTrue(r.items(false).single().units.first().useDamaged)
        r.issue(false, item.id, 1.0, date, worker, "", ids)
        assertTrue(runCatching { r.changeMaterialState(item.id, MaterialDamageAction.DAMAGE, 1.0, date, "Rotura", ids) }.isFailure)
        val loan = r.movements(false).first { it.kind == "Egreso" }
        r.returnStock(false, loan.id, 1.0, date, worker, "", ids)
        assertEquals(2.0, r.items(false).single().quantity, 0.0)
        assertTrue(r.items(false).single().units.first().useDamaged)
    }

    @Test fun invalidDamageDoesNotChangeStockAndMigrationPreservesMaterials() = withStock { r, context ->
        r.receive(false, entry())
        val item = r.items(false).single()
        for ((quantity, notes) in listOf(11.0 to "Daño", 0.0 to "Daño", 1.0 to "")) {
            assertTrue(runCatching { r.changeMaterialState(item.id, MaterialDamageAction.DAMAGE, quantity, date, notes) }.isFailure)
        }
        assertEquals(item, r.items(false).single())
        assertTrue(r.damagedMaterials().isEmpty())
        r.writableDatabase.execSQL("DROP TABLE eventos_materiales")
        r.writableDatabase.execSQL("DROP TABLE danos_materiales")
        r.writableDatabase.version = 10
        r.close()
        LocalStockRepository(context).use { upgraded ->
            assertEquals(12, upgraded.readableDatabase.version)
            assertEquals(item, upgraded.items(false).single())
            assertTrue(upgraded.damagedMaterials().isEmpty())
        }
    }

    @Test fun mixedDamagedReturnClosesLoanAndOnlyRestocksHealthyUnitsAtomically() = withStock { r, context ->
        r.receive(false, entry(true))
        val item = r.items(false).single()
        val ids = item.units.map { it.id }
        r.addWorker("Ana", "Ruiz", "Obra", "")
        val worker = r.personnel().single().id
        r.issue(false, item.id, 2.0, date, worker, "", ids)
        val loan = r.movements(false).first { it.kind == "Egreso" }
        assertTrue(runCatching { r.returnStock(false, loan.id, 2.0, date, worker, "", ids, mapOf(ids.first() to "")) }.isFailure)
        assertEquals(2.0, r.movements(false).first { it.id == loan.id }.pending, 0.0)
        assertEquals(0.0, r.items(false).single().quantity, 0.0)
        r.returnStock(false, loan.id, 2.0, date, worker, "Entrega", ids, mapOf(ids.first() to "Hebilla rota"))
        assertEquals(0.0, r.movements(false).first { it.id == loan.id }.pending, 0.0)
        assertEquals(1.0, r.items(false).single().quantity, 0.0)
        assertEquals(listOf(ids.last()), r.items(false).single().availableUnits.map { it.id })
        assertTrue(r.items(false).single().units.all { it.loanId == null })
        val damage = r.damagedMaterials().single()
        assertEquals(ids.first(), damage.unitId)
        assertEquals("Hebilla rota", damage.notes)
        assertTrue(runCatching { r.deleteMovement(false, loan.id) }.isFailure)
        LocalStockRepository(context).use { reopened ->
            assertEquals(1.0, reopened.items(false).single().quantity, 0.0)
            assertEquals(ToolCondition.DAMAGED, reopened.items(false).single().units.first().condition)
            assertEquals(0, reopened.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
        }
        r.changeMaterialState(item.id, MaterialDamageAction.USE, 1.0, date, "Uso autorizado", damageId = damage.id)
        assertEquals(2.0, r.items(false).single().quantity, 0.0)
        r.issue(false, item.id, 1.0, date, worker, "", listOf(ids.first()))
        val secondLoan = r.movements(false).first { it.kind == "Egreso" && it.id != loan.id }
        r.returnStock(false, secondLoan.id, 1.0, date, worker, "", listOf(ids.first()), mapOf(ids.first() to "Se agravó el daño"))
        assertEquals(1.0, r.items(false).single().quantity, 0.0)
        assertEquals(1, r.damagedMaterials().count { it.unitId == ids.first() })
        assertEquals("DAMAGED", r.damagedMaterials().single().state)
    }
}
