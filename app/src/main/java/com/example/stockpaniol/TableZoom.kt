package com.example.stockpaniol

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density

/** Remeasure the table at its new scale so its scroll ranges and touch targets follow the zoom. */
@Composable
internal fun ZoomableTable(scale: Float, onScaleChange: (Float) -> Unit, modifier: Modifier = Modifier,
    content: @Composable () -> Unit) {
    val currentScale = rememberUpdatedState(scale)
    val updateScale = rememberUpdatedState(onScaleChange)
    val density = LocalDensity.current
    Box(modifier.clipToBounds().testTag("zoomable-table").pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pinching = false
            var gestureScale = currentScale.value
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    pinching = true
                    val zoom = event.calculateZoom()
                    if (zoom.isFinite() && zoom > 0f) {
                        gestureScale = (gestureScale * zoom).coerceIn(0.55f, 2f)
                        updateScale.value(gestureScale)
                    }
                }
                // Leave one-finger scrolling and clicks to the table. Once pinching starts,
                // consume until both fingers lift so the gesture cannot open a record.
                if (pinching) event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }) {
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale), content = content)
    }
}
