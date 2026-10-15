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
class EntryQuantityUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = isolatedStockContext("entry_quantity_ui_")
    private lateinit var repository: LocalStockRepository

    @Before fun prepare() {
        context.deleteDatabase("stock_paniol.db")
        repository = LocalStockRepository(context)
        compose.setContent { MaterialTheme { StockApp(repository) } }
        compose.waitForIdle()
    }

    @After fun clean() { repository.close(); context.deleteDatabase("stock_paniol.db") }

    private fun input(label: String, value: String) {
        val matcher = hasSetTextAction() and hasText(label)
        compose.onAllNodes(hasScrollToIndexAction()).onLast().performScrollToNode(matcher)
        compose.onNode(matcher).performTextReplacement(value)
    }

    private fun open(tools: Boolean, individual: Boolean) {
        if (tools) compose.onNodeWithText("Herramientas").performClick()
        compose.onNodeWithText(if (tools) "+ Agregar herramienta" else "+ Agregar material").performClick()
        input("Nombre *", if (tools) "Taladro" else "Guantes")
        if (!tools && individual) compose.onNode(isToggleable()).performClick()
    }

    private fun save(tools: Boolean) {
        compose.onNodeWithText("Guardar ingreso").performClick()
        compose.waitUntil(10_000) { repository.items(tools).isNotEmpty() }
        compose.waitForIdle()
    }

    @Test fun toolsAcceptSeveralUnitsWithIndependentOptionalDetails() {
        open(tools = true, individual = true)
        input("Cantidad a ingresar *", "3")
        input("Marca de unidad 1 (opcional)", "Bosch")
        input("Identificador de unidad 1 (opcional)", "T-01")
        input("Marca de unidad 2 (opcional)", "Otra")
        input("Identificador de unidad 2 (opcional)", "T-02")
        // Reducing and increasing the quantity must retain the entered details.
        input("Cantidad a ingresar *", "1")
        input("Cantidad a ingresar *", "3")
        save(true)
        val item = repository.items(true).single()
        assertEquals(3.0, item.quantity, 0.0)
        assertEquals("Bosch", item.units.first { it.identifier == "T-01" }.brand)
        assertEquals("Otra", item.units.first { it.identifier == "T-02" }.brand)
        assertEquals(1, item.units.count { it.brand.isBlank() && it.identifier.isNotBlank() })
    }

    @Test fun identifiedMaterialAcceptsZeroWithoutCreatingPhysicalUnits() {
        open(tools = false, individual = true)
        input("Cantidad a ingresar *", "0")
        save(false)
        val item = repository.items(false).single()
        assertTrue(item.individuallyTracked)
        assertEquals(0.0, item.quantity, 0.0)
        assertTrue(item.units.isEmpty())
        assertTrue(repository.movements(false).isEmpty())
    }

    @Test fun bulkMaterialAcceptsZero() {
        open(tools = false, individual = false)
        input("Cantidad a ingresar *", "0")
        save(false)
        val item = repository.items(false).single()
        assertFalse(item.individuallyTracked)
        assertEquals(0.0, item.quantity, 0.0)
        assertTrue(repository.purchases(false).isEmpty())
    }
}
