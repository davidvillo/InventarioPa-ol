package com.example.stockpaniol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

internal fun personnelIssueMatches(label: String, query: String): Boolean {
    fun normalize(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    val text = normalize(label)
    return normalize(query).trim().split(Regex("\\s+")).all { it in text }
}

private val IssueLinesSaver = listSaver<List<StockIssueLine>, String>(
    save = { lines -> lines.map { "${it.tools};${it.itemId};${it.quantity};${it.unitIds.joinToString(",")}" } },
    restore = { lines -> lines.map { encoded ->
        val parts = encoded.split(';')
        StockIssueLine(parts[0].toBoolean(), parts[1].toLong(), parts[2].toDouble(),
            parts[3].split(',').filter { it.isNotBlank() }.map { it.toLong() })
    } })

@Composable
internal fun PersonnelIssueDialog(repository: StockRepository, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var workers by remember { mutableStateOf(emptyList<Worker>()) }
    var tools by remember { mutableStateOf(emptyList<StockItem>()) }
    var materials by remember { mutableStateOf(emptyList<StockItem>()) }
    var loading by remember { mutableStateOf(true) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var workerId by rememberSaveable { mutableLongStateOf(0L) }
    var search by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(todayStockDate()) }
    var notes by rememberSaveable { mutableStateOf("") }
    var lines by rememberSaveable(stateSaver = IssueLinesSaver) { mutableStateOf(emptyList<StockIssueLine>()) }
    var pickerTools by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var error by rememberStockError()
    LaunchedEffect(revision) {
        loading = true
        runCatching { withContext(Dispatchers.IO) { Triple(repository.personnel(), repository.items(true), repository.items(false)) } }
            .onSuccess { workers = it.first; tools = it.second; materials = it.third }
            .onFailure { error = "No se pudieron cargar el personal y el stock. Volvé a intentar." }
        loading = false
    }
    val worker = workers.firstOrNull { it.id == workerId }
    val collator = remember { Collator.getInstance(Locale.forLanguageTag("es-AR")).apply { strength = Collator.PRIMARY } }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.96f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Prestar por personal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (workerId == 0L) {
                    OutlinedTextField(search, { search = it }, label = { Text("Buscar personal") },
                        placeholder = { Text("Nombre, apellido o apodo") },
                        trailingIcon = { ClearSearchButton(search) { search = "" } },
                        singleLine = true, modifier = Modifier.fillMaxWidth().testTag("personnel-issue-search"))
                    Text(if (search.isBlank()) "Seleccioná quién retira" else "Sugerencias", style = MaterialTheme.typography.labelLarge)
                    val filtered = workers.filter { personnelIssueMatches(it.selectionLabel, search) }
                        .sortedWith(compareBy(collator) { person: Worker -> person.surname }.thenBy(collator) { it.name }.thenBy { it.id })
                    LazyColumn(Modifier.weight(1f)) {
                        items(filtered, key = { it.id }) { person ->
                            TextButton(enabled = !loading, modifier = Modifier.fillMaxWidth().testTag("personnel-issue-worker-${person.id}"),
                                onClick = { focus.clearFocus(); workerId = person.id }) {
                                Text(person.selectionLabel, Modifier.fillMaxWidth())
                            }
                        }
                        if (!loading && filtered.isEmpty()) item {
                            Text(if (workers.isEmpty()) "Registrá primero al personal desde Ajustes." else "No se encontró personal con esa búsqueda.", Modifier.padding(12.dp))
                        }
                    }
                } else {
                    Text(worker?.selectionLabel ?: "La persona seleccionada ya no está disponible.", fontWeight = FontWeight.Bold)
                    TextButton(enabled = !busy && !loading, onClick = { workerId = 0; search = "" }) { Text("Cambiar personal") }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            StockDateField("Fecha del movimiento", date, !busy) { date = it }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(enabled = !busy && !loading, modifier = Modifier.weight(1f), onClick = { pickerTools = true }) { Text("Agregar herramientas") }
                                OutlinedButton(enabled = !busy && !loading, modifier = Modifier.weight(1f), onClick = { pickerTools = false }) { Text("Agregar materiales") }
                            }
                        }
                        if (lines.isEmpty()) item { Text("Agregá los artículos que retira esta persona.", Modifier.padding(vertical = 12.dp)) }
                        items(lines, key = { "${it.tools}-${it.itemId}" }) { line ->
                            val item = (if (line.tools) tools else materials).firstOrNull { it.id == line.itemId }
                            Card(Modifier.fillMaxWidth().testTag("personnel-issue-line-${line.tools}-${line.itemId}")) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(item?.name ?: "Artículo no disponible", fontWeight = FontWeight.Bold)
                                    Text("${if (line.tools) "Herramientas" else "Materiales"} · ${formatStockAmount(line.quantity, item?.unit.orEmpty())}")
                                    item?.units?.filter { it.id in line.unitIds }?.forEach { Text(it.label, style = MaterialTheme.typography.bodySmall) }
                                    TextButton(enabled = !busy, onClick = { lines = removePersonnelIssueLine(lines, line, tools) }) { Text("Quitar") }
                                }
                            }
                        }
                        item {
                            OutlinedTextField(notes, { notes = it }, enabled = !busy, label = { Text("Observaciones de la entrega") }, modifier = Modifier.fillMaxWidth())
                            Text("Se guardará un egreso por artículo, todos a nombre de la persona seleccionada.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(enabled = !busy, onClick = onDismiss) { Text("Cerrar") }
                    if (workerId != 0L) Button(enabled = !busy && !loading, modifier = Modifier.testTag("personnel-issue-accept"), onClick = {
                        if (worker == null) error = "Seleccioná la persona que recibe."
                        else if (lines.isEmpty()) error = "Agregá al menos una herramienta o un material."
                        else {
                            focus.clearFocus(); busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { runCatching { repository.issueForPersonnel(workerId, date, notes, lines) } }
                                busy = false
                                result.onSuccess { onSaved() }.onFailure {
                                    error = it.message ?: "No se pudo guardar la entrega. No se registró ningún egreso."
                                    revision++
                                }
                            }
                        }
                    }) { Text(if (busy) "Guardando…" else "Aceptar") }
                }
                if (!loading) TextButton(enabled = !busy, onClick = { revision++ }) { Text("Actualizar personal y stock") }
            }
        }
    }
    pickerTools?.let { isTool ->
        PersonnelIssueItemPicker(isTool, if (isTool) tools else materials, lines, tools,
            onDismiss = { pickerTools = null }, onAdd = { line ->
                lines = expandPersonnelIssueLines(lines + line, tools)
                pickerTools = null
            })
    }
}

