package com.example.stockpaniol

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal const val STOCK_ERROR_DURATION_MS = 2_000L

internal data class StockErrorNotice(val message: String, val sequence: Long)

/** Keep validation state while giving every failed attempt (even identical ones) a new notice. */
internal class StockErrorState : MutableState<String> {
    private var message by mutableStateOf("")
    private var sequence = 0L
    var notice by mutableStateOf<StockErrorNotice?>(null)
        private set
    override var value: String
        get() = message
        set(value) {
            message = value
            notice = if (value.isBlank()) null else StockErrorNotice(value, ++sequence)
        }
    fun dismiss(current: StockErrorNotice) { if (notice == current) notice = null }
    override fun component1(): String = value
    override fun component2(): (String) -> Unit = { value = it }
}

internal object StockErrorPosition : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
        popupContentSize: IntSize): IntOffset = IntOffset(
        ((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(0),
        ((windowSize.height - popupContentSize.height).coerceAtLeast(0) * 0.72f).roundToInt())
}

@Composable
internal fun rememberStockError(tag: String = "transient-error"): MutableState<String> {
    val state = remember { StockErrorState() }
    val notice = state.notice
    LaunchedEffect(notice) {
        if (notice != null) { delay(STOCK_ERROR_DURATION_MS); state.dismiss(notice) }
    }
    if (notice != null) {
        val width = (LocalConfiguration.current.screenWidthDp - 32).coerceIn(1, 480).dp
        Popup(popupPositionProvider = StockErrorPosition,
            properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer, shadowElevation = 8.dp,
                modifier = Modifier.widthIn(max = width).testTag(tag).semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(notice.message, Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
            }
        }
    }
    return state
}

@Composable
internal fun StockErrorEffect(message: String, tag: String = "transient-error") {
    var error by rememberStockError(tag)
    LaunchedEffect(message) { error = message }
}
