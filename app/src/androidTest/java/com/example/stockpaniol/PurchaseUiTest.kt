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
class PurchaseUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = isolatedStockContext("purchase_ui_")
    private lateinit var repository: LocalStockRepository
    @Before fun prepare() {
        context.deleteDatabase("stock_paniol.db")
        repository = LocalStockRepository(context)
        compose.setContent { MaterialTheme { StockApp(repository) } }
        compose.waitForIdle()
    }
    @After fun clean() { repository.close(); context.deleteDatabase("stock_paniol.db") }
    private fun input(label: String, value: String) {
        compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextReplacement(value)
    }

    @Test fun repeatedToolNameCreatesDistinctBrandsAndIdentifiersInOneCatalogueItem() {
        compose.onNodeWithText("Herramientas").performClick()
        listOf("Bosch" to "B-01", "Wadfow" to "W-01").forEachIndexed { index, (brand, code) ->
            compose.onNodeWithText("+ Agregar herramienta").performClick()
            input("Nombre *", "Amoladora de 7 pulgadas")
            input("Marca", brand)
            input("Identificador del pañolero (opcional)", code)
            compose.onNodeWithText("Guardar ingreso").performClick()
            compose.waitUntil(10_000) { repository.purchases(true).size == index + 1 }
            compose.waitForIdle()
        }
        val item = repository.items(true).single()
        assertEquals(2.0, item.quantity, 0.0)
        assertEquals(listOf("Bosch", "Wadfow"), item.units.map { it.brand })
        compose.onNodeWithText("Amoladora de 7 pulgadas").performClick()
        compose.onNodeWithText("Bosch · B-01").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Wadfow · W-01").performScrollTo().assertIsDisplayed()
    }

    @Test fun confirmedMaterialAndToolIncomeAppearInSeparatePurchaseLists() {
        for (tools in listOf(false, true)) {
            if (tools) compose.onNodeWithText("Herramientas").performClick()
            compose.onNodeWithText(if (tools) "+ Agregar herramienta" else "+ Agregar material").performClick()
            val name = if (tools) "Martillo" else "Arena"
            val company = if (tools) "Herramientas SA" else "Áridos SA"
            val receipt = if (tools) "R-H/12" else "R-M/12"
            input("Nombre *", name)
            if (!tools) input("Cantidad a ingresar *", "2,5")
            if (!tools) compose.onNodeWithText("kg").performScrollTo().performClick()
            input("Número de remito", receipt)
            input("Empresa a la que se compró", company)
            compose.onNodeWithText("Guardar ingreso").performClick()
            compose.waitUntil(10_000) { repository.purchases(tools).size == 1 }
            compose.waitForIdle()
            compose.onNodeWithText("☰").performClick()
            val title = "Registro de compras ${if (tools) "herramientas" else "materiales"}"
            compose.onNodeWithText(title).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("1 compras").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Fecha ↓").assertExists()
            compose.onNodeWithText(if (tools) "Herramienta ↕" else "Material ↕").assertExists()
            compose.onNodeWithText("Cantidad ↕").assertExists()
            compose.onNodeWithText("Unidad ↕").assertExists()
            compose.onNodeWithText("Empresa ↕").assertExists()
            compose.onNodeWithText("Remito ↕").assertExists()
            compose.onNodeWithText("Vista completa").performClick()
            compose.onNodeWithText("Empresa: $company").assertIsDisplayed()
            compose.onNodeWithText("Remito: $receipt").assertIsDisplayed()
            compose.onNodeWithText("Filtros y orden").performClick()
            input("Texto a filtrar", "Sin coincidencias")
            compose.onNodeWithText("No hay compras con estos filtros.").assertIsDisplayed()
            input("Texto a filtrar", company)
            compose.onNodeWithText("Ocultar filtros").performClick()
            compose.onNodeWithText("Empresa: $company").assertIsDisplayed()
            compose.onNodeWithText("Cerrar").performClick()
            assertEquals(receipt, repository.purchases(tools).single().receipt)
        }
        assertEquals(1, repository.purchases(false).size)
        assertEquals(1, repository.purchases(true).size)
    }
}
