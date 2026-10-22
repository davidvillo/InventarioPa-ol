package com.example.stockpaniol

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StockErrorsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun errorsDisappearAfterTwoSecondsAndTheSameErrorCanBeShownAgain() {
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme {
            var error by rememberStockError()
            Column { Button(onClick = { error = "Seleccioná la persona que recibe." }) { Text("Probar error") } }
        } }
        compose.onNodeWithText("Probar error").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("transient-error").assertExists()
        compose.mainClock.advanceTimeBy(1_800)
        compose.onNodeWithTag("transient-error").assertExists()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("transient-error").assertDoesNotExist()
        compose.onNodeWithText("Probar error").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("transient-error").assertExists()
    }

    @Test fun missingPersonnelOnAnIssueShowsTheErrorWithoutSavingTheIssue() {
        val context = isolatedStockContext("missing_person_error_")
        val r = LocalStockRepository(context)
        try {
            r.receive(false, StockEntry(0, "Guantes moteados", 4.0, "pares", "", "", todayStockDate(), "", "", ""))
            compose.setContent { MaterialTheme { IssueDialog(false, r.items(false).single().id, r, {}, {}) } }
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("Cantidad (pares)") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("Cantidad (pares)") and hasSetTextAction()).performScrollTo().performTextInput("1")
            compose.onNodeWithText("Confirmar egreso").performClick()
            compose.onNodeWithText("Seleccioná la persona que recibe.").assertIsDisplayed()
            assertEquals(1, r.movements(false).size)
            assertEquals(4.0, r.items(false).single().quantity, 0.0)
        } finally { r.close(); context.deleteDatabase("stock_paniol.db") }
    }
}
