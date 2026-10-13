package com.example.stockpaniol

import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseSortingTest {
    @Test fun sortsDatesQuantitiesSuppliersAndReceiptsInBothDirections() {
        val rows = listOf(StockPurchase(1, 1, "Arena", 10.0, "kg", "2026-10-01", "Zeta", "R-B"),
            StockPurchase(2, 1, "Arena", 2.5, "kg", "2026-09-30", "Áridos", "R-A"))
        for (column in listOf(PurchaseColumn.DATE, PurchaseColumn.QUANTITY, PurchaseColumn.COMPANY, PurchaseColumn.RECEIPT)) {
            assertEquals(listOf(2L, 1L), sortPurchases(rows, column, false).map { it.id })
            assertEquals(listOf(1L, 2L), sortPurchases(rows, column, true).map { it.id })
        }
    }
}
