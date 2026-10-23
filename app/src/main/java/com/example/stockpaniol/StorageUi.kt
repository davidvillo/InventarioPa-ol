package com.example.stockpaniol

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class StockApplication : Application() {
    val repository by lazy { LocalStockRepository(this) }
    val storage by lazy { StockStorageController(StockBackupManager(this, repository)) }
    override fun onCreate() {
        super.onCreate()
        StockBackupManager.recoverInterruptedRestore(this)
    }
}

data class StockStorageState(
    val busy: Boolean = false,
    val progress: String = "",
    val message: String = "",
    val error: String = "",
    val folder: Uri? = null,
    val documents: List<StockBackupDocument> = emptyList(),
    val prepared: PreparedStockBackup? = null,
    val restored: Int = 0
)

/** Application-scoped work survives rotation and blocks edits during import/export. */
class StockStorageController(val manager: StockBackupManager) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = manager.context.getSharedPreferences("stock_storage", 0)
    private val mutable = MutableStateFlow(StockStorageState(folder = prefs.getString("folder", null)?.let(Uri::parse)))
    val state = mutable.asStateFlow()

    @Synchronized private fun work(progress: String, action: () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, progress = progress, message = "", error = "")
        scope.launch {
            try { action() }
            catch (e: Exception) {
                val message = when (e) {
                    is SecurityException -> "Volvé a elegir la carpeta o el archivo para autorizar el acceso. Tus datos se conservaron."
                    is org.json.JSONException, is java.util.zip.ZipException -> "La copia no es válida o está incompleta. Tus datos se conservaron."
                    is java.io.IOException -> "No se pudo leer o guardar la copia. Revisá el espacio disponible y el acceso a la carpeta."
                    is android.database.sqlite.SQLiteException -> "No se pudo abrir o verificar la base. Probá una copia válida o una versión más reciente de la app. Se conservaron los datos actuales."
                    else -> e.message ?: "No se pudo completar la operación. Tus datos se conservaron."
                }
                mutable.value = mutable.value.copy(error = message)
            }
            finally { mutable.value = mutable.value.copy(busy = false, progress = "") }
        }
    }

    fun selectFolder(uri: Uri) = work("Abriendo carpeta…") {
        manager.context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val found = manager.listFolder(uri)
        check(prefs.edit().putString("folder", uri.toString()).commit())
        mutable.value = mutable.value.copy(folder = uri, documents = found, message = if (found.isEmpty()) "No se encontraron copias en esta carpeta." else "Se encontraron ${found.size} copias.")
    }

    fun search() = work("Buscando copias…") {
        val folder = checkNotNull(mutable.value.folder) { "Elegí la carpeta donde guardaste las copias." }
        val documents = manager.listFolder(folder)
        mutable.value = mutable.value.copy(documents = documents, message = if (documents.isEmpty()) "No se encontraron copias en esta carpeta." else "Se encontraron ${documents.size} copias.")
    }

    fun save() = work("Guardando y verificando la base y las fotos…") {
        val folder = checkNotNull(mutable.value.folder) { "Elegí primero una carpeta del almacenamiento interno." }
        val document = manager.saveToFolder(folder)
        mutable.value = mutable.value.copy(documents = manager.listFolder(folder), message = "Copia guardada y verificada: ${document.name}")
    }

    fun inspect(uri: Uri) = work("Verificando compatibilidad, base y fotos…") {
        mutable.value.prepared?.directory?.deleteRecursively()
        mutable.value = mutable.value.copy(prepared = null)
        val prepared = manager.inspect(uri)
        mutable.value = mutable.value.copy(prepared = prepared)
    }

    fun cancelRestore() {
        if (mutable.value.busy) return
        mutable.value.prepared?.directory?.deleteRecursively()
        mutable.value = mutable.value.copy(prepared = null)
    }

    fun restore() = work("Restaurando la base y las fotos…") {
        val prepared = checkNotNull(mutable.value.prepared)
        mutable.value = mutable.value.copy(prepared = null)
        manager.restore(prepared)
        mutable.value = mutable.value.copy(restored = mutable.value.restored + 1, message = "Copia restaurada. Se recuperaron la base y las fotos.")
    }
}

