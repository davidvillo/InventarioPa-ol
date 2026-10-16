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
class StockCatalogueUiTest {
    @get:Rule val compose = createComposeRule()
    private fun entryInput(label: String, value: String) {
        val matcher = hasSetTextAction() and hasText(label)
        compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(isDialog())).performScrollToNode(matcher)
        compose.onNode(matcher).performScrollTo().performTextReplacement(value)
    }

    @Test fun toolsShareABrandAndIdentifiableMaterialsCanSwitchToIndividualBrands() {
        val context = isolatedStockContext("catalogue_entry_ui_")
        val r = LocalStockRepository(context)
        try {
            r.addStockOption(StockCatalogueKind.BRAND, "Bosch")
            r.addStockOption(StockCatalogueKind.BRAND, "Makita")
            r.addStockOption(StockCatalogueKind.COMPANY, "Proveedor Norte")
            compose.setContent { MaterialTheme { StockApp(r) } }
            for (tools in listOf(true, false)) {
                compose.onNodeWithText(if (tools) "Herramientas" else "Materiales").performClick()
                compose.onNodeWithText(if (tools) "+ Agregar herramienta" else "+ Agregar material").performClick()
                entryInput("Nombre *", if (tools) "Taladro" else "Balde")
                if (!tools) compose.onNode(isToggleable() and hasAnySibling(hasText("Identificar cada unidad (reutilizable)"))).performScrollTo().performClick()
                entryInput("Cantidad a ingresar *", "2")
                compose.onNodeWithText("Todos tienen la misma marca").performScrollTo().performClick()
                entryInput("Marca de todas las unidades (opcional)", "bo")
                compose.onNode(hasText("Bosch") and hasClickAction()).performClick()
                if (!tools) {
                    compose.onNodeWithText("Todos tienen la misma marca").performScrollTo().performClick()
                    entryInput("Marca de unidad 2 (opcional)", "mak")
                    compose.onNode(hasText("Makita") and hasClickAction()).performClick()
                }
                entryInput("Empresa a la que se compró", "prove")
                compose.onNode(hasText("Proveedor Norte") and hasClickAction()).performClick()
                compose.onNodeWithText("Guardar ingreso").performClick()
                compose.waitUntil(10_000) { r.items(tools).isNotEmpty() }
                compose.waitForIdle()
                assertEquals(if (tools) listOf("Bosch", "Bosch") else listOf("Bosch", "Makita"), r.items(tools).single().units.map { it.brand })
                assertEquals("Proveedor Norte", r.items(tools).single().company)
                assertEquals(2.0, r.items(tools).single().quantity, 0.0)
            }
        } finally { r.close(); context.deleteDatabase("stock_paniol.db") }
    }

    @Test fun settingsAddModifySearchAndRemoveBothBrandsAndCompanies() {
        val context = isolatedStockContext("catalogue_settings_ui_")
        val r = LocalStockRepository(context)
        try {
            compose.setContent { MaterialTheme { SettingsDialog(r, {}) } }
            for (kind in StockCatalogueKind.entries) {
                compose.onNodeWithText(kind.title).performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Todavía no hay opciones registradas.").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Agregar ${kind.singular}").performClick()
                compose.onNode(hasSetTextAction() and hasText("Nombre de la ${kind.singular} *")).performTextReplacement("Primera opción")
                compose.onNodeWithText("Guardar ${kind.singular}").performClick()
                compose.waitUntil(10_000) { r.stockOptions(kind).size == 1 }
                val id = r.stockOptions(kind).single().id
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("modify-catalogue-$id").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("modify-catalogue-$id").performClick()
                compose.onNode(hasSetTextAction() and hasText("Nombre de la ${kind.singular} *")).performTextReplacement("Opción corregida")
                compose.onNodeWithText("Guardar ${kind.singular}").performClick()
                compose.waitUntil(10_000) { r.stockOptions(kind).single().name == "Opción corregida" }
                compose.onNode(hasSetTextAction() and hasText("Buscar en ${kind.title.lowercase()}")).performTextReplacement("corregida")
                compose.onNodeWithTag("remove-catalogue-$id").performClick()
                compose.onNodeWithText("Eliminar de la lista").performClick()
                compose.waitUntil(10_000) { r.stockOptions(kind).isEmpty() }
                compose.onNodeWithText("Cerrar").performClick()
                compose.onNodeWithText("Ajustes").assertExists()
            }
        } finally { r.close(); context.deleteDatabase("stock_paniol.db") }
    }
}
