package com.example.stockpaniol

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ToolBundlesDialog(repository: StockRepository, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var bundles by remember { mutableStateOf(emptyList<ToolBundle>()) }
    var stock by remember { mutableStateOf(emptyList<StockItem>()) }
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var form by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(longArrayOf()) }
    var dissolve by remember { mutableStateOf<ToolBundle?>(null) }
    LaunchedEffect(revision) {
        loading = true
        runCatching { withContext(Dispatchers.IO) { repository.toolBundles() to repository.items(true) } }
            .onSuccess { bundles = it.first; stock = it.second }
            .onFailure { error = "No se pudieron cargar los conjuntos." }
        loading = false
    }
    fun open(bundle: ToolBundle?) {
        editId = bundle?.id; name = bundle?.name.orEmpty(); selected = bundle?.unitIds?.toLongArray() ?: longArrayOf()
        search = ""; error = ""; form = true
    }
    val units = stock.flatMap { item -> item.units.map { item.name to it } }
    StockOverlay({ if (!busy) onDismiss() }) {
        Text(if (form) "Unir herramientas" else "Conjuntos de herramientas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (form) {
            OutlinedTextField(name, { name = it }, enabled = !busy, label = { Text("Nombre del conjunto *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Seleccioná cada unidad que viaja junta. Por ejemplo, martillo y cincel, o una caja con varias herramientas.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(search, { search = it }, label = { Text("Buscar herramienta, marca o identificador") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("${selected.size} unidades seleccionadas", fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(units.filter { (title, unit) -> unit.inActiveStock && (title.contains(search, true) || unit.label.contains(search, true)) }, key = { it.second.id }) { (title, unit) ->
                    val available = !busy && unit.available && (unit.bundleId == null || unit.bundleId == editId)
                    Row(Modifier.fillMaxWidth().clickable(enabled = available) {
                        selected = if (unit.id in selected) selected.filter { it != unit.id }.toLongArray() else selected + unit.id
                    }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(unit.id in selected, onCheckedChange = null, enabled = available)
                        Column(Modifier.weight(1f)) {
                            Text(title, fontWeight = FontWeight.SemiBold)
                            Text(unit.label, style = MaterialTheme.typography.bodySmall)
                            if (!unit.available) Text(unit.statusLabel, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } else {
            Text("Al prestar una unidad del conjunto se descuentan todas. Al devolverla, se reintegran juntas.", style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(bundles, key = { it.id }) { bundle ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(bundle.name, fontWeight = FontWeight.Bold)
                            units.filter { it.second.id in bundle.unitIds }.forEach { (title, unit) -> Text("$title · ${unit.label}", style = MaterialTheme.typography.bodySmall) }
                            Row {
                                TextButton(enabled = !busy, onClick = { open(bundle) }) { Text("Modificar herramientas unidas") }
                                TextButton(enabled = !busy, onClick = { dissolve = bundle }) { Text("Desunir") }
                            }
                        }
                    }
                }
                if (bundles.isEmpty() && !loading) item { Text("Todavía no hay herramientas unidas.") }
            }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(enabled = !busy, onClick = { if (form) { form = false; error = "" } else onDismiss() }) { Text(if (form) "Cancelar" else "Cerrar") }
            Button(enabled = !busy && !loading, onClick = {
                if (!form) open(null) else {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { repository.saveToolBundle(editId, name, selected.toList()) } }
                        busy = false
                        result.onSuccess { form = false; error = ""; revision++; onChanged() }
                            .onFailure { error = it.message ?: "No se pudo guardar el conjunto." }
                    }
                }
            }) { Text(if (busy) "Guardando…" else if (form) "Guardar conjunto" else "Unir herramientas") }
        }
    }
    dissolve?.let { bundle ->
        AlertDialog(onDismissRequest = { if (!busy) dissolve = null }, title = { Text("Desunir herramientas") },
            text = { Text("¿Desunir ${bundle.name}? Cada herramienta quedará independiente. Las cantidades y los movimientos anteriores se conservan.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { repository.dissolveToolBundle(bundle.id) } }
                    busy = false; dissolve = null
                    result.onSuccess { error = ""; revision++; onChanged() }.onFailure { error = it.message ?: "No se pudo desunir." }
                }
            }) { Text("Desunir conjunto") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { dissolve = null }) { Text("Cancelar") } })
    }
}
