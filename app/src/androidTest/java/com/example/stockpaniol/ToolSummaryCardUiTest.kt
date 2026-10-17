package com.example.stockpaniol

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolSummaryCardUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cardsKeepTheSameCollapsedHeightAndOnlyDetailExpandsEveryUnit() {
        val single = StockItem(1, "Cincel", 1.0, "unidades", "", "", "", "", control = StockControl.INDIVIDUAL,
            units = listOf(InventoryUnit(1, 1, "CIN 001", "")))
        val multiple = StockItem(2, "Martillo", 1.0, "unidades", "", "", "", "", control = StockControl.INDIVIDUAL,
            units = listOf(
                InventoryUnit(2, 2, "MAR 001", "Marca larga para verificar el espacio del detalle"),
                InventoryUnit(3, 2, "MAR 002", "", condition = ToolCondition.DAMAGED),
                InventoryUnit(4, 2, "MAR 003", "", condition = ToolCondition.IN_REPAIR),
                InventoryUnit(5, 2, "MAR 004", "", condition = ToolCondition.RETIRED)))
        var photos = 0
        var edits = 0
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.2f)) { MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                StockSummaryCard(single, { photos++ }, { edits++ })
                StockSummaryCard(multiple, { photos++ }, { edits++ })
            }
        } } }
        val height = compose.onNodeWithTag("tool-card-1").fetchSemanticsNode().boundsInRoot.height
        assertEquals(height, compose.onNodeWithTag("tool-card-2").fetchSemanticsNode().boundsInRoot.height, 0.1f)
        compose.onNodeWithTag("tool-units-2").assertDoesNotExist()
        compose.onNodeWithText("CIN 001 · Disponible en pañol", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("MAR 001 · Marca larga para verificar el espacio del detalle · Disponible en pañol", useUnmergedTree = true).assertDoesNotExist()
        val hint = compose.onNodeWithText("Ver 4 unidades ▾", useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        hint.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("El texto para desplegar las unidades debe verse completo.",
            hint.fetchSemanticsNode().boundsInRoot.height + 1f >= layouts.single().size.height)
        compose.onNode(hasText("Sin imagen") and hasAnyAncestor(hasTestTag("tool-card-2"))).performClick()
        assertEquals(1, photos)
        compose.onNodeWithTag("tool-units-2").assertDoesNotExist()
        compose.onNodeWithTag("tool-detail-2").performClick()
        compose.onNodeWithTag("tool-units-2", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertEquals(0, edits)
        listOf("MAR 001 · Marca larga para verificar el espacio del detalle · Disponible en pañol", "MAR 002 · Dañada en pañol", "MAR 003 · En reparación", "MAR 004 · Baja · Papelera").forEach { label ->
            compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("tool-units-2")), useUnmergedTree = true).assertExists()
        }
        assertTrue(compose.onNodeWithTag("tool-card-2").fetchSemanticsNode().boundsInRoot.height > height)
        compose.onNodeWithTag("tool-detail-2").performClick()
        assertEquals(height, compose.onNodeWithTag("tool-card-2").fetchSemanticsNode().boundsInRoot.height, 0.1f)
        compose.onNodeWithText("Martillo").performClick()
        assertEquals(1, edits)
    }

    @Test fun reusableMaterialsMatchToolsAndRevealBulletsOnlyFromTheUnitsButton() {
        val item = StockItem(1, "Arnés reutilizable con nombre largo", 2.0, "unidades", "", "", "", "",
            control = StockControl.INDIVIDUAL, units = listOf(
                InventoryUnit(1, 1, "ARN 001", "Marca A"),
                InventoryUnit(2, 1, "ARN 002", "Marca B")))
        var materialOpens = 0
        var photoOpens = 0
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            StockSummaryCard(item, {}, {})
            StockSummaryCard(item, { photoOpens++ }, { materialOpens++ }, tools = false)
        } } }
        val height = compose.onNodeWithTag("tool-card-1").fetchSemanticsNode().boundsInRoot.height
        assertEquals(height, compose.onNodeWithTag("material-card-1").fetchSemanticsNode().boundsInRoot.height, 0.1f)
        compose.onNodeWithTag("material-units-1").assertDoesNotExist()
        compose.onAllNodesWithText("ARN 001 · Marca A · Disponible en pañol", useUnmergedTree = true).assertCountEquals(0)
        compose.onNode(hasText("Sin imagen") and hasAnyAncestor(hasTestTag("material-card-1"))).performClick()
        assertEquals(1, photoOpens)
        compose.onNodeWithTag("material-units-1").assertDoesNotExist()
        compose.onNodeWithTag("material-detail-1").performClick()
        compose.onNodeWithTag("material-units-1", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("ARN 001 · Marca A · Disponible en pañol", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("ARN 002 · Marca B · Disponible en pañol", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("tool-units-1").assertDoesNotExist()
        assertEquals(0, materialOpens)
        compose.onNodeWithTag("material-detail-1").performClick()
        assertEquals(height, compose.onNodeWithTag("material-card-1").fetchSemanticsNode().boundsInRoot.height, 0.1f)
        compose.onNode(hasText(item.name) and hasAnyAncestor(hasTestTag("material-card-1"))).performClick()
        assertEquals(1, materialOpens)
    }
}
