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
fun ToolRentalsDialog(repository: StockRepository, onDismiss: () -> Unit, onChanged: () -> Unit) {
    var records by remember { mutableStateOf(emptyList<StockItem>()) }
    var revision by remember { mutableIntStateOf(0) }
    var closed by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<InventoryUnit?>(null) }
    var historyCode by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(revision) {
        runCatching { withContext(Dispatchers.IO) { repository.items(true) } }
            .onSuccess { records = it; error = "" }.onFailure { error = it.message ?: "No se pudieron cargar los alquileres." }
        loaded = true
    }
    val rows = records.flatMap { item -> item.units.filter { it.rental?.closed == closed }.map { item.name to it } }
        .filter { (name, unit) -> listOf(name, unit.label, unit.rental?.reference.orEmpty()).any { it.contains(query, true) } }
    StockOverlay(onDismiss) {
        Text("Herramientas alquiladas", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!closed, { closed = false }, label = { Text("Alquileres vigentes") })
            FilterChip(closed, { closed = true }, label = { Text("Devueltos al proveedor") })
        }
        OutlinedTextField(query, { query = it }, label = { Text("Buscar herramienta, proveedor o contrato") }, trailingIcon = { ClearSearchButton(query) { query = "" } }, modifier = Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        if (!loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(rows, key = { it.second.id }) { (name, unit) ->
                val rental = requireNotNull(unit.rental)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(name, fontWeight = FontWeight.Bold)
                        Text(unit.label)
                        Text(unit.statusLabel)
                        Text("Inicio del alquiler: ${displayStockDate(rental.startDate)}")
                        if (rental.reference.isNotBlank()) Text("Remito / contrato: ${rental.reference}")
                        if (closed) {
                            Text("Devuelta al proveedor: ${displayStockDate(rental.returnedDate)}")
                            Text("Estado al devolver: ${unit.condition.label}")
                            if (rental.returnNotes.isNotBlank()) Text("Observaciones: ${rental.returnNotes}")
                        } else {
                            TextButton(enabled = unit.loanId == null && unit.condition != ToolCondition.IN_REPAIR,
                                onClick = { selected = unit }) { Text("Devolver al proveedor") }
                            if (unit.loanId != null) Text("Primero debe volver del personal al pañol.", style = MaterialTheme.typography.bodySmall)
                            if (unit.condition == ToolCondition.IN_REPAIR) Text("Primero debe regresar de la reparación.", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { historyCode = unit.identifier }) { Text("Ver movimientos del alquiler") }
                    }
                }
            }
            if (loaded && rows.isEmpty()) item { Text("No hay alquileres en esta vista.", Modifier.padding(12.dp)) }
        }
        TextButton(onClick = onDismiss) { Text("Cerrar") }
    }
    selected?.let { unit -> ReturnRentalDialog(repository, unit, { selected = null }, {
        selected = null; revision++; onChanged()
    }) }
    historyCode?.let { code -> key(code) {
        MovementHistoryDialog(true, repository, { historyCode = null }, { revision++; onChanged() }, initialQuery = code)
    } }
}

@Composable
private fun ReturnRentalDialog(repository: StockRepository, unit: InventoryUnit, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var date by rememberSaveable(unit.id) { mutableStateOf(todayStockDate()) }
    var notes by rememberSaveable(unit.id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    StockOverlay({ if (!busy) onDismiss() }) {
        Text("Devolver al proveedor", style = MaterialTheme.typography.titleLarge)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(unit.label, fontWeight = FontWeight.Bold)
            Text("Estado: ${unit.condition.label}")
            StockDateField("Fecha de devolución al proveedor", date, !busy) { date = it }
            OutlinedTextField(notes, { notes = it }, enabled = !busy,
                label = { Text(if (unit.condition == ToolCondition.OPERATIVE) "Observaciones de entrega" else "Estado de entrega / observaciones *") },
                minLines = 3, modifier = Modifier.fillMaxWidth())
            Text("Cierra el alquiler de esta unidad y la retira del stock disponible. Su historial se conserva en Movimientos de herramientas.", style = MaterialTheme.typography.bodySmall)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") }
            Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { repository.returnRental(unit.id, date, notes) } }
                    busy = false
                    result.onSuccess { onSaved() }.onFailure { error = it.message ?: "No se pudo cerrar el alquiler." }
                }
            }) { Text(if (busy) "Guardando…" else "Confirmar devolución al proveedor") }
        }
    }
}
