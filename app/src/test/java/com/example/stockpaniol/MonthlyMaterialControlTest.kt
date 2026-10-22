package com.example.stockpaniol

import org.junit.Assert.*
import org.junit.Test

class MonthlyMaterialControlTest {
    private val workers = listOf(
        Worker(1, "Ana", "Ruiz", Worksite(1, "Obra"), "Ani"),
        Worker(2, "Luis", "Paz", Worksite(1, "Obra"), ""),
        Worker(3, "Ana", "Ruiz", Worksite(1, "Obra"), "", active = false))
    private fun delivery(id: Long, person: Long = 1, quantity: Double = 1.0, date: String = "2026-10-22") =
        StockMovement(id, 8, "Guantes moteados", quantity, "pares", date, "Ana Ruiz", person, "Egreso", MaterialTypes.PPE,
            "", null, 0.0, "", null)

    @Test fun totalsIncludeAllDeliveriesAndZeroRowsAndKeepPeopleWithSameNameSeparate() {
        val result = monthlyMaterialRows(8, "2026-10", workers, listOf(delivery(1), delivery(2), delivery(3, 3)))
        assertEquals(listOf(2L, 1L, 3L), result.map { it.worker.id })
        assertEquals(mapOf(1L to 2.0, 2L to 0.0, 3L to 1.0), result.associate { it.worker.id to it.quantity })
        assertEquals(StockAlert.AVAILABLE, monthlyMaterialAlert(0.0))
        assertEquals(StockAlert.LOANED, monthlyMaterialAlert(1.0))
        assertEquals(StockAlert.EMPTY, monthlyMaterialAlert(2.0))
        assertEquals(StockAlert.EMPTY, monthlyMaterialAlert(3.0))
    }

    @Test fun totalsUseTheChosenMonthYearItemAndDeliveriesRatherThanReturnsOrPurchases() {
        val rows = listOf(delivery(1, quantity = 2.0).copy(returned = 2.0),
            delivery(2).copy(kind = "Devolución"), delivery(3).copy(kind = "Ingreso"),
            delivery(4).copy(itemId = 9), delivery(5, date = "2026-09-30"),
            delivery(6, date = "2025-10-22"), delivery(7).copy(workerId = null))
        assertEquals(2.0, monthlyMaterialRows(8, "2026-10", workers, rows).first { it.worker.id == 1L }.quantity, 0.0)
        assertEquals(1.0, monthlyMaterialRows(8, "2025-10", workers, rows).first { it.worker.id == 1L }.quantity, 0.0)
        assertTrue(monthlyMaterialRows(8, "2026-11", workers, rows).all { it.quantity == 0.0 })
    }

    @Test fun searchIncludesNameSurnameAndNicknameAndZerosRemainVisible() {
        assertEquals(listOf(1L), monthlyMaterialRows(8, "2026-10", workers, emptyList(), " ANI ").map { it.worker.id })
        assertEquals(listOf(1L, 3L), monthlyMaterialRows(8, "2026-10", workers, emptyList(), "ruiz").map { it.worker.id })
        assertTrue(monthlyMaterialRows(8, "2026-10", workers, emptyList(), "sin coincidencias").isEmpty())
    }

    @Test fun controlIsOnlyAvailableForTheNamedMaterial() {
        val item = StockItem(8, "  GUANTES   MOTEADOS ", 10.0, "pares", "", "", "", "")
        assertTrue(hasGloveControl(item))
        assertFalse(hasGloveControl(item.copy(name = "Guantes de cuero")))
    }

    @Test fun quantityOrderingKeepsZerosAndBreaksTiesAlphabeticallyWhileRespectingSearch() {
        val people = workers + Worker(4, "Marta", "Alonso", Worksite(1, "Obra"), "")
        val deliveries = listOf(delivery(1, person = 1, quantity = 3.0), delivery(2, person = 3, quantity = 1.0))
        fun ids(sort: MonthlyMaterialSort, query: String = "") =
            monthlyMaterialRows(8, "2026-10", people, deliveries, query, sort).map { it.worker.id }
        assertEquals(listOf(4L, 2L, 1L, 3L), ids(MonthlyMaterialSort.PERSONAL_ASC))
        assertEquals(listOf(1L, 3L, 2L, 4L), ids(MonthlyMaterialSort.PERSONAL_DESC))
        assertEquals(listOf(1L, 3L, 4L, 2L), ids(MonthlyMaterialSort.QUANTITY_DESC))
        assertEquals(listOf(4L, 2L, 3L, 1L), ids(MonthlyMaterialSort.QUANTITY_ASC))
        assertEquals(listOf(3L, 1L), ids(MonthlyMaterialSort.QUANTITY_ASC, "ruiz"))
    }
}
