package com.example.stockpaniol

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TableZoomUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pinchResizesTableBothWaysWithoutOpeningRowsAndKeepsScrollingAndClicks() {
        var scale by mutableFloatStateOf(1f)
        var opened = 0
        compose.setContent { MaterialTheme {
            ZoomableTable(scale, { scale = it }, Modifier.width(300.dp).height(400.dp)) {
                Column(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                    LazyColumn(Modifier.testTag("table-rows")) {
                        items(40) { index ->
                            Row(Modifier.clickable { opened++ }.height(60.dp)) {
                                Text("Artículo $index", Modifier.width(200.dp).testTag("name-$index"))
                                Text("Detalle $index", Modifier.width(240.dp))
                            }
                        }
                    }
                }
            }
        } }
        val initialWidth = compose.onNodeWithTag("name-0", useUnmergedTree = true).fetchSemanticsNode().size.width
        compose.onNodeWithTag("zoomable-table").performTouchInput {
            pinch(center - Offset(width * 0.12f, 0f), center - Offset(width * 0.22f, 0f),
                center + Offset(width * 0.12f, 0f), center + Offset(width * 0.22f, 0f))
        }
        compose.runOnIdle { assertTrue(scale > 1.5f); assertEquals(0, opened) }
        val largerWidth = compose.onNodeWithTag("name-0", useUnmergedTree = true).fetchSemanticsNode().size.width
        assertTrue(largerWidth > initialWidth * 1.5f)
        compose.onNodeWithTag("zoomable-table").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Detalle 0", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("zoomable-table").performTouchInput { swipeRight() }
        compose.onNodeWithTag("zoomable-table").performTouchInput {
            pinch(center - Offset(width * 0.22f, 0f), center - Offset(width * 0.07f, 0f),
                center + Offset(width * 0.22f, 0f), center + Offset(width * 0.07f, 0f))
        }
        compose.runOnIdle { assertTrue(scale < 0.8f); assertEquals(0, opened) }
        compose.onNodeWithTag("zoomable-table").performTouchInput { click(Offset(20f, 20f)) }
        compose.runOnIdle { assertEquals(1, opened) }
        compose.onNodeWithTag("table-rows").performScrollToIndex(30)
        compose.onNodeWithText("Artículo 30", useUnmergedTree = true).assertIsDisplayed()
    }
}
