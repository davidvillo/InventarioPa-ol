package com.example.stockpaniol

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReusablePairsRepositoryTest {
    private val date = "2026-10-16"
    private fun withStock(block: (LocalStockRepository, android.content.Context) -> Unit) {
        val context = isolatedStockContext("reusable_pairs_")
        try { LocalStockRepository(context).use { block(it, context) } }
        finally { context.deleteDatabase("stock_paniol.db") }
    }
    private fun gloves(unit: String = "pares") = StockEntry(0, "Guantes", 2.0, unit, "", "", date, "", "", "",
        materialType = MaterialTypes.PPE, control = StockControl.INDIVIDUAL)

    @Test fun pairsAreIdentifiedAndFractionalPairsAndToolsUsingPairsAreRejected() = withStock { r, _ ->
        r.receive(false, gloves())
        val item = r.items(false).single()
        assertEquals("pares", item.unit)
        assertEquals(2, item.units.size)
        assertEquals(2, item.units.map { it.identifier }.distinct().size)
        assertTrue(runCatching { r.receive(false, gloves().copy(itemId = item.id, quantity = 0.5)) }.isFailure)
        assertTrue(runCatching { r.receive(true, gloves().copy(name = "Taladro")) }.isFailure)
        assertEquals(2.0, r.items(false).single().quantity, 0.0)
        assertTrue(r.items(true).isEmpty())
        val pairs = r.measurements().first { it.pairs }
        r.updateMeasurement(pairs.id, "juegos de guantes", true)
        r.receive(false, gloves("juegos de guantes").copy(itemId = item.id, quantity = 1.0))
        assertEquals(3, r.items(false).single().units.size)
    }

    @Test fun migrationAndChangingExistingGlovesToPairsPreserveLoansDamagePhotosAndHistory() = withStock { r, context ->
        r.receive(false, gloves("unidades"))
        val item = r.items(false).single()
        r.updateImages(false, item.id, listOf("content://photos/gloves"))
        r.addWorker("Ana", "Ruiz", "Obra", "")
        val worker = r.personnel().single().id
        r.issue(false, item.id, 1.0, date, worker, "", listOf(item.units.first().id))
        r.changeMaterialState(item.id, MaterialDamageAction.DAMAGE, 1.0, date, "Rotura", listOf(item.units.last().id))
        val before = r.items(false).single()
        val history = r.movements(false)
        val purchases = r.purchases(false)
        val damages = r.damagedMaterials()
        r.writableDatabase.execSQL("ALTER TABLE unidades_medida DROP COLUMN pareado")
        r.writableDatabase.version = 11
        r.close()
        LocalStockRepository(context).use { upgraded ->
            assertEquals(13, upgraded.readableDatabase.version)
            val pairs = upgraded.measurements().first { it.pairs }
            upgraded.updateMaterialUnit(item.id, pairs.id)
            assertEquals(before.copy(unit = "pares"), upgraded.items(false).single())
            assertEquals(history, upgraded.movements(false))
            assertEquals(purchases, upgraded.purchases(false))
            assertEquals(damages, upgraded.damagedMaterials())
            upgraded.receive(false, gloves().copy(itemId = item.id, quantity = 1.0))
            assertEquals(1.0, upgraded.items(false).single().quantity, 0.0)
            val loan = upgraded.movements(false).first { it.kind == "Egreso" }
            upgraded.returnStock(false, loan.id, 1.0, date, worker, "", listOf(item.units.first().id))
            assertEquals(2.0, upgraded.items(false).single().quantity, 0.0)
            assertEquals("unidades", upgraded.movements(false).first { it.id == loan.id }.unit)
            assertEquals(0, upgraded.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
        }
        LocalStockRepository(context).use { reopened ->
            assertEquals("pares", reopened.items(false).single().unit)
            assertEquals(3, reopened.items(false).single().units.size)
        }
    }

    @Test fun unitChangeCannotConvertAQuantityMaterialOrUseDecimalMeasures() = withStock { r, _ ->
        r.receive(false, gloves("unidades"))
        val gloves = r.items(false).single()
        val kg = r.measurements().first { it.name == "kg" }
        assertTrue(runCatching { r.updateMaterialUnit(gloves.id, kg.id) }.isFailure)
        assertEquals(gloves, r.items(false).single())
        r.receive(false, StockEntry(0, "Arena", 2.5, "kg", "", "", date, "", "", ""))
        val sand = r.items(false).first { it.name == "Arena" }
        assertTrue(runCatching { r.updateMaterialUnit(sand.id, r.measurements().first { it.pairs }.id) }.isFailure)
        assertEquals(sand, r.items(false).first { it.id == sand.id })
    }
}
