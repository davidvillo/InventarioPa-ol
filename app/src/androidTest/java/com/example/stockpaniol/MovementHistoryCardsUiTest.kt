package com.example.stockpaniol

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovementHistoryCardsUiTest {
    @get:Rule val compose = createComposeRule()

    private fun movement(id: Long = 1, date: String = "2026-10-16") = StockMovement(
        id, 1, "Artículo de prueba", 2.0, "unidades", date, "Ana Ruiz", 1,
        "Egreso", MaterialTypes.CONSTRUCTION, "Observación del detalle", null, 1.0, "", null,
        control = StockControl.INDIVIDUAL,
        units = listOf(MovementUnit(InventoryUnit(1, 1, "ART 001", "Marca"), false)))

    private fun checkCardOrder(tools: Boolean, labels: List<String>) {
        val movement = movement()
        compose.setContent { MaterialTheme {
            MovementHistoryCard(movement, MovementStatus.LOANED, tools, false, {}, {})
        } }
        val positions = labels.map { label ->
            compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
        }
        positions.zipWithNext().forEach { (above, below) ->
            assertTrue("Cada dato debe aparecer debajo del anterior.", above.bottom <= below.top)
        }
        compose.onNodeWithText(displayStockDate(movement.date), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(movement.notes, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun toolCardOrdersCodeBeforeMovementAndDoesNotShowMaterialType() {
        checkCardOrder(true, listOf("Artículo de prueba", "Código: ART 001", "Egreso",
            "Cantidad: 2 unidades", "Personal: Ana Ruiz", "Pendiente: 1 unidades"))
        compose.onNodeWithText("Tipo de material:", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun materialCardOrdersCategoryBeforePendingAndDoesNotShowCode() {
        checkCardOrder(false, listOf("Artículo de prueba", "Egreso", "Cantidad: 2 unidades",
            "Personal: Ana Ruiz", "Tipo de material: ${MaterialTypes.CONSTRUCTION}", "Pendiente: 1 unidades"))
        compose.onNodeWithText("Código:", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun currentDateStaysCenteredAtTheTopAndChangesInBothScrollDirections() {
        val dates = listOf("2026-10-16", "2026-10-15", "2026-10-14")
        val rows = dates.flatMapIndexed { day, date ->
            (1..8).map { movement((day * 8 + it).toLong(), date) }
        }
        compose.setContent { MaterialTheme {
            MovementHistoryCards(rows, rows.associate { it.id to MovementStatus.LOANED }, false, false, {}, {},
                modifier = Modifier.width(340.dp).height(420.dp))
        } }
        fun checkHeader(date: String) {
            val list = compose.onNodeWithTag("movement-history-cards").fetchSemanticsNode().boundsInRoot
            val header = compose.onNodeWithTag("movement-date-$date").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals(list.top, header.top, 1f)
            val text = compose.onNodeWithText(displayStockDate(date), useUnmergedTree = true)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals(list.center.x, text.center.x, 1f)
        }
        checkHeader(dates[0])
        compose.onNodeWithTag("movement-history-cards").performScrollToIndex(13)
        checkHeader(dates[1])
        compose.onNodeWithTag("movement-history-cards").performScrollToIndex(22)
        checkHeader(dates[2])
        compose.onNodeWithTag("movement-history-cards").performScrollToIndex(13)
        checkHeader(dates[1])
        compose.onNodeWithTag("movement-history-cards").performScrollToIndex(4)
        checkHeader(dates[0])
    }
}
