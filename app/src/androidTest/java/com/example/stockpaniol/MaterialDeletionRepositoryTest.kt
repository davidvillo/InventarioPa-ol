package com.example.stockpaniol

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaterialDeletionRepositoryTest {
    private val date = "2026-10-16"
    private fun withStock(block: (LocalStockRepository, android.content.Context) -> Unit) {
        val context = isolatedStockContext("material_deletion_")
        try { LocalStockRepository(context).use { block(it, context) } }
        finally { context.deleteDatabase("stock_paniol.db") }
    }
    private fun harness() = StockEntry(0, "Arnés", 2.0, "unidades", "", "R1", date, "", "Marca", "Proveedor",
        materialType = MaterialTypes.PPE, control = StockControl.INDIVIDUAL)

    @Test fun identifiableMaterialDeletionRemovesAllItsReceiptsUnitsAndPurchasesOnly() = withStock { r, context ->
        r.receive(false, harness())
        val item = r.items(false).single()
        r.receive(false, harness().copy(itemId = item.id, quantity = 1.0))
        r.receive(false, StockEntry(0, "Arena", 10.0, "kg", "", "", date, "", "", ""))
        r.receive(true, StockEntry(0, "Taladro", 1.0, "unidades", "", "", date, "", "", ""))
        val tool = r.items(true).single()
        r.deleteMaterial(item.id)
        assertEquals(listOf("Arena"), r.items(false).map { it.name })
        assertTrue(r.movements(false).none { it.itemId == item.id })
        assertTrue(r.purchases(false).none { it.itemId == item.id })
        assertEquals(tool, r.items(true).single())
        assertEquals(0, r.readableDatabase.rawQuery("SELECT 1 FROM unidades_materiales WHERE articulo_id=?", arrayOf(item.id.toString())).use { it.count })
        assertEquals(0, r.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
        LocalStockRepository(context).use { reopened -> assertEquals(listOf("Arena"), reopened.items(false).map { it.name }) }
    }

    @Test fun quantityAndZeroStockMaterialsCanBeDeleted() = withStock { r, _ ->
        r.receive(false, StockEntry(0, "Arena", 10.0, "kg", "", "", date, "", "", ""))
        r.receive(false, StockEntry(0, "Vacío", 0.0, "unidades", "", "", date, "", "", ""))
        r.items(false).forEach { r.deleteMaterial(it.id) }
        assertTrue(r.items(false).isEmpty())
        assertTrue(r.movements(false).isEmpty())
        assertTrue(r.purchases(false).isEmpty())
    }

    @Test fun materialLoansAndReturnsArePreservedWhenDeletionIsRejected() = withStock { r, _ ->
        r.receive(false, harness())
        val item = r.items(false).single()
        r.addWorker("Ana", "Ruiz", "Obra", "")
        val workerId = r.personnel().single().id
        val ids = listOf(item.units.first().id)
        r.issue(false, item.id, 1.0, date, workerId, "", ids)
        val loan = r.movements(false).first { it.kind == "Egreso" }
        assertTrue(runCatching { r.deleteMaterial(item.id) }.isFailure)
        assertEquals(1.0, r.items(false).single().quantity, 0.0)
        r.returnStock(false, loan.id, 1.0, date, workerId, "", ids)
        val history = r.movements(false)
        assertTrue(runCatching { r.deleteMaterial(item.id) }.isFailure)
        assertEquals(history, r.movements(false))
        assertEquals(2.0, r.items(false).single().quantity, 0.0)
    }

    @Test fun harnessCreatedInAPreviousVersionCanBeDeletedAfterUpgrade() = withStock { r, context ->
        r.receive(false, harness())
        val id = r.items(false).single().id
        removeStockCatalogueSchema(r.writableDatabase)
        r.writableDatabase.version = 9
        r.close()
        LocalStockRepository(context).use { upgraded ->
            upgraded.deleteMaterial(id)
            assertTrue(upgraded.items(false).isEmpty())
            assertTrue(upgraded.purchases(false).isEmpty())
            assertEquals(0, upgraded.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
        }
    }
}
