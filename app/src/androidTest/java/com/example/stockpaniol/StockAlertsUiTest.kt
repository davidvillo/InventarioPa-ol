package com.example.stockpaniol

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StockAlertsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun titlesReflectStockAndReturnToNormalWhenDisabled() {
        val enabled = mutableStateOf(false)
        val quantity = mutableStateOf(10.0)
        compose.setContent { MaterialTheme {
            StockSummaryCard(StockItem(1, "Arena", quantity.value, "kg", "", "", "", ""), {}, {}, tools = false,
                stockAlertsEnabled = enabled.value)
        } }
        val title = compose.onNodeWithTag("material-title-1", useUnmergedTree = true)
        title.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.runOnIdle { enabled.value = true }
        title.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, StockAlert.AVAILABLE.label))
        compose.runOnIdle { quantity.value = 5.0 }
        title.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, StockAlert.LOW.label))
        compose.runOnIdle { quantity.value = 0.0 }
        title.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, StockAlert.EMPTY.label))
        compose.runOnIdle { enabled.value = false }
        title.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test fun settingsSwitchSavesThePreference() {
        val context = isolatedStockContext("stock_alerts_ui_")
        val repository = LocalStockRepository(context)
        var saved = false
        try {
            compose.setContent { MaterialTheme { StockAlertsDialog(repository, {}, { saved = it }) } }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("stock-alerts-switch").fetchSemanticsNodes().any {
                    !it.config.contains(SemanticsProperties.Disabled)
                }
            }
            compose.onNodeWithTag("stock-alerts-switch").assertIsOff().performClick()
            compose.onNodeWithText("Guardar").performClick()
            compose.waitUntil(5_000) { saved }
            assertTrue(repository.stockAlertsEnabled)
        } finally { repository.close(); context.deleteDatabase("stock_paniol.db") }
    }
}
