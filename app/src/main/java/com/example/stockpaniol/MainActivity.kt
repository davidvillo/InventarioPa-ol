package com.example.stockpaniol

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var editToolId by rememberSaveable { mutableStateOf<Long?>(null) }
    var issueItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var issueTools by rememberSaveable { mutableStateOf(false) }
    var historyTools by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var purchaseTools by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var toolCare by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var focusedToolId by rememberSaveable { mutableStateOf<Long?>(null) }
    var returnMovement by remember { mutableStateOf<StockMovement?>(null) }
    var returnTools by rememberSaveable { mutableStateOf(false) }
    var returnUnitId by rememberSaveable { mutableStateOf<Long?>(null) }
    var materialCare by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var rentals by rememberSaveable { mutableStateOf(false) }
    var bundles by rememberSaveable { mutableStateOf(false) }
    var rename by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var photoBusy by remember { mutableStateOf(false) }
    var previewPhotos by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var previewName by rememberSaveable { mutableStateOf("") }
    var previewItemId by rememberSaveable { mutableLongStateOf(0L) }
    var previewTools by rememberSaveable { mutableStateOf(false) }
    fun openUnit(targetTools: Boolean, unit: InventoryUnit) {
        val loanId = unit.loanId
        if (loanId != null) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) {
                    repository.movements(targetTools).firstOrNull { it.id == loanId && it.kind == "Egreso" && it.units.any { entry -> entry.unit.id == unit.id && !entry.returned } }
                        ?: error("El préstamo cambió. Actualizá la lista.")
                } }.onSuccess {
                    returnTools = targetTools; returnUnitId = unit.id; returnMovement = it
                }.onFailure { snackbar.showSnackbar(it.message ?: "No se pudo abrir la devolución.") }
            }
        } else if (targetTools && unit.condition == ToolCondition.IN_REPAIR) focusedToolId = unit.id
    }
    fun savePhotos(targetTools: Boolean, itemId: Long, photos: List<String>) {
        photoBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    repository.updateImages(targetTools, itemId, photos)
                }
            }
            photoBusy = false
            result.onSuccess {
                revision++
                if (previewPhotos != null && previewItemId == itemId && previewTools == targetTools) {
                    previewPhotos = photos
                }
            }.onFailure {
                snackbar.showSnackbar("No se pudo guardar la imagen. Intentá de nuevo.")
            }
        }
    }
    LaunchedEffect(tools, revision) {
        withContext(Dispatchers.IO) { repository.items(tools) to repository.stockName }.let { records = it.first; title = it.second }
    }
    val activeRecords = records.filter { !it.individuallyTracked || it.units.isEmpty() || it.units.any { unit -> unit.inActiveStock } }
    Scaffold(containerColor = Background, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        Column {
            TopAppBar(title = { Text(title, modifier = Modifier.clickable { rename = true }, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = {
                Box {
                    TextButton(onClick = { menu = true }) { Text("☰", color = Color.White, style = MaterialTheme.typography.headlineSmall) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(if (tools) "Movimiento de herramientas" else "Movimientos de materiales") }, onClick = { menu = false; historyTools = tools })
                        DropdownMenuItem(text = { Text(if (tools) "Registro de compras herramientas" else "Registro de compras materiales") }, onClick = { menu = false; purchaseTools = tools })
                        if (tools) {
                            DropdownMenuItem(text = { Text("Estado y reparación de herramientas") }, onClick = { menu = false; toolCare = false })
                            DropdownMenuItem(text = { Text("Unir / desunir herramientas") }, onClick = { menu = false; bundles = true })
                            DropdownMenuItem(text = { Text("Herramientas alquiladas") }, onClick = { menu = false; rentals = true })
                            DropdownMenuItem(text = { Text("Papelera de herramientas") }, onClick = { menu = false; toolCare = true })
                        } else {
                            DropdownMenuItem(text = { Text("Materiales dañados") }, onClick = { menu = false; materialCare = false })
                            DropdownMenuItem(text = { Text("Papelera de materiales") }, onClick = { menu = false; materialCare = true })
                        }
                        DropdownMenuItem(text = { Text("Ajustes") }, onClick = { menu = false; settings = true })
                        DropdownMenuItem(text = { Text("Acerca del almacenamiento") }, onClick = { menu = false; about = true })
                    }
                }
            }, actions = {
                TextButton(onClick = { complete = !complete }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text(if (complete) "Vista sencilla" else "Vista completa", color = Color.White,
                        style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp),
                cursorBrush = SolidColor(Blue),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)
                    .semantics { contentDescription = "Buscar nombre, marca o identificador" },
                decorationBox = { input ->
                    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Buscar nombre, marca o identificador",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        input()
                    }
                }
            )
            Text("${activeRecords.size} ${if (tools) "herramientas" else "materiales"} · ${if (complete) "Vista completa" else "Vista sencilla"}", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
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
            val filtered = remember(activeRecords, query, sortColumn, sortDescending) {
                sortStock(activeRecords.filter { it.name.contains(query, ignoreCase = true) || it.identitySummary.contains(query, true) }, sortColumn, sortDescending)
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
                                if (tools) editToolId = item.id else { issueTools = false; issueItemId = item.id }
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
                        if (tools || item.individuallyTracked) {
                            StockSummaryCard(item, onPhoto = {
                                previewName = item.name
                                previewItemId = item.id
                                previewTools = tools
                                previewPhotos = item.images
                            }, onEdit = {
                                if (tools) editToolId = item.id else { issueTools = false; issueItemId = item.id }
                            }, tools = tools, onUnitClick = { openUnit(tools, it) })
                        } else Card(modifier = Modifier.clickable { issueTools = false; issueItemId = item.id }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                StockPhoto(item.images.firstOrNull().orEmpty(), Modifier.size(72.dp).clickable(onClickLabel = "Ampliar imagen") {
                                    previewName = item.name
                                    previewItemId = item.id
                                    previewTools = tools
                                    previewPhotos = item.images
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
    editToolId?.let { id -> key(id) {
        ToolEditDialog(id, repository, onDismiss = { editToolId = null },
            onSaved = { editToolId = null; revision++ },
            onIssue = { editToolId = null; issueTools = true; issueItemId = id })
    } }
    issueItemId?.let { id -> key(issueTools, id) {
        IssueDialog(issueTools, id, repository, { issueItemId = null }, { issueItemId = null; revision++ })
    } }
    historyTools?.let { stockTools -> key(stockTools) {
        MovementHistoryDialog(stockTools, repository, { historyTools = null }, { revision++ })
    } }
    purchaseTools?.let { stockTools -> key(stockTools) {
        PurchaseHistoryDialog(stockTools, repository, { purchaseTools = null })
    } }
    if (settings) SettingsDialog(repository, { settings = false }, onNameChanged = { title = it })
    materialCare?.let { trash -> MaterialCareDialog(repository, trash, { materialCare = null }, { revision++ }) }
    toolCare?.let { trash -> ToolCareDialog(repository, trash, { toolCare = null }, { revision++ }) }
    focusedToolId?.let { id -> key(id) {
        ToolCareDialog(repository, false, { focusedToolId = null }, { revision++ }, initialUnitId = id)
    } }
    returnMovement?.let { movement -> key(returnTools, movement.id, returnUnitId) {
        ReturnDialog(returnTools, movement, repository, { returnMovement = null }, { returnMovement = null; revision++ }, initialUnitId = returnUnitId)
    } }
    if (rentals) ToolRentalsDialog(repository, { rentals = false }, { revision++ })
    if (bundles) ToolBundlesDialog(repository, { bundles = false }, { revision++ })
    previewPhotos?.let { photos ->
        StockPhotoGallery(previewName, photos, photoBusy,
            onSave = { savePhotos(previewTools, previewItemId, it) },
            onError = { message -> scope.launch { snackbar.showSnackbar(message) } },
            onDismiss = { previewPhotos = null })
    }
    if (rename) StockNameDialog(repository, { rename = false }, { title = it; rename = false })
    if (about) AlertDialog(onDismissRequest = { about = false }, title = { Text("Stock local") }, text = { Text("Los materiales, herramientas e ingresos se guardan en este dispositivo. El nombre identifica esta base. La sincronización por internet y los permisos de administrador se podrán incorporar en una próxima etapa.") }, confirmButton = { TextButton(onClick = { about = false }) { Text("Entendido") } })
}

@Composable
internal fun StockPhoto(uri: String, modifier: Modifier = Modifier.size(48.dp), expanded: Boolean = false) {
    val context = LocalContext.current
    val targetPixels = with(LocalDensity.current) { (if (expanded) 360.dp else 72.dp).roundToPx() }
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri, targetPixels) {
        bitmap = withContext(Dispatchers.IO) {
            if (uri.isBlank()) null else runCatching {
                StockImages.loadBitmap(context, Uri.parse(uri), targetPixels)
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
    var selectedId by rememberSaveable { mutableLongStateOf(0L) }
    val selected = records.firstOrNull { it.id == selectedId }
    var existingQuery by rememberSaveable { mutableStateOf(selected?.name.orEmpty()) }
    var name by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("unidades") }
    var measures by remember { mutableStateOf(emptyList<MeasurementUnit>()) }
    var brands by remember { mutableStateOf(emptyList<StockCatalogueOption>()) }
    var companies by remember { mutableStateOf(emptyList<StockCatalogueOption>()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { Triple(repository.measurements(true), repository.stockOptions(StockCatalogueKind.BRAND), repository.stockOptions(StockCatalogueKind.COMPANY)) }
            .let { measures = it.first; brands = it.second; companies = it.third }
    }
    var receipt by rememberSaveable { mutableStateOf("no hay") }
    var date by rememberSaveable { mutableStateOf(todayStockDate()) }
    var notes by rememberSaveable { mutableStateOf("") }
    var unitBrands by rememberSaveable { mutableStateOf(listOf<String>()) }
    var sameBrand by rememberSaveable { mutableStateOf(false) }
    var commonBrand by rememberSaveable { mutableStateOf("") }
    var unitIdentifiers by rememberSaveable { mutableStateOf(listOf<String>()) }
    var individualMaterial by rememberSaveable { mutableStateOf(false) }
    var company by rememberSaveable { mutableStateOf("") }
    var rented by rememberSaveable { mutableStateOf(false) }
    var image by rememberSaveable { mutableStateOf("") }
    var materialType by rememberSaveable { mutableStateOf(MaterialTypes.CONSTRUCTION) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val individual = tools || if (existing) selected?.individuallyTracked == true else individualMaterial
    val countingUnit = measures.firstOrNull { it.counting }?.name ?: "unidades"
    val actualUnit = if (existing) selected?.unit.orEmpty() else if (tools) countingUnit else unit
    val selectedMeasure = measures.firstOrNull { it.name == actualUnit }
    val enteredQuantity = quantity.trim().replace(',', '.').toDoubleOrNull()
    val individualCount = if (individual && enteredQuantity != null && enteredQuantity.isFinite() &&
        enteredQuantity >= 0 && enteredQuantity <= Int.MAX_VALUE && enteredQuantity % 1.0 == 0.0) enteredQuantity.toInt() else 0
    androidx.compose.ui.window.Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp).fillMaxHeight(0.94f), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(if (tools) "Ingreso de herramientas" else "Ingreso de materiales", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(!existing, { existing = false }, enabled = !busy); Text("Nuevo")
                        RadioButton(existing, { existing = true }, enabled = records.isNotEmpty() && !busy); Text("Existente", color = if (records.isEmpty()) Color.Gray else Color.Unspecified)
                    }
                    if (records.isEmpty()) Text("Todavía no hay artículos existentes.", style = MaterialTheme.typography.bodySmall)
                    if (existing) {
                        ExistingArticleField(existingQuery, records, !busy,
                            onQuery = { existingQuery = it; selectedId = 0L; error = "" },
                            onSelect = { selectedId = it.id; existingQuery = it.name; error = "" })
                    } else OutlinedTextField(name, { name = it }, label = { Text("Nombre *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (tools) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(rented, { rented = it }, enabled = !busy)
                            Text("Es una herramienta alquilada")
                        }
                        if (rented) {
                            StockCatalogueField("Proveedor del alquiler *", company, companies, !busy) { company = it }
                            Text("El alquiler se registra por cada unidad ingresada.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (!tools) {
                        if (existing) Text("Tipo de material: ${selected?.materialType.orEmpty()}")
                        else StockChoice("Tipo de material", materialType, MaterialTypes.all.map { it to it }, !busy) { materialType = it }
                        if (!existing) Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(individualMaterial, { individualMaterial = it; if (it) unit = countingUnit }, enabled = !busy)
                            Text("Identificar cada unidad (reutilizable)")
                        }
                    }
                    OutlinedTextField(quantity, { quantity = it; error = "" }, enabled = !busy, label = { Text("Cantidad a ingresar *") }, keyboardOptions = KeyboardOptions(keyboardType = if (individual) KeyboardType.Number else KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Podés ingresar 0 para guardar el artículo sin agregar stock.", style = MaterialTheme.typography.bodySmall)
                    if (tools || existing) OutlinedTextField(actualUnit, {}, readOnly = true, label = { Text("Unidad (fija)") }, modifier = Modifier.fillMaxWidth())
                    else if (individual) {
                        StockChoice("Unidad del material reutilizable", actualUnit, measures.filter { it.identifiesMaterial }.map { it.name to it.name }, !busy) { unit = it }
                        if (selectedMeasure?.pairs == true) Text("Cada identificador representa un par completo. La cantidad se expresa en pares.", style = MaterialTheme.typography.bodySmall)
                    }
                    else {
                        Text("Unidad")
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            measures.filter { it.active }.forEach { option -> FilterChip(unit == option.name, { unit = option.name }, label = { Text(option.name) }) }
                        }
                    }
                    OutlinedTextField(if (tools && rented && receipt == "no hay") "" else receipt, { receipt = it }, label = { Text(if (tools && rented) "Remito / contrato de alquiler (opcional)" else "Número de remito") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    StockDateField(if (tools && rented) "Fecha de inicio del alquiler" else "Fecha de adquisición", date, !busy) { date = it }
                    if (individual) {
                        SameBrandCheckbox(sameBrand, !busy) { checked ->
                                if (checked && commonBrand.isBlank()) commonBrand = unitBrands.firstOrNull().orEmpty()
                                if (!checked) unitBrands = List(maxOf(individualCount, unitBrands.size)) { commonBrand }
                                sameBrand = checked
                        }
                        if (sameBrand) StockCatalogueField("Marca de todas las unidades (opcional)", commonBrand, brands, !busy) { commonBrand = it }
                    } else StockCatalogueField("Marca (opcional)", commonBrand, brands, !busy) { commonBrand = it }
                    if (individual && individualCount > 0) Text("Marca e identificación opcionales para cada unidad. Si dejás el identificador vacío, se usan tres letras del nombre y un número correlativo: BAL 001, BAL 002… Si las letras ya están en uso, se toma la siguiente del nombre.", style = MaterialTheme.typography.bodySmall)
                    } }
                    if (individual) {
                        items(individualCount) { index ->
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Unidad ${index + 1}", fontWeight = FontWeight.Bold)
                            if (!sameBrand) StockCatalogueField("Marca de unidad ${index + 1} (opcional)", unitBrands.getOrElse(index) { "" }, brands, !busy) { value ->
                                unitBrands = List(maxOf(unitBrands.size, index + 1)) { if (it == index) value else unitBrands.getOrElse(it) { "" } }
                            }
                            OutlinedTextField(unitIdentifiers.getOrElse(index) { "" }, { value ->
                                unitIdentifiers = List(maxOf(unitIdentifiers.size, index + 1)) { if (it == index) value else unitIdentifiers.getOrElse(it) { "" } }
                            }, enabled = !busy, label = { Text("Identificador de unidad ${index + 1} (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!tools || !rented) StockCatalogueField("Empresa a la que se compró", company, companies, !busy) { company = it }
                    OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    val addedImages = StockPhotos.decode(image)
                    val allImages = ((if (existing) selected?.images.orEmpty() else emptyList()) + addedImages).distinct()
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        allImages.forEach { photo -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            StockPhoto(photo)
                            if (photo in addedImages) TextButton(enabled = !busy, onClick = { image = StockPhotos.encode(addedImages - photo) }) { Text("Quitar") }
                        } }
                    }
                    StockImagePicker(enabled = !busy && allImages.size < StockPhotos.LIMIT, onSelected = {
                        if (it.toString() !in allImages && allImages.size < StockPhotos.LIMIT) image = StockPhotos.encode(addedImages + it.toString())
                        error = ""
                    }, onError = { error = it }) { openChooser ->
                            TextButton(enabled = !busy && allImages.size < StockPhotos.LIMIT, onClick = openChooser) { Text("Agregar foto (${allImages.size}/${StockPhotos.LIMIT})") }
                        }
                    } }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") }
                    Button(enabled = !busy, onClick = {
                        val number = enteredQuantity
                        val actualName = if (existing) selected?.name.orEmpty() else name.trim()
                        error = when {
                            existing && selected == null -> "Elegí un artículo de las coincidencias."
                            actualName.isBlank() -> "Ingresá el nombre del artículo."
                            tools && rented && company.isBlank() -> "Ingresá el proveedor del alquiler."
                            number == null || !number.isFinite() || number < 0 -> "Ingresá una cantidad igual o mayor a cero."
                            individual && (number % 1.0 != 0.0 || number > Int.MAX_VALUE) -> "Ingresá una cantidad entera de unidades válida."
                            selectedMeasure == null || (!selectedMeasure.active && !existing && !individual) -> "Seleccioná una unidad disponible."
                            selectedMeasure.wholeNumbers && number % 1.0 != 0.0 -> "La cantidad de ${selectedMeasure.name} debe ser un número entero."
                            !individual && !existing && records.any { it.name.equals(actualName, true) } -> "Ese nombre ya existe. Seleccioná Existente para sumar stock."
                            else -> ""
                        }
                        if (error.isBlank() && number != null) {
                            busy = true
                            val details = if (individual) List(individualCount) { StockUnitEntry(if (sameBrand) commonBrand else unitBrands.getOrElse(it) { "" }, unitIdentifiers.getOrElse(it) { "" }) } else emptyList()
                            val entry = StockEntry(if (existing) selectedId else 0, actualName, number, actualUnit, notes, receipt, date, image, if (!individual || sameBrand) commonBrand else "", company, if (existing) selected!!.materialType else materialType, if (individual) StockControl.INDIVIDUAL else StockControl.QUANTITY, unitDetails = details,
                                rental = if (tools && rented) RentalEntry(company, receipt.takeUnless { it == "no hay" }.orEmpty()) else null)
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
