package com.example.stockpaniol

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolRentalsUiTest {
    @get:Rule val compose = createComposeRule()
    private fun input(label: String, value: String) {
        compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextReplacement(value)
    }

    @Test fun rentalEntrySupplierReturnAndUnitHistoryAreAvailableFromTheApp() {
        val context = isolatedStockContext("tool_rentals_ui_")
        context.deleteDatabase("stock_paniol.db")
        val repository = LocalStockRepository(context)
        try {
            compose.setContent { MaterialTheme { StockApp(repository) } }
            compose.onNodeWithText("Herramientas").performClick()
            compose.onNodeWithText("+ Agregar herramienta").performClick()
            input("Nombre *", "Amoladora")
            compose.onNode(isToggleable() and hasAnySibling(hasText("Es una herramienta alquilada"))).performScrollTo().performClick()
            input("Proveedor del alquiler *", "Alquileres Norte")
            input("Cantidad a ingresar *", "1")
            input("Remito / contrato de alquiler (opcional)", "A-25")
            compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(isDialog()))
                .performScrollToNode(hasSetTextAction() and hasText("Identificador de unidad 1 (opcional)"))
            input("Identificador de unidad 1 (opcional)", "ALQ-01")
            compose.onNodeWithText("Guardar ingreso").performClick()
            compose.waitUntil(10_000) { repository.items(true).isNotEmpty() }
            compose.waitForIdle()
            val unit = repository.items(true).single().units.single()
            assertEquals("Alquileres Norte", unit.rental!!.supplier)
            assertTrue(repository.purchases(true).isEmpty())
            compose.onNodeWithText("☰").performClick()
            compose.onNodeWithText("Herramientas alquiladas").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Devolver al proveedor").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Remito / contrato: A-25").assertIsDisplayed()
            compose.onNodeWithText("Devolver al proveedor").performScrollTo().performClick()
            input("Observaciones de entrega", "Entregada en buen estado")
            compose.onNodeWithText("Confirmar devolución al proveedor").performClick()
            compose.waitUntil(10_000) { repository.items(true).single().units.single().rental!!.closed }
            compose.waitForIdle()
            assertEquals(0.0, repository.items(true).single().quantity, 0.0)
            compose.onNodeWithText("Devueltos al proveedor").performClick()
            compose.onNodeWithText("Alquiler devuelto al proveedor").assertIsDisplayed()
            compose.onNodeWithText("Ver movimientos del alquiler").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("2 movimientos").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Todos los movimientos · Todas las fechas").assertIsDisplayed()
            compose.onNodeWithText("Ingreso por alquiler · ${displayStockDate(todayStockDate())}").assertExists()
            compose.onNodeWithText("Devolución de alquiler · ${displayStockDate(todayStockDate())}").assertExists()
        } finally { repository.close(); context.deleteDatabase("stock_paniol.db") }
    }
}
