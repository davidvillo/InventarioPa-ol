package com.example.stockpaniol

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import android.util.AtomicFile
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class StockBackupDocument(val uri: Uri, val name: String, val modified: Long)
data class PreparedStockBackup(val directory: File, val manifest: JSONObject, val summary: String)

/** Portable format 1: manifest, consistent SQLite snapshot, and every referenced photo. */
class StockBackupManager(val context: Context, private val repository: LocalStockRepository) {
    companion object {
        const val FORMAT = 1
        const val DATABASE = "stock_paniol.db"
        const val MAX_BYTES = 16L * 1024 * 1024 * 1024
        private const val MARKER = "stock-restore-pending.json"

        fun recoverInterruptedRestore(context: Context) {
            val marker = AtomicFile(File(context.filesDir, MARKER))
            if (!marker.baseFile.exists() && !File(marker.baseFile.path + ".bak").exists()) return
            val info = JSONObject(marker.openRead().bufferedReader().use { it.readText() })
            val live = context.getDatabasePath(DATABASE)
            val old = File(live.parentFile, info.getString("previous"))
            require(old.parentFile?.canonicalFile == live.parentFile?.canonicalFile) { "Ruta de recuperación inválida." }
            if (old.exists()) {
                SQLiteDatabase.deleteDatabase(live)
                check(old.renameTo(live)) { "No se pudo recuperar la base anterior." }
                for (suffix in listOf("-wal", "-shm", "-journal")) {
                    val sidecar = File(old.path + suffix)
                    if (sidecar.exists()) check(sidecar.renameTo(File(live.path + suffix)))
                }
            }
            marker.delete()
        }
    }

    private fun digest(file: File): String = file.inputStream().use { input ->
        val sha = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(128 * 1024)
        while (true) { val n = input.read(buffer); if (n < 0) break; sha.update(buffer, 0, n) }
        sha.digest().joinToString("") { "%02x".format(it) }
    }

