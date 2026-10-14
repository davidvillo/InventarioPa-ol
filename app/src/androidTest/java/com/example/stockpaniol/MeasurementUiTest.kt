package com.example.stockpaniol

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeasurementUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = isolatedStockContext("measurements_ui_")
    private lateinit var repository: LocalStockRepository
    @Before fun prepare() {
        context.deleteDatabase("stock_paniol.db")
        repository = LocalStockRepository(context)
        repository.measurements()
        compose.setContent { MaterialTheme { StockApp(repository) } }
        compose.waitForIdle()
    }
    @After fun clean() { repository.close(); context.deleteDatabase("stock_paniol.db") }
    private fun openUnits() {
        compose.onNodeWithText("☰").performClick()
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithText("Obras").assertIsDisplayed()
        compose.onNodeWithText("Unidades").performClick()
    }
    private fun input(label: String, value: String, scroll: Boolean = true) {
        val node = compose.onNode(hasText(label) and hasSetTextAction())
        if (scroll) node.performScrollTo()
        node.performTextReplacement(value)
    }

    @Test fun unifiedSettingsUnitCrudUpdatesMaterialChoicesAndWholeQuantityValidation() {
        openUnits()
        compose.onNodeWithText("Agregar unidad").performClick()
        input("Nombre de la unidad *","bidones",false)
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Guardar unidad").performClick()
        compose.waitUntil(10_000) { repository.measurements().any { it.name=="bidones" } }
        assertTrue(repository.measurements().first { it.name=="bidones" }.wholeNumbers)
        val unitId = repository.measurements().first { it.name=="bidones" }.id
        compose.waitForIdle()
        compose.onNodeWithTag("measurement-units-list").performScrollToNode(hasTestTag("modify-measure-$unitId"))
        compose.onNodeWithTag("modify-measure-$unitId").performClick()
        input("Nombre de la unidad *","bultos",false)
        compose.onNodeWithText("Guardar unidad").performClick()
        compose.waitUntil(10_000) { repository.measurements().any { it.name=="bultos" } }
        assertTrue(repository.measurements().first { it.name=="bultos" }.wholeNumbers)
        assertEquals(unitId, repository.measurements().first { it.name=="bultos" }.id)
        compose.waitForIdle()
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("+ Agregar material").performClick()
        input("Nombre *","Material embalado")
        input("Cantidad a ingresar *","1,5")
        compose.onNodeWithText("litros").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("pares").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("bultos").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("bultos").assertIsSelected()
        compose.onNodeWithText("Guardar ingreso").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("La cantidad de bultos debe ser un número entero.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("La cantidad de bultos debe ser un número entero.").performScrollTo().assertIsDisplayed()
        input("Cantidad a ingresar *","2")
        compose.onNodeWithText("Guardar ingreso").performClick()
        compose.waitUntil(10_000) { repository.items(false).size==1 }
        assertEquals("bultos",repository.items(false).single().unit)
        compose.waitForIdle()
        openUnits()
        compose.onNodeWithTag("measurement-units-list").performScrollToNode(hasTestTag("remove-measure-$unitId"))
        compose.onNodeWithTag("remove-measure-$unitId").performClick()
        compose.onNodeWithText("Quitar unidad").assertIsDisplayed()
        compose.onAllNodesWithText("Quitar").onLast().performClick()
        compose.waitUntil(10_000) { repository.measurements().none { it.name=="bultos" } }
        assertEquals("bultos",repository.items(false).single().unit)
    }
}
