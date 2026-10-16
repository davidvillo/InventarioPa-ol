package com.example.stockpaniol

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolSummaryCardUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cardsKeepTheSameCollapsedHeightAndOnlyDetailExpandsEveryUnit() {
        val single = StockItem(1, "Cincel", 1.0, "unidades", "", "", "", "", control = StockControl.INDIVIDUAL,
            units = listOf(InventoryUnit(1, 1, "CIN 001", "")))
        val multiple = StockItem(2, "Martillo", 1.0, "unidades", "", "", "", "", control = StockControl.INDIVIDUAL,
            units = listOf(
                InventoryUnit(2, 2, "MAR 001", ""),
                InventoryUnit(3, 2, "MAR 002", "", condition = ToolCondition.DAMAGED),
                InventoryUnit(4, 2, "MAR 003", "", condition = ToolCondition.IN_REPAIR),
                InventoryUnit(5, 2, "MAR 004", "", condition = ToolCondition.RETIRED)))
        var photos = 0
        var edits = 0
        compose.setContent { MaterialTheme {
            Column {
                ToolSummaryCard(single, { photos++ }, { edits++ })
                ToolSummaryCard(multiple, { photos++ }, { edits++ })
            }
        } }
        val height = compose.onNodeWithTag("tool-card-1").fetchSemanticsNode().boundsInRoot.height
        assertEquals(height, compose.onNodeWithTag("tool-card-2").fetchSemanticsNode().boundsInRoot.height, 0.1f)
        compose.onNodeWithTag("tool-units-2").assertDoesNotExist()
        compose.onNode(hasText("Sin imagen") and hasAnyAncestor(hasTestTag("tool-card-2"))).performClick()
        assertEquals(1, photos)
        compose.onNodeWithTag("tool-units-2").assertDoesNotExist()
        compose.onNodeWithTag("tool-detail-2").performClick()
        compose.onNodeWithTag("tool-units-2").assertIsDisplayed()
        assertEquals(0, edits)
        listOf("MAR 001 · Disponible en pañol", "MAR 002 · Dañada en pañol", "MAR 003 · En reparación", "MAR 004 · Baja · Papelera").forEach { label ->
            compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("tool-units-2")), useUnmergedTree = true).assertExists()
        }
        assertTrue(compose.onNodeWithTag("tool-card-2").fetchSemanticsNode().boundsInRoot.height > height)
        compose.onNodeWithTag("tool-detail-2").performClick()
        assertEquals(height, compose.onNodeWithTag("tool-card-2").fetchSemanticsNode().boundsInRoot.height, 0.1f)
        compose.onNodeWithText("Martillo").performClick()
        assertEquals(1, edits)
    }
}
