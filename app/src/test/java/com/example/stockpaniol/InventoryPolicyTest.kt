package com.example.stockpaniol

import org.junit.Assert.*
import org.junit.Test

class InventoryPolicyTest {
    @Test fun receiptsAllowZeroButRejectNegativeNonFiniteAndFractionalUnits() {
        for (policy in listOf(InventoryPolicy.ByUnit, InventoryPolicy.ByQuantity)) {
            policy.validateReceipt(0.0, "unidades", "")
            for (quantity in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1.5)) {
                rejects { policy.validateReceipt(quantity, "unidades", "") }
            }
        }
        InventoryPolicy.ByQuantity.validateReceipt(0.0, "kg", "")
        rejects { validateMovementQuantity(0.0, "unidades") }
    }

    private fun rejects(action: () -> Unit) {
        assertTrue(runCatching(action).exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun individualStockRequiresExactDistinctSelection() {
        InventoryPolicy.ByUnit.validateReceipt(1.0, "unidades", "BOSCH-01")
        InventoryPolicy.ByUnit.validateReceipt(3.0, "unidades", "")
        InventoryPolicy.ByUnit.validateReceipt(2.0, "pares", "")
        rejects { InventoryPolicy.ByUnit.validateReceipt(1.5, "pares", "") }
        InventoryPolicy.ByUnit.validateSelection(2.0, listOf(1L, 2L))
        rejects { InventoryPolicy.ByUnit.validateReceipt(2.0, "unidades", "BOSCH-01") }
        rejects { InventoryPolicy.ByUnit.validateReceipt(1.0, "kg", "") }
        rejects { InventoryPolicy.ByUnit.validateSelection(2.0, listOf(1L, 1L)) }
        rejects { InventoryPolicy.ByUnit.validateSelection(2.0, listOf(1L)) }
        rejects { InventoryPolicy.ByUnit.validateSelection(1.0, emptyList()) }
    }

    @Test fun consumableStockAllowsBulkAndDecimalsWithoutUnitIdentity() {
        InventoryPolicy.ByQuantity.validateReceipt(2.5, "kg", "")
        InventoryPolicy.ByQuantity.validateReceipt(20.0, "unidades", "")
        InventoryPolicy.ByQuantity.validateSelection(20.0, emptyList())
        rejects { InventoryPolicy.ByQuantity.validateReceipt(1.0, "unidades", "CAL-01") }
        rejects { InventoryPolicy.ByQuantity.validateSelection(1.0, listOf(1L)) }
    }
}
