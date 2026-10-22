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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PersonnelChoice(label: String, workers: List<Worker>, selectedId: Long, enabled: Boolean = true, onSelect: (Long) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun closePicker() { focus.clearFocus(force = true); keyboard?.hide(); open = false }
    OutlinedButton(enabled = enabled, onClick = { search = ""; open = true }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(workers.firstOrNull { it.id == selectedId }?.selectionLabel ?: "Seleccionar personal ▾")
        }
    }
    if (open) StockOverlay({ closePicker() }) {
        Text(label, style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(search, { search = it }, label = { Text("Buscar nombre, apellido o apodo") }, trailingIcon = { ClearSearchButton(search) { search = "" } },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        val collator = remember { java.text.Collator.getInstance(java.util.Locale.forLanguageTag("es-AR")).apply { strength = java.text.Collator.PRIMARY } }
        val filtered = workers.filter { it.selectionLabel.contains(search.trim(), true) }
            .sortedWith(compareBy(collator) { worker: Worker -> worker.name }.thenBy(collator) { worker: Worker -> worker.surname }.thenBy { it.id })
        LazyColumn(Modifier.weight(1f)) {
            items(filtered, key = { it.id }) { worker ->
                TextButton(onClick = { closePicker(); onSelect(worker.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(worker.selectionLabel, Modifier.fillMaxWidth())
                }
            }
            if (filtered.isEmpty()) item { Text("No se encontró personal con esa búsqueda.", Modifier.padding(12.dp)) }
        }
        TextButton(onClick = { closePicker() }) { Text("Cancelar") }
    }
}

@Composable
fun PersonnelDialog(repository: PersonnelRepository, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var workers by remember { mutableStateOf(emptyList<Worker>()) }
    var worksites by remember { mutableStateOf(emptyList<Worksite>()) }
    var name by rememberSaveable { mutableStateOf("") }
    var surname by rememberSaveable { mutableStateOf("") }
    var worksiteId by rememberSaveable { mutableLongStateOf(0L) }
    var nickname by rememberSaveable { mutableStateOf("") }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var form by rememberSaveable { mutableStateOf(false) }
    var manageWorksites by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by rememberStockError()
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(revision) {
        runCatching { withContext(Dispatchers.IO) { repository.personnel() to repository.worksites(true) } }
            .onSuccess { workers = it.first; worksites = it.second; error = "" }
            .onFailure { error = "No se pudieron cargar el personal y las obras." }
        loading = false
    }
    fun openForm(worker: Worker?) {
        editId = worker?.id
        name = worker?.name.orEmpty(); surname = worker?.surname.orEmpty()
        worksiteId = worker?.assignment?.id ?: 0L; nickname = worker?.nickname.orEmpty()
        error = ""; form = true
    }
    val current = workers.firstOrNull { it.id == editId }
    val choices = worksites.filter { it.active || it.id == current?.assignment?.id }
    StockOverlay({ if (!busy) onDismiss() }) {
        Text(if (form && editId != null) "Editar personal" else "Personal de obra", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (form) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, enabled = !busy, label = { Text("Nombre *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(surname, { surname = it }, enabled = !busy, label = { Text("Apellido *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                StockChoice("Obra *", choices.firstOrNull { it.id == worksiteId }?.name.orEmpty(),
                    choices.map { it.id.toString() to (it.name + if (it.active) "" else " (quitada de la lista)") }, !busy && !loading) { worksiteId = it.toLong() }
                TextButton(enabled = !busy, onClick = { manageWorksites = true }) { Text("Administrar opciones de obra") }
                if (choices.isEmpty()) Text("Agregá una opción de obra para registrar personal.")
                OutlinedTextField(nickname, { nickname = it }, enabled = !busy, label = { Text("Apodo") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(enabled = !busy, onClick = { form = false; error = "" }) { Text("Cancelar") }
                Button(enabled = !busy && !loading, onClick = {
                    busy = true
                    val draft = WorkerDraft(name, surname, worksiteId, nickname)
                    val targetId = editId
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching {
                            if (targetId == null) repository.addWorker(draft) else repository.updateWorker(targetId, draft)
                        } }
                        busy = false
                        result.onSuccess { form = false; error = ""; revision++ }
                            .onFailure { error = it.message ?: "No se pudo guardar el personal." }
                    }
                }) { Text(if (busy) "Guardando…" else "Guardar personal") }
            }
        } else {
            OutlinedTextField(search, { search = it }, label = { Text("Buscar nombre, apodo u obra") }, trailingIcon = { ClearSearchButton(search) { search = "" } }, singleLine = true, modifier = Modifier.fillMaxWidth())

            val filtered = workers.filter { it.selectionLabel.contains(search, true) }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                items(filtered, key = { it.id }) { worker ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(worker.displayName, fontWeight = FontWeight.SemiBold)
                            Text("Obra: ${worker.worksite}")
                            Row {
                                TextButton(enabled = !busy, onClick = { openForm(worker) }) { Text("Editar") }
                                TextButton(enabled = !busy, onClick = { deleteId = worker.id }) { Text("Eliminar") }
                            }
                        }
                    }
                }
                if (filtered.isEmpty() && !loading) item { Text(if (workers.isEmpty()) "Todavía no hay personal registrado." else "No hay personal con esta búsqueda.") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(enabled = !busy, onClick = onDismiss) { Text("Cerrar") }
                Button(enabled = !busy && !loading, onClick = { openForm(null) }) { Text("Agregar personal") }
            }
        }
    }
    workers.firstOrNull { it.id == deleteId }?.let { worker ->
        AlertDialog(onDismissRequest = { if (!busy) deleteId = null }, title = { Text("Eliminar personal") }, text = {
            Text("¿Eliminar a ${worker.displayName}? Se quitará de las nuevas entregas. Sus movimientos anteriores y devoluciones pendientes se conservan.")
        }, confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { repository.deleteWorker(worker.id) } }
                busy = false; deleteId = null
                result.onSuccess { error = ""; revision++ }.onFailure { error = it.message ?: "No se pudo eliminar el personal." }
            }
        }) { Text("Eliminar") } }, dismissButton = { TextButton(enabled = !busy, onClick = { deleteId = null }) { Text("Cancelar") } })
    }
    if (manageWorksites) WorksitesDialog(repository, { manageWorksites = false; revision++ })
}

