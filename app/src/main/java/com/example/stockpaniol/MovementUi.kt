package com.example.stockpaniol

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Calendar
import java.util.Locale

@Composable
fun StockOverlay(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.8f), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockChoice(label: String, value: String, choices: List<Pair<String, String>>, enabled: Boolean = true, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded && enabled, { if (enabled) expanded = !expanded }) {
        OutlinedTextField(value, {}, label = { Text(label) }, readOnly = true, enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled))
        ExposedDropdownMenu(expanded && enabled, { expanded = false }) {
            choices.forEach { (id, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; onSelect(id) }) }
        }
    }
}

@Composable
fun StockDateField(label: String, date: String, enabled: Boolean = true, allowClear: Boolean = false, onDate: (String) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(enabled = enabled, modifier = Modifier.weight(1f), onClick = {
            val calendar = Calendar.getInstance()
            if (date.isNotBlank()) {
                val parts = date.split("-")
                calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
            DatePickerDialog(context, { _, year, month, day ->
                onDate(String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }) { Text("$label: ${if (date.isBlank()) "Todas" else displayStockDate(date)}") }
        if (allowClear && date.isNotBlank()) TextButton(enabled = enabled, onClick = { onDate("") }) { Text("Quitar") }
    }
}

@Composable
fun IssueDialog(tools: Boolean, initialId: Long, repository: StockRepository, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf(emptyList<StockItem>()) }
    var workers by remember { mutableStateOf(emptyList<Worker>()) }
    var selectedId by rememberSaveable { mutableLongStateOf(initialId) }
    var workerId by rememberSaveable { mutableLongStateOf(0L) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var selectedUnits by rememberSaveable { mutableStateOf(longArrayOf()) }
    var editUnit by remember { mutableStateOf<InventoryUnit?>(null) }
    var date by rememberSaveable { mutableStateOf(todayStockDate()) }
    var notes by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(1) }
    var personal by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(revision) {
        runCatching { withContext(Dispatchers.IO) { repository.items(tools) to repository.personnel() } }
            .onSuccess { records = it.first; workers = it.second }
            .onFailure { error = "No se pudieron cargar los artículos y el personal." }
        loading = false
    }
    val selected = records.firstOrNull { it.id == selectedId }
    StockOverlay(onDismiss = { if (!busy) onDismiss() }) {
        Text(selected?.name ?: if (tools) "Herramienta" else "Material", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        TabRow(tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Detalle") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Egreso") })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (tab == 0) {
                selected?.let {
                    Text("Disponible: ${formatStockQuantity(it.quantity)} ${it.unit}")
                    if (!tools) Text("Tipo: ${it.materialType}")
                    if (tools) { Text("Marcas: ${it.units.map { u -> u.brand }.filter { b -> b.isNotBlank() }.distinct().joinToString(", ").ifBlank { "—" }}"); Text("Empresa: ${it.company.ifBlank { "—" }}") }
                    Text("Observaciones: ${it.notes.ifBlank { "—" }}")
                    if (it.individuallyTracked) {
                        Text("Unidades identificadas", fontWeight = FontWeight.Bold)
                        it.units.filter { unit -> unit.inActiveStock }.forEach { unit ->
                            Text("${unit.label} · ${unit.statusLabel}")
                            if (unit.damageNotes.isNotBlank()) Text("Daño: ${unit.damageNotes}")
                            if (unit.legacy) Text("Unidad de stock anterior: verificá marca y etiqueta.", style = MaterialTheme.typography.bodySmall)
                            TextButton(enabled = !busy, onClick = { editUnit = unit }) { Text("Editar marca / identificador") }
                        }
                    }
                }
            } else {
                StockChoice(if (tools) "Herramienta" else "Material", selected?.name.orEmpty(), records.map { it.id.toString() to "${it.name} · ${formatStockQuantity(it.quantity)} ${it.unit}" }, !busy) {
                    selectedId = it.toLong(); quantity = ""; selectedUnits = longArrayOf(); error = ""
                }
                Text("Disponible: ${formatStockQuantity(selected?.quantity ?: 0.0)} ${selected?.unit.orEmpty()}")
                if (selected?.individuallyTracked == true) {
                    Text("Marcá cuáles se entregan", fontWeight = FontWeight.Bold)
                    selected.availableUnits.forEach { unit ->
                        UnitSelection(unit.label + if (unit.legacy) " · Verificar etiqueta histórica" else "", unit.id in selectedUnits, !busy) {
                            selectedUnits = if (unit.id in selectedUnits) selectedUnits.filter { it != unit.id }.toLongArray() else selectedUnits + unit.id
                        }
                    }
                    Text("Cantidad seleccionada: ${selectedUnits.size}")
                    if (tools) {
                        val groups = selected.units.filter { it.id in selectedUnits }.mapNotNull { it.bundleId }.toSet()
                        val companions = records.flatMap { item -> item.units.filter { it.bundleId in groups && it.id !in selectedUnits }.map { item.name to it } }
                        if (companions.isNotEmpty()) {
                            Text("También se prestan con el conjunto:", fontWeight = FontWeight.Bold)
                            companions.forEach { (name, unit) -> Text("$name · ${unit.label} · ${unit.statusLabel}") }
                        }
                    }
                } else OutlinedTextField(quantity, { quantity = it; error = "" }, enabled = !busy, label = { Text("Cantidad (${selected?.unit.orEmpty()})") },
                    keyboardOptions = KeyboardOptions(keyboardType = if (selected?.wholeNumbers == true) KeyboardType.Number else KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                StockDateField("Fecha del movimiento", date, !busy) { date = it }
                PersonnelChoice("Personal que recibe", workers, workerId, !busy) { workerId = it }
                TextButton(enabled = !busy, onClick = { personal = true }) { Text("Agregar personal de obra") }
                if (workers.isEmpty()) Text("Registrá primero a la persona que recibe.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(notes, { notes = it }, enabled = !busy, label = { Text("Observaciones") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cerrar") }
            if (tab == 1) Button(enabled = !busy && !loading && selected != null, onClick = {
                val number = if (selected?.individuallyTracked == true) selectedUnits.size.toDouble() else quantity.trim().replace(',', '.').toDoubleOrNull()
                error = runCatching {
                    require(number != null) { "Ingresá una cantidad válida." }
                    validateMovementQuantity(number, selected!!.unit, selected.wholeNumbers)
                    require(number <= selected.quantity) { "La cantidad supera el stock disponible (${formatStockQuantity(selected.quantity)} ${selected.unit})." }
                    require(workerId != 0L) { "Seleccioná la persona que recibe." }
                }.exceptionOrNull()?.message.orEmpty()
                if (error.isBlank() && number != null) {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { repository.issue(tools, selectedId, number, date, workerId, notes, selectedUnits.toList()) } }
                        busy = false
                        result.onSuccess { onSaved() }.onFailure { error = it.message ?: "No se pudo guardar el egreso."; revision++ }
                    }
                }
            }) { Text(if (busy) "Guardando…" else "Confirmar egreso") }
        }
    }
    if (personal) PersonnelDialog(repository, { personal = false; revision++ })
    editUnit?.let { unit ->
        IdentifyUnitDialog(tools, unit, repository, { editUnit = null }, { editUnit = null; revision++ })
    }
}