    private fun copyLimited(input: InputStream, output: OutputStream, maximum: Long = MAX_BYTES): Long {
        val buffer = ByteArray(128 * 1024)
        var count = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return count
            count += n
            require(count <= maximum) { "La copia excede el tamaño permitido." }
            output.write(buffer, 0, n)
        }
    }

    private fun verifyDatabase(db: SQLiteDatabase) {
        check(db.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }) { "La base de la copia está dañada." }
        check(db.rawQuery("PRAGMA foreign_key_check", null).use { it.count == 0 }) { "La copia tiene relaciones incompletas." }
    }

    private fun snapshot(target: File) {
        val source = repository.writableDatabase
        source.beginTransaction()
        try {
            SQLiteDatabase.openOrCreateDatabase(target, null).use { destination ->
                destination.beginTransaction()
                try {
                    val tables = source.rawQuery("SELECT name, sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name", null).use { c ->
                        buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
                    }
                    tables.forEach {
                        if (it.first == "android_metadata") destination.execSQL("DROP TABLE IF EXISTS android_metadata")
                        destination.execSQL(it.second)
                    }
                    fun copyRows(table: String) {
                        source.rawQuery("SELECT * FROM [${table.replace("]", "]]")}]", null).use { c ->
                            while (c.moveToNext()) {
                                val values = ContentValues()
                                for (i in 0 until c.columnCount) when (c.getType(i)) {
                                    0 -> values.putNull(c.getColumnName(i))
                                    1 -> values.put(c.getColumnName(i), c.getLong(i))
                                    2 -> values.put(c.getColumnName(i), c.getDouble(i))
                                    4 -> values.put(c.getColumnName(i), c.getBlob(i))
                                    else -> values.put(c.getColumnName(i), c.getString(i))
                                }
                                destination.insertOrThrow(table, null, values)
                            }
                        }
                    }
                    tables.forEach { copyRows(it.first) }
                    val hasSequence = source.rawQuery("SELECT 1 FROM sqlite_master WHERE name='sqlite_sequence'", null).use { it.moveToFirst() }
                    if (hasSequence) { destination.execSQL("DELETE FROM sqlite_sequence"); copyRows("sqlite_sequence") }
                    source.rawQuery("SELECT sql FROM sqlite_master WHERE type IN ('index','view','trigger') AND sql IS NOT NULL ORDER BY CASE type WHEN 'index' THEN 0 WHEN 'view' THEN 1 ELSE 2 END", null).use { c ->
                        while (c.moveToNext()) destination.execSQL(c.getString(0))
                    }
                    destination.version = source.version
                    destination.setTransactionSuccessful()
                } finally { destination.endTransaction() }
                verifyDatabase(destination)
            }
        } finally { source.endTransaction() }
    }

    fun createArchive(output: File): JSONObject {
        val stage = File(context.cacheDir, "stock-export-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val dbFile = File(stage, DATABASE)
            snapshot(dbFile)
            val photos = JSONArray()
            val files = JSONObject()
            val sources = linkedMapOf("database/$DATABASE" to dbFile)
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val uris = linkedSetOf<String>()
                for (table in listOf("materiales", "herramientas")) db.rawQuery("SELECT imagen FROM $table", null).use { c ->
                    while (c.moveToNext()) uris.addAll(StockPhotos.decode(c.getString(0)))
                }
                uris.forEachIndexed { index, value ->
                    val photo = File(stage, "photo-$index")
                    try {
                        context.contentResolver.openInputStream(Uri.parse(value)).use { input ->
                            checkNotNull(input) { "No se pudo leer una foto." }
                            photo.outputStream().use { copyLimited(input, it) }
                        }
                    } catch (e: Exception) { throw IllegalStateException("No se pudo incluir una foto. Revisá sus permisos antes de guardar la copia.", e) }
                    val name = "images/${digest(photo)}.img"
                    if (!sources.containsKey(name)) sources[name] = photo
                    photos.put(JSONObject().put("uri", value).put("file", name))
                }
                sources.forEach { (name, file) -> files.put(name, JSONObject().put("size", file.length()).put("sha256", digest(file))) }
                val counts = JSONObject()
                for (table in listOf("materiales", "herramientas", "personal", "obras", "movimientos_materiales", "movimientos_herramientas")) {
                    counts.put(table, db.rawQuery("SELECT count(*) FROM $table", null).use { it.moveToFirst(); it.getLong(0) })
                }
                val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
                val manifest = JSONObject().put("application", "StockPanol").put("formatVersion", FORMAT)
                    .put("databaseVersion", db.version).put("appVersion", version).put("createdAt", System.currentTimeMillis())
                    .put("stockName", repository.stockName).put("counts", counts).put("photos", photos).put("files", files)
                ZipOutputStream(output.outputStream().buffered()).use { zip ->
                    zip.setLevel(java.util.zip.Deflater.BEST_SPEED)
                    zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8)); zip.closeEntry()
                    sources.forEach { (name, file) ->
                        zip.putNextEntry(ZipEntry(name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    }
                }
                return manifest
            }
        } finally { stage.deleteRecursively() }
    }

    fun saveToFolder(tree: Uri): StockBackupDocument {
        val local = File(context.cacheDir, "stock-backup-${UUID.randomUUID()}.zip")
        var document: Uri? = null
        try {
            createArchive(local)
            val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())
            val name = "Stock-Panol-$stamp-${UUID.randomUUID().toString().take(6)}.zip"
            document = checkNotNull(DocumentsContract.createDocument(context.contentResolver, root, "application/octet-stream", "$name.partial")) { "No se pudo crear el archivo en esa carpeta." }
            context.contentResolver.openOutputStream(document, "wt").use { output ->
                checkNotNull(output); local.inputStream().use { it.copyTo(output) }
            }
            // Reopen the actual document; only report success after every entry passes validation.
            inspect(document).directory.deleteRecursively()
            document = checkNotNull(DocumentsContract.renameDocument(context.contentResolver, document, name)) { "No se pudo finalizar la copia en esa carpeta." }
            return StockBackupDocument(document, name, System.currentTimeMillis())
        } catch (e: Exception) {
            document?.let { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) } }
            throw e
        } finally { local.delete() }
    }

    fun listFolder(tree: Uri): List<StockBackupDocument> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        return context.contentResolver.query(children, columns, null, null, null).use { c ->
            checkNotNull(c) { "No se pudo abrir la carpeta. Volvé a seleccionarla." }
            buildList { while (c.moveToNext()) {
                val name = c.getString(1)
                if (name.startsWith("Stock-Panol-") && name.endsWith(".zip", true)) add(StockBackupDocument(DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)), name, c.getLong(2)))
            } }.sortedByDescending { it.modified }
        }
    }

    fun inspect(uri: Uri): PreparedStockBackup = context.contentResolver.openInputStream(uri).use { input ->
        checkNotNull(input) { "No se pudo abrir la copia." }
        inspect(input)
    }

    fun inspect(input: InputStream): PreparedStockBackup {
        val stage = File(context.cacheDir, "stock-restore-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val manifest: JSONObject
            ZipInputStream(input.buffered()).use { zip ->
                require(zip.nextEntry?.name == "manifest.json") { "El archivo no es una copia de Stock Pañol." }
                val buffer = java.io.ByteArrayOutputStream()
                copyLimited(zip, buffer, 1024 * 1024)
                manifest = JSONObject(buffer.toString("UTF-8"))
                require(manifest.getString("application") == "StockPanol") { "La copia pertenece a otra aplicación." }
                require(manifest.getInt("formatVersion") == FORMAT) { "Esta copia necesita una versión más nueva de la app." }
                require(manifest.getInt("databaseVersion") in 1..LocalStockRepository.DATABASE_VERSION) { "La copia necesita una versión más nueva de la app. No se modificaron tus datos." }
                val files = manifest.getJSONObject("files")
                require(files.has("database/$DATABASE") && files.length() <= 100_000) { "La copia está incompleta." }
                val seen = mutableSetOf<String>()
                var total = 0L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    require(name == "database/$DATABASE" || Regex("images/[a-f0-9]{64}\\.img").matches(name)) { "La copia contiene un archivo no permitido." }
                    require(!entry.isDirectory && files.has(name) && seen.add(name)) { "La copia contiene archivos repetidos o no declarados." }
                    val target = File(stage, name).apply { parentFile?.mkdirs() }
                    total += target.outputStream().use { copyLimited(zip, it, MAX_BYTES - total) }
                    val expected = files.getJSONObject(name)
                    require(target.length() == expected.getLong("size") && digest(target) == expected.getString("sha256")) { "La copia está incompleta o dañada. No se modificaron tus datos." }
                }
                require(seen.size == files.length()) { "Faltan archivos en la copia." }
            }
            val database = File(stage, "database/$DATABASE")
            SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                require(db.version == manifest.getInt("databaseVersion")) { "La versión de la base no coincide con la copia." }
                verifyDatabase(db)
            }
            val counts = JSONObject()
            stagedRepository(stage).use { staged ->
                verifyDatabase(staged.writableDatabase)
                staged.items(false); staged.items(true); staged.personnel(true); staged.toolBundles()
                validatePhotos(stage, manifest, staged.readableDatabase)
                for (table in listOf("materiales", "herramientas", "personal")) {
                    counts.put(table, staged.readableDatabase.rawQuery("SELECT count(*) FROM $table", null).use { it.moveToFirst(); it.getLong(0) })
                }
            }
            val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ROOT).format(Date(manifest.getLong("createdAt")))
            val summary = "${manifest.getString("stockName")}\n$date · versión ${manifest.optString("appVersion")}\n${counts.getLong("materiales")} materiales · ${counts.getLong("herramientas")} herramientas · ${counts.getLong("personal")} personas\nIncluye ${manifest.getJSONArray("photos").length()} referencias a fotos."
            return PreparedStockBackup(stage, manifest, summary)
        } catch (e: Exception) { stage.deleteRecursively(); throw e }
    }

    private fun stagedRepository(stage: File): LocalStockRepository {
        val directory = File(stage, "database")
        val wrapped = object : ContextWrapper(context) {
            override fun getDatabasePath(name: String) = File(directory, name)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?) = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?) = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, handler)
        }
        return LocalStockRepository(wrapped)
    }

    private fun validatePhotos(stage: File, manifest: JSONObject, db: SQLiteDatabase): Map<String, String> {
        val photos = manifest.getJSONArray("photos")
        val result = mutableMapOf<String, String>()
        for (i in 0 until photos.length()) {
            val photo = photos.getJSONObject(i)
            val name = photo.getString("file")
            require(Regex("images/[a-f0-9]{64}\\.img").matches(name) && manifest.getJSONObject("files").has(name) && File(stage, name).isFile) { "Falta una foto en la copia." }
            require(result.put(photo.getString("uri"), name) == null) { "La copia repite una referencia a una foto." }
        }
        for (table in listOf("materiales", "herramientas")) db.rawQuery("SELECT imagen FROM $table", null).use { c ->
            while (c.moveToNext()) require(StockPhotos.decode(c.getString(0)).all { it in result }) { "Faltan fotos de un artículo en la copia." }
        }
        return result
    }

    fun restore(prepared: PreparedStockBackup) {
        val stage = prepared.directory
        try {
            stagedRepository(stage).use { staged ->
                val db = staged.writableDatabase
                val mapping = validatePhotos(stage, prepared.manifest, db).mapValues { (_, name) ->
                    val image = File(context.filesDir, "stock_images/${name.substringAfter('/')}")
                    image.parentFile?.mkdirs()
                    val source = File(stage, name)
                    require(digest(source) == prepared.manifest.getJSONObject("files").getJSONObject(name).getString("sha256")) { "Una foto de la copia cambió durante la restauración." }
                    if (image.exists()) require(digest(image) == digest(source)) { "Hay una foto diferente con el mismo nombre." }
                    else source.copyTo(image)
                    FileProvider.getUriForFile(context, "${context.packageName}.images", image).toString()
                }
                db.beginTransaction()
                try {
                    for (table in listOf("materiales", "herramientas")) db.rawQuery("SELECT id, imagen FROM $table", null).use { c ->
                        while (c.moveToNext()) {
                            val values = ContentValues().apply { put("imagen", StockPhotos.encode(StockPhotos.decode(c.getString(1)).map { mapping.getValue(it) })) }
                            db.update(table, values, "id=?", arrayOf(c.getLong(0).toString()))
                        }
                    }
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
                verifyDatabase(db)
            }
            val live = context.getDatabasePath(DATABASE)
            val replacement = File(live.parentFile, "$DATABASE.incoming-${UUID.randomUUID()}")
            File(stage, "database/$DATABASE").inputStream().use { input ->
                replacement.outputStream().use { output -> input.copyTo(output); output.fd.sync() }
            }
            synchronized(repository) {
                repository.close()
                val previous = File(live.parentFile, "$DATABASE.previous-${UUID.randomUUID()}")
                val marker = AtomicFile(File(context.filesDir, MARKER))
                val stream = marker.startWrite()
                try { stream.write(JSONObject().put("previous", previous.name).toString().toByteArray()); marker.finishWrite(stream) }
                catch (e: Exception) { marker.failWrite(stream); throw e }
                try {
                    if (live.exists()) check(live.renameTo(previous)) { "No se pudo conservar la base anterior." }
                    for (suffix in listOf("-wal", "-shm", "-journal")) {
                        val sidecar = File(live.path + suffix)
                        if (sidecar.exists()) check(sidecar.renameTo(File(previous.path + suffix)))
                    }
                    check(replacement.renameTo(live)) { "No se pudo instalar la base restaurada." }
                    verifyDatabase(repository.writableDatabase)
                    repository.items(false); repository.items(true)
                    marker.delete()
                } catch (e: Exception) {
                    repository.close()
                    recoverInterruptedRestore(context)
                    throw e
                } finally { replacement.delete() }
            }
        } finally { stage.deleteRecursively() }
    }
}
