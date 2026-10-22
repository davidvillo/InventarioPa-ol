package com.example.stockpaniol

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints

@Composable
internal fun stockCardTitleHeight(items: List<StockItem>, cardWidth: Dp): Dp {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    val pixels = with(density) { (cardWidth - 24.dp).roundToPx().coerceAtLeast(1) }
    val height = remember(items, pixels, style, density.fontScale) {
        items.maxOfOrNull { measurer.measure(AnnotatedString(it.name), style, constraints = Constraints(maxWidth = pixels)).size.height } ?: 0
    }
    return with(density) { height.toDp() }
}

@Composable
internal fun StockSummaryCard(item: StockItem, onPhoto: () -> Unit, onEdit: () -> Unit, tools: Boolean = true,
    onUnitClick: ((InventoryUnit) -> Unit)? = null, titleMinHeight: Dp = 0.dp, bundleLines: List<String> = emptyList(),
    stockAlertsEnabled: Boolean = false) {
    var expanded by rememberSaveable(item.id, tools) { mutableStateOf(false) }
    val tag = if (tools) "tool" else "material"
    val alert = if (stockAlertsEnabled) stockAlert(item, tools) else null
    val brands = if (item.individuallyTracked) item.units.filter { it.inActiveStock }.map { it.brand }.filter { it.isNotBlank() }.distinct().joinToString(", ") else item.brand
    val bodyHeight = with(LocalDensity.current) { MaterialTheme.typography.bodySmall.lineHeight.toDp() * 4 } + 80.dp
    Card(modifier = Modifier.fillMaxWidth().testTag("$tag-card-${item.id}").clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
        Text(item.name, color = Color.Black, modifier = Modifier.fillMaxWidth().background(alert?.color ?: cardTitleColor(Color.White))
            .semantics { if (alert != null) stateDescription = alert.label }
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)
            .heightIn(min = titleMinHeight).testTag("$tag-title-${item.id}")
            .clickable(onClickLabel = if (tools) "Abrir herramienta" else "Abrir material", onClick = onEdit),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().then(if (bundleLines.isEmpty()) Modifier.height(bodyHeight) else Modifier.heightIn(min = bodyHeight)).padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            StockPhoto(item.images.firstOrNull().orEmpty(), Modifier.size(72.dp).testTag("$tag-photo-${item.id}")
                .clickable(onClickLabel = "Ampliar imagen", onClick = onPhoto))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp).then(if (bundleLines.isEmpty()) Modifier.fillMaxHeight() else Modifier), verticalArrangement = Arrangement.Top) {
                Text("Marca: ${brands.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Observaciones: ${item.notes.ifBlank { "—" }}", modifier = Modifier.testTag("$tag-notes-${item.id}"), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                bundleLines.forEachIndexed { index, line ->
                    Text(line, modifier = Modifier.testTag("$tag-bundle-${item.id}-$index"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                if (item.individuallyTracked) TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("$tag-detail-${item.id}"),
                    contentPadding = PaddingValues(0.dp)) {
                    Text(if (expanded) "Ocultar detalle ▴" else "Ver ${item.units.size} ${if (item.units.size == 1) "unidad" else "unidades"} ▾",
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(Modifier.widthIn(min = 40.dp, max = 88.dp).testTag("$tag-quantity-${item.id}"), horizontalAlignment = Alignment.End) {
                Text(formatStockQuantity(item.quantity), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(stockUnitLabel(item.unit, item.quantity), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (expanded) {
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().padding(12.dp).testTag("$tag-units-${item.id}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Todas las unidades", fontWeight = FontWeight.SemiBold)
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