@Composable
private fun UnitSelection(label: String, selected: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(selected, { onToggle() }, enabled = enabled)
        Text(label, Modifier.weight(1f))
    }
}

@Composable
private fun IdentifyUnitDialog(tools: Boolean, unit: InventoryUnit, repository: StockRepository, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var brand by rememberSaveable(unit.id) { mutableStateOf(unit.brand) }
    var brands by remember { mutableStateOf(emptyList<StockCatalogueOption>()) }
    LaunchedEffect(Unit) { runCatching { withContext(Dispatchers.IO) { repository.stockOptions(StockCatalogueKind.BRAND) } }.onSuccess { brands = it } }
    var identifier by rememberSaveable(unit.id) { mutableStateOf(unit.identifier) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Identificar unidad") }, text = {
        Column {
            StockCatalogueField("Marca", brand, brands, !busy) { brand = it }
            OutlinedTextField(identifier, { identifier = it }, enabled = !busy, label = { Text("Identificador único") })
            Text("Los movimientos anteriores conservan la identificación registrada al realizarlos.", style = MaterialTheme.typography.bodySmall)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.identifyUnit(tools, unit.id, brand, identifier) } }
            busy = false
            result.onSuccess { onSaved() }.onFailure { error = it.message ?: "No se pudo identificar la unidad." }
        }
    }) { Text("Guardar") } }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}

