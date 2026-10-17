package com.example.stockpaniol

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PurchaseHistoryDialog(tools: Boolean, repository: StockRepository, onDismiss: () -> Unit) {
    var rows by remember { mutableStateOf(emptyList<StockPurchase>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var complete by rememberSaveable { mutableStateOf(true) }
    var filters by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchColumn by rememberSaveable { mutableStateOf("Todas") }
    var from by rememberSaveable { mutableStateOf("") }
    var until by rememberSaveable { mutableStateOf("") }
    var sortColumn by rememberSaveable { mutableStateOf(PurchaseColumn.DATE) }
    var descending by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(tools) {
        runCatching { withContext(Dispatchers.IO) { repository.purchases(tools) } }
            .onSuccess { rows = it }.onFailure { error = "No se pudieron cargar las compras." }
        loading = false
    }
    val columns = PurchaseColumn.entries
    val filtered = remember(rows, query, searchColumn, from, until, sortColumn, descending) {
        sortPurchases(rows.filter { p ->
            (from.isBlank() || p.date >= from) && (until.isBlank() || p.date <= until) &&
                (if (searchColumn == "Todas") columns.any { it.display(p).contains(query, true) || it.text(p).contains(query, true) }
                else PurchaseColumn.valueOf(searchColumn).let { it.display(p).contains(query, true) || it.text(p).contains(query, true) })
        }, sortColumn, descending)
    }
    StockOverlay(onDismiss) {
        Text("Registro de compras ${if (tools) "herramientas" else "materiales"}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { complete = !complete }) { Text(if (complete) "Vista completa" else "Vista sencilla") }
            TextButton(onClick = { filters = !filters }) { Text(if (filters) "Ocultar filtros" else "Filtros y orden") }
        }
        if (filters) {
            Column(Modifier.fillMaxWidth().heightIn(max = 230.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StockChoice("Buscar en", if (searchColumn == "Todas") "Todas las columnas" else PurchaseColumn.valueOf(searchColumn).label(tools),
                    listOf("Todas" to "Todas las columnas") + columns.map { it.name to it.label(tools) }) { searchColumn = it }
                OutlinedTextField(query, { query = it }, label = { Text("Texto a filtrar") }, trailingIcon = { ClearSearchButton(query) { query = "" } }, singleLine = true, modifier = Modifier.fillMaxWidth())
                StockDateField("Desde", from, allowClear = true) { from = it }
                StockDateField("Hasta", until, allowClear = true) { until = it }
                StockChoice("Ordenar por", sortColumn.label(tools), columns.map { it.name to it.label(tools) }) { sortColumn = PurchaseColumn.valueOf(it) }
                Row {
                    TextButton(onClick = { descending = !descending }) {
                        Text(when (sortColumn) {
                            PurchaseColumn.QUANTITY -> if (descending) "Mayor a menor ↓" else "Menor a mayor ↑"
                            PurchaseColumn.DATE -> if (descending) "Más recientes ↓" else "Más antiguas ↑"
                            else -> if (descending) "Z–A ↓" else "A–Z ↑"
                        })
                    }
                    TextButton(onClick = { query = ""; searchColumn = "Todas"; from = ""; until = "" }) { Text("Limpiar filtros") }
                }
            }
        }
        Text("${filtered.size} compras", style = MaterialTheme.typography.labelMedium)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        if (from.isNotBlank() && until.isNotBlank() && from > until) Text("La fecha desde debe ser anterior o igual a hasta.", color = MaterialTheme.colorScheme.error)
        if (complete) {
            Column(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                Row(Modifier.background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp)) {
                    columns.forEach { column ->
                        Text(column.label(tools) + if (column == sortColumn) { if (descending) " ↓" else " ↑" } else " ↕",
                            Modifier.width(column.width.dp).clickable {
                                descending = column == sortColumn && !descending; sortColumn = column
                            }.padding(vertical = 12.dp), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                LazyColumn {
                    items(filtered, key = { it.id }) { purchase ->
                        Row(Modifier.padding(horizontal = 8.dp)) {
                            columns.forEach { column -> Text(column.display(purchase), Modifier.width(column.width.dp).padding(vertical = 12.dp)) }
                        }
                        HorizontalDivider()
                    }
                    if (filtered.isEmpty() && !loading) item { Text("No hay compras con estos filtros.", Modifier.padding(16.dp)) }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(filtered, key = { it.id }) { purchase ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(purchase.itemName, fontWeight = FontWeight.Bold)
                            if (purchase.identitySummary.isNotBlank()) Text(purchase.identitySummary)
                            Text("Fecha: ${displayStockDate(purchase.date)}")
                            Text("${formatStockQuantity(purchase.quantity)} ${purchase.unit}")
                            Text("Empresa: ${purchase.company.ifBlank { "—" }}")
                            Text("Remito: ${purchase.receipt.ifBlank { "—" }}")
                        }
                    }
                }
                if (filtered.isEmpty() && !loading) item { Text("No hay compras con estos filtros.", Modifier.padding(16.dp)) }
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cerrar") }
    }
}
