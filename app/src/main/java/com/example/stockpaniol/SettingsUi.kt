package com.example.stockpaniol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsDialog(repository: StockRepository, onDismiss: () -> Unit) {
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    when (section) {
        "obras" -> WorksitesDialog(repository, { section = null })
        "unidades" -> MeasurementSettingsDialog(repository, { section = null })
        else -> StockOverlay(onDismiss) {
            Text("Ajustes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Administrá las opciones disponibles en los formularios.")
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { section = "obras" }, modifier = Modifier.fillMaxWidth()) { Text("Obras") }
            OutlinedButton(onClick = { section = "unidades" }, modifier = Modifier.fillMaxWidth()) { Text("Unidades") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    }
}

@Composable
fun MeasurementSettingsDialog(repository: MeasurementRepository, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf(emptyList<MeasurementUnit>()) }
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var form by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var whole by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(revision) {
        runCatching { withContext(Dispatchers.IO) { repository.measurements() } }
            .onSuccess { rows = it }.onFailure { error = "No se pudieron cargar las unidades." }
        loading = false
    }
    StockOverlay({ if (!busy) onDismiss() }) {
        Text("Unidades", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Unidades de medida para materiales.", style = MaterialTheme.typography.bodySmall)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.weight(1f).testTag("measurement-units-list"), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.id }) { unit ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(unit.name, fontWeight = FontWeight.SemiBold)
                        Text(if (unit.wholeNumbers) "Cantidades enteras" else "Permite decimales", style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(modifier = Modifier.testTag("modify-measure-${unit.id}"), enabled = !busy, onClick = { editId = unit.id; name = unit.name; whole = unit.wholeNumbers; form = true; error = "" }) { Text("Modificar") }
                            TextButton(modifier = Modifier.testTag("remove-measure-${unit.id}"), enabled = !busy, onClick = { deleteId = unit.id; error = "" }) { Text("Quitar") }
                        }
                    }
                }
            }
            if (rows.isEmpty() && !loading) item { Text("No hay unidades disponibles. Agregá una unidad para registrar materiales.") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cerrar") }
            Button(enabled = !busy && !loading, onClick = { editId = null; name = ""; whole = false; error = ""; form = true }) { Text("Agregar unidad") }
        }
    }
    if (form) AlertDialog(onDismissRequest = { if (!busy) form = false }, title = { Text(if (editId == null) "Agregar unidad" else "Modificar unidad") }, text = {
        Column {
            OutlinedTextField(name, { name = it }, enabled = !busy, label = { Text("Nombre de la unidad *") }, singleLine = true)
            Row {
                Checkbox(whole, { whole = it }, enabled = !busy && rows.firstOrNull { it.id == editId }?.counting != true)
                Text("Solo cantidades enteras")
            }
            Text("Cambiar el nombre no convierte las cantidades. Los movimientos anteriores conservan su unidad original.", style = MaterialTheme.typography.bodySmall)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = {
        busy = true
        val targetId = editId
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                if (targetId == null) repository.addMeasurement(name, whole) else repository.updateMeasurement(targetId, name, whole)
            } }
            busy = false
            result.onSuccess { form = false; error = ""; revision++ }.onFailure { error = it.message ?: "No se pudo guardar la unidad." }
        }
    }) { Text("Guardar unidad") } }, dismissButton = { TextButton(enabled = !busy, onClick = { form = false; error = "" }) { Text("Cancelar") } })
    rows.firstOrNull { it.id == deleteId }?.let { unit ->
        AlertDialog(onDismissRequest = { if (!busy) deleteId = null }, title = { Text("Quitar unidad") }, text = {
            Text("¿Quitar ${unit.name} de la lista de materiales nuevos? Los artículos existentes conservan su unidad. Las herramientas y los ejemplares identificados mantienen su unidad de conteo.")
        }, confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { repository.deleteMeasurement(unit.id) } }
                busy = false; deleteId = null
                result.onSuccess { error = ""; revision++ }.onFailure { error = it.message ?: "No se pudo quitar la unidad." }
            }
        }) { Text("Quitar") } }, dismissButton = { TextButton(enabled = !busy, onClick = { deleteId = null }) { Text("Cancelar") } })
    }
}