enum class MovementColumn(val label: String, val width: Int, val numeric: Boolean = false) {
    DATE("Fecha de movimiento", 320), ITEM("Artículo", 180), PERSON("Personal", 210), QUANTITY("Cantidad", 110, true),
    UNIT("Unidad", 100), IDENTITY("Marca / identificador", 280), KIND("Tipo de movimiento", 160), MATERIAL("Tipo de material", 180),
    RETURN_DATE("Fecha de devolución", 320), RETURNED("Devueltas", 110, true), PENDING("Pendientes", 110, true), NOTES("Observaciones", 240);
    fun text(m: StockMovement): String = when (this) {
        DATE -> m.date; ITEM -> m.itemName; PERSON -> m.person; QUANTITY -> formatStockQuantity(m.quantity)
        UNIT -> m.unit; KIND -> m.kind; MATERIAL -> m.materialType; RETURN_DATE -> m.returnDate
        IDENTITY -> m.identitySummary
        RETURNED -> if (m.kind == "Egreso") formatStockQuantity(m.returned) else "—"
        PENDING -> if (m.kind == "Egreso") formatStockQuantity(m.pending) else "—"
        NOTES -> m.notes
    }
    fun display(m: StockMovement): String = if (this == DATE || this == RETURN_DATE) displayStockDate(text(m)) else text(m).ifBlank { "—" }
}
fun movementColumns(tools: Boolean): List<MovementColumn> = MovementColumn.entries.filter {
    if (tools) it != MovementColumn.MATERIAL else it !in listOf(MovementColumn.RETURN_DATE, MovementColumn.RETURNED, MovementColumn.PENDING)
}
fun sortMovements(rows: List<StockMovement>, column: MovementColumn, descending: Boolean): List<StockMovement> {
    val collator = Collator.getInstance(Locale.forLanguageTag("es-AR")).apply { strength = Collator.PRIMARY }
    val comparator = Comparator<StockMovement> { a, b ->
        when (column) {
            MovementColumn.QUANTITY -> a.quantity.compareTo(b.quantity)
            MovementColumn.RETURNED -> a.returned.compareTo(b.returned)
            MovementColumn.PENDING -> a.pending.compareTo(b.pending)
            else -> collator.compare(column.text(a), column.text(b))
        }
    }
    return rows.sortedWith((if (descending) comparator.reversed() else comparator).thenByDescending { it.id })
}

