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
class ExistingArticlesAndPairsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = isolatedStockContext("existing_pairs_ui_")
    private lateinit var repository: LocalStockRepository
    @Before fun prepare() { repository = LocalStockRepository(context) }
    @After fun clean() { repository.close(); context.deleteDatabase("stock_paniol.db") }
    private fun input(label: String, value: String) {
        val matcher = hasText(label) and hasSetTextAction()
        compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(isDialog())).performScrollToNode(matcher)
        compose.onNode(matcher).performScrollTo().performTextReplacement(value)
    }
    private fun entry(name: String) = StockEntry(0, name, 2.0, "unidades", "", "", todayStockDate(), "", "", "", control = StockControl.INDIVIDUAL)

    @Test fun typingFiltersExistingArticlesAndAddsStockOnlyToTheChosenMaterialOrTool() {
        repository.receive(false, entry("Guantes de cuero"))
        repository.receive(false, entry("Arnés"))
        repository.receive(true, entry("Taladro industrial"))
        repository.receive(true, entry("Taladro manual"))
        compose.setContent { MaterialTheme { StockApp(repository) } }
        for (tools in listOf(false, true)) {
            compose.onNodeWithText(if (tools) "Herramientas" else "Materiales").performClick()
            compose.onNodeWithText(if (tools) "+ Agregar herramienta" else "+ Agregar material").performClick()
            compose.onAllNodes(isSelectable() and hasAnyAncestor(isDialog())).onLast().performClick()
            input("Artículo existente", if (tools) "industrial" else "cuero")
            val name = if (tools) "Taladro industrial" else "Guantes de cuero"
            compose.onNode(hasText(name) and hasAnyAncestor(isPopup())).performClick()
            input("Cantidad a ingresar *", "1")
            compose.onNodeWithText("Guardar ingreso").performClick()
            compose.waitUntil(10_000) { repository.items(tools).first { it.name == name }.quantity == 3.0 }
            assertEquals(2.0, repository.items(tools).first { it.name != name }.quantity, 0.0)
        }
    }

    @Test fun newReusableMaterialCanUsePairsWithOneIdentifierPerPair() {
        compose.setContent { MaterialTheme { StockApp(repository) } }
        compose.onNodeWithText("+ Agregar material").performClick()
        input("Nombre *", "Guantes")
        compose.onNode(isToggleable() and hasAnySibling(hasText("Identificar cada unidad (reutilizable)"))).performScrollTo().performClick()
        compose.onNodeWithText("Unidad del material reutilizable").performScrollTo().performClick()
        compose.onNode(hasText("pares") and hasAnyAncestor(isPopup())).performClick()
        input("Cantidad a ingresar *", "2")
        compose.onNodeWithText("Guardar ingreso").performClick()
        compose.waitUntil(10_000) { repository.items(false).isNotEmpty() }
        assertEquals("pares", repository.items(false).single().unit)
        assertEquals(2, repository.items(false).single().units.size)
    }

    @Test fun existingGlovesCanChangeToPairsWithoutChangingStockOrIdentifiers() {
        repository.receive(false, entry("Guantes"))
        val before = repository.items(false).single()
        compose.setContent { MaterialTheme { StockApp(repository) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Guantes").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Guantes").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Cambiar unidad: unidades").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Cambiar unidad: unidades").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("unidades").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Unidad") and hasAnyAncestor(isDialog())).performClick()
        compose.onNode(hasText("pares") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("Guardar unidad del material").performClick()
        compose.waitUntil(10_000) { repository.items(false).single().unit == "pares" }
        assertEquals(before.copy(unit = "pares"), repository.items(false).single())
        assertTrue(repository.movements(false).all { it.unit == "unidades" })
    }
}
