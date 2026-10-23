package com.example.stockpaniol

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Tables keep their horizontal scroll; a further drag beyond an edge changes stock. */
private class StockSectionScroll(private val threshold: Float, private val onSwipe: (Boolean) -> Unit) : NestedScrollConnection {
    var overflow = 0f
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.Drag) {
            if (consumed.x != 0f) overflow = 0f
            overflow += available.x
        }
        return Offset.Zero
    }
    override suspend fun onPreFling(available: Velocity): Velocity {
        val distance = overflow
        overflow = 0f
        if (abs(distance) >= threshold) onSwipe(distance < 0f)
        return Velocity.Zero
    }
}

@Composable
internal fun Modifier.stockSectionSwipe(onSwipe: (tools: Boolean) -> Unit): Modifier {
    val update = rememberUpdatedState(onSwipe)
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val scroll = remember(threshold) { StockSectionScroll(threshold) { update.value(it) } }
    return nestedScroll(scroll).pointerInput(scroll) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            scroll.overflow = 0f
            var distance = Offset.Zero
            var horizontal = false
            var cancelled = false
            do {
                val event = awaitPointerEvent()
                if (event.changes.size > 1) { cancelled = true; scroll.overflow = 0f }
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null && !cancelled) {
                    val delta = change.position - change.previousPosition
                    if (delta != Offset.Zero) {
                        if (change.isConsumed) cancelled = true
                        else {
                            distance += delta
                            if (!horizontal && abs(distance.y) > viewConfiguration.touchSlop && abs(distance.y) >= abs(distance.x)) cancelled = true
                            if (!cancelled && abs(distance.x) > viewConfiguration.touchSlop && abs(distance.x) > abs(distance.y) * 1.4f) horizontal = true
                            if (horizontal && !cancelled) change.consume()
                        }
                    }
                }
            } while (event.changes.any { it.pressed })
            if (!cancelled && horizontal && abs(distance.x) >= threshold && abs(distance.x) > abs(distance.y) * 1.4f) {
                update.value(distance.x < 0f)
            }
        }
    }
}
