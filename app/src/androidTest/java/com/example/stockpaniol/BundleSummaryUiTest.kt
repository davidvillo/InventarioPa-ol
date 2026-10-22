package com.example.stockpaniol

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundleSummaryUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Before fun keepAwake() {
        compose.activityRule.scenario.onActivity {
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (android.os.Build.VERSION.SDK_INT >= 27) it.setTurnScreenOn(true)
        }
    }
    private fun item(id: Long, name: String, unitId: Long, code: String) = StockItem(id, name, 1.0,
        "unidades", "Observación de prueba", "", "Marca", "", control = StockControl.INDIVIDUAL,
        units = listOf(InventoryUnit(unitId, id, code, "Marca")))

    @Test fun collapsedCardsShowFullRelationsDirectlyBelowObservations() {
        val principal = item(1, "Taladro", 10, "TAL 001")
        val accessory = item(2, "Cargador de batería profesional con adaptador y cable de alimentación para taladro", 20, "CAR 001")
        val links = toolBundleSummary(listOf(principal, accessory), listOf(ToolBundle(1, "Equipo", listOf(10, 20), 10)))
        compose.setContent { MaterialTheme {
            Column(Modifier.width(340.dp).verticalScroll(rememberScrollState())) {
                StockSummaryCard(principal, {}, {}, bundleLines = links[1].orEmpty())
                StockSummaryCard(accessory, {}, {}, bundleLines = links[2].orEmpty())
            }
        } }
        val link = compose.onNodeWithTag("tool-bundle-1-0", useUnmergedTree = true).performScrollTo()
        link.assertTextEquals("con ${accessory.name} · CAR 001")
        val notes = compose.onNodeWithTag("tool-notes-1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(link.fetchSemanticsNode().boundsInRoot.top >= notes.bottom)
        val layouts = mutableListOf<TextLayoutResult>()
        link.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("El nombre y el código no deben quedar recortados", layouts.single().hasVisualOverflow)
        compose.onNodeWithTag("tool-units-1").assertDoesNotExist()
        compose.onNodeWithTag("tool-bundle-2-0", useUnmergedTree = true).performScrollTo().assertTextEquals("en Taladro · TAL 001")
    }

    @Test fun filteringAccessoryKeepsPrincipalNameAndCodeVisible() {
        val ctx = isolatedStockContext("bundle_summary_filter_")
        val r = LocalStockRepository(ctx)
        try {
            listOf("Taladro", "Cincel").forEach { r.receive(true, StockEntry(0, it, 1.0, "unidades", "", "", "2026-10-21", "", "", "")) }
            val items = r.items(true).associateBy { it.name }
            val principal = items.getValue("Taladro")
            val accessory = items.getValue("Cincel")
            val principalUnit = principal.units.single()
            r.saveToolBundle(null, "Equipo", listOf(principalUnit.id, accessory.units.single().id), principalUnit.id)
            compose.setContent { MaterialTheme { StockApp(r) } }
            compose.onNodeWithText("Herramientas").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("tool-bundle-${accessory.id}-0", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasSetTextAction()).performTextReplacement("Cincel")
            compose.onNodeWithTag("tool-bundle-${accessory.id}-0", useUnmergedTree = true).assertTextEquals("en Taladro · ${principalUnit.identifier}")
            compose.onNodeWithTag("tool-card-${principal.id}").assertDoesNotExist()
        } finally { r.close(); ctx.deleteDatabase("stock_paniol.db") }
    }

    @Test fun makePrincipalButtonAssignsTheOtherMembersAsAccessories() {
        val ctx = isolatedStockContext("bundle_summary_principal_")
        val r = LocalStockRepository(ctx)
        try {
            listOf("Taladro", "Cincel").forEach { r.receive(true, StockEntry(0, it, 1.0, "unidades", "", "", "2026-10-21", "", "", "")) }
            val items = r.items(true).associateBy { it.name }
            compose.setContent { MaterialTheme { ToolBundlesDialog(r, {}, {}) } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Todavía no hay herramientas unidas.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Unir herramientas").performClick()
            compose.onNode(hasSetTextAction() and hasText("Nombre del conjunto *")).performTextReplacement("Equipo")
            compose.onNodeWithText("Taladro").performClick()
            compose.onNodeWithText("Cincel").performClick()
            compose.onNodeWithText("Accesorio del conjunto").assertIsDisplayed()
            compose.onNodeWithText("Hacer principal").performClick()
            compose.onNodeWithText("Guardar conjunto").performClick()
            compose.waitUntil(10_000) { r.toolBundles().singleOrNull()?.principalUnitId == items.getValue("Cincel").units.single().id }
            compose.onNode(hasText("Principal: Cincel", substring = true)).assertExists()
            compose.onNode(hasText("Accesorio: Taladro", substring = true)).assertExists()
            val links = toolBundleSummary(r.items(true), r.toolBundles())
            assertEquals(listOf("en Cincel · ${items.getValue("Cincel").units.single().identifier}"), links[items.getValue("Taladro").id])
        } finally { r.close(); ctx.deleteDatabase("stock_paniol.db") }
    }
}
