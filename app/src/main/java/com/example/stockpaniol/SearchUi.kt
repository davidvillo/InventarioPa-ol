package com.example.stockpaniol

import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp

@Composable
internal fun ClearSearchButton(value: String, enabled: Boolean = true, onClear: () -> Unit) {
    if (value.isNotEmpty()) IconButton(enabled = enabled, onClick = onClear,
        modifier = Modifier.semantics { contentDescription = "Limpiar búsqueda" }) { Text("×", fontSize = 24.sp) }
}
