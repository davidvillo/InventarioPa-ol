package com.example.stockpaniol

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StockAlertsRepositoryTest {
    @Test fun settingsAndPerMaterialThresholdsPersistAndInvalidChangesRollBack() {
        val context = isolatedStockContext("stock_alerts_")
        val date = "2026-10-22"
        try {
            LocalStockRepository(context).use { r ->
                assertFalse(r.stockAlertsEnabled)
                r.receive(false, StockEntry(0, "Arena", 10.0, "kg", "", "", date, "", "", ""))
                r.receive(false, StockEntry(0, "Cemento", 0.0, "kg", "", "", date, "", "", "", stockThreshold = 2.5))
                val sand = r.items(false).first { it.name == "Arena" }
                assertEquals(5.0, sand.stockThreshold, 0.0)
                r.stockAlertsEnabled = true
                val edit = MaterialEdit(sand.name, sand.quantity, sand.quantity, sand.notes, sand.brand, sand.company, sand.materialType, stockThreshold = 7.5)
                r.updateMaterial(sand.id, edit)
                assertTrue(runCatching { r.updateMaterial(sand.id, edit.copy(stockThreshold = -1.0)) }.isFailure)
                assertEquals(7.5, r.items(false).first { it.id == sand.id }.stockThreshold, 0.0)
                assertTrue(runCatching { r.updateMaterial(sand.id, edit.copy(name = "", stockThreshold = 20.0)) }.isFailure)
                assertEquals(7.5, r.items(false).first { it.id == sand.id }.stockThreshold, 0.0)
                r.receive(false, StockEntry(sand.id, sand.name, 1.0, "kg", "", "", date, "", "", ""))
                assertEquals(7.5, r.items(false).first { it.id == sand.id }.stockThreshold, 0.0)
            }
            LocalStockRepository(context).use { r ->
                assertTrue(r.stockAlertsEnabled)
                assertEquals(7.5, r.items(false).first { it.name == "Arena" }.stockThreshold, 0.0)
                assertEquals(2.5, r.items(false).first { it.name == "Cemento" }.stockThreshold, 0.0)
                r.stockAlertsEnabled = false
            }
            LocalStockRepository(context).use { assertFalse(it.stockAlertsEnabled) }
        } finally { context.deleteDatabase("stock_paniol.db") }
    }
}
