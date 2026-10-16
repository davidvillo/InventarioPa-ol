package com.example.stockpaniol

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ToolSummaryCard(item: StockItem, onPhoto: () -> Unit, onEdit: () -> Unit) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val preview = item.units.firstOrNull { it.inActiveStock } ?: item.units.firstOrNull()
    Card(modifier = Modifier.fillMaxWidth().testTag("tool-card-${item.id}").clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.fillMaxWidth().height(96.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StockPhoto(item.images.firstOrNull().orEmpty(), Modifier.size(72.dp)
                .clickable(onClickLabel = "Ampliar imagen", onClick = onPhoto))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.name, modifier = Modifier.clickable(onClickLabel = "Editar herramienta", onClick = onEdit),
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Column(Modifier.fillMaxWidth().testTag("tool-detail-${item.id}")
                    .clickable(onClickLabel = if (expanded) "Ocultar unidades" else "Ver todas las unidades") { expanded = !expanded }) {
                    if (preview != null) ToolUnitBullet(preview, maxLines = 2)
                    else Text("Sin unidades registradas", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (expanded) "Ocultar detalle ▴" else "Ver ${item.units.size} ${if (item.units.size == 1) "unidad" else "unidades"} ▾",
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatStockQuantity(item.quantity), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(item.unit, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (expanded) {
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().padding(12.dp).testTag("tool-units-${item.id}")
                .clickable(onClickLabel = "Ocultar unidades") { expanded = false }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${item.name} · Todas las unidades", fontWeight = FontWeight.SemiBold)
                item.units.forEach { ToolUnitBullet(it) }
                if (item.units.isEmpty()) Text("Sin unidades registradas", style = MaterialTheme.typography.bodySmall)
                Text("Ocultar detalle ▴", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ToolUnitBullet(unit: InventoryUnit, maxLines: Int = Int.MAX_VALUE) {
    Row(verticalAlignment = Alignment.Top) {
        Text("•", color = Color.Black, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 6.dp))
        Text(listOf(unit.identifier, unit.brand, unit.statusLabel,
            unit.bundleName.takeIf { it.isNotBlank() }?.let { "Conjunto: $it" }.orEmpty(),
            unit.rental?.let { "Alquilada a ${it.supplier}" }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}
