package com.example.stockpaniol

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpRect

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val DpRect.width get() = right - left

@RunWith(AndroidJUnit4::class)
class CompleteTableUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun multilineRowsCenterEveryCellAndAlignWithHeader() {
        compose.setContent { MaterialTheme {
            CompleteTable(listOf(100, 160), Modifier.width(280.dp).height(380.dp), header = { widths ->
                CompleteTableRow {
                    CompleteTableText("Ítem", widths[0], Modifier.testTag("head-first"), header = true)
                    CompleteTableText("Observaciones", widths[1], Modifier.testTag("head-second"), header = true)
                }
            }) { widths ->
                item {
                    CompleteTableRow {
                        CompleteTableText("12", widths[0], Modifier.testTag("body-first"))
                        CompleteTableText("Una observación extensa que debe ocupar varias líneas sin cortar ningún dato del inventario.",
                            widths[1], Modifier.testTag("body-second"))
                    }
                }
            }
        } }
        val first = compose.onNodeWithTag("body-first", true).getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag("body-second", true).getUnclippedBoundsInRoot()
        assertEquals(first.top.value, second.top.value, 0.5f)
        assertEquals(first.bottom.value, second.bottom.value, 0.5f)
        val shortText = compose.onNodeWithText("12", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals((first.top.value + first.bottom.value) / 2, (shortText.top.value + shortText.bottom.value) / 2, 0.5f)
        assertEquals((first.left.value + first.right.value) / 2, (shortText.left.value + shortText.right.value) / 2, 0.5f)
        listOf("first", "second").forEach {
            val header = compose.onNodeWithTag("head-$it", true).getUnclippedBoundsInRoot()
            val body = compose.onNodeWithTag("body-$it", true).getUnclippedBoundsInRoot()
            assertEquals(header.left.value, body.left.value, 0.5f)
            assertEquals(header.width.value, body.width.value, 0.5f)
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("12", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(TextAlign.Center, layouts.single().layoutInput.style.textAlign)
    }

    @Test fun columnsExpandTogetherWhenAvailableWidthIncreases() {
        var expanded by mutableStateOf(false)
        compose.setContent { MaterialTheme {
            Box(Modifier.width(if (expanded) 320.dp else 220.dp).height(300.dp).testTag("viewport")) {
                CompleteTable(listOf(80, 100), header = { widths ->
                    CompleteTableRow {
                        CompleteTableText("Nombre", widths[0], Modifier.testTag("header"), header = true)
                        CompleteTableText("Cantidad", widths[1], header = true)
                    }
                }) { widths -> item {
                    CompleteTableRow {
                        CompleteTableText("Taladro", widths[0], Modifier.testTag("name"))
                        CompleteTableText("2", widths[1], Modifier.testTag("quantity"))
                    }
                } }
            }
        } }
        val initial = compose.onNodeWithTag("name", true).getUnclippedBoundsInRoot().width
        compose.runOnIdle { expanded = true }
        val name = compose.onNodeWithTag("name", true).getUnclippedBoundsInRoot()
        val quantity = compose.onNodeWithTag("quantity", true).getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("viewport").getUnclippedBoundsInRoot()
        assertTrue(name.width > initial)
        assertEquals(viewport.width.value, (name.width + quantity.width).value, 1f)
        assertEquals(name.width.value, compose.onNodeWithTag("header", true).getUnclippedBoundsInRoot().width.value, 0.5f)
    }

    @Test fun narrowTableKeepsHorizontalAndVerticalScrollAndActions() {
        var sorted = false
        var opened = -1
        var deleted = -1
        compose.setContent { MaterialTheme {
            CompleteTable(listOf(160, 180, 96), Modifier.width(280.dp).height(350.dp).testTag("table"), header = { widths ->
                CompleteTableRow(Modifier.background(Color.Blue)) {
                    CompleteTableText("Nombre", widths[0], header = true, onClick = { sorted = true })
                    CompleteTableText("Observaciones", widths[1], header = true)
                    CompleteTableText("Eliminar", widths[2], header = true)
                }
            }) { widths ->
                items((0..30).toList()) { index ->
                    CompleteTableRow(Modifier.clickable { opened = index }) {
                        CompleteTableText("Artículo $index", widths[0])
                        CompleteTableText("Detalle $index", widths[1])
                        CompleteTableCell(widths[2]) {
                            Text("Borrar $index", Modifier.clickable { deleted = index })
                        }
                    }
                }
            }
        } }
        compose.onNodeWithText("Nombre").performClick()
        compose.runOnIdle { assertTrue(sorted) }
        compose.onNodeWithText("Artículo 0").performClick()
        compose.runOnIdle { assertEquals(0, opened) }
        compose.onNodeWithTag("table").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Borrar 0", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(0, deleted); assertEquals(0, opened) }
        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(25)
        compose.onNodeWithText("Borrar 25", useUnmergedTree = true).assertIsDisplayed()
    }
}
