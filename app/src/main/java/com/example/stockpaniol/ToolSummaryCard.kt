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
internal fun StockSummaryCard(item: StockItem, onPhoto: () -> Unit, onEdit: () -> Unit, tools: Boolean = true,
    onUnitClick: ((InventoryUnit) -> Unit)? = null) {
    var expanded by rememberSaveable(item.id, tools) { mutableStateOf(false) }
    val tag = if (tools) "tool" else "material"
    Card(modifier = Modifier.fillMaxWidth().testTag("$tag-card-${item.id}").clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.fillMaxWidth().height(112.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StockPhoto(item.images.firstOrNull().orEmpty(), Modifier.size(72.dp)
                .clickable(onClickLabel = "Ampliar imagen", onClick = onPhoto))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Text(item.name, modifier = Modifier.clickable(onClickLabel = if (tools) "Editar herramienta" else "Abrir material", onClick = onEdit),
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("$tag-detail-${item.id}"),
                    contentPadding = PaddingValues(0.dp)) {
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
            Column(Modifier.fillMaxWidth().padding(12.dp).testTag("$tag-units-${item.id}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${item.name} · Todas las unidades", fontWeight = FontWeight.SemiBold)
                item.units.forEach { unit ->
                    val actionable = unit.loanId != null || (tools && unit.condition == ToolCondition.IN_REPAIR)
                    val modifier = if (actionable && onUnitClick != null) Modifier
                        .testTag("$tag-unit-${unit.id}")
                        .clickable(onClickLabel = if (unit.loanId != null) "Cargar devolución" else "Estado y reparación") { onUnitClick(unit) }
                        .padding(vertical = 6.dp) else Modifier
                    StockUnitBullet(unit, modifier, color = if (actionable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                if (item.units.isEmpty()) Text("Sin unidades registradas", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { expanded = false }, contentPadding = PaddingValues(0.dp)) { Text("Ocultar detalle ▴") }
            }
        }
    }
}

@Composable
private fun StockUnitBullet(unit: InventoryUnit, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE,
    color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Text("•", color = color, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 6.dp))
        Text(listOf(unit.identifier, unit.brand, unit.statusLabel,
            unit.bundleName.takeIf { it.isNotBlank() }?.let { "Conjunto: $it" }.orEmpty(),
            unit.rental?.let { "Alquilada a ${it.supplier}" }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
            color = color, style = MaterialTheme.typography.bodySmall, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}
