package com.example.stockpaniol

import org.junit.Assert.*
import org.junit.Test

class StockDisplayTest {
    @Test fun oneUsesSingularButZeroDecimalsAndMultipleKeepTheirUnit() {
        val units = mapOf("unidades" to "unidad", "pares" to "par", "litros" to "litro", "mts" to "metro",
            "metros cuadrados" to "metro cuadrado", "metros cúbicos" to "metro cúbico", "bidones" to "bidón",
            "bolsas" to "bolsa", "rollos" to "rollo", "paquetes" to "paquete", "kg" to "kg", "bolsones" to "bolsón")
        units.forEach { (plural, singular) ->
            assertEquals(singular, stockUnitLabel(plural, 1.0))
            for (quantity in listOf(0.0, 0.5, 1.5, 2.0)) assertEquals(plural, stockUnitLabel(plural, quantity))
        }
        assertEquals("Unidad", stockUnitLabel("Unidades", 1.0))
        assertEquals("bolsa de 10 unidades", stockUnitLabel("bolsas de 10 unidades", 1.0))
        assertEquals("1 litro", formatStockAmount(1.0, "litros"))
    }

    @Test fun todayFilterIncludesOnlyPendingDeliveriesAndDoesNotChangeAvailableStock() {
        val items = (1L..5L).map { StockItem(it, "Artículo $it", 4.0, "unidades", "", "", "", "") }
        fun loan(id: Long, date: String = "2026-10-22", returned: Double = 0.0, kind: String = "Egreso") =
            StockMovement(id, id, "Artículo $id", 2.0, "unidades", date, "Ana", 1, kind, "", "", null, returned, "", null)
        val movements = listOf(loan(1), loan(2, returned = 1.0), loan(3, returned = 2.0),
            loan(4, date = "2026-10-21"), loan(5, kind = "Ingreso"))
        val result = stockLoanedToday(items, movements, "2026-10-22")
        assertEquals(listOf(1L, 2L), result.map { it.id })
        assertTrue(result.all { it.quantity == 4.0 })
        assertEquals(listOf(4L), stockLoanedToday(items, movements, "2026-10-21").map { it.id })
        assertTrue(stockLoanedToday(items, movements, "2026-10-23").isEmpty())
    }
}
