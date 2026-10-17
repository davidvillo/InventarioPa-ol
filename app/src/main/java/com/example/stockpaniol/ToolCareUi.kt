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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ToolCareDialog(repository: StockRepository, trash: Boolean, onDismiss: () -> Unit, onChanged: () -> Unit,
    initialUnitId: Long? = null) {
    var records by remember { mutableStateOf(emptyList<StockItem>()) }
    var revision by remember { mutableIntStateOf(0) }
    var condition by rememberSaveable { mutableStateOf(if (trash) ToolCondition.RETIRED else ToolCondition.DAMAGED) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf<InventoryUnit?>(null) }
    var selectedAction by remember { mutableStateOf<ToolAction?>(null) }
    var error by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(revision) {
        runCatching { withContext(Dispatchers.IO) { repository.items(true) } }
            .onSuccess { records = it; error = "" }.onFailure { error = it.message ?: "No se pudieron cargar las herramientas." }
        loaded = true
    }
    val rows = records.flatMap { item -> item.units.filter { it.loanId == null &&
        (if (initialUnitId != null) it.id == initialUnitId else it.condition == condition) && it.rental?.closed != true }.map { item.name to it } }
        .filter { (name, unit) -> name.contains(query, true) || unit.label.contains(query, true) || unit.repairPlace.contains(query, true) }
    StockOverlay(onDismiss) {
        Text(if (trash) "Papelera de herramientas" else "Estado y reparación de herramientas", style = MaterialTheme.typography.titleLarge)
        if (!trash && initialUnitId == null) StockChoice("Estado", condition.label, ToolCondition.entries.filter { it != ToolCondition.RETIRED }.map { it.name to it.label }) {
            condition = ToolCondition.valueOf(it)
        }
        Text(if (trash) "Las bajas conservan su historial. Restaurar las devuelve a dañadas, sin sumarlas al stock disponible."
            else "Las herramientas dañadas o en reparación no están disponibles para entregar.", style = MaterialTheme.typography.bodySmall)
        if (initialUnitId == null) OutlinedTextField(query, { query = it }, label = { Text("Buscar herramienta, código o taller") }, trailingIcon = { ClearSearchButton(query) { query = "" } }, modifier = Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        if (!loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(rows, key = { it.second.id }) { (name, unit) ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(name, fontWeight = FontWeight.Bold)
                        Text(unit.label)
                        Text(unit.condition.label)
                        if (unit.damageNotes.isNotBlank()) Text("Daño: ${unit.damageNotes}")
                        if (unit.statusDate.isNotBlank()) Text("Último cambio: ${displayStockDate(unit.statusDate)}")
                        if (unit.repairPlace.isNotBlank()) Text("Lugar de reparación: ${unit.repairPlace}")
                        if (unit.repairDate.isNotBlank()) Text("Enviada: ${displayStockDate(unit.repairDate)}")
                        ToolAction.entries.filter { it.allows(unit.condition) }.forEach { action ->
                            TextButton(onClick = { selectedUnit = unit; selectedAction = action }) { Text(action.label) }
                        }
                    }
                }
            }
            if (loaded && rows.isEmpty()) item { Text("No hay herramientas en este estado.", Modifier.padding(12.dp)) }
        }
        TextButton(onClick = onDismiss) { Text("Cerrar") }
    }
    val unit = selectedUnit
    val action = selectedAction
    if (unit != null && action != null) ToolActionDialog(repository, unit, action,
        onDismiss = { selectedUnit = null; selectedAction = null },
        onSaved = { selectedUnit = null; selectedAction = null; revision++; onChanged() })
}

@Composable
private fun ToolActionDialog(repository: StockRepository, unit: InventoryUnit, action: ToolAction, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var date by rememberSaveable(unit.id, action) { mutableStateOf(todayStockDate()) }
    var notes by rememberSaveable(unit.id, action) { mutableStateOf("") }
    var workshop by rememberSaveable(unit.id, action) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    StockOverlay({ if (!busy) onDismiss() }) {
        Text(action.label, style = MaterialTheme.typography.titleLarge)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(unit.label, fontWeight = FontWeight.Bold)
            StockDateField(if (action == ToolAction.SEND_REPAIR) "Fecha de envío" else "Fecha del cambio", date, !busy) { date = it }
            if (action == ToolAction.SEND_REPAIR) OutlinedTextField(workshop, { workshop = it }, enabled = !busy,
                label = { Text("Lugar de reparación *") }, modifier = Modifier.fillMaxWidth())
            val label = when (action) {
                ToolAction.DAMAGE -> "Qué le pasó *"
                ToolAction.RETIRE -> "Motivo de baja *"
                ToolAction.RESTORE -> "Motivo de restauración *"
                else -> "Observaciones"
            }
            OutlinedTextField(notes, { notes = it }, enabled = !busy, label = { Text(label) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Text("Este cambio quedará registrado en Movimientos de herramientas.", style = MaterialTheme.typography.bodySmall)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") }
            Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { repository.changeToolState(unit.id, action, date, notes, workshop) } }
                    busy = false
                    result.onSuccess { onSaved() }.onFailure { error = it.message ?: "No se pudo guardar el cambio." }
                }
            }) { Text(if (busy) "Guardando…" else "Guardar cambio") }
        }
    }
}
