package com.example.stockpaniol

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MovementHistoryCards(rows: List<StockMovement>, statuses: Map<Long, MovementStatus>, tools: Boolean,
    busy: Boolean, onSelect: (Long) -> Unit, onDelete: (Long) -> Unit, modifier: Modifier = Modifier,
    newestFirst: Boolean = true, state: LazyListState = rememberLazyListState()) {
    val days = remember(rows, newestFirst) { groupMovementDays(rows, newestFirst) }
    LazyColumn(modifier.testTag("movement-history-cards"), state = state,
        verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
        days.forEach { day ->
            stickyHeader(key = "movement-date-${day.date}") {
                Box(Modifier.fillMaxWidth().testTag("movement-date-${day.date}")
                    .background(MaterialTheme.colorScheme.surface).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(displayStockDate(day.date), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    }
                }
            }
            items(day.movements, key = { it.id }) { movement ->
                MovementHistoryCard(movement, statuses.getValue(movement.id), tools, busy,
                    onSelect = { onSelect(movement.id) }, onDelete = { onDelete(movement.id) })
            }
        }
        if (rows.isEmpty()) item { Text("No hay movimientos con estos filtros.", Modifier.padding(16.dp)) }
    }
}

@Composable
internal fun MovementHistoryCard(movement: StockMovement, status: MovementStatus, tools: Boolean, busy: Boolean,
    onSelect: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("movement-card-${movement.id}").clickable(enabled = !busy, onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = movementBackgroundColor(status))) {
        Text(movement.itemName, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth()
            .background(cardTitleColor(movementBackgroundColor(status))).padding(horizontal = 12.dp, vertical = 8.dp))
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (tools) {
                    val codes = movement.units.map { it.unit.identifier }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                    Text("Código: ${codes.ifBlank { "—" }}")
                }
                Text(movement.kind)
                Text("Cantidad: ${formatStockAmount(movement.quantity, movement.unit)}")
                Text("Personal: ${movement.person.ifBlank { "—" }}")
                if (!tools) Text("Tipo de material: ${movement.materialType.ifBlank { "—" }}")
                Text(if (movement.kind == "Egreso") "Pendiente: ${formatStockAmount(movement.pending, movement.unit)}" else "Pendiente: —")
            }
            IconButton(enabled = !busy, onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete_image), "Eliminar movimiento ${movement.id}", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
