package com.example.stockpaniol

import org.junit.Assert.*
import org.junit.Test

class MovementStatusTest {
    private val unit = InventoryUnit(1, 1, "TAL 001", "Bosch")
    private fun loan(quantity: Double = 1.0, returned: Double = 0.0) = StockMovement(1, 1, "Taladro", quantity,
        "unidades", "2026-09-01", "Ana", 1, "Egreso", "", "", null, returned, "", null,
        StockControl.INDIVIDUAL, listOf(MovementUnit(unit, returned == quantity)))

    @Test fun defaultLoanFilterIncludesOldAndPartialLoansAndExcludesCompletedOnes() {
        val pending = loan(2.0, 1.0)
        val completed = loan(2.0, 2.0)
        assertTrue(matchesMovementStatus(pending, emptyMap(), MovementStatus.LOANED))
        assertFalse(matchesMovementStatus(completed, emptyMap(), MovementStatus.LOANED))
        assertTrue(matchesMovementStatus(completed, emptyMap(), MovementStatus.RETURNED))
        assertTrue(matchesMovementStatus(completed, emptyMap(), MovementStatus.ALL))
        assertFalse(matchesMovementStatus(pending.copy(kind = "Ingreso"), emptyMap(), MovementStatus.LOANED))
    }

    @Test fun completedLoansFollowReturnedUnitsThroughDamageAndRepair() {
        val returned = loan(returned = 1.0)
        assertEquals(MovementStatus.RETURNED, movementStatus(returned, mapOf(1L to unit)))
        assertEquals(MovementStatus.DAMAGED, movementStatus(returned, mapOf(1L to unit.copy(condition = ToolCondition.DAMAGED))))
        assertEquals(MovementStatus.DAMAGED, movementStatus(returned, mapOf(1L to unit.copy(useDamaged = true))))
        assertEquals(MovementStatus.IN_REPAIR, movementStatus(returned, mapOf(1L to unit.copy(condition = ToolCondition.IN_REPAIR))))
        assertEquals(MovementStatus.REPAIRED, movementStatus(returned.copy(kind = ToolAction.REPAIRED.movement), mapOf(1L to unit)))
    }

    @Test fun oldReturnsAreNotShownAsInPanolWhenTheUnitWasLoanedAgainOrRetired() {
        val returned = loan(returned = 1.0).copy(kind = "Devolución")
        for (current in listOf(unit.copy(loanId = 7, holder = "Luis"), unit.copy(condition = ToolCondition.RETIRED), unit.copy(excludedByAdjustment = true))) {
            assertEquals(MovementStatus.OTHER, movementStatus(returned, mapOf(1L to current)))
            assertFalse(matchesMovementStatus(returned, mapOf(1L to current), MovementStatus.RETURNED))
        }
    }

    @Test fun physicalEventsKeepTheirRepairAndDamageCategoriesAndBulkReturnsAreIncluded() {
        val row = loan(returned = 1.0)
        assertEquals(MovementStatus.IN_REPAIR, movementStatus(row.copy(kind = ToolAction.SEND_REPAIR.movement), emptyMap()))
        assertEquals(MovementStatus.DAMAGED, movementStatus(row.copy(kind = "Devolución dañada", units = emptyList()), emptyMap()))
        assertEquals(MovementStatus.DAMAGED, movementStatus(row.copy(kind = MaterialDamageAction.DAMAGE.movement), emptyMap()))
        assertEquals(MovementStatus.RETURNED, movementStatus(row.copy(kind = "Devolución", control = StockControl.QUANTITY, units = emptyList()), emptyMap()))
        assertEquals(MovementStatus.OTHER, movementStatus(row.copy(kind = MaterialDamage.CORRECTION), emptyMap()))
    }
}
