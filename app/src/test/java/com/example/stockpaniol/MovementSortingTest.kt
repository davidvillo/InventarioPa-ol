package com.example.stockpaniol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementSortingTest {
    private fun row(id: Long, date: String, person: String, quantity: Double) = StockMovement(id, 1, "Arena", quantity,
        "kg", date, person, 1, "Egreso", MaterialTypes.CONSTRUCTION, "", null, 0.0, "", null)

    @Test fun sortingUsesChronologicalDatesNumericQuantitiesAndSpanishNames() {
        val rows = listOf(row(1, "2026-10-01", "Zoe", 10.0), row(2, "2026-09-30", "Álvaro", 2.5))
        assertEquals(listOf(2L, 1L), sortMovements(rows, MovementColumn.DATE, false).map { it.id })
        assertEquals(listOf(1L, 2L), sortMovements(rows, MovementColumn.DATE, true).map { it.id })
        assertEquals(listOf(2L, 1L), sortMovements(rows, MovementColumn.QUANTITY, false).map { it.id })
        assertEquals(listOf(1L, 2L), sortMovements(rows, MovementColumn.QUANTITY, true).map { it.id })
        assertEquals(listOf(2L, 1L), sortMovements(rows, MovementColumn.PERSON, false).map { it.id })
    }

    @Test fun onlyMaterialsHaveCategoryAndOnlyToolsHaveReturnColumns() {
        assertTrue(MovementColumn.MATERIAL in movementColumns(false))
        assertFalse(MovementColumn.RETURN_DATE in movementColumns(false))
        assertFalse(MovementColumn.MATERIAL in movementColumns(true))
        assertTrue(MovementColumn.RETURN_DATE in movementColumns(true))
        assertTrue(MovementColumn.PENDING in movementColumns(true))
    }
}
