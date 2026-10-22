package com.example.stockpaniol

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StockLoanedTodayUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun materialsFilterWorksInBothViewsAndRestoresTheSearch() = verifyFilter(false)
    @Test fun toolsFilterWorksInBothViewsAndRestoresTheSearch() = verifyFilter(true)

    private fun verifyFilter(tools: Boolean) {
        val context = isolatedStockContext("today_filter_${tools}_")
        val r = LocalStockRepository(context)
        try {
            val unit = if (tools) "unidades" else "litros"
            for (name in listOf("Filtro hoy", "Filtro ayer")) {
                r.receive(tools, StockEntry(0, name, 2.0, unit, "", "", yesterdayStockDate(), "", "", ""))
            }
            r.addWorker("Ana", "Ruiz", "Obra", "")
            r.items(tools).forEach { item ->
                r.issue(tools, item.id, 1.0, if (item.name == "Filtro hoy") todayStockDate() else yesterdayStockDate(),
                    r.personnel().single().id, "", item.units.take(1).map { it.id })
            }
            compose.setContent { MaterialTheme { StockApp(r) } }
            if (tools) compose.onNodeWithText("Herramientas").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Filtro hoy").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Buscar nombre, marca o identificador").performTextInput("Filtro")
            compose.onNodeWithTag("stock-loaned-today").assertIsNotSelected().performClick().assertIsSelected()
            compose.onNodeWithText("Filtro hoy").assertExists()
            compose.onNodeWithText("Filtro ayer").assertDoesNotExist()
            compose.onNodeWithText(if (tools) "unidad" else "litro", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("Vista completa").performClick()
            compose.onNodeWithText("Filtro hoy", substring = true, useUnmergedTree = true).assertExists()
            compose.onNodeWithText("Filtro ayer", substring = true, useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag("stock-loaned-today").performClick().assertIsNotSelected()
            compose.onNodeWithText("Filtro ayer", substring = true, useUnmergedTree = true).assertExists()
            compose.onNodeWithContentDescription("Buscar nombre, marca o identificador").assertTextContains("Filtro")
            compose.onNodeWithText("Vista sencilla").performClick()
            compose.onNodeWithText("Filtro hoy").assertExists()
            compose.onNodeWithText("Filtro ayer").assertExists()
        } finally { r.close(); context.deleteDatabase("stock_paniol.db") }
    }
}
