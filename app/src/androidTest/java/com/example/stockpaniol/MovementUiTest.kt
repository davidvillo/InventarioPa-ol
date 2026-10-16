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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class MovementUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = isolatedStockContext("movement_ui_")
    private lateinit var repository: LocalStockRepository
    @Before fun prepare() {
        context.deleteDatabase("stock_paniol.db")
        repository = LocalStockRepository(context)
        repository.addWorker("Ana", "Ruiz", "Obra norte", "")
        repository.receive(false, StockEntry(0, "Arena", 10.0, "kg", "", "", todayStockDate(), "", "", ""))
        repository.receive(true, StockEntry(0, "Martillo", 4.0, "unidades", "", "", todayStockDate(), "", "", ""))
        compose.setContent { MaterialTheme { StockApp(repository) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Arena").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun clean() { repository.close(); context.deleteDatabase("stock_paniol.db") }
    private fun input(label: String, value: String) {
        compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextReplacement(value)
    }
    private fun selectWorker() {
        compose.onNodeWithText("Personal que recibe").performScrollTo().performClick()
        compose.onNodeWithText("Ana Ruiz · Obra norte").performClick()
    }
    private fun openHistory(tools: Boolean) {
        compose.onNodeWithText("☰").performClick()
        compose.onNodeWithText("Movimientos de ${if (tools) "herramientas" else "materiales"}").performClick()
    }
    private fun scrollHistoryTo(matcher: SemanticsMatcher) {
        compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(isDialog())).performScrollToNode(matcher)
    }

    @Test fun personnelFormAndMaterialCategoryAreAvailableForNewIssues() {
        compose.onNodeWithText("☰").performClick()
        compose.onNodeWithText("Personal de obra").performClick()
        compose.onNodeWithText("Agregar personal").performClick()
        input("Nombre *", "Luis")
        input("Apellido *", "Pérez")
        compose.onNodeWithText("Obra *").performScrollTo().performClick()
        compose.onNodeWithText("519").performClick()
        input("Apodo", "Lucho")
        compose.onNodeWithText("Guardar personal").performClick()
        compose.waitUntil(10_000) { repository.personnel().size == 2 }
        compose.waitForIdle()
        compose.onNodeWithText("Luis Pérez (Lucho)").assertIsDisplayed()
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("+ Agregar material").performClick()
        input("Nombre *", "Guantes")
        compose.onNodeWithText("Tipo de material").performScrollTo().performClick()
        compose.onNodeWithText("EPP").performClick()
        input("Cantidad a ingresar *", "2")
        compose.onNodeWithText("unidades").performScrollTo().performClick()
        compose.onNodeWithText("Guardar ingreso").performClick()
        compose.waitUntil(10_000) { repository.items(false).any { it.name == "Guantes" } }
        compose.waitForIdle()
        compose.onNodeWithText("Guantes").performClick()
        input("Cantidad (unidades)", "1")
        compose.onNodeWithText("Personal que recibe").performScrollTo().performClick()
        compose.onNodeWithText("Luis Pérez (Lucho) · 519").performClick()
        compose.onNodeWithText("Confirmar egreso").performClick()
        compose.waitUntil(10_000) { repository.movements(false).any { it.kind == "Egreso" && it.itemName == "Guantes" } }
        val movement = repository.movements(false).first { it.kind == "Egreso" }
        assertEquals(MaterialTypes.PPE, movement.materialType)
        assertEquals("Luis Pérez (Lucho)", movement.person)
    }

    @Test fun issueRejectsExcessAndMaterialReturnCreatesNewMovement() {
        compose.onNodeWithText("Arena").performClick()
        compose.onNodeWithText("Egreso").assertIsDisplayed()
        input("Cantidad (kg)", "12")
        selectWorker()
        compose.onNodeWithText("Confirmar egreso").performClick()
        compose.onNodeWithText("La cantidad supera el stock disponible (10 kg).").performScrollTo().assertIsDisplayed()
        input("Cantidad (kg)", "2,5")
        compose.onNodeWithText("Confirmar egreso").performClick()
        compose.waitUntil(10_000) { repository.items(false).single().quantity == 7.5 }
        compose.waitForIdle()
        openHistory(false)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Egreso · ${displayStockDate(todayStockDate())}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Egreso · ${displayStockDate(todayStockDate())}").performClick()
        input("Cantidad a devolver (kg)", "2,5")
        compose.onNodeWithText("Confirmar devolución").performClick()
        compose.waitUntil(10_000) { repository.movements(false).any { it.kind == "Devolución" } }
        compose.waitForIdle()
        compose.onNodeWithText("Devolución · ${displayStockDate(todayStockDate())}").assertIsDisplayed()
        assertEquals(10.0, repository.items(false).single().quantity, 0.0)
    }

    @Test fun toolHistoryIncludesReturnsAndCompletedLoansInBothViews() {
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val olderDate = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(yesterday.time)
        val tool = repository.items(true).single()
        repository.receive(true, StockEntry(tool.id, tool.name, 1.0, tool.unit, "", "", todayStockDate(), "", "", ""))
        repository.issue(true, tool.id, 1.0, olderDate, repository.personnel().single().id, "Préstamo anterior", listOf(tool.availableUnits.first().id))
        val units = repository.items(true).single().availableUnits.take(3)
        compose.onNodeWithText("Herramientas").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Martillo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Martillo").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction() and hasText("Nombre *")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(isDialog())).performScrollToNode(hasText("Registrar egreso"))
        compose.onNodeWithText("Registrar egreso").performClick()
        units.forEach { compose.onNodeWithText(it.label).performScrollTo().performClick() }
        selectWorker()
        compose.onNodeWithText("Confirmar egreso").performClick()
        compose.waitUntil(10_000) { repository.items(true).single().quantity == 1.0 }
        compose.waitForIdle()
        openHistory(true)
        compose.onNodeWithText("Todos los movimientos · Ayer y hoy").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("4 movimientos").fetchSemanticsNodes().isNotEmpty() }
        scrollHistoryTo(hasText("Préstamo anterior"))
        compose.onNodeWithText("Préstamo anterior").assertIsDisplayed()
        scrollHistoryTo(hasText("Egreso · ${displayStockDate(todayStockDate())}"))
        compose.onNodeWithText("Egreso · ${displayStockDate(todayStockDate())}").performClick()
        compose.onNodeWithText(units.first().label).performScrollTo().performClick()
        compose.onNodeWithText("Confirmar devolución").performClick()
        compose.waitUntil(10_000) { repository.items(true).single().quantity == 2.0 }
        compose.waitForIdle()
        scrollHistoryTo(hasText("Pendiente: 2 unidades"))
        compose.onNodeWithText("Pendiente: 2 unidades").assertIsDisplayed()
        compose.onNodeWithText("Egreso · ${displayStockDate(todayStockDate())}").performClick()
        units.drop(1).forEach { compose.onNodeWithText(it.label).performScrollTo().performClick() }
        compose.onNodeWithText("Confirmar devolución").performClick()
        compose.waitUntil(10_000) { repository.items(true).single().quantity == 4.0 }
        compose.waitForIdle()
        scrollHistoryTo(hasText("Egreso · ${displayStockDate(todayStockDate())}"))
        compose.onNodeWithText("Egreso · ${displayStockDate(todayStockDate())}").assertExists()
        scrollHistoryTo(hasText("Préstamo anterior"))
        compose.onNodeWithText("Préstamo anterior").assertIsDisplayed()
        compose.onNodeWithText("Filtros y orden").performClick()
        compose.onNodeWithText("Limpiar filtros").performScrollTo().performClick()
        compose.onNodeWithText("Ocultar filtros").performClick()
        scrollHistoryTo(hasText("Préstamo anterior"))
        compose.onNodeWithText("Préstamo anterior").assertIsDisplayed()
        compose.onNodeWithText("Vista sencilla").performClick()
        compose.onNodeWithText("7 movimientos").assertIsDisplayed()
        assertEquals(7, repository.movements(true).size)
    }
}
