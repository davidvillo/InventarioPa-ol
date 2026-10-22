package com.example.stockpaniol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun StockThresholdField(value: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, enabled = enabled, label = { Text("Umbral de stock bajo") },
        supportingText = { Text("Por defecto: 5. Verde por encima del umbral; anaranjado con una cantidad positiva igual o menor; rojo en cero.") },
        isError = parseStockThreshold(value) == null, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag("stock-threshold"))
}

@Composable
internal fun StockAlertsDialog(repository: StockRepository, onDismiss: () -> Unit, onChanged: (Boolean) -> Unit) {
    var enabled by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by rememberStockError()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { repository.stockAlertsEnabled } }
            .onSuccess { enabled = it; loaded = true }
            .onFailure { error = "No se pudo cargar el aviso de control de stock." }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Aviso de control de stock") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Habilitar aviso", Modifier.weight(1f))
                Switch(enabled, { enabled = it }, enabled = loaded && !busy, modifier = Modifier.testTag("stock-alerts-switch"))
            }
            Text("Colorea los títulos de las listas en vista sencilla.")
            Text("Herramientas y materiales reutilizables: verde con disponibilidad total; amarillo si hay préstamos; anaranjado si se prestó todo; rojo sin stock. Las unidades fuera de servicio no cuentan como disponibilidad total.")
            Text("Materiales no reutilizables: verde por encima del umbral, anaranjado con stock positivo igual o menor, rojo en cero. Ajustá el umbral al agregar o editar cada material; el valor inicial es 5.")

        }
    }, confirmButton = {
        TextButton(enabled = loaded && !busy, onClick = {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { repository.stockAlertsEnabled = enabled } }
                busy = false
                result.onSuccess { onChanged(enabled); onDismiss() }
                    .onFailure { error = "No se pudo guardar el aviso de control de stock." }
            }
        }) { Text("Guardar") }
    }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}