@Composable
fun MovementHistoryDialog(tools: Boolean, repository: StockRepository, onDismiss: () -> Unit, onChanged: () -> Unit, initialQuery: String = "") {
    val scope = rememberCoroutineScope()
    val columns = movementColumns(tools)
    var rows by remember { mutableStateOf(emptyList<StockMovement>()) }
    var revision by remember { mutableIntStateOf(0) }
    var complete by rememberSaveable { mutableStateOf(false) }
    var filters by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var searchColumn by rememberSaveable { mutableStateOf("Todas") }
    var from by rememberSaveable { mutableStateOf(if (tools && initialQuery.isBlank()) yesterdayStockDate() else "") }
    var until by rememberSaveable { mutableStateOf(if (tools && initialQuery.isBlank()) todayStockDate() else "") }
    var kind by rememberSaveable { mutableStateOf("Todos") }
    var category by rememberSaveable { mutableStateOf("Todos") }
    var sortColumn by rememberSaveable { mutableStateOf(MovementColumn.DATE) }
    var descending by rememberSaveable { mutableStateOf(true) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(revision) {
        runCatching { withContext(Dispatchers.IO) { repository.movements(tools) } }.onSuccess { rows = it }
            .onFailure { error = "No se pudieron cargar los movimientos." }
        loading = false
    }
    val filtered = remember(rows, complete, query, searchColumn, from, until, kind, category, sortColumn, descending) {
        sortMovements(rows.filter { m ->
            (from.isBlank() || m.date >= from) && (until.isBlank() || m.date <= until) &&
                (kind == "Todos" || m.kind == kind) && (category == "Todos" || m.materialType == category) &&
                (if (searchColumn == "Todas") columns.any { it.display(m).contains(query, true) || it.text(m).contains(query, true) }
                else columns.first { it.name == searchColumn }.let { it.display(m).contains(query, true) || it.text(m).contains(query, true) })
        }, sortColumn, descending)
    }
    StockOverlay({ if (!busy) onDismiss() }) {
        Text("Movimientos de ${if (tools) "herramientas" else "materiales"}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = {
                complete = !complete
                if (tools) { from = if (complete) "" else yesterdayStockDate(); until = if (complete) "" else todayStockDate() }
            }) { Text(if (complete) "Vista completa" else "Vista sencilla") }
            TextButton(onClick = { filters = !filters }) { Text(if (filters) "Ocultar filtros" else "Filtros y orden") }
        }
        if (tools && !complete) Text("Todos los movimientos · ${if (from.isBlank() && until.isBlank()) "Todas las fechas" else if (from == yesterdayStockDate() && until == todayStockDate()) "Ayer y hoy" else "Fechas seleccionadas"}", style = MaterialTheme.typography.bodySmall)
        if (filters) {
            Column(Modifier.fillMaxWidth().heightIn(max = 230.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StockChoice("Buscar en", if (searchColumn == "Todas") "Todas las columnas" else columns.first { it.name == searchColumn }.label,
                    listOf("Todas" to "Todas las columnas") + columns.map { it.name to it.label }) { searchColumn = it }
                OutlinedTextField(query, { query = it }, label = { Text("Texto a filtrar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                StockChoice("Tipo de movimiento", kind, (listOf("Todos", "Ingreso", "Egreso") + if (tools) ToolLifecycle.movementKinds else listOf("Devolución")).map { it to it }) { kind = it }
                if (!tools) StockChoice("Tipo de material", category, (listOf("Todos") + MaterialTypes.all).map { it to it }) { category = it }
                StockDateField("Desde", from, allowClear = true) { from = it }
                StockDateField("Hasta", until, allowClear = true) { until = it }
                StockChoice("Ordenar por", sortColumn.label, columns.map { it.name to it.label }) { sortColumn = MovementColumn.valueOf(it) }
                Row {
                    TextButton(onClick = { descending = !descending }) { Text(if (sortColumn.numeric) { if (descending) "Mayor a menor ↓" else "Menor a mayor ↑" } else if (sortColumn == MovementColumn.DATE || sortColumn == MovementColumn.RETURN_DATE) { if (descending) "Más recientes ↓" else "Más antiguas ↑" } else { if (descending) "Z–A ↓" else "A–Z ↑" }) }
                    TextButton(onClick = { query = ""; searchColumn = "Todas"; from = ""; until = ""; kind = "Todos"; category = "Todos" }) { Text("Limpiar filtros") }
                }
            }
        }
        Text("${filtered.size} movimientos", style = MaterialTheme.typography.labelMedium)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        if (from.isNotBlank() && until.isNotBlank() && from > until) Text("La fecha desde debe ser anterior o igual a hasta.", color = MaterialTheme.colorScheme.error)
        if (complete) {
            Column(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                Row(Modifier.background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp)) {
                    columns.forEach { column -> Text(column.label + if (column == sortColumn) { if (descending) " ↓" else " ↑" } else " ↕",
                        Modifier.width(column.width.dp).clickable { descending = column == sortColumn && !descending; sortColumn = column }.padding(vertical = 12.dp), color = Color.White, fontWeight = FontWeight.Bold) }
                    Text("Eliminar", Modifier.width(64.dp).padding(vertical = 12.dp), color = Color.White)
                }
                LazyColumn {
                    items(filtered, key = { it.id }) { movement ->
                        Row(Modifier.clickable(enabled = !busy) { selectedId = movement.id }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            columns.forEach { Text(it.display(movement), Modifier.width(it.width.dp).padding(vertical = 12.dp)) }
                            IconButton(enabled = !busy && !movement.auditProtected, onClick = { deleteId = movement.id }, modifier = Modifier.width(64.dp)) {
                                Icon(painterResource(R.drawable.ic_delete_image), "Eliminar movimiento ${movement.id}", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        HorizontalDivider()
                    }
                    if (filtered.isEmpty()) item { Text("No hay movimientos con estos filtros.", Modifier.padding(16.dp)) }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(filtered, key = { it.id }) { movement ->
                    Card(Modifier.fillMaxWidth().clickable(enabled = !busy) { selectedId = movement.id }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(movement.itemName, fontWeight = FontWeight.Bold)
                                if (movement.identitySummary.isNotBlank()) Text(movement.identitySummary)
                                Text("${movement.kind} · ${displayStockDate(movement.date)}")
                                Text(movement.person.ifBlank { "—" })
                                Text("${formatStockQuantity(movement.quantity)} ${movement.unit}")
                                if (!tools) Text(movement.materialType)
                                if (movement.kind == "Egreso") Text("Pendiente: ${formatStockQuantity(movement.pending)} ${movement.unit}")
                                if (tools && movement.returnDate.isNotBlank()) Text("Última devolución: ${displayStockDate(movement.returnDate)}")
                                if (movement.notes.isNotBlank()) Text(movement.notes, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(enabled = !busy && !movement.auditProtected, onClick = { deleteId = movement.id }) {
                                Icon(painterResource(R.drawable.ic_delete_image), "Eliminar movimiento ${movement.id}", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) item { Text("No hay movimientos con estos filtros.", Modifier.padding(16.dp)) }
            }
        }
        TextButton(enabled = !busy, onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cerrar") }
    }
    rows.firstOrNull { it.id == selectedId }?.let { movement ->
        ReturnDialog(tools, movement, repository, { selectedId = null }, { selectedId = null; revision++; onChanged() })
    }
    rows.firstOrNull { it.id == deleteId }?.let { movement ->
        AlertDialog(onDismissRequest = { if (!busy) deleteId = null }, title = { Text("Eliminar movimiento") }, text = {
            Text("${movement.itemName} · ${movement.kind}\nSe ajustará el stock ${if (movement.kind == "Egreso") "sumando ${formatStockQuantity(movement.pending)}" else "restando ${formatStockQuantity(movement.quantity)}"} ${movement.unit}." + if (movement.kind == "Egreso" && !tools && movement.returned > 0) " También se eliminarán sus devoluciones asociadas." else "")
        }, confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { repository.deleteMovement(tools, movement.id) } }
                busy = false; deleteId = null
                result.onSuccess { error = ""; revision++; onChanged() }.onFailure { error = it.message ?: "No se pudo eliminar el movimiento." }
            }
        }) { Text(if (busy) "Eliminando…" else "Eliminar") } }, dismissButton = { TextButton(enabled = !busy, onClick = { deleteId = null }) { Text("Cancelar") } })
    }
}

@Composable
fun ReturnDialog(tools: Boolean, movement: StockMovement, repository: StockRepository, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var workers by remember { mutableStateOf(emptyList<Worker>()) }
    var workerId by rememberSaveable { mutableStateOf(movement.workerId ?: 0L) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var selectedUnits by rememberSaveable { mutableStateOf(longArrayOf()) }
    var damagedUnits by rememberSaveable { mutableStateOf(longArrayOf()) }
    var damageNotes by rememberSaveable { mutableStateOf(listOf<String>()) }
    var date by rememberSaveable { mutableStateOf(todayStockDate()) }
    var notes by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var loanUnits by remember { mutableStateOf(emptyList<ToolLoanUnit>()) }
    var loadingUnits by remember { mutableStateOf(tools && movement.kind == "Egreso") }
    LaunchedEffect(Unit) {
        if (!tools) runCatching { withContext(Dispatchers.IO) { repository.personnel(true).filter { it.active || it.id == movement.workerId } } }.onSuccess { workers = it }.onFailure { error = "No se pudo cargar el personal." }
        if (tools && movement.kind == "Egreso") {
            runCatching { withContext(Dispatchers.IO) { repository.toolLoanUnits(movement.id) } }
                .onSuccess { loanUnits = it }
                .onFailure { error = "No se pudieron cargar las herramientas del préstamo." }
            loadingUnits = false
        }
    }
    val returnEntries = if (tools) loanUnits.map { MovementUnit(it.unit, false) } else movement.units
    val canReturn = movement.kind == "Egreso" && movement.pending > 0
    StockOverlay({ if (!busy) onDismiss() }) {
        Text(movement.itemName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${movement.kind} del ${displayStockDate(movement.date)}")
            Text("Personal: ${movement.person.ifBlank { "—" }}")
            if (movement.identitySummary.isNotBlank()) Text(movement.identitySummary)
            if (movement.units.any { it.unit.legacy }) Text("Préstamo anterior a la identificación individual: los códigos son provisionales y no confirman qué ejemplar se entregó. Verificá la unidad física.", style = MaterialTheme.typography.bodySmall)
            Text("Cantidad: ${formatStockQuantity(movement.quantity)} ${movement.unit}")
            if (!tools) Text("Tipo: ${movement.materialType}")
            if (movement.notes.isNotBlank()) Text("Observaciones: ${movement.notes}")
            if (movement.kind == "Egreso") {
                Text("Devuelto: ${formatStockQuantity(movement.returned)} ${movement.unit}")
                Text("Pendiente: ${formatStockQuantity(movement.pending)} ${movement.unit}", fontWeight = FontWeight.Bold)
                if (movement.returnDate.isNotBlank()) Text("Última devolución: ${displayStockDate(movement.returnDate)}")
            }
            if (canReturn) {
                Text("Cargar devolución", style = MaterialTheme.typography.titleMedium)
                if (tools) Text("Marcá las unidades dañadas y explicá qué les pasó. Se cierra su préstamo, pero no vuelven al stock disponible.", style = MaterialTheme.typography.bodySmall)
                if (!tools) PersonnelChoice("Persona que devuelve", workers, workerId, !busy) { workerId = it }
                if (movement.individuallyTracked) {
                    Text("Marcá cuáles vuelven al pañol", fontWeight = FontWeight.Bold)
                    if (loadingUnits) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (loanUnits.any { it.bundleLoan != null }) Text("Las herramientas unidas se seleccionan y devuelven juntas. Podés indicar el daño de cada una.", style = MaterialTheme.typography.bodySmall)
                    returnEntries.filter { !it.returned }.forEach { entry ->
                        val loanUnit = loanUnits.firstOrNull { it.unit.id == entry.unit.id }
                        val label = if (loanUnit != null && loanUnit.movementId != movement.id) "${loanUnit.itemName} · ${entry.unit.label}" else entry.unit.label
                        UnitSelection(label, entry.unit.id in selectedUnits, !busy && !loadingUnits) {
                            val linked = if (loanUnit?.bundleLoan != null) loanUnits.filter { it.bundleLoan == loanUnit.bundleLoan }.map { it.unit.id } else listOf(entry.unit.id)
                            selectedUnits = if (entry.unit.id in selectedUnits) selectedUnits.filter { it !in linked }.toLongArray() else (selectedUnits.toList() + linked).distinct().toLongArray()
                            damagedUnits = damagedUnits.filter { it in selectedUnits }.toLongArray()
                        }
                        if (tools && entry.unit.id in selectedUnits) {
                            UnitSelection("Dañada: ${entry.unit.label}", entry.unit.id in damagedUnits, !busy) {
                                damagedUnits = if (entry.unit.id in damagedUnits) damagedUnits.filter { it != entry.unit.id }.toLongArray() else damagedUnits + entry.unit.id
                            }
                            if (entry.unit.id in damagedUnits) {
                                val index = returnEntries.indexOf(entry)
                                OutlinedTextField(damageNotes.getOrElse(index) { "" }, { value ->
                                    damageNotes = List(maxOf(damageNotes.size, index + 1)) { if (it == index) value else damageNotes.getOrElse(it) { "" } }
                                }, enabled = !busy, label = { Text("Qué le pasó a ${entry.unit.identifier} *") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    Text("Cantidad seleccionada: ${selectedUnits.size}")
                } else OutlinedTextField(quantity, { quantity = it; error = "" }, enabled = !busy, label = { Text("Cantidad a devolver (${movement.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = if (movement.wholeNumbers) KeyboardType.Number else KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                StockDateField("Fecha de devolución", date, !busy) { date = it }
                OutlinedTextField(notes, { notes = it }, enabled = !busy, label = { Text("Observaciones de devolución") }, modifier = Modifier.fillMaxWidth())
            } else Text(if (movement.kind == "Egreso") "Devolución completa." else "Las devoluciones se cargan desde el egreso correspondiente.")
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cerrar") }
            if (canReturn) Button(enabled = !busy && !loadingUnits, onClick = {
                val number = if (movement.individuallyTracked) movement.units.count { it.unit.id in selectedUnits }.toDouble() else quantity.trim().replace(',', '.').toDoubleOrNull()
                error = runCatching {
                    require(number != null) { "Ingresá una cantidad válida." }
                    validateMovementQuantity(number, movement.unit, movement.wholeNumbers)
                    require(number <= movement.pending) { "La cantidad supera lo pendiente (${formatStockQuantity(movement.pending)} ${movement.unit})." }
                }.exceptionOrNull()?.message.orEmpty()
                if (error.isBlank() && number != null) {
                    busy = true
                    scope.launch {
                        val damages = returnEntries.mapIndexedNotNull { index, entry ->
                            if (entry.unit.id in damagedUnits && entry.unit.id in selectedUnits) entry.unit.id to damageNotes.getOrElse(index) { "" } else null
                        }.toMap()
                        val ownIds = movement.units.filter { it.unit.id in selectedUnits }.map { it.unit.id }
                        val result = withContext(Dispatchers.IO) { runCatching { repository.returnStock(tools, movement.id, number, date, if (tools) null else workerId, notes, ownIds, damages) } }
                        busy = false
                        result.onSuccess { onSaved() }.onFailure { error = it.message ?: "No se pudo guardar la devolución." }
                    }
                }
            }) { Text(if (busy) "Guardando…" else "Confirmar devolución") }
        }
    }
}
