package com.example.stockpaniol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val controlMonths = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")

@Composable
internal fun MonthlyMaterialControl(item: StockItem, repository: StockRepository, modifier: Modifier = Modifier) {
    var workers by remember { mutableStateOf(emptyList<Worker>()) }
    var movements by remember { mutableStateOf(emptyList<StockMovement>()) }
    var loading by remember { mutableStateOf(true) }
    var error by rememberStockError()
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(item.id, revision) {
        loading = true; error = ""
        runCatching { withContext(Dispatchers.IO) { repository.personnel(true) to repository.movements(false) } }
            .onSuccess { workers = it.first; movements = it.second }
            .onFailure { error = "No se pudo cargar el control mensual." }
        loading = false
    }
    Column(modifier) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) {

            TextButton(onClick = { revision++ }) { Text("Reintentar") }
        } else if (!loading) MonthlyMaterialControlContent(item, workers, movements, Modifier.weight(1f))
    }
}

@Composable
internal fun MonthlyMaterialControlContent(item: StockItem, workers: List<Worker>, movements: List<StockMovement>,
    modifier: Modifier = Modifier) {
    var month by rememberSaveable(item.id) { mutableStateOf(todayStockDate().take(7)) }
    var query by rememberSaveable(item.id) { mutableStateOf("") }
    var choosingMonth by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable(item.id) { mutableStateOf(MonthlyMaterialSort.PERSONAL_ASC) }
    var sortMenu by remember { mutableStateOf(false) }
    val rows = remember(item.id, month, workers, movements, query, sort) { monthlyMaterialRows(item.id, month, workers, movements, query, sort) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { choosingMonth = true }, modifier = Modifier.weight(1f).testTag("control-month"),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
                Text("${controlMonths[month.takeLast(2).toInt() - 1]} ${month.take(4)} ▾", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedTextField(query, { query = it }, label = { Text("Buscar personal", style = MaterialTheme.typography.labelSmall) },
                singleLine = true, modifier = Modifier.weight(1f).testTag("control-search"))
        }
        Box {
            TextButton(onClick = { sortMenu = true }, modifier = Modifier.testTag("control-sort")) {
                Text("Ordenar: ${sort.label} ▾")
            }
            DropdownMenu(sortMenu, { sortMenu = false }) {
                MonthlyMaterialSort.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(option.label) }, onClick = { sort = option; sortMenu = false })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text("Personal", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text("Cantidad (${item.unit})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f).testTag("monthly-material-control"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(rows, key = { it.worker.id }) { row ->
                Row(Modifier.fillMaxWidth().background(monthlyMaterialAlert(row.quantity).color)
                    .testTag("control-worker-${row.worker.id}")
                    .semantics { stateDescription = "${formatStockAmount(row.quantity, item.unit)} ${if (row.quantity == 1.0) "recibido" else "recibidos"} en el mes" }
                    .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(row.worker.displayName + if (!row.worker.active) " (eliminado)" else "", Modifier.weight(1f), color = Color.Black)
                    Text(formatStockQuantity(row.quantity), Modifier.testTag("control-quantity-${row.worker.id}"), color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
            if (rows.isEmpty()) item { Text(if (query.isBlank()) "No hay personal registrado." else "No se encontró personal.") }
        }
    }
    if (choosingMonth) {
        var year by rememberSaveable { mutableIntStateOf(month.take(4).toInt()) }
        AlertDialog(onDismissRequest = { choosingMonth = false }, title = { Text("Seleccionar mes") }, text = {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = year > 1, onClick = { year-- }, modifier = Modifier.testTag("control-previous-year")) { Text("‹") }
                    Text(year.toString(), fontWeight = FontWeight.Bold)
                    TextButton(enabled = year < 9999, onClick = { year++ }, modifier = Modifier.testTag("control-next-year")) { Text("›") }
                }
                controlMonths.chunked(3).forEachIndexed { group, names ->
                    Row(Modifier.fillMaxWidth()) { names.forEachIndexed { index, name ->
                        TextButton(onClick = {
                            month = "%04d-%02d".format(java.util.Locale.ROOT, year, group * 3 + index + 1)
                            choosingMonth = false
                        }, modifier = Modifier.weight(1f)) { Text(name.take(3)) }
                    } }
                }
            }
        }, confirmButton = { TextButton(onClick = { choosingMonth = false }) { Text("Cerrar") } })
    }
}