@Composable
fun WorksitesDialog(repository: PersonnelRepository, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf(emptyList<Worksite>()) }
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var form by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by rememberStockError()
    LaunchedEffect(revision) {
        runCatching { withContext(Dispatchers.IO) { repository.worksites() } }.onSuccess { rows = it }
            .onFailure { error = "No se pudieron cargar las obras." }
        loading = false
    }
    StockOverlay({ if (!busy) onDismiss() }) {
        Text("Obras", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Opciones de la lista desplegable de personal.", style = MaterialTheme.typography.bodySmall)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        LazyColumn(Modifier.weight(1f).testTag("worksites-list"), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            items(rows, key = { it.id }) { site ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(site.name, fontWeight = FontWeight.SemiBold)
                        Row {
                            TextButton(modifier = Modifier.testTag("modify-worksite-${site.id}"), enabled = !busy, onClick = { editId = site.id; name = site.name; error = ""; form = true }) { Text("Modificar") }
                            TextButton(modifier = Modifier.testTag("remove-worksite-${site.id}"), enabled = !busy, onClick = { deleteId = site.id; error = "" }) { Text("Quitar") }
                        }
                    }
                }
            }
            if (rows.isEmpty() && !loading) item { Text("No hay opciones. Agregá una obra para poder seleccionarla.") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cerrar") }
            Button(enabled = !busy && !loading, onClick = { editId = null; name = ""; error = ""; form = true }) { Text("Agregar opción") }
        }
    }
    if (form) AlertDialog(onDismissRequest = { if (!busy) form = false }, title = { Text(if (editId == null) "Agregar opción de obra" else "Modificar opción de obra") }, text = {
        Column {
            OutlinedTextField(name, { name = it }, enabled = !busy, label = { Text("Nombre de la opción *") }, singleLine = true)

        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = {
        busy = true
        val targetId = editId
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { if (targetId == null) repository.addWorksite(name) else repository.updateWorksite(targetId, name) } }
            busy = false
            result.onSuccess { form = false; error = ""; revision++ }.onFailure { error = it.message ?: "No se pudo guardar la opción." }
        }
    }) { Text("Guardar opción") } }, dismissButton = { TextButton(enabled = !busy, onClick = { form = false; error = "" }) { Text("Cancelar") } })
    rows.firstOrNull { it.id == deleteId }?.let { site ->
        AlertDialog(onDismissRequest = { if (!busy) deleteId = null }, title = { Text("Quitar opción de obra") }, text = {
            Text("¿Quitar ${site.name} de la lista? El personal que ya tiene esta obra conserva su asignación hasta que la cambies.")
        }, confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { repository.deleteWorksite(site.id) } }
                busy = false; deleteId = null
                result.onSuccess { error = ""; revision++ }.onFailure { error = it.message ?: "No se pudo quitar la opción." }
            }
        }) { Text("Quitar") } }, dismissButton = { TextButton(enabled = !busy, onClick = { deleteId = null }) { Text("Cancelar") } })
    }
}
