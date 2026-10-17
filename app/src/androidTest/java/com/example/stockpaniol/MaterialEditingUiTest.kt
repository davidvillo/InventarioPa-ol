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
class MaterialEditingUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun tappingAMaterialAllowsSavingItsCorrectedNameAndQuantityAndCancellationDoesNotChangeIt() {
        val context = isolatedStockContext("material_edit_ui_")
        val r = LocalStockRepository(context)
        try {
            r.receive(false, StockEntry(0, "Arenaa", 10.0, "kg", "", "", todayStockDate(), "", "", ""))
            compose.setContent { MaterialTheme { StockApp(r) } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Arenaa").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Arenaa").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Editar material").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Editar material").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("Nombre del material *") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("Nombre del material *") and hasSetTextAction()).performTextReplacement("Arena")
            compose.onNode(hasText("Cantidad disponible en pañol (kg) *") and hasSetTextAction()).performScrollTo().performTextReplacement("7,5")
            compose.onNodeWithText("Guardar cambios del material").performClick()
            compose.waitUntil(10_000) { r.items(false).single().name == "Arena" }
            assertEquals(7.5, r.items(false).single().quantity, 0.0)
            compose.onNodeWithText("Arena").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Editar material").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Editar material").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("Nombre del material *") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("Nombre del material *") and hasSetTextAction()).performTextReplacement("Cancelar este cambio")
            compose.onNodeWithText("Cancelar").performClick()
            assertEquals("Arena", r.items(false).single().name)
        } finally { r.close(); context.deleteDatabase("stock_paniol.db") }
    }
}