@Composable
private fun PersonnelIssueItemPicker(tools: Boolean, records: List<StockItem>, lines: List<StockIssueLine>, allTools: List<StockItem>,
    onDismiss: () -> Unit, onAdd: (StockIssueLine) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableLongStateOf(0L) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var selectedUnits by rememberSaveable { mutableStateOf(longArrayOf()) }
    var error by rememberStockError()
    val item = records.firstOrNull { it.id == selectedId }
    StockOverlay(onDismiss) {
        Text(if (tools) "Agregar herramienta" else "Agregar material", style = MaterialTheme.typography.titleLarge)
        if (item == null) {
            OutlinedTextField(query, { query = it }, label = { Text("Buscar artículo") }, singleLine = true,
                trailingIcon = { ClearSearchButton(query) { query = "" } }, modifier = Modifier.fillMaxWidth().testTag("personnel-issue-item-search"))
            val filtered = records.filter { record -> record.quantity > 0 && lines.none { it.tools == tools && it.itemId == record.id } &&
                personnelIssueMatches("${record.name} ${record.identitySummary}", query) }.sortedBy { it.name.lowercase(Locale.ROOT) }
            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { it.id }) { record ->
                    TextButton(modifier = Modifier.fillMaxWidth().testTag("personnel-issue-item-${record.id}"), onClick = {
                        selectedId = record.id; quantity = ""; selectedUnits = longArrayOf()
                    }) { Text("${record.name} · ${formatStockAmount(record.quantity, record.unit)}", Modifier.fillMaxWidth()) }
                }
                if (filtered.isEmpty()) item { Text("No hay artículos disponibles para agregar con esa búsqueda.", Modifier.padding(12.dp)) }
            }
        } else {
            Text(item.name, fontWeight = FontWeight.Bold)
            Text("Disponible: ${formatStockAmount(item.quantity, item.unit)}")
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (item.individuallyTracked) {
                    Text("Marcá las unidades que retira")
                    item.availableUnits.forEach { unit ->
                        UnitSelection(unit.label, unit.id in selectedUnits, true) {
                            selectedUnits = if (unit.id in selectedUnits) selectedUnits.filterNot { it == unit.id }.toLongArray() else selectedUnits + unit.id
                        }
                    }
                    if (tools) {
                        val bundles = item.units.filter { it.id in selectedUnits }.mapNotNull { it.bundleId }.toSet()
                        val companions = allTools.flatMap { stock -> stock.units.filter { it.bundleId != null && it.bundleId in bundles && it.id !in selectedUnits }.map { stock.name to it } }
                        if (companions.isNotEmpty()) {
                            Text("También se agregan por conjunto:", fontWeight = FontWeight.Bold)
                            companions.forEach { (name, unit) -> Text("$name · ${unit.label}") }
                        }
                    }
                } else OutlinedTextField(quantity, { quantity = it }, label = { Text("Cantidad") },
                    keyboardOptions = KeyboardOptions(keyboardType = if (item.wholeNumbers) KeyboardType.Number else KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("personnel-issue-quantity"))
            }
            TextButton(onClick = { selectedId = 0 }) { Text("Elegir otro artículo") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
            if (item != null) Button(onClick = {
                val amount = if (item.individuallyTracked) selectedUnits.size.toDouble() else quantity.trim().replace(',', '.').toDoubleOrNull()
                runCatching {
                    require(amount != null) { "Ingresá una cantidad válida." }
                    validateMovementQuantity(amount, item.unit, item.wholeNumbers)
                    item.policy.validateSelection(amount, selectedUnits.toList())
                    require(amount <= item.quantity) { "La cantidad supera el stock disponible." }
                    val line = StockIssueLine(tools, item.id, amount, selectedUnits.toList())
                    val expanded = expandPersonnelIssueLines(lines + line, allTools)
                    require(expanded.filter { it.tools }.all { row -> allTools.firstOrNull { it.id == row.itemId }?.let { stock ->
                        row.unitIds.all { id -> stock.availableUnits.any { it.id == id } }
                    } == true }) { "Alguna herramienta del conjunto no está disponible." }
                    onAdd(line)
                }.onFailure { error = it.message ?: "No se pudo agregar el artículo." }
            }, modifier = Modifier.testTag("personnel-issue-add")) { Text("Agregar") }
        }
    }
}
