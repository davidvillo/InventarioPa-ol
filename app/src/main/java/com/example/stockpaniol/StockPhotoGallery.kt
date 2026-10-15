package com.example.stockpaniol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
internal fun StockPhotoGallery(name: String, images: List<String>, busy: Boolean,
    onSave: (List<String>) -> Unit, onError: (String) -> Unit, onDismiss: () -> Unit) {
    val pager = rememberPagerState(pageCount = { maxOf(images.size, 1) })
    val index = pager.currentPage.coerceAtMost(maxOf(images.lastIndex, 0))
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(name) }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalPager(state = pager, userScrollEnabled = !busy, modifier = Modifier.fillMaxWidth().height(280.dp).testTag("Galería de fotos")) { page ->
                StockPhoto(images.getOrNull(page).orEmpty(), Modifier.fillMaxSize(), expanded = true)
            }
            Text(if (images.isEmpty()) "Sin fotos (0/${StockPhotos.LIMIT})" else "Foto ${index + 1} de ${images.size} · ${images.size}/${StockPhotos.LIMIT} fotos")
            if (images.size > 1) Text("Deslizá para ver las demás fotos.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                StockImagePicker(enabled = !busy && images.isNotEmpty(), onSelected = { uri ->
                    val updated = images.toMutableList().apply { set(index, uri.toString()) }
                    if (updated.distinct().size == updated.size) onSave(updated) else onError("Esa foto ya está en la galería.")
                }, onError = onError) { open ->
                    IconButton(enabled = !busy && images.isNotEmpty(), onClick = open) {
                        Icon(painterResource(R.drawable.ic_edit_image), "Cambiar foto ${index + 1} de $name")
                    }
                }
                IconButton(enabled = !busy && images.isNotEmpty(), onClick = { onSave(images.filterIndexed { position, _ -> position != index }) }) {
                    Icon(painterResource(R.drawable.ic_delete_image), "Eliminar foto ${index + 1} de $name")
                }
            }
            StockImagePicker(enabled = !busy && images.size < StockPhotos.LIMIT, onSelected = { uri ->
                if (uri.toString() !in images) onSave(images + uri.toString()) else onError("Esa foto ya está en la galería.")
            }, onError = onError) { open ->
                TextButton(enabled = !busy && images.size < StockPhotos.LIMIT, onClick = open) { Text("Agregar foto (${images.size}/${StockPhotos.LIMIT})") }
            }
            if (busy) Text("Guardando…", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cerrar") } })
}
