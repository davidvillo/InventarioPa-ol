package com.example.stockpaniol

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Blue = Color(0xFF087BC1)
private val Background = Color(0xFFF3F6FA)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = LocalStockRepository(applicationContext)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Blue, secondary = Color(0xFF008577), background = Background)) {
                StockApp(repository)
            }
        }
    }
}
private fun amount(value: Double): String = formatStockQuantity(value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockApp(repository: StockRepository) {
    var tools by rememberSaveable { mutableStateOf(false) }
    var complete by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var materialColumn by rememberSaveable { mutableStateOf(StockColumn.NAME) }
    var toolColumn by rememberSaveable { mutableStateOf(StockColumn.NAME) }
    var materialDescending by rememberSaveable { mutableStateOf(false) }
    var toolDescending by rememberSaveable { mutableStateOf(false) }
    val sortColumn = if (tools) toolColumn else materialColumn
    val sortDescending = if (tools) toolDescending else materialDescending
    var sortMenu by remember { mutableStateOf(false) }
    fun selectSort(column: StockColumn, descending: Boolean) {
        if (tools) { toolColumn = column; toolDescending = descending }
        else { materialColumn = column; materialDescending = descending }
    }
    var title by remember { mutableStateOf("") }
    var records by remember { mutableStateOf(emptyList<StockItem>()) }
    var revision by remember { mutableIntStateOf(0) }
    var form by rememberSaveable { mutableStateOf(false) }
    var issueItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var issueTools by rememberSaveable { mutableStateOf(false) }
    var historyTools by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var purchaseTools by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var personnel by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var rename by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var photoBusy by remember { mutableStateOf(false) }
    var previewUri by rememberSaveable { mutableStateOf<String?>(null) }
    var previewName by rememberSaveable { mutableStateOf("") }
    var previewItemId by rememberSaveable { mutableLongStateOf(0L) }
    var previewTools by rememberSaveable { mutableStateOf(false) }
    fun savePhoto(targetTools: Boolean, itemId: Long, uri: Uri?) {
        photoBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    repository.updateImage(targetTools, itemId, uri?.toString().orEmpty())
                }
            }
            photoBusy = false
            result.onSuccess {
                revision++
                if (previewUri != null && previewItemId == itemId && previewTools == targetTools) {
                    previewUri = uri?.toString().orEmpty()
                }
            }.onFailure {
                snackbar.showSnackbar("No se pudo guardar la imagen. Intentá de nuevo.")
            }
        }
    }
    LaunchedEffect(tools, revision) {
        withContext(Dispatchers.IO) { repository.items(tools) to repository.stockName }.let { records = it.first; title = it.second }
    }
    Scaffold(containerColor = Background, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        Column {
            TopAppBar(title = { Text(title, modifier = Modifier.clickable { rename = true }, maxLines = 1) }, navigationIcon = {
                Box {
                    TextButton(onClick = { menu = true }) { Text("☰", color = Color.White, style = MaterialTheme.typography.headlineSmall) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(if (tools) "Movimientos de herramientas" else "Movimientos de materiales") }, onClick = { menu = false; historyTools = tools })
                        DropdownMenuItem(text = { Text(if (tools) "Compras de herramientas" else "Compras de materiales") }, onClick = { menu = false; purchaseTools = tools })
                        DropdownMenuItem(text = { Text("Personal de obra") }, onClick = { menu = false; personnel = true })
                        DropdownMenuItem(text = { Text("Ajustes") }, onClick = { menu = false; settings = true })
                        DropdownMenuItem(text = { Text("Cambiar nombre del stock") }, onClick = { menu = false; rename = true })
                        DropdownMenuItem(text = { Text(if (complete) "Vista sencilla" else "Vista completa") }, onClick = { menu = false; complete = !complete })
                        DropdownMenuItem(text = { Text("Acerca del almacenamiento") }, onClick = { menu = false; about = true })
                    }
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Blue, titleContentColor = Color.White))
            TabRow(selectedTabIndex = if (tools) 1 else 0, containerColor = Color.White) {
                Tab(selected = !tools, onClick = { if (tools) records = emptyList(); tools = false; query = "" }, text = { Text("Materiales") })
                Tab(selected = tools, onClick = { if (!tools) records = emptyList(); tools = true; query = "" }, text = { Text("Herramientas") })
            }
        }
    }, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { form = true }, containerColor = Blue, contentColor = Color.White) { Text(if (tools) "+ Agregar herramienta" else "+ Agregar material") }
    }) { inset ->
        Column(Modifier.padding(inset).fillMaxSize()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, label = { Text("Buscar nombre, marca o identificador") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { complete = !complete }) { Text(if (complete) "Sencilla" else "Completa") }
            }
            Text("${records.size} ${if (tools) "herramientas" else "materiales"} · ${if (complete) "Vista completa" else "Vista sencilla"}", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    TextButton(onClick = { sortMenu = true }) { Text("Ordenar: ${sortColumn.label} ▾") }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        StockColumn.entries.forEach { column ->
                            DropdownMenuItem(text = { Text(column.label) }, onClick = {
                                selectSort(column, false)
                                sortMenu = false
                            })
                        }
                    }
                }
                TextButton(onClick = { selectSort(sortColumn, !sortDescending) }) {
                    Text(if (sortColumn.numeric) {
                        if (sortDescending) "Mayor a menor ↓" else "Menor a mayor ↑"
                    } else {
                        if (sortDescending) "Z–A ↓" else "A–Z ↑"
                    })
                }
            }
            val filtered = remember(records, query, sortColumn, sortDescending) {
                sortStock(records.filter { it.name.contains(query, ignoreCase = true) || it.identitySummary.contains(query, true) }, sortColumn, sortDescending)
            }
            if (filtered.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                        Text(if (tools) "⚒" else "▦", style = MaterialTheme.typography.displayMedium, color = Blue)
                        Text(if (query.isBlank()) "Tu stock empieza acá" else "Sin resultados", style = MaterialTheme.typography.titleLarge)
                        Text(if (query.isBlank()) "Agregá ${if (tools) "herramientas" else "materiales"} para empezar a llevar el control." else "Probá con otro nombre.", modifier = Modifier.padding(top = 8.dp), color = Color.Gray)
                    }
                }
            } else if (complete) {
                Column(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState())) {
                    Row(Modifier.background(Blue).padding(12.dp)) {
                        StockColumn.entries.forEach { column ->
                            Text(column.label + if (column == sortColumn) { if (sortDescending) " ↓" else " ↑" } else " ↕",
                                Modifier.width(column.width.dp).clickable(onClickLabel = "Ordenar por ${column.label}") {
                                    selectSort(column, column == sortColumn && !sortDescending)
                                }.padding(vertical = 8.dp), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                        items(filtered, key = { it.id }) { item ->
                            Row(Modifier.background(Color.White).clickable {
                                issueTools = tools; issueItemId = item.id
                            }.padding(12.dp)) {
                                StockColumn.entries.forEach { column ->
                                    val value = when (column) {
                                        StockColumn.ITEM -> item.id.toString()
                                        StockColumn.NAME -> item.name + if (item.individuallyTracked) "\n${item.identitySummary}" else ""
                                        StockColumn.QUANTITY -> amount(item.quantity)
                                        StockColumn.UNIT -> item.unit
                                        StockColumn.NOTES -> item.notes.ifBlank { "—" }
                                    }
                                    Text(value, Modifier.width(column.width.dp))
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { item ->
                        Card(modifier = Modifier.clickable { issueTools = tools; issueItemId = item.id }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                StockPhoto(item.image, Modifier.size(72.dp).clickable(onClickLabel = "Ampliar imagen") {
                                    previewName = item.name
                                    previewItemId = item.id
                                    previewTools = tools
                                    previewUri = item.image
                                })
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(item.name, fontWeight = FontWeight.SemiBold)
                                    if (!item.individuallyTracked && item.brand.isNotBlank()) Text(item.brand, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    if (item.individuallyTracked) Text(item.identitySummary, style = MaterialTheme.typography.bodySmall)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(amount(item.quantity), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text(item.unit, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (form) EntryDialog(tools, records, repository, onDismiss = { form = false }, onSaved = { form = false; revision++ })
    issueItemId?.let { id -> key(issueTools, id) {
        IssueDialog(issueTools, id, repository, { issueItemId = null }, { issueItemId = null; revision++ })
    } }
    historyTools?.let { stockTools -> key(stockTools) {
        MovementHistoryDialog(stockTools, repository, { historyTools = null }, { revision++ })
    } }
    purchaseTools?.let { stockTools -> key(stockTools) {
        PurchaseHistoryDialog(stockTools, repository, { purchaseTools = null })
    } }
    if (personnel) PersonnelDialog(repository, { personnel = false })
    if (settings) SettingsDialog(repository, { settings = false })
    previewUri?.let { uri ->
        AlertDialog(onDismissRequest = { if (!photoBusy) previewUri = null }, title = { Text(previewName) }, text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StockPhoto(uri, Modifier.fillMaxWidth().height(280.dp), expanded = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    StockImagePicker(enabled = !photoBusy,
                        onSelected = { savePhoto(previewTools, previewItemId, it) },
                        onError = { message -> scope.launch { snackbar.showSnackbar(message) } }) { openChooser ->
                        IconButton(enabled = !photoBusy, onClick = openChooser) {
                            Icon(painterResource(R.drawable.ic_edit_image), "Cambiar imagen de $previewName", tint = if (photoBusy) Color.Gray else Blue)
                        }
                    }
                    IconButton(enabled = !photoBusy && uri.isNotBlank(), onClick = {
                        savePhoto(previewTools, previewItemId, null)
                    }) {
                        Icon(painterResource(R.drawable.ic_delete_image), "Eliminar imagen de $previewName", tint = if (!photoBusy && uri.isNotBlank()) MaterialTheme.colorScheme.error else Color.Gray)
                    }
                }
                if (photoBusy) Text("Guardando…", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(enabled = !photoBusy, onClick = { previewUri = null }) { Text("Cerrar") } })
    }
    if (rename) {
        var name by remember { mutableStateOf(title) }
        AlertDialog(onDismissRequest = { rename = false }, title = { Text("Nombre del control de stock") }, text = {
            Column { OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true); Text("Identifica tu base de datos local.", style = MaterialTheme.typography.bodySmall) }
        }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { scope.launch { withContext(Dispatchers.IO) { repository.stockName = name }; title = name.trim(); rename = false } }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancelar") } })
    }
    if (about) AlertDialog(onDismissRequest = { about = false }, title = { Text("Stock local") }, text = { Text("Los materiales, herramientas e ingresos se guardan en este dispositivo. El nombre identifica esta base. La sincronización por internet y los permisos de administrador se podrán incorporar en una próxima etapa.") }, confirmButton = { TextButton(onClick = { about = false }) { Text("Entendido") } })
}

@Composable
private fun StockPhoto(uri: String, modifier: Modifier = Modifier.size(48.dp), expanded: Boolean = false) {
    val context = LocalContext.current
    val targetPixels = with(LocalDensity.current) { (if (expanded) 360.dp else 72.dp).roundToPx() }
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri, targetPixels) {
        bitmap = withContext(Dispatchers.IO) {
            if (uri.isBlank()) null else runCatching {
                val imageUri = Uri.parse(uri)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it, null, options) }
                options.inSampleSize = 1
                while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > targetPixels * 2) {
                    options.inSampleSize *= 2
                }
                options.inJustDecodeBounds = false
                context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Background), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), "Imagen del artículo", Modifier.fillMaxSize(), contentScale = if (expanded) ContentScale.Fit else ContentScale.Crop) } ?: Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(R.drawable.ic_image_placeholder), null, tint = Color.Gray, modifier = Modifier.size(if (expanded) 48.dp else 24.dp))
            Text("Sin imagen", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDialog(tools: Boolean, records: List<StockItem>, repository: StockRepository, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var existing by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableLongStateOf(records.firstOrNull()?.id ?: 0) }
    val selected = records.firstOrNull { it.id == selectedId }
    var name by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("unidades") }
    var measures by remember { mutableStateOf(emptyList<MeasurementUnit>()) }
    LaunchedEffect(Unit) {
        measures = withContext(Dispatchers.IO) { repository.measurements(true) }
    }
    var receipt by rememberSaveable { mutableStateOf("no hay") }
    var date by rememberSaveable { mutableStateOf(todayStockDate()) }
    var notes by rememberSaveable { mutableStateOf("") }
    var brand by rememberSaveable { mutableStateOf("") }
    var identifier by rememberSaveable { mutableStateOf("") }
    var individualMaterial by rememberSaveable { mutableStateOf(false) }
    var company by rememberSaveable { mutableStateOf("") }
    var image by rememberSaveable { mutableStateOf("") }
    var materialType by rememberSaveable { mutableStateOf(MaterialTypes.CONSTRUCTION) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var dropdown by remember { mutableStateOf(false) }
    val individual = tools || if (existing) selected?.individuallyTracked == true else individualMaterial
    val countingUnit = measures.firstOrNull { it.counting }?.name ?: "unidades"
    val actualUnit = if (existing) selected?.unit.orEmpty() else if (individual) countingUnit else unit
    val selectedMeasure = measures.firstOrNull { it.name == actualUnit }
    androidx.compose.ui.window.Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp).fillMaxHeight(0.94f), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(if (tools) "Ingreso de herramientas" else "Ingreso de materiales", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(!existing, { existing = false }, enabled = !busy); Text("Nuevo")
                        RadioButton(existing, { existing = true }, enabled = records.isNotEmpty() && !busy); Text("Existente", color = if (records.isEmpty()) Color.Gray else Color.Unspecified)
                    }
                    if (records.isEmpty()) Text("Todavía no hay artículos existentes.", style = MaterialTheme.typography.bodySmall)
                    if (existing) {
                        ExposedDropdownMenuBox(dropdown, { dropdown = !dropdown }) {
                            OutlinedTextField(selected?.name.orEmpty(), {}, readOnly = true, label = { Text("Artículo existente") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dropdown) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                            ExposedDropdownMenu(dropdown, { dropdown = false }) { records.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { selectedId = item.id; dropdown = false }) } }
                        }
                    } else OutlinedTextField(name, { name = it }, label = { Text("Nombre *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (!tools) {
                        if (existing) Text("Tipo de material: ${selected?.materialType.orEmpty()}")
                        else StockChoice("Tipo de material", materialType, MaterialTypes.all.map { it to it }, !busy) { materialType = it }
                        if (!existing) Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(individualMaterial, { individualMaterial = it; if (it) unit = "unidades" }, enabled = !busy)
                            Text("Identificar cada unidad (reutilizable)")
                        }
                    }
                    if (individual) Text("Se agrega una unidad identificada. Para agregar otra marca o ejemplar, registrá otro ingreso con el mismo nombre.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(if (individual) "1" else quantity, { quantity = it }, readOnly = individual, label = { Text("Cantidad a ingresar *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (individual || existing) OutlinedTextField(actualUnit, {}, readOnly = true, label = { Text("Unidad (fija)") }, modifier = Modifier.fillMaxWidth())
                    else {
                        Text("Unidad")
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            measures.filter { it.active }.forEach { option -> FilterChip(unit == option.name, { unit = option.name }, label = { Text(option.name) }) }
                        }
                    }
                    OutlinedTextField(receipt, { receipt = it }, label = { Text("Número de remito") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    StockDateField("Fecha de adquisición", date, !busy) { date = it }
                    if (individual) {
                        OutlinedTextField(brand, { brand = it }, label = { Text("Marca") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(identifier, { identifier = it }, label = { Text("Identificador del pañolero (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("Si lo dejás vacío se asigna un código único. Podés usar una etiqueta o número de serie.", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(company, { company = it }, label = { Text("Empresa a la que se compró") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StockPhoto(image.ifBlank { if (existing) selected?.image.orEmpty() else "" })
                        StockImagePicker(enabled = !busy, onSelected = { image = it.toString(); error = "" }, onError = { error = it }) { openChooser ->
                            TextButton(enabled = !busy, onClick = openChooser) { Text(if (image.isBlank()) "Agregar imagen (opcional)" else "Cambiar imagen") }
                        }
                    }
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") }
                    Button(enabled = !busy, onClick = {
                        val number = if (individual) 1.0 else quantity.trim().replace(',', '.').toDoubleOrNull()
                        val actualName = if (existing) selected?.name.orEmpty() else name.trim()
                        error = when {
                            actualName.isBlank() -> "Ingresá el nombre del artículo."
                            number == null || !number.isFinite() || number <= 0 -> "Ingresá una cantidad mayor a cero."
                            selectedMeasure == null || (!selectedMeasure.active && !existing && !individual) -> "Seleccioná una unidad disponible."
                            selectedMeasure.wholeNumbers && number % 1.0 != 0.0 -> "La cantidad de ${selectedMeasure.name} debe ser un número entero."
                            !individual && !existing && records.any { it.name.equals(actualName, true) } -> "Ese nombre ya existe. Seleccioná Existente para sumar stock."
                            else -> ""
                        }
                        if (error.isBlank() && number != null) {
                            busy = true
                            val entry = StockEntry(if (existing) selectedId else 0, actualName, number, actualUnit, notes, receipt, date, image, brand, company, if (existing) selected!!.materialType else materialType, if (individual) StockControl.INDIVIDUAL else StockControl.QUANTITY, if (individual) identifier else "")
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { runCatching { repository.receive(tools, entry) } }
                                busy = false
                                result.onSuccess { onSaved() }.onFailure { error = it.message ?: "No se pudo guardar el ingreso. Revisá los datos e intentá de nuevo." }
                            }
                        }
                    }) { Text(if (busy) "Guardando…" else "Guardar ingreso") }
                }
            }
        }
    }
}
