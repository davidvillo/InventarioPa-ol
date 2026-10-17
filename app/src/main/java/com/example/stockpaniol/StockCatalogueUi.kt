package com.example.stockpaniol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SameBrandCheckbox(checked: Boolean, enabled: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChecked),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null, enabled = enabled)
        Text("Todos tienen la misma marca")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExistingArticleField(value: String, items: List<StockItem>, enabled: Boolean,
    onQuery: (String) -> Unit, onSelect: (StockItem) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val matches = items.filter { matchesStockOption(it.name, value) || matchesStockOption(it.identitySummary, value) }
    ExposedDropdownMenuBox(expanded && enabled, { if (enabled) expanded = !expanded }) {
        OutlinedTextField(value, { onQuery(it); expanded = true }, enabled = enabled, singleLine = true,
            label = { Text("Artículo existente") }, placeholder = { Text("Escribí para buscar") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && enabled) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled))
        ExposedDropdownMenu(expanded && enabled, { expanded = false }, modifier = Modifier.heightIn(max = 240.dp)) {
            matches.forEach { item ->
                DropdownMenuItem(text = { Text(item.name) }, onClick = { onSelect(item); expanded = false })
            }
            if (matches.isEmpty()) DropdownMenuItem(text = { Text("Sin coincidencias") }, enabled = false, onClick = {})
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockCatalogueField(label: String, value: String, options: List<StockCatalogueOption>, enabled: Boolean = true, onValue: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = options.filter { it.active && matchesStockOption(it.name, value) }
    val showMenu = expanded && enabled && filtered.isNotEmpty()
    ExposedDropdownMenuBox(showMenu, { if (enabled) expanded = !expanded }) {
        OutlinedTextField(value, { onValue(it); expanded = true }, enabled = enabled, singleLine = true,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(showMenu) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled))
        ExposedDropdownMenu(showMenu, { expanded = false }, modifier = Modifier.heightIn(max = 240.dp)) {
            filtered.forEach { option ->
                DropdownMenuItem(text = { Text(option.name) }, onClick = { onValue(option.name); expanded = false })
            }
        }
    }
}

@Composable
fun StockCatalogueSettingsDialog(repository: StockCatalogueRepository, kind: StockCatalogueKind, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf(emptyList<StockCatalogueOption>()) }
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var form by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(kind, revision) {
        loading = true
        runCatching { withContext(Dispatchers.IO) { repository.stockOptions(kind) } }
            .onSuccess { options = it }.onFailure { error = "No se pudo cargar la lista." }
        loading = false
    }
    StockOverlay({ if (!busy) onDismiss() }) {
        Text(kind.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Cambiar o eliminar una opción no modifica los datos ya guardados.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(search, { search = it }, label = { Text("Buscar en ${kind.title.lowercase()}") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        val filtered = options.filter { matchesStockOption(it.name, search) }
        LazyColumn(Modifier.weight(1f).testTag("catalogue-list"), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { option ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(option.name, fontWeight = FontWeight.SemiBold)
                        Row {
                            TextButton(enabled = !busy, modifier = Modifier.testTag("modify-catalogue-${option.id}"), onClick = {
                                editId = option.id; name = option.name; form = true; error = ""
                            }) { Text("Modificar") }
                            TextButton(enabled = !busy, modifier = Modifier.testTag("remove-catalogue-${option.id}"), onClick = { deleteId = option.id; error = "" }) { Text("Eliminar") }
                        }
                    }
                }
            }
            if (filtered.isEmpty() && !loading) item { Text(if (options.isEmpty()) "Todavía no hay opciones registradas." else "No hay coincidencias.") }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cerrar") }
            Button(enabled = !busy && !loading, onClick = { editId = null; name = ""; form = true; error = "" }) { Text("Agregar ${kind.singular}") }
        }
    }
    if (form) AlertDialog(onDismissRequest = { if (!busy) form = false }, title = { Text("${if (editId == null) "Agregar" else "Modificar"} ${kind.singular}") },
        text = { Column {
            OutlinedTextField(name, { name = it }, enabled = !busy, label = { Text("Nombre de la ${kind.singular} *") }, singleLine = true)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        } }, confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            val target = editId
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching {
                    if (target == null) repository.addStockOption(kind, name) else repository.updateStockOption(kind, target, name)
                } }
                busy = false
                result.onSuccess { form = false; error = ""; revision++ }.onFailure { error = it.message ?: "No se pudo guardar la opción." }
            }
        }) { Text("Guardar ${kind.singular}") } }, dismissButton = { TextButton(enabled = !busy, onClick = { form = false; error = "" }) { Text("Cancelar") } })
    options.firstOrNull { it.id == deleteId }?.let { option ->
        AlertDialog(onDismissRequest = { if (!busy) deleteId = null }, title = { Text("Eliminar ${kind.singular}") },
            text = { Text("¿Eliminar ${option.name} de las opciones? Los artículos y compras ya guardados conservan sus datos.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { repository.deleteStockOption(kind, option.id) } }
                    busy = false; deleteId = null
                    result.onSuccess { error = ""; revision++ }.onFailure { error = it.message ?: "No se pudo eliminar la opción." }
                }
            }) { Text("Eliminar de la lista") } }, dismissButton = { TextButton(enabled = !busy, onClick = { deleteId = null }) { Text("Cancelar") } })
    }
}
