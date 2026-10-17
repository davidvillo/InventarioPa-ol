package com.example.stockpaniol

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaterialDamageUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = isolatedStockContext("material_damage_ui_")
    private lateinit var repository: LocalStockRepository
    @Before fun prepare() {
        repository = LocalStockRepository(context)
        repository.receive(false, StockEntry(0, "Arena", 10.0, "kg", "", "", todayStockDate(), "", "", ""))
    }
    @After fun clean() { repository.close(); context.deleteDatabase("stock_paniol.db") }
    private fun input(label: String, value: String) {
        compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextReplacement(value)
    }
    private fun waitFor(state: String) {
        compose.waitUntil(10_000) { repository.damagedMaterials().singleOrNull()?.state == state }
        compose.waitForIdle()
    }
    @Test fun menuRegistersDamageAndMovesItToTrashAndBackToUse() {
        compose.setContent { MaterialTheme { StockApp(repository) } }
        compose.onNodeWithText("☰").performClick()
        compose.onNodeWithText("Materiales dañados").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Registrar daño") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Registrar daño").performClick()
        input("Cantidad dañada (kg)", "2,5")
        input("Qué le pasó *", "Contaminada")
        compose.onNodeWithText("Guardar cambio").performClick()
        waitFor("DAMAGED")
        assertEquals(7.5, repository.items(false).single().quantity, 0.0)
        compose.onNodeWithText("Enviar a papelera").performScrollTo().performClick()
        input("Motivo *", "No se puede reparar")
        compose.onNodeWithText("Guardar cambio").performClick()
        waitFor("TRASH")
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("☰").performClick()
        compose.onNodeWithText("Papelera de materiales").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Restaurar a materiales dañados").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Restaurar a materiales dañados").performScrollTo().performClick()
        input("Motivo *", "Reevaluar uso")
        compose.onNodeWithText("Guardar cambio").performClick()
        waitFor("DAMAGED")
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("☰").performClick()
        compose.onNodeWithText("Materiales dañados").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Utilizar dañado").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Utilizar dañado").performScrollTo().performClick()
        input("Motivo *", "Apto para relleno")
        compose.onNodeWithText("Guardar cambio").performClick()
        waitFor("USABLE")
        assertEquals(10.0, repository.items(false).single().quantity, 0.0)
    }
}
