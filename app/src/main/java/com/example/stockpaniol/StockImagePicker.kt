package com.example.stockpaniol

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/** Full-size camera photos live in durable app storage, never in the cache. */
object StockImages {
    fun loadBitmap(context: Context, uri: Uri, targetPixels: Int): Bitmap? {
        require(targetPixels > 0)
        val resolver = context.contentResolver
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > targetPixels * 2) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    fun createCapture(context: Context): Uri {
        val directory = File(context.filesDir, "stock_images")
        check(directory.isDirectory || directory.mkdirs()) { "No se pudo crear la carpeta de fotos." }
        val file = File.createTempFile("foto_", ".jpg", directory)
        return FileProvider.getUriForFile(context, "${context.packageName}.images", file)
    }

    fun discardCapture(context: Context, uri: Uri) {
        // Delete only captures generated in our own directory, never gallery documents.
        if (uri.authority != "${context.packageName}.images") return
        val directory = File(context.filesDir, "stock_images").canonicalFile
        val file = File(directory, uri.lastPathSegment ?: return).canonicalFile
        if (file.parentFile == directory) file.delete()
    }
}

@Composable
fun StockImagePicker(enabled: Boolean = true, onSelected: (Uri) -> Unit, onError: (String) -> Unit,
    content: @Composable (openChooser: () -> Unit) -> Unit) {
    val context = LocalContext.current
    var chooser by rememberSaveable { mutableStateOf(false) }
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraContract = remember {
        object : ActivityResultContracts.TakePicture() {
            override fun createIntent(context: Context, input: Uri): Intent = super.createIntent(context, input)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }
    val camera = rememberLauncherForActivityResult(cameraContract) { success ->
        val uri = pendingCapture?.let(Uri::parse)
        pendingCapture = null
        if (uri != null) {
            if (success) onSelected(uri) else StockImages.discardCapture(context, uri)
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                .onSuccess { onSelected(uri) }
                .onFailure { onError("No se pudo conservar la imagen. Elegí otra.") }
        }
    }
    content { if (enabled && pendingCapture == null) chooser = true }
    if (chooser) AlertDialog(onDismissRequest = { chooser = false }, title = { Text("Agregar imagen") }, text = {
        Column {
            TextButton(enabled = enabled, onClick = {
                chooser = false
                runCatching {
                    val uri = StockImages.createCapture(context)
                    pendingCapture = uri.toString()
                    camera.launch(uri)
                }.onFailure {
                    pendingCapture?.let { StockImages.discardCapture(context, Uri.parse(it)) }
                    pendingCapture = null
                    onError("No se pudo abrir la cámara. Comprobá que haya una aplicación de cámara disponible.")
                }
            }) { Text("Sacar foto con la cámara") }
            TextButton(enabled = enabled, onClick = {
                chooser = false
                runCatching { gallery.launch(arrayOf("image/*")) }
                    .onFailure { onError("No se pudo abrir el selector de imágenes.") }
            }) { Text("Elegir de la galería") }
        }
    }, confirmButton = { TextButton(onClick = { chooser = false }) { Text("Cancelar") } })
}
