package com.example.stockpaniol

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StockSectionSwipeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun swipesSwitchStocksButVerticalScrollAndTapsKeepTheirActions() {
        val context = isolatedStockContext("stock_section_swipe_")
        context.deleteDatabase("stock_paniol.db")
        val repository = LocalStockRepository(context)
        try {
            repository.receive(false, StockEntry(0, "Material para deslizar", 2.0, "unidades", "", "", todayStockDate(), "", "", ""))
            repository.receive(true, StockEntry(0, "Herramienta para deslizar", 1.0, "unidades", "", "", todayStockDate(), "", "", ""))
            compose.setContent { MaterialTheme { StockApp(repository) } }
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Material para deslizar").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("stock-viewport").performTouchInput { swipeLeft() }
            compose.onNodeWithText("Herramientas").assertIsSelected()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Herramienta para deslizar").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("stock-viewport").performTouchInput { swipeRight() }
            compose.onNodeWithText("Materiales").assertIsSelected()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Material para deslizar").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("stock-viewport").performTouchInput { swipeUp() }
            compose.onNodeWithText("Materiales").assertIsSelected()
            compose.onNodeWithText("Material para deslizar").performClick()
            compose.onNodeWithText("Confirmar egreso").assertExists()
            compose.onNodeWithText("Cerrar").performClick()
            compose.onNodeWithText("Materiales").assertIsSelected()
        } finally { repository.close(); context.deleteDatabase("stock_paniol.db") }
    }

    @Test fun completeTableScrollsNormallyAndEdgeSwipesChangeStock() {
        val context = isolatedStockContext("stock_section_table_swipe_")
        context.deleteDatabase("stock_paniol.db")
        val repository = LocalStockRepository(context)
        try {
            repository.receive(false, StockEntry(0, "Material de tabla", 2.0, "unidades", "", "", todayStockDate(), "", "", ""))
            repository.receive(true, StockEntry(0, "Herramienta de tabla", 1.0, "unidades", "", "", todayStockDate(), "", "", ""))
            compose.setContent { MaterialTheme { StockApp(repository) } }
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Material de tabla").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Vista completa").performClick()
            compose.onNodeWithTag("zoomable-table").performTouchInput {
                pinch(center - Offset(width * 0.12f, 0f), center - Offset(width * 0.18f, 0f),
                    center + Offset(width * 0.12f, 0f), center + Offset(width * 0.18f, 0f))
            }
            compose.onNodeWithText("Materiales").assertIsSelected()
            val scroll = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and hasAnyAncestor(hasTestTag("zoomable-table")))
            scroll.performTouchInput { swipe(center, center - Offset(width * 0.12f, 0f), durationMillis = 450) }
            compose.onNodeWithText("Materiales").assertIsSelected()
            assertTrue(scroll.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value() > 0f)
            // Reach the end of the columns, then continue left to the next stock.
            repeat(3) {
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("zoomable-table").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("zoomable-table").performTouchInput { swipeLeft() }
            }
            compose.onNodeWithText("Herramientas").assertIsSelected()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("zoomable-table").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("zoomable-table").performTouchInput { swipeRight() }
            compose.onNodeWithText("Materiales").assertIsSelected()
            compose.onNodeWithTag("stock-tabs").performTouchInput { swipeLeft() }
            compose.onNodeWithText("Herramientas").assertIsSelected()
        } finally { repository.close(); context.deleteDatabase("stock_paniol.db") }
    }
}
