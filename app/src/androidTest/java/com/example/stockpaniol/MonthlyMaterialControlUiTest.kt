package com.example.stockpaniol

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonthlyMaterialControlUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun currentMonthShowsEveryoneAndSearchAndMonthSelectionUpdateTheTotals() {
        val date = todayStockDate()
        val item = StockItem(8, "Guantes moteados", 10.0, "pares", "", "", "", "")
        val workers = listOf(Worker(1, "Ana", "Ruiz", Worksite(1, "Obra"), ""),
            Worker(2, "Luis", "Paz", Worksite(1, "Obra"), ""))
        val movement = StockMovement(1, 8, item.name, 2.0, "pares", date, "Ana Ruiz", 1, "Egreso", MaterialTypes.PPE,
            "", null, 0.0, "", null)
        compose.setContent { MaterialTheme {
            MonthlyMaterialControlContent(item, workers, listOf(movement), Modifier.fillMaxSize())
        } }
        compose.onNodeWithTag("control-quantity-1", useUnmergedTree = true).assertTextEquals("2")
        compose.onNodeWithTag("control-quantity-2", useUnmergedTree = true).assertTextEquals("0")
        compose.onNodeWithTag("control-worker-1", useUnmergedTree = true).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2 pares recibidos en el mes"))
        compose.onNodeWithTag("control-search").performTextInput("Luis")
        compose.onNodeWithTag("control-worker-1", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("control-worker-2", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("control-search").performTextClearance()
        compose.onNodeWithTag("control-month").performClick()
        compose.onNodeWithTag("control-previous-year").performClick()
        compose.onNodeWithText("Ene").performClick()
        compose.onNodeWithTag("control-quantity-1", useUnmergedTree = true).assertTextEquals("0")
    }

    @Test fun gloveDetailOffersTheControlTab() {
        val context = isolatedStockContext("glove_control_ui_")
        val repository = LocalStockRepository(context)
        try {
            repository.receive(false, StockEntry(0, "Guantes moteados", 10.0, "pares", "", "", todayStockDate(), "", "", "", MaterialTypes.PPE))
            val id = repository.items(false).single().id
            compose.setContent { MaterialTheme { IssueDialog(false, id, repository, {}, {}) } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Control").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Control").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("control-month").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Personal").assertExists()
            compose.onNodeWithText("Cantidad (pares)").assertExists()
            compose.onNodeWithText("Confirmar egreso").assertDoesNotExist()
        } finally { repository.close(); context.deleteDatabase("stock_paniol.db") }
    }
}
