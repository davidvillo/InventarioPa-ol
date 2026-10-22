package com.example.stockpaniol

import org.junit.Assert.*
import org.junit.Test

class StockAlertsTest {
    private fun item(quantity: Double = 2.0) = StockItem(1, "Material", quantity, "kg", "", "", "", "")
    private fun reusable() = item().copy(control = StockControl.INDIVIDUAL, units = listOf(
        InventoryUnit(1, 1, "A", ""), InventoryUnit(2, 1, "B", "")))

    @Test fun consumablesUseEachThresholdIncludingEqualityAndZero() {
        assertEquals(StockAlert.AVAILABLE, stockAlert(item(5.1), false))
        assertEquals(StockAlert.LOW, stockAlert(item(5.0), false))
        assertEquals(StockAlert.LOW, stockAlert(item(0.1), false))
        assertEquals(StockAlert.EMPTY, stockAlert(item(0.0), false))
        assertEquals(StockAlert.AVAILABLE, stockAlert(item(3.0).copy(stockThreshold = 2.5), false))
        assertEquals(StockAlert.LOW, stockAlert(item(2.5).copy(stockThreshold = 2.5), false))
        assertEquals(StockAlert.AVAILABLE, stockAlert(item(0.1).copy(stockThreshold = 0.0), false))
    }

    @Test fun loansAndEmptyInventoryHaveDifferentColorsForBothReusableLists() {
        for (tools in listOf(false, true)) {
            val full = reusable()
            assertEquals(StockAlert.AVAILABLE, stockAlert(full, tools))
            val partial = full.copy(quantity = 1.0, units = full.units.mapIndexed { index, unit ->
                if (index == 0) unit.copy(loanId = 10) else unit
            })
            assertEquals(StockAlert.LOANED, stockAlert(partial, tools))
            val allLoaned = full.copy(quantity = 0.0, units = full.units.map { it.copy(loanId = 10) })
            assertEquals(StockAlert.LOW, stockAlert(allLoaned, tools))
            assertEquals(StockAlert.EMPTY, stockAlert(full.copy(quantity = 0.0, units = emptyList()), tools))
            assertEquals(StockAlert.EMPTY, stockAlert(allLoaned.copy(units = allLoaned.units.map { it.copy(excludedByAdjustment = true) }), tools))
        }
    }

    @Test fun outOfServiceUnitsDoNotShowFullAvailability() {
        val full = reusable()
        val damaged = full.copy(quantity = 1.0, units = listOf(full.units[0], full.units[1].copy(condition = ToolCondition.DAMAGED)))
        assertEquals(StockAlert.LOW, stockAlert(damaged, true))
        assertEquals(StockAlert.AVAILABLE, stockAlert(damaged.copy(units = listOf(full.units[0], full.units[1].copy(condition = ToolCondition.RETIRED))), true))
    }

    @Test fun thresholdSupportsDecimalsAndRejectsInvalidNumbers() {
        assertEquals(2.5, parseStockThreshold("2,5")!!, 0.0)
        listOf("", "-1", "NaN", "Infinity", "texto").forEach { assertNull(parseStockThreshold(it)) }
    }
}
