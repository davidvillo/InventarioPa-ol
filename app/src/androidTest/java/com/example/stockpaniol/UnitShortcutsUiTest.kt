package com.example.stockpaniol

import androidx.compose.material3.MaterialTheme
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
class UnitShortcutsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = isolatedStockContext("unit_shortcuts_")
    private lateinit var repository: LocalStockRepository
    @Before fun prepare() { repository = LocalStockRepository(context); repository.addWorker("Ana", "Ruiz", "Obra", "") }
    @After fun clean() { repository.close(); context.deleteDatabase("stock_paniol.db") }
    private fun receive(tools: Boolean, name: String, quantity: Double = 1.0, individual: Boolean = true): StockItem {
        repository.receive(tools, StockEntry(0, name, quantity, if (individual) "unidades" else "kg", "", "", todayStockDate(), "", "", "",
            control = if (individual) StockControl.INDIVIDUAL else StockControl.QUANTITY))
        return repository.items(tools).first { it.name == name }
    }
    private fun start(tools: Boolean = false) {
        compose.setContent { MaterialTheme { StockApp(repository) } }
        if (tools) compose.onNodeWithText("Herramientas").performClick()
    }
    private fun waitTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun input(label: String, value: String) = compose.onNode(hasText(label) and hasSetTextAction()).performScrollTo().performTextReplacement(value)

    @Test fun loanedToolOpensExistingReturnWithItsUnitSelected() {
        val item = receive(true, "Taladro", 2.0)
        val id = item.units.first().id
        repository.issue(true, item.id, 1.0, todayStockDate(), repository.personnel().single().id, "", listOf(id))
        start(true)
        waitTag("tool-detail-${item.id}")
        compose.onNodeWithTag("tool-detail-${item.id}").performClick()
        compose.onNodeWithTag("tool-unit-$id").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Cantidad seleccionada: 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Confirmar devolución").performClick()
        compose.waitUntil(10_000) { repository.items(true).single().quantity == 2.0 }
        assertTrue(repository.items(true).single().units.all { it.loanId == null })
    }

    @Test fun loanedMaterialReturnsDamagedWithoutBecomingAvailable() {
        val item = receive(false, "Arnés")
        val id = item.units.single().id
        repository.issue(false, item.id, 1.0, todayStockDate(), repository.personnel().single().id, "", listOf(id))
        start()
        waitTag("material-detail-${item.id}")
        compose.onNodeWithTag("material-detail-${item.id}").performClick()
        compose.onNodeWithTag("material-unit-$id").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Dañada: ${item.units.single().label}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Dañada: ${item.units.single().label}").performScrollTo().performClick()
        input("Qué le pasó a ${item.units.single().identifier} *", "Hebilla rota")
        compose.onNodeWithText("Confirmar devolución").performClick()
        compose.waitUntil(10_000) { repository.damagedMaterials().isNotEmpty() }
        assertEquals(0.0, repository.items(false).single().quantity, 0.0)
        assertNull(repository.items(false).single().units.single().loanId)
        assertEquals(ToolCondition.DAMAGED, repository.items(false).single().units.single().condition)
    }

    @Test fun repairingToolOpensOnlyItsOwnStateOptions() {
        val item = receive(true, "Taladro")
        val other = receive(true, "Amoladora")
        for (entry in listOf(item, other)) {
            repository.changeToolState(entry.units.single().id, ToolAction.DAMAGE, todayStockDate(), "No enciende")
            repository.changeToolState(entry.units.single().id, ToolAction.SEND_REPAIR, todayStockDate(), "", "Taller Central")
        }
        start(true)
        waitTag("tool-detail-${item.id}")
        compose.onNodeWithTag("tool-detail-${item.id}").performClick()
        compose.onNodeWithTag("tool-unit-${item.units.single().id}").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Recibir reparada").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Recibir reparada").assertCountEquals(1)
        compose.onNodeWithText("Recibir reparada").performScrollTo().performClick()
        compose.onNodeWithText("Guardar cambio").performClick()
        compose.waitUntil(10_000) { repository.items(true).first { it.id == item.id }.quantity == 1.0 }
        assertEquals(0.0, repository.items(true).first { it.id == other.id }.quantity, 0.0)
    }

    @Test fun materialWindowRegistersQuantityDamageForThatArticle() {
        val item = receive(false, "Arena", 10.0, false)
        receive(false, "Cal", 10.0, false)
        start()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Arena").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Arena").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Registrar daño en pañol").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Registrar daño en pañol").performClick()
        input("Cantidad dañada (kg)", "2,5")
        input("Qué le pasó *", "Contaminada")
        compose.onNodeWithText("Guardar cambio").performClick()
        compose.waitUntil(10_000) { repository.damagedMaterials().isNotEmpty() }
        assertEquals(item.id, repository.damagedMaterials().single().itemId)
        assertEquals(7.5, repository.items(false).first { it.id == item.id }.quantity, 0.0)
        assertEquals(10.0, repository.items(false).first { it.name == "Cal" }.quantity, 0.0)
    }

    @Test fun materialWindowRegistersOnlyTheSelectedReusableUnitAsDamaged() {
        val item = receive(false, "Arnés", 2.0)
        start()
        waitTag("material-card-${item.id}")
        compose.onNodeWithText("Arnés").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Registrar daño en pañol").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Registrar daño en pañol").performClick()
        compose.onNode(hasText(item.units.first().label) and hasAnyAncestor(isDialog())).assertExists()
        compose.onNodeWithTag("material-damage-unit-${item.units.first().id}").performScrollTo().performClick()
        input("Qué le pasó *", "Hebilla rota")
        compose.onNodeWithText("Guardar cambio").performClick()
        compose.waitUntil(10_000) { repository.damagedMaterials().isNotEmpty() }
        assertEquals(item.units.first().id, repository.damagedMaterials().single().unitId)
        assertEquals(1.0, repository.items(false).single().quantity, 0.0)
    }
}
