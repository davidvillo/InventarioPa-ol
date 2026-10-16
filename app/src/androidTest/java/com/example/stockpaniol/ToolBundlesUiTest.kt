package com.example.stockpaniol

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolBundlesUiTest {
    @get:Rule val compose = createComposeRule()
    private fun input(label: String, text: String) = compose.onNode(hasSetTextAction() and hasText(label)).performTextReplacement(text)

    @Test fun createIssueReturnAndDissolveASetThroughTheApp() {
        val context = isolatedStockContext("tool_bundle_ui_")
        val r = LocalStockRepository(context)
        try {
            listOf("Martillo", "Cincel").forEach { r.receive(true, StockEntry(0, it, 1.0, "unidades", "", "", todayStockDate(), "", "", "")) }
            r.addWorker("Ana", "Ruiz", "Obra", "")
            compose.setContent { MaterialTheme { StockApp(r) } }
            compose.onNodeWithText("Herramientas").performClick()
            compose.onNodeWithText("☰").performClick()
            compose.onNodeWithText("Unir / desunir herramientas").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Todavía no hay herramientas unidas.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Unir herramientas").performClick()
            input("Nombre del conjunto *", "Demolición")
            compose.onNode(hasText("Martillo") and hasAnyAncestor(isDialog())).performClick()
            compose.onNode(hasText("Cincel") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithText("Guardar conjunto").performClick()
            compose.waitUntil(10_000) { r.toolBundles().size == 1 }
            compose.onNodeWithText("Cerrar").performClick()
            compose.onNodeWithText("Martillo").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction() and hasText("Nombre *")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(isDialog())).performScrollToNode(hasText("Registrar egreso"))
            compose.onNodeWithText("Registrar egreso").performClick()
            val unit = r.items(true).first { it.name == "Martillo" }.units.single()
            compose.waitUntil(10_000) { compose.onAllNodesWithText(unit.label).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(unit.label).performScrollTo().performClick()
            compose.onNodeWithText("También se prestan con el conjunto:").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Personal que recibe").performScrollTo().performClick()
            input("Buscar nombre, apellido o apodo", "Ana")
            compose.onNodeWithText("Ana Ruiz · Obra").performClick()
            compose.onNodeWithText("Confirmar egreso").performClick()
            compose.waitUntil(10_000) { r.items(true).all { it.quantity == 0.0 } }
            compose.onNodeWithText("☰").performClick()
            compose.onNodeWithText("Movimientos de herramientas").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Egreso · ${displayStockDate(todayStockDate())}").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText("Egreso · ${displayStockDate(todayStockDate())}").onFirst().performClick()
            val loan = r.movements(true).first { it.kind == "Egreso" }
            val label = loan.units.single().unit.label
            compose.waitUntil(10_000) { compose.onAllNodes(hasText(label) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasText(label) and hasClickAction()).onLast().performScrollTo().performClick()
            compose.onNodeWithText("Cantidad seleccionada: 2").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Confirmar devolución").performClick()
            compose.waitUntil(10_000) { r.items(true).all { it.quantity == 1.0 } }
            compose.onNodeWithText("Cerrar").performClick()
            compose.onNodeWithText("☰").performClick()
            compose.onNodeWithText("Unir / desunir herramientas").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Desunir").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Desunir").performClick()
            compose.onNodeWithText("Desunir conjunto").performClick()
            compose.waitUntil(10_000) { r.toolBundles().isEmpty() }
            assertTrue(r.items(true).all { it.quantity == 1.0 })
        } finally { r.close(); context.deleteDatabase("stock_paniol.db") }
    }

    @Test fun personnelPickerFindsNamesAtTheEndOfALongAlphabeticalList() {
        val workers = (1..30).map { Worker(it.toLong(), "Ana $it", "Pérez", Worksite(1, "Obra"), "") } +
            Worker(100, "Zoe", "Ruiz", Worksite(1, "Obra"), "Zeta")
        var selected by mutableLongStateOf(0L)
        compose.setContent { MaterialTheme { PersonnelChoice("Personal que recibe", workers, selected) { selected = it } } }
        compose.onNodeWithText("Personal que recibe").performClick()
        input("Buscar nombre, apellido o apodo", "Zeta")
        compose.onNodeWithText("Zoe Ruiz (Zeta) · Obra").performClick()
        assertEquals(100L, selected)
        compose.onNodeWithText("Zoe Ruiz (Zeta) · Obra").assertIsDisplayed()
    }
}
