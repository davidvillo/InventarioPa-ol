package com.example.stockpaniol

import org.junit.Assert.assertEquals
import org.junit.Test

class StockSortingTest {
    private fun item(id: Long, name: String, quantity: Double, unit: String = "kg", notes: String = "") =
        StockItem(id, name, quantity, unit, notes, "", "", "")

    @Test fun numericColumnsUseNumericOrderInBothDirections() {
        val items = listOf(item(10, "Arena", 10.0), item(2, "Ripio", 2.5), item(1, "Cal", 0.75))
        for (column in listOf(StockColumn.ITEM, StockColumn.QUANTITY)) {
            assertEquals(listOf(1L, 2L, 10L), sortStock(items, column, false).map { it.id })
            assertEquals(listOf(10L, 2L, 1L), sortStock(items, column, true).map { it.id })
        }
    }

    @Test fun namesUseSpanishAlphabetIncludingAccentsAndEnye() {
        val items = listOf(item(1, "Ñandú", 1.0), item(2, "Zinc", 1.0), item(3, "áridos", 1.0), item(4, "Arena", 1.0), item(5, "Nivel", 1.0))
        assertEquals(listOf(4L, 3L, 5L, 1L, 2L), sortStock(items, StockColumn.NAME, false).map { it.id })
        assertEquals(listOf(2L, 1L, 5L, 3L, 4L), sortStock(items, StockColumn.NAME, true).map { it.id })
    }

    @Test fun unitsAndNotesSortIndependentlyWithStableTies() {
        val items = listOf(item(3, "C", 1.0, "mts", "Taller"), item(2, "B", 1.0, "kg", "Obra"), item(1, "A", 1.0, "kg", ""))
        assertEquals(listOf(1L, 2L, 3L), sortStock(items, StockColumn.UNIT, false).map { it.id })
        assertEquals(listOf(3L, 1L, 2L), sortStock(items, StockColumn.UNIT, true).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), sortStock(items, StockColumn.NOTES, false).map { it.id })
        assertEquals(listOf(3L, 2L, 1L), sortStock(items, StockColumn.NOTES, true).map { it.id })
    }
}
