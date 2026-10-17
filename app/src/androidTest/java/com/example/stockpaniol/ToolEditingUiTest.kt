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
class ToolEditingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun completeViewOpensIssueAndOffersTheFourTabsInOrder() {
        val context = isolatedStockContext("tool_edit_full_ui_")
        val repository = LocalStockRepository(context)
        try {
            repository.receive(true, StockEntry(0, "Taladro", 1.0, "unidades", "", "", "2026-10-10", "", "", ""))
            compose.setContent { MaterialTheme { StockApp(repository) } }
            compose.onNodeWithText("Herramientas").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Taladro").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Vista completa").performClick()
            compose.onNodeWithText("Taladro", substring = true).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Confirmar egreso").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Confirmar egreso").assertExists()
            val tabs = listOf("Egreso", "Editar", "Unir/Desunir", "Detalle").map {
                compose.onNodeWithText(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            }
            tabs.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }
            compose.onNodeWithText("Eliminar herramienta").assertDoesNotExist()
            compose.onNodeWithText("Editar").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction() and hasText("Nombre *")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(isDialog())).performScrollToNode(hasText("Eliminar herramienta"))
            compose.onNodeWithText("Eliminar herramienta").assertIsDisplayed()
            compose.onNodeWithText("Unir/Desunir").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Conjuntos de herramientas").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Eliminar herramienta").assertDoesNotExist()
            compose.onNodeWithText("Detalle").performClick()
            compose.onNodeWithText("Unidades identificadas").assertExists()
            compose.onNodeWithText("Confirmar egreso").assertDoesNotExist()
            compose.onNodeWithText("Egreso").performClick()
            compose.onNodeWithText("Confirmar egreso").assertExists()
            assertEquals(1.0, repository.items(true).single().quantity, 0.0)
        } finally { repository.close(); context.deleteDatabase("stock_paniol.db") }
    }

    @Test fun tapEditSaveReopenCancelDeletionAndConfirmDeletion() {
        val context = isolatedStockContext("tool_edit_ui_")
        val repository = LocalStockRepository(context)
        try {
            repository.receive(true, StockEntry(0, "Taladro", 1.0, "unidades", "", "R1", "2026-10-10", "", "", ""))
            compose.setContent { MaterialTheme { StockApp(repository) } }
            compose.onNodeWithText("Herramientas").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Taladro").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Taladro").performClick()
            compose.onNodeWithText("Editar").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction() and hasText("Nombre *")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasSetTextAction() and hasText("Nombre *")).performTextReplacement("Taladro corregido")
            compose.onNodeWithText("Detalle").performClick()
            compose.onNodeWithText("Editar").performClick()
            compose.onNode(hasSetTextAction() and hasText("Nombre *")).assertTextContains("Taladro corregido")
            compose.onNodeWithText("Guardar cambios").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Taladro corregido").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1.0, repository.items(true).single().quantity, 0.0)
            compose.onNodeWithText("Taladro corregido").performClick()
            compose.onNodeWithText("Editar").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction() and hasText("Nombre *")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(isDialog())).performScrollToNode(hasText("Eliminar herramienta"))
            compose.onNodeWithText("Eliminar herramienta").performScrollTo().performClick()
            compose.onAllNodesWithText("Cancelar").onLast().performClick()
            compose.onNodeWithText("¿Eliminar herramienta?").assertDoesNotExist()
            assertEquals(1, repository.items(true).size)
            compose.onNodeWithText("Eliminar herramienta").performScrollTo().performClick()
            compose.onNodeWithText("Eliminar definitivamente").performClick()
            compose.waitUntil(10_000) { repository.items(true).isEmpty() }
            compose.onNodeWithText("Tu stock empieza acá").assertExists()
        } finally { repository.close(); context.deleteDatabase("stock_paniol.db") }
    }
}
