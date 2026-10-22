package com.example.stockpaniol

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ToolEditorSession {
    var item by mutableStateOf<StockItem?>(null)
    var draft by mutableStateOf<ToolEdit?>(null)
    var sameBrand by mutableStateOf(false)
}

@Composable
internal fun ToolEditDialog(itemId: Long, repository: StockRepository, onDismiss: () -> Unit,
    onSaved: () -> Unit, embedded: Boolean = false, onBusyChanged: (Boolean) -> Unit = {},
    session: ToolEditorSession = remember(itemId) { ToolEditorSession() }) {
    val scope = rememberCoroutineScope()
    var item by session::item
    var draft by session::draft
    var busy by remember { mutableStateOf(false) }
    var error by rememberStockError()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var brands by remember { mutableStateOf(emptyList<StockCatalogueOption>()) }
    var companies by remember { mutableStateOf(emptyList<StockCatalogueOption>()) }
    var sameBrand by session::sameBrand
    SideEffect { onBusyChanged(busy) }
    DisposableEffect(Unit) { onDispose { onBusyChanged(false) } }
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { repository.stockOptions(StockCatalogueKind.BRAND) to repository.stockOptions(StockCatalogueKind.COMPANY) } }
            .onSuccess { brands = it.first; companies = it.second }
    }
    LaunchedEffect(itemId) {
        if (draft != null) return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) {
            val stock = repository.items(true).first { it.id == itemId }
            stock to ToolEdit(stock.name, stock.notes, stock.company, stock.images,
                stock.units.map { ToolUnitEdit(it.id, it.brand, it.identifier) },
                repository.purchases(true).filter { it.itemId == itemId }.map { ToolPurchaseEdit(it.id, it.date, it.receipt, it.company) })
        } }.onSuccess { item = it.first; draft = it.second }
            .onFailure { error = "No se pudo cargar la herramienta. Cerrá la ventana y volvé a intentar." }
    }
    fun save() {
        val edit = draft ?: return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.updateTool(itemId, edit) } }
            busy = false
            result.onSuccess { onSaved() }.onFailure { error = it.message ?: "No se pudieron guardar los cambios." }
        }
    }
    StockPanel(embedded, onDismiss = { if (!busy) onDismiss() }) {
        if (!embedded) Text("Editar herramienta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val edit = draft
            if (edit != null) {
                item {
                    OutlinedTextField(edit.name, { draft = edit.copy(name = it) }, enabled = !busy,
                        label = { Text("Nombre *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Disponible: ${formatStockAmount(item?.quantity ?: 0.0, item?.unit.orEmpty())}")
                    Text("La cantidad se actualiza con los ingresos y egresos.", style = MaterialTheme.typography.bodySmall)
                    StockCatalogueField("Empresa a la que se compró", edit.company, companies, !busy) { draft = edit.copy(company = it) }
                    OutlinedTextField(edit.notes, { draft = edit.copy(notes = it) }, enabled = !busy,
                        label = { Text("Observaciones") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    if (edit.units.isNotEmpty()) SameBrandCheckbox(sameBrand, !busy) { checked ->
                            sameBrand = checked
                            if (checked) draft = edit.copy(units = edit.units.map { it.copy(brand = edit.units.firstOrNull()?.brand.orEmpty()) })
                    }
                    if (sameBrand) StockCatalogueField("Marca de todas las unidades (opcional)", edit.units.firstOrNull()?.brand.orEmpty(), brands, !busy) { value ->
                        draft = edit.copy(units = edit.units.map { it.copy(brand = value) })
                    }
                }
                items(edit.units, key = { "unit-${it.id}" }) { unit ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Unidad ${item?.units?.indexOfFirst { it.id == unit.id }?.plus(1)}", fontWeight = FontWeight.Bold)
                        Text(item?.units?.firstOrNull { it.id == unit.id }?.statusLabel.orEmpty())
                        if (!sameBrand) StockCatalogueField("Marca de la unidad", unit.brand, brands, !busy) { value ->
                            draft = edit.copy(units = edit.units.map { if (it.id == unit.id) it.copy(brand = value) else it })
                        }
                        OutlinedTextField(unit.identifier, { value -> draft = edit.copy(units = edit.units.map { if (it.id == unit.id) it.copy(identifier = value) else it }) },
                            enabled = !busy, label = { Text("Identificador de la unidad") }, modifier = Modifier.fillMaxWidth())
                        item?.units?.firstOrNull { it.id == unit.id }?.rental?.let { rental ->
                            Text("Alquilada a: ${rental.supplier} · ${displayStockDate(rental.startDate)}")
                        }
                    }
                }
                items(edit.purchases, key = { "purchase-${it.id}" }) { purchase ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ingreso #${purchase.id}", fontWeight = FontWeight.Bold)
                        StockDateField("Fecha de adquisición", purchase.date, !busy) { value ->
                            draft = edit.copy(purchases = edit.purchases.map { if (it.id == purchase.id) it.copy(date = value) else it })
                        }
                        OutlinedTextField(purchase.receipt, { value -> draft = edit.copy(purchases = edit.purchases.map { if (it.id == purchase.id) it.copy(receipt = value) else it }) },
                            enabled = !busy, label = { Text("Número de remito") }, modifier = Modifier.fillMaxWidth())
                        StockCatalogueField("Empresa del ingreso", purchase.company, companies, !busy) { value ->
                            draft = edit.copy(purchases = edit.purchases.map { if (it.id == purchase.id) it.copy(company = value) else it })
                        }
                    }
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        edit.images.forEach { photo ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                StockPhoto(photo)
                                TextButton(enabled = !busy, onClick = { draft = edit.copy(images = edit.images - photo) }) { Text("Quitar") }
                            }
                        }
                    }
                    StockImagePicker(enabled = !busy && edit.images.size < StockPhotos.LIMIT, onSelected = {
                        draft = edit.copy(images = (edit.images + it.toString()).distinct())
                    }, onError = { error = it }) { open ->
                        TextButton(enabled = !busy && edit.images.size < StockPhotos.LIMIT, onClick = open) { Text("Agregar foto (${edit.images.size}/${StockPhotos.LIMIT})") }
                    }
                    TextButton(enabled = !busy, onClick = { confirmDelete = true }) {
                        Text("Eliminar herramienta", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else if (error.isBlank()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") }
            Button(enabled = !busy && draft != null, onClick = { save() }) { Text(if (busy) "Guardando…" else "Guardar cambios") }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { if (!busy) confirmDelete = false },
        title = { Text("¿Eliminar herramienta?") },
        text = { Text("Se eliminará ${item?.name.orEmpty()} con todas sus unidades, ingresos y compras. Esta acción no se puede deshacer.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { repository.deleteTool(itemId) } }
                busy = false
                confirmDelete = false
                result.onSuccess { onSaved() }.onFailure { error = it.message ?: "No se pudo eliminar la herramienta." }
            }
        }) { Text("Eliminar definitivamente") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text("Cancelar") } })
}
