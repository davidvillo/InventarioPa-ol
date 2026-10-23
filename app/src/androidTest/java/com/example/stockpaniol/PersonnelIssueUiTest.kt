package com.example.stockpaniol

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonnelIssueUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchSuggestionsAndMixedDeliverySaveSeparateMovementsForJuan() {
        val context = isolatedStockContext("personnel_delivery_ui_")
        val repository = LocalStockRepository(context)
        try {
            repository.addWorker("Juan", "Pérez", "Otros", "Toto")
            repository.addWorker("Ana", "Ruiz", "Otros", "")
            repository.addMeasurement("bolsas", true)
            listOf("Sierra circular", "Amoladora").forEach { name ->
                repository.receive(true, StockEntry(0, name, 1.0, "unidades", "", "", todayStockDate(), "", "", ""))
            }
            repository.receive(false, StockEntry(0, "Cemento", 5.0, "bolsas", "", "", todayStockDate(), "", "", ""))
            val worker = repository.personnel().first { it.name == "Juan" }
            val other = repository.personnel().first { it.name == "Ana" }
            compose.setContent { MaterialTheme { StockApp(repository) } }
            compose.onNodeWithTag("personnel-issue-open").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("personnel-issue-worker-${worker.id}").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("personnel-issue-search").performTextInput("perez")
            compose.onNodeWithTag("personnel-issue-worker-${other.id}").assertDoesNotExist()
            compose.onNodeWithTag("personnel-issue-worker-${worker.id}").performClick()
            repository.items(true).forEach { item ->
                compose.onNodeWithText("Agregar herramientas").performScrollTo().performClick()
                compose.onNodeWithTag("personnel-issue-item-${item.id}").performClick()
                compose.onNodeWithText(item.availableUnits.single().label).performScrollTo().performClick()
                compose.onNodeWithTag("personnel-issue-add").performClick()
            }
            val material = repository.items(false).single()
            compose.onNodeWithText("Agregar materiales").performScrollTo().performClick()
            compose.onNodeWithTag("personnel-issue-item-${material.id}").performClick()
            compose.onNodeWithTag("personnel-issue-quantity").performTextInput("1")
            compose.onNodeWithTag("personnel-issue-add").performClick()
            assertFalse(repository.movements(false).any { it.kind == "Egreso" })
            compose.onNodeWithTag("personnel-issue-accept").performClick()
            compose.waitUntil(15_000) { repository.movements(false).count { it.kind == "Egreso" } == 1 }
            compose.onNodeWithTag("personnel-issue-accept").assertDoesNotExist()
            assertEquals(2, repository.movements(true).count { it.kind == "Egreso" && it.workerId == worker.id })
            assertEquals(1, repository.movements(false).count { it.kind == "Egreso" && it.workerId == worker.id })
            assertEquals(4.0, repository.items(false).single().quantity, 0.0)

            // Preparing another delivery and closing it must not save anything.
            compose.onNodeWithTag("personnel-issue-open").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("personnel-issue-worker-${worker.id}").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("personnel-issue-worker-${worker.id}").performClick()
            compose.onNodeWithText("Agregar materiales").performScrollTo().performClick()
            compose.onNodeWithTag("personnel-issue-item-${material.id}").performClick()
            compose.onNodeWithTag("personnel-issue-quantity").performTextInput("2")
            compose.onNodeWithTag("personnel-issue-add").performClick()
            compose.onNodeWithText("Cerrar").performClick()
            assertEquals(1, repository.movements(false).count { it.kind == "Egreso" })
        } finally { repository.close(); context.deleteDatabase("stock_paniol.db") }
    }
}
