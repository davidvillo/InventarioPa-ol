package com.example.stockpaniol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MaterialUnitDialog(repository: StockRepository, item: StockItem, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var measures by remember { mutableStateOf(emptyList<MeasurementUnit>()) }
    var selected by rememberSaveable(item.id) { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by rememberStockError()
    val scope = rememberCoroutineScope()
    LaunchedEffect(item.id) {
        runCatching { withContext(Dispatchers.IO) { repository.measurements(true).filter { it.identifiesMaterial } } }
            .onSuccess { options -> measures = options; if (selected == null) selected = options.firstOrNull { it.name == item.unit }?.id }
            .onFailure { error = "No se pudieron cargar las unidades." }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Cambiar unidad del material") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(item.name)
            Text("Actual: ${formatStockAmount(item.quantity, item.unit)}")
            StockChoice("Unidad", measures.firstOrNull { it.id == selected }?.name.orEmpty(), measures.map { it.id.toString() to it.name }, !busy) { selected = it.toLong() }
            Text("La cantidad se mantiene: ${formatStockAmount(item.quantity, measures.firstOrNull { it.id == selected }?.name ?: item.unit)}. Cada identificador representará una unidad o un par completo. Los préstamos y el historial se conservan.", style = MaterialTheme.typography.bodySmall)

        }
    }, confirmButton = { TextButton(enabled = !busy && selected != null, onClick = {
        busy = true
        val measureId = selected!!
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.updateMaterialUnit(item.id, measureId) } }
            busy = false
            result.onSuccess { onSaved() }.onFailure { error = it.message ?: "No se pudo cambiar la unidad." }
        }
    }) { Text("Guardar unidad del material") } }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun MaterialCareDialog(repository: StockRepository, trash: Boolean, onDismiss: () -> Unit, onChanged: () -> Unit) {
    var records by remember { mutableStateOf(emptyList<StockItem>()) }
    var damages by remember { mutableStateOf(emptyList<DamagedMaterial>()) }
    var revision by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var error by rememberStockError()
    var loaded by remember { mutableStateOf(false) }
    var register by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<DamagedMaterial?>(null) }
    var action by remember { mutableStateOf<MaterialDamageAction?>(null) }
    LaunchedEffect(revision) {
        runCatching { withContext(Dispatchers.IO) { repository.items(false) to repository.damagedMaterials() } }
            .onSuccess { records = it.first; damages = it.second; error = "" }
            .onFailure { error = it.message ?: "No se pudieron cargar los materiales." }
        loaded = true
    }
    val rows = damages.filter { it.state == if (trash) "TRASH" else "DAMAGED" }.mapNotNull { damage ->
        records.firstOrNull { it.id == damage.itemId }?.let { it to damage }
    }.filter { (item, damage) -> (item.name + " " + damage.notes + " " + item.units.firstOrNull { it.id == damage.unitId }?.label.orEmpty()).contains(query, true) }
    StockOverlay(onDismiss) {
        Text(if (trash) "Papelera de materiales" else "Materiales dañados", style = MaterialTheme.typography.titleLarge)
        Text(if (trash) "Los materiales descartados conservan su historial. Restaurar no los habilita para usar."
            else "Para materiales reutilizables o no. Registrá el daño y elegí si se pueden utilizar dañados o deben ir a la papelera.", style = MaterialTheme.typography.bodySmall)
        if (!trash) Button(enabled = loaded, onClick = { register = true }) { Text("Registrar daño") }
        OutlinedTextField(query, { query = it }, label = { Text("Buscar material o identificación") }, trailingIcon = { ClearSearchButton(query) { query = "" } }, modifier = Modifier.fillMaxWidth())

        if (!loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.second.id }) { (item, damage) ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text("${formatStockAmount(damage.quantity, item.unit)}")
                        item.units.firstOrNull { it.id == damage.unitId }?.let { Text(it.label) }
                        Text("Daño: ${damage.notes}")
                        Text(displayStockDate(damage.date))
                        val actions = if (trash) listOf(MaterialDamageAction.RESTORE) else listOf(MaterialDamageAction.USE, MaterialDamageAction.TRASH)
                        actions.forEach { next -> TextButton(onClick = { selected = damage; action = next }) { Text(next.label) } }
                    }
                }
            }
            if (loaded && rows.isEmpty()) item { Text(if (trash) "No hay materiales en la papelera." else "No hay materiales dañados pendientes de decisión.") }
        }
        TextButton(onClick = onDismiss) { Text("Cerrar") }
    }
    if (register || action != null) MaterialDamageForm(repository, records, if (register) MaterialDamageAction.DAMAGE else action!!,
        selected, { register = false; action = null; selected = null }, {
            register = false; action = null; selected = null; revision++; onChanged()
        })
}