@Composable
fun StorageDialog(controller: StockStorageController, onDismiss: () -> Unit) {
    val state by controller.state.collectAsState()
    var pickerError by rememberStockError()
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) controller.selectFolder(uri) }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) controller.inspect(uri) }
    StockOverlay({ if (!state.busy) onDismiss() }) {
        Text("Almacenamiento", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Guardá una copia antes de desinstalar o cambiar de versión. Incluye la base de datos y las fotos.")
        Text("Elegí una carpeta del almacenamiento interno, por ejemplo Documentos / Copias Stock Pañol. Sus copias se conservan al desinstalar la app.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled = !state.busy, modifier = Modifier.fillMaxWidth(), onClick = {
            runCatching { folder.launch(state.folder) }
                .onFailure { pickerError = "No se pudo abrir el selector de carpetas." }
        }) {
            Text(if (state.folder == null) "Elegir carpeta de copias" else "Cambiar carpeta de copias")
        }
        state.folder?.let { Text("Carpeta: ${Uri.decode(it.lastPathSegment.orEmpty()).substringAfter(':')}", style = MaterialTheme.typography.bodySmall) }
        Button(enabled = !state.busy && state.folder != null, modifier = Modifier.fillMaxWidth().testTag("save-stock-backup"), onClick = controller::save) { Text("Guardar copia ahora") }
        Row(Modifier.fillMaxWidth()) {
            OutlinedButton(enabled = !state.busy && state.folder != null, modifier = Modifier.weight(1f), onClick = controller::search) { Text("Buscar copias") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(enabled = !state.busy, modifier = Modifier.weight(1f), onClick = {
                runCatching { file.launch(arrayOf("application/zip", "application/octet-stream")) }
                    .onFailure { pickerError = "No se pudo abrir el selector de archivos." }
            }) { Text("Abrir archivo") }
        }
        Text("Después de reinstalar, elegí la carpeta anterior para buscar tus copias. La app comprueba que sean compatibles antes de restaurar.", style = MaterialTheme.typography.bodySmall)
        if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(state.progress) }
        if (state.message.isNotBlank()) Text(state.message, modifier = Modifier.testTag("storage-result"))
        StockErrorEffect(state.error, tag = "storage-error")
        LazyColumn(Modifier.weight(1f).testTag("stock-backups"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.documents, key = { it.uri.toString() }) { document ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(document.name, style = MaterialTheme.typography.bodySmall)
                        TextButton(enabled = !state.busy, onClick = { controller.inspect(document.uri) }) { Text("Revisar y restaurar") }
                    }
                }
            }
        }
        TextButton(enabled = !state.busy, onClick = onDismiss) { Text("Cerrar") }
    }
    state.prepared?.let { prepared ->
        AlertDialog(onDismissRequest = controller::cancelRestore, title = { Text("Restaurar copia") },
            text = { Column { Text(prepared.summary); Spacer(Modifier.height(12.dp)); Text("Esta copia reemplazará los datos actuales. ¿Querés continuar?") } },
            confirmButton = { TextButton(enabled = !state.busy, onClick = controller::restore) { Text("Restaurar esta copia") } },
            dismissButton = { TextButton(enabled = !state.busy, onClick = controller::cancelRestore) { Text("Cancelar") } })
    }
}

@Composable
fun StockStorageGuard(repository: StockRepository, onRestored: () -> Unit, onOpenStorage: () -> Unit) {
    val app = LocalContext.current.applicationContext as? StockApplication ?: return
    if (repository !== app.repository) return
    val controller = app.storage
    val state by controller.state.collectAsState()
    var handled by remember { mutableIntStateOf(state.restored) }
    LaunchedEffect(state.restored) { if (handled != state.restored) { handled = state.restored; onRestored() } }
    val prefs = remember { app.getSharedPreferences("stock_storage", 0) }
    var recovery by remember { mutableStateOf(false) }
    var recoveryError by rememberStockError()
    StockErrorEffect(if (recovery) state.error else "", tag = "storage-recovery-error")
    LaunchedEffect(repository) {
        if (!prefs.getBoolean("recovery_seen", false)) {
            recovery = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching { repository.items(false).isEmpty() && repository.items(true).isEmpty() }
                    .onFailure { recoveryError = "No se pudo consultar el stock. Podés recuperar una copia desde Almacenamiento." }
                    .getOrDefault(true)
            }
            if (recovery && state.folder != null) controller.search()
        }
    }
    if (state.busy) AlertDialog(onDismissRequest = {}, title = { Text("Almacenamiento") },
        text = { Column { LinearProgressIndicator(Modifier.fillMaxWidth()); Spacer(Modifier.height(12.dp)); Text(state.progress) } }, confirmButton = {})
    else if (recovery) AlertDialog(onDismissRequest = { recovery = false; prefs.edit().putBoolean("recovery_seen", true).apply() },
        title = { Text("Recuperar tus datos") },
        text = { Text(if (state.documents.isNotEmpty()) "Encontramos copias en la carpeta guardada. Podés revisarlas y recuperar tus datos." else "Si ya usabas Stock Pañol, podés buscar una copia en el almacenamiento del celular. Elegí la carpeta donde la guardaste.") },
        confirmButton = { TextButton(onClick = { recovery = false; prefs.edit().putBoolean("recovery_seen", true).apply(); onOpenStorage() }) { Text("Buscar copia guardada") } },
        dismissButton = { TextButton(onClick = { recovery = false; prefs.edit().putBoolean("recovery_seen", true).apply() }) { Text("Ahora no") } })
}
