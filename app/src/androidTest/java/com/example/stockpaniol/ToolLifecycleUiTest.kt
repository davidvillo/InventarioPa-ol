package com.example.stockpaniol

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolLifecycleUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = isolatedStockContext("tool_lifecycle_ui_")
    private lateinit var repository: LocalStockRepository
    private lateinit var unit: InventoryUnit
    private lateinit var loan: StockMovement
    @Before fun prepare() {
        context.deleteDatabase("stock_paniol.db")
        repository = LocalStockRepository(context)
        repository.receive(true, StockEntry(0, "Amoladora", 1.0, "unidades", "", "", todayStockDate(), "", "Bosch", ""))
        repository.addWorker("Ana", "Ruiz", "Obra", "")
        val item = repository.items(true).single()
        unit = item.units.single()
        repository.issue(true, item.id, 1.0, todayStockDate(), repository.personnel().single().id, "", listOf(unit.id))
        loan = repository.movements(true).first { it.kind == "Egreso" }
    }
    @After fun clean() { repository.close(); context.deleteDatabase("stock_paniol.db") }
    private fun input(label: String, value: String) {
        compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextReplacement(value)
    }
    private fun waitFor(condition: ToolCondition) {
        compose.waitUntil(10_000) { repository.items(true).single().units.single().condition == condition }
        compose.waitForIdle()
    }

    @Test fun damagedReturnRequiresExplanationAndDoesNotIncreaseAvailableStock() {
        var saved by mutableStateOf(false)
        compose.setContent { MaterialTheme {
            if (!saved) ReturnDialog(true, loan, repository, {}, { saved = true }) else androidx.compose.material3.Text("Devolución guardada")
        } }
        compose.onNode(hasText(unit.label) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Dañada: ${unit.label}").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar devolución").performClick()
        compose.waitForIdle()
        assertFalse(saved)
        assertEquals(1.0, repository.movements(true).first { it.id == loan.id }.pending, 0.0)
        input("Qué le pasó a ${unit.identifier} *", "No enciende")
        compose.onNodeWithText("Confirmar devolución").performClick()
        waitFor(ToolCondition.DAMAGED)
        compose.onNodeWithText("Devolución guardada").assertIsDisplayed()
        assertEquals(0.0, repository.items(true).single().quantity, 0.0)
        assertTrue(repository.movements(true).any { it.kind == "Devolución dañada" && it.notes.contains("No enciende") })
    }

    @Test fun repairAndTrashMenusCompletePhysicalLifecycleAndLogEachChange() {
        repository.returnStock(true, loan.id, 1.0, todayStockDate(), null, "", listOf(unit.id), mapOf(unit.id to "Motor quemado"))
        compose.setContent { MaterialTheme { StockApp(repository) } }
        compose.onNodeWithText("☰").performClick()
        compose.onNodeWithText("Estado y reparación de herramientas").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Enviar a reparar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Enviar a reparar").performScrollTo().performClick()
        input("Lugar de reparación *", "Taller Central")
        compose.onNodeWithText("Guardar cambio").performClick()
        waitFor(ToolCondition.IN_REPAIR)
        compose.onNodeWithText("Estado").performClick()
        compose.onNodeWithText("En reparación").performClick()
        compose.onNodeWithText("Lugar de reparación: Taller Central").assertIsDisplayed()
        compose.onNodeWithText("Recibir reparada").performScrollTo().performClick()
        compose.onNodeWithText("Guardar cambio").performClick()
        waitFor(ToolCondition.OPERATIVE)
        assertEquals(1.0, repository.items(true).single().quantity, 0.0)
        compose.onNodeWithText("Estado").performClick()
        compose.onNodeWithText("Disponible en pañol").performClick()
        compose.onNodeWithText("Registrar daño").performScrollTo().performClick()
        input("Qué le pasó *", "Carcasa rota")
        compose.onNodeWithText("Guardar cambio").performClick()
        waitFor(ToolCondition.DAMAGED)
        compose.onNodeWithText("Estado").performClick()
        compose.onNodeWithText("Dañada en pañol").performClick()
        compose.onNodeWithText("Dar de baja").performScrollTo().performClick()
        input("Motivo de baja *", "Sin reparación")
        compose.onNodeWithText("Guardar cambio").performClick()
        waitFor(ToolCondition.RETIRED)
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("☰").performClick()
        compose.onNodeWithText("Papelera de herramientas").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Restaurar de papelera").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(unit.label).assertIsDisplayed()
        compose.onNodeWithText("Restaurar de papelera").performScrollTo().performClick()
        input("Motivo de restauración *", "Revisar nuevamente")
        compose.onNodeWithText("Guardar cambio").performClick()
        waitFor(ToolCondition.DAMAGED)
        assertEquals(0.0, repository.items(true).single().quantity, 0.0)
        val kinds = repository.movements(true).map { it.kind }
        assertTrue(kinds.containsAll(listOf("Devolución dañada", "Envío a reparación", "Retorno reparada", "Daño en pañol", "Baja", "Restauración")))
    }
}
