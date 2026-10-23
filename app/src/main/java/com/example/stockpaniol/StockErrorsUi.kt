package com.example.stockpaniol

import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.delay

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

@Composable
internal fun rememberStockError(tag: String = "transient-error"): MutableState<String> {
    val state = remember { StockErrorState() }
    val notice = state.notice
    LaunchedEffect(notice) {
        if (notice != null) { delay(STOCK_ERROR_DURATION_MS); state.dismiss(notice) }
    }
    if (notice != null) {
        val width = (LocalConfiguration.current.screenWidthDp - 32).coerceIn(1, 480).dp
        // A popup attached to the activity can be hidden underneath a form's Dialog.
        // A separate, non-interactive window keeps notices above every open form.
        Dialog(onDismissRequest = {}, properties = DialogProperties(
            dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
            val window = (LocalView.current.parent as DialogWindowProvider).window
            SideEffect {
                window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setDimAmount(0f)
            }
            Box(Modifier.fillMaxSize().testTag("$tag-window"), contentAlignment = BiasAlignment(0f, 0.44f)) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer, shadowElevation = 8.dp,
                    modifier = Modifier.widthIn(max = width).testTag(tag).semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(notice.message, Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
                }
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
