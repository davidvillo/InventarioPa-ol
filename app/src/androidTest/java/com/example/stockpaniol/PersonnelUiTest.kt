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
class PersonnelUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = isolatedStockContext("personnel_ui_")
    private lateinit var repository: LocalStockRepository
    @Before fun prepare() {
        context.deleteDatabase("stock_paniol.db")
        repository = LocalStockRepository(context)
        repository.worksites()
        compose.setContent { MaterialTheme { StockApp(repository) } }
        compose.waitForIdle()
    }
    @After fun clean() { repository.close(); context.deleteDatabase("stock_paniol.db") }
    private fun input(label: String, value: String) {
        val field = compose.onNode(hasSetTextAction() and hasText(label))
        if (label != "Nombre de la opción *") field.performScrollTo()
        field.performTextReplacement(value)
    }
    private fun openMenu(label: String) {
        compose.onNodeWithText("☰").performClick()
        compose.onNodeWithText(label).performClick()
    }
    private fun chooseSite(name: String) {
        compose.onNodeWithText("Obra *").performScrollTo().performClick()
        compose.onNodeWithText(name).performScrollTo().performClick()
    }

    @Test fun workerCanBeCreatedEditedAndDeletedUsingDropdown() {
        openMenu("Personal de obra")
        compose.onNodeWithText("Agregar personal").performClick()
        input("Nombre *", "Ana")
        input("Apellido *", "Ruiz")
        chooseSite("Camionero")
        compose.onNodeWithText("Guardar personal").performClick()
        compose.waitUntil(10_000) { repository.personnel().size == 1 }
        val id = repository.personnel().single().id
        compose.waitForIdle()
        compose.onNodeWithText("Editar").performClick()
        input("Nombre *", "Ana María")
        chooseSite("519")
        compose.onNodeWithText("Guardar personal").performClick()
        compose.waitUntil(10_000) { repository.personnel().single().name == "Ana María" }
        assertEquals(id, repository.personnel().single().id)
        assertEquals("519", repository.personnel().single().worksite)
        compose.waitForIdle()
        compose.onNodeWithText("Eliminar").performClick()
        compose.onNodeWithText("Eliminar personal").assertIsDisplayed()
        compose.onAllNodesWithText("Eliminar").onLast().performClick()
        compose.waitUntil(10_000) { repository.personnel().isEmpty() }
    }

    @Test fun worksiteSettingsCreateRenameAndRemoveOptionsAndRefreshOpenPersonnelForm() {
        openMenu("Personal de obra")
        compose.onNodeWithText("Agregar personal").performClick()
        compose.onNodeWithText("Administrar opciones de obra").performScrollTo().performClick()
        compose.onNodeWithText("Agregar opción").performClick()
        input("Nombre de la opción *", "Obra nueva")
        compose.onNodeWithText("Guardar opción").performClick()
        compose.waitUntil(10_000) { repository.worksites().any { it.name == "Obra nueva" } }
        val siteId = repository.worksites().first { it.name == "Obra nueva" }.id
        compose.waitForIdle()
        compose.onNodeWithTag("worksites-list").performScrollToNode(hasTestTag("modify-worksite-$siteId"))
        compose.onNodeWithTag("modify-worksite-$siteId").performClick()
        input("Nombre de la opción *", "Obra norte nueva")
        compose.onNodeWithText("Guardar opción").performClick()
        compose.waitUntil(10_000) { repository.worksites().any { it.name == "Obra norte nueva" } }
        assertEquals(siteId, repository.worksites().first { it.name == "Obra norte nueva" }.id)
        compose.waitForIdle()
        compose.onNodeWithText("Cerrar").performClick()
        chooseSite("Obra norte nueva")
        input("Nombre *", "Luis")
        input("Apellido *", "Pérez")
        compose.onNodeWithText("Guardar personal").performClick()
        compose.waitUntil(10_000) { repository.personnel().size == 1 }
        compose.waitForIdle()
        compose.onNodeWithText("Cerrar").performClick()
        openMenu("Ajustes")
        compose.onNodeWithText("Obras").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("worksites-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("worksites-list").performScrollToNode(hasTestTag("remove-worksite-$siteId"))
        compose.onNodeWithTag("remove-worksite-$siteId").performClick()
        compose.onNodeWithText("Quitar opción de obra").assertIsDisplayed()
        compose.onAllNodesWithText("Quitar").onLast().performClick()
        compose.waitUntil(10_000) { repository.worksites().none { it.name == "Obra norte nueva" } }
        assertEquals("Obra norte nueva", repository.personnel().single().worksite)
    }
}
