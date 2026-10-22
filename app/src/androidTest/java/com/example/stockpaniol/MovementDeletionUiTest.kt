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
class MovementDeletionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun purchaseTrashDeletesItsIncomeAndStockInTheDatabase() {
        val context = isolatedStockContext("purchase_delete_ui_")
        val r = LocalStockRepository(context)
        try {
            r.receive(false, StockEntry(0, "Guantes moteados", 4.0, "pares", "", "", todayStockDate(), "", "", ""))
            val id = r.purchases(false).single().id
            var changed = false
            compose.setContent { MaterialTheme { PurchaseHistoryDialog(false, r, {}, { changed = true }) } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("1 compras").fetchSemanticsNodes().isNotEmpty() }
            repeat(5) { compose.onNodeWithTag("zoomable-table").performTouchInput { swipeLeft() } }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("delete-purchase-$id").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("delete-purchase-$id").performScrollTo().performClick()
            compose.onNode(hasText("Eliminar") and hasClickAction()).performClick()
            compose.waitUntil(10_000) { changed }
            assertTrue(r.purchases(false).isEmpty())
            assertTrue(r.movements(false).isEmpty())
            assertEquals(0.0, r.items(false).single().quantity, 0.0)
        } finally { r.close(); context.deleteDatabase("stock_paniol.db") }
    }

    @Test fun formerlyProtectedEventTrashIsEnabledAndDeletesTheActualEvent() {
        val context = isolatedStockContext("event_delete_ui_")
        val r = LocalStockRepository(context)
        try {
            r.receive(true, StockEntry(0, "Taladro", 1.0, "unidades", "", "", todayStockDate(), "", "", ""))
            r.changeToolState(r.items(true).single().units.single().id, ToolAction.DAMAGE, todayStockDate(), "Rotura")
            val event = r.movements(true).first { it.id < 0 }
            compose.setContent { MaterialTheme { MovementHistoryDialog(true, r, {}, {}, initialQuery = "Taladro") } }
            val label = "Eliminar movimiento ${event.id}"
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription(label).assertIsEnabled().performScrollTo().performClick()
            compose.onNode(hasText("Eliminar") and hasClickAction()).performClick()
            compose.waitUntil(10_000) { r.movements(true).none { it.id == event.id } }
            assertEquals(1.0, r.items(true).single().quantity, 0.0)
        } finally { r.close(); context.deleteDatabase("stock_paniol.db") }
    }

    @Test fun aboutGuideOpensAndExpandsTheGloveInstructions() {
        val context = isolatedStockContext("guide_ui_")
        val r = LocalStockRepository(context)
        try {
            compose.setContent { MaterialTheme { SettingsDialog(r, {}) } }
            compose.onNodeWithText("Acerca de la aplicación").performScrollTo().performClick()
            compose.onNodeWithTag("app-guide").performScrollToNode(hasTestTag("guide-section-6"))
            compose.onNodeWithTag("guide-section-6").performClick()
            compose.onNodeWithTag("guide-detail-6").assertExists()
            compose.onNodeWithTag("guide-detail-6").assertTextContains("Entregas del mes", substring = true)
            compose.onNodeWithTag("app-guide").performScrollToNode(hasTestTag("guide-section-6"))
            compose.onNodeWithTag("guide-section-6").performClick()
            compose.onNodeWithTag("guide-detail-6").assertDoesNotExist()
        } finally { r.close(); context.deleteDatabase("stock_paniol.db") }
    }
}
