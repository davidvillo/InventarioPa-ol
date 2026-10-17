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
class MaterialDeletionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun harnessCanBeDeletedFromItsWindowAfterConfirmingAndCancellationKeepsIt() {
        val context = isolatedStockContext("material_deletion_ui_")
        val r = LocalStockRepository(context)
        try {
            r.receive(false, StockEntry(0, "Arnés", 1.0, "unidades", "", "", todayStockDate(), "", "", "",
                materialType = MaterialTypes.PPE, control = StockControl.INDIVIDUAL))
            compose.setContent { MaterialTheme { StockApp(r) } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Arnés").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Arnés").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Eliminar material").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Eliminar material").assertIsDisplayed().performClick()
            compose.onNodeWithText("Cancelar").performClick()
            assertEquals(1, r.items(false).size)
            compose.onNodeWithText("Eliminar material").performClick()
            compose.onNodeWithText("Eliminar definitivamente").performClick()
            compose.waitUntil(10_000) { r.items(false).isEmpty() }
            compose.onNodeWithText("Tu stock empieza acá").assertExists()
        } finally { r.close(); context.deleteDatabase("stock_paniol.db") }
    }

    @Test fun deletionErrorIsVisibleWhenTheMaterialHasALoan() {
        val context = isolatedStockContext("material_protected_ui_")
        val r = LocalStockRepository(context)
        try {
            r.receive(false, StockEntry(0, "Arena", 2.0, "kg", "", "", todayStockDate(), "", "", ""))
            r.addWorker("Ana", "Ruiz", "Obra", "")
            r.issue(false, r.items(false).single().id, 1.0, todayStockDate(), r.personnel().single().id, "")
            compose.setContent { MaterialTheme { StockApp(r) } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Arena").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Arena").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Eliminar material").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Eliminar material").performClick()
            compose.onNodeWithText("Eliminar definitivamente").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("No se puede eliminar: el material tiene egresos o devoluciones. Se conserva su historial.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("No se puede eliminar: el material tiene egresos o devoluciones. Se conserva su historial.").assertIsDisplayed()
            assertEquals(1.0, r.items(false).single().quantity, 0.0)
        } finally { r.close(); context.deleteDatabase("stock_paniol.db") }
    }
}
