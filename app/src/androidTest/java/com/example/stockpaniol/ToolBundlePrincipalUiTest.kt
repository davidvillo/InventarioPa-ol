package com.example.stockpaniol

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolBundlePrincipalUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun chooseAndChangePrincipalFromBundleEditor() {
        val ctx = isolatedStockContext("principal_ui_")
        val r = LocalStockRepository(ctx)
        try {
            listOf("Martillo", "Cincel").forEach {
                r.receive(true, StockEntry(0, it, 1.0, "unidades", "", "", "2026-10-21", "", "", ""))
            }
            val tools = r.items(true).associateBy { it.name }
            val chisel = tools.getValue("Cincel").units.single()
            val hammer = tools.getValue("Martillo").units.single()
            compose.setContent { MaterialTheme { ToolBundlesDialog(r, {}, {}) } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Todavía no hay herramientas unidas.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Unir herramientas").performClick()
            compose.onNode(hasSetTextAction() and hasText("Nombre del conjunto *")).performTextReplacement("Equipo de prueba")
            compose.onNodeWithText("Martillo").performClick()
            compose.onNodeWithText("Cincel").performClick()
            compose.onNodeWithContentDescription("Principal: Cincel · ${chisel.identifier}").performClick()
            compose.onNodeWithText("Guardar conjunto").performClick()
            compose.waitUntil(10_000) { r.toolBundles().singleOrNull()?.principalUnitId == chisel.id }
            compose.onNodeWithText("Modificar herramientas unidas").performClick()
            compose.onNodeWithContentDescription("Principal: Martillo · ${hammer.identifier}").performClick()
            compose.onNodeWithText("Guardar conjunto").performClick()
            compose.waitUntil(10_000) { r.toolBundles().single().principalUnitId == hammer.id }
            assertEquals(hammer.id, r.toolBundles().single().principalUnitId)
        } finally { r.close(); ctx.deleteDatabase("stock_paniol.db") }
    }
}