@Composable
internal fun MaterialDamageForm(repository: StockRepository, records: List<StockItem>, action: MaterialDamageAction,
    damage: DamagedMaterial?, onDismiss: () -> Unit, onSaved: () -> Unit, initialItemId: Long? = null) {
    var itemId by rememberSaveable { mutableStateOf(initialItemId ?: damage?.itemId ?: records.firstOrNull { it.quantity > 0 }?.id) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var ids by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var date by rememberSaveable { mutableStateOf(todayStockDate()) }
    var notes by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by rememberStockError()
    val scope = rememberCoroutineScope()
    val item = records.firstOrNull { it.id == itemId }
    StockOverlay({ if (!busy) onDismiss() }) {
        Text(action.label, style = MaterialTheme.typography.titleLarge)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (action == MaterialDamageAction.DAMAGE) {
                StockChoice("Material", item?.name ?: "Seleccionar material", records.filter { it.quantity > 0 }.map { it.id.toString() to it.name }, enabled = !busy && initialItemId == null) {
                    itemId = it.toLong(); ids = emptyList(); quantity = ""
                }
                item?.let { current ->
                    Text("Disponible: ${formatStockAmount(current.quantity, current.unit)}")
                    if (current.individuallyTracked) current.availableUnits.forEach { unit ->
                        Row {
                            Checkbox(unit.id in ids, { checked -> if (!busy) ids = if (checked) ids + unit.id else ids - unit.id }, enabled = !busy,
                                modifier = Modifier.testTag("material-damage-unit-${unit.id}"))
                            Text(unit.label)
                        }
                    } else OutlinedTextField(quantity, { quantity = it }, enabled = !busy, label = { Text("Cantidad dañada (${stockUnitLabel(current.unit, quantity.trim().replace(',', '.').toDoubleOrNull() ?: 0.0)})") }, modifier = Modifier.fillMaxWidth())
                }
                Text("La cantidad dañada queda apartada hasta que decidas utilizarla o enviarla a la papelera.")
            } else {
                Text(item?.name.orEmpty())
                Text(formatStockAmount(damage!!.quantity, item?.unit.orEmpty()))
                Text("Daño: ${damage.notes}")
                if (action == MaterialDamageAction.USE) Text("Se habilitará esta cantidad para egresos, conservando el registro del daño.")
            }
            StockDateField("Fecha del cambio", date, !busy) { date = it }
            OutlinedTextField(notes, { notes = it }, enabled = !busy, label = { Text(if (action == MaterialDamageAction.DAMAGE) "Qué le pasó *" else "Motivo *") }, minLines = 3, modifier = Modifier.fillMaxWidth())

        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") }
            Button(enabled = !busy && item != null, onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching {
                        val amount = damage?.quantity ?: if (item!!.individuallyTracked) ids.size.toDouble() else quantity.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
                        repository.changeMaterialState(item!!.id, action, amount, date, notes, ids, damage?.id)
                    } }
                    busy = false
                    result.onSuccess { onSaved() }.onFailure { error = it.message ?: "No se pudo guardar el cambio." }
                }
            }) { Text(if (busy) "Guardando…" else "Guardar cambio") }
        }
    }
}
