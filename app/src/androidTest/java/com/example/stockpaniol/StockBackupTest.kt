package com.example.stockpaniol

import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class StockBackupTest {
    private fun withStock(block: (LocalStockRepository, android.content.Context, StockBackupManager) -> Unit) {
        val context = isolatedStockContext("backup_${UUID.randomUUID()}_")
        val r = LocalStockRepository(context)
        try { block(r, context, StockBackupManager(context, r)) }
        finally { r.close(); context.deleteDatabase(StockBackupManager.DATABASE) }
    }
    private fun add(r: LocalStockRepository, name: String, tools: Boolean = false) {
        r.receive(tools, StockEntry(0, name, 2.0, if (tools) "unidades" else "kg", "Notas", "R-1", "2026-10-21", "", "Marca", "Empresa"))
    }
    private fun archive(context: android.content.Context) = File(context.cacheDir, "test-backup-${UUID.randomUUID()}.zip")
    private fun editedArchive(original: File, edit: (JSONObject) -> Unit): File {
        val output = File(original.parentFile, "edited-${UUID.randomUUID()}.zip")
        ZipInputStream(original.inputStream()).use { input ->
            ZipOutputStream(output.outputStream()).use { zip ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    zip.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == "manifest.json") {
                        val json = JSONObject(input.readBytes().toString(Charsets.UTF_8)); edit(json)
                        zip.write(json.toString().toByteArray())
                    } else input.copyTo(zip)
                    zip.closeEntry()
                }
            }
        }
        return output
    }

    @Test fun walSnapshotRestoresStockPhotosPrincipalsAndCanReopen() = withStock { r, ctx, manager ->
        r.setWriteAheadLoggingEnabled(true)
        add(r, "Arena")
        add(r, "Martillo", true)
        val item = r.items(false).single()
        val image = File(ctx.filesDir, "stock_images/test-${UUID.randomUUID()}.png").apply { parentFile!!.mkdirs() }
        image.outputStream().use { Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val originalBytes = image.readBytes()
        r.updateImages(false, item.id, listOf(FileProvider.getUriForFile(ctx, "${ctx.packageName}.images", image).toString()))
        val ids = r.items(true).single().units.map { it.id }
        r.saveToolBundle(null, "Equipo", ids, ids.last())
        r.stockName = "Mi inventario respaldado"
        val zip = archive(ctx)
        try {
            val manifest = manager.createArchive(zip)
            assertEquals(14, manifest.getInt("databaseVersion"))
            assertEquals(1, manifest.getJSONArray("photos").length())
            r.stockName = "Cambio posterior"
            add(r, "Cemento")
            val prepared = manager.inspect(zip.inputStream())
            assertEquals("Cambio posterior", r.stockName) // Inspection is read-only for the live inventory.
            manager.restore(prepared)
            assertEquals("Mi inventario respaldado", r.stockName)
            assertEquals(listOf("Arena"), r.items(false).map { it.name })
            assertEquals(ids.last(), r.toolBundles().single().principalUnitId)
            val restoredImage = r.items(false).single().images.single()
            ctx.contentResolver.openInputStream(android.net.Uri.parse(restoredImage)).use { assertArrayEquals(originalBytes, it!!.readBytes()) }
            r.close()
            LocalStockRepository(ctx).use { reopened ->
                assertEquals("Mi inventario respaldado", reopened.stockName)
                assertEquals(0, reopened.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
                add(reopened, "Piedra")
                assertEquals(2, reopened.items(false).size)
            }
        } finally { zip.delete(); image.delete() }
    }

    @Test fun futureFormatsAndFutureSchemasAreRejectedWithoutTouchingLiveData() = withStock { r, ctx, manager ->
        add(r, "Arena")
        val zip = archive(ctx)
        try {
            manager.createArchive(zip)
            for (key in listOf("formatVersion", "databaseVersion")) {
                val future = editedArchive(zip) { it.put(key, 999) }
                try {
                    assertTrue(runCatching { manager.inspect(future.inputStream()) }.isFailure)
                    assertEquals("Arena", r.items(false).single().name)
                    assertEquals(2.0, r.items(false).single().quantity, 0.0)
                } finally { future.delete() }
            }
        } finally { zip.delete() }
    }

    @Test fun damagedAndIncompleteArchivesNeverReplaceCurrentData() = withStock { r, ctx, manager ->
        add(r, "Arena")
        val zip = archive(ctx)
        try {
            manager.createArchive(zip)
            val corrupt = editedArchive(zip) { it.getJSONObject("files").getJSONObject("database/stock_paniol.db").put("sha256", "0".repeat(64)) }
            try { assertTrue(runCatching { manager.inspect(corrupt.inputStream()) }.isFailure) }
            finally { corrupt.delete() }
            val truncated = File(zip.parentFile, "truncated-${UUID.randomUUID()}.zip")
            try {
                truncated.writeBytes(zip.readBytes().copyOf(100))
                assertTrue(runCatching { manager.inspect(truncated.inputStream()) }.isFailure)
            } finally { truncated.delete() }
            assertEquals("Arena", r.items(false).single().name)
        } finally { zip.delete() }
    }

    @Test fun interruptedReplacementRecoversThePreviousDatabase() = withStock { r, ctx, _ ->
        add(r, "Arena")
        r.close()
        val live = ctx.getDatabasePath(StockBackupManager.DATABASE)
        val previous = File(live.parentFile, "${live.name}.previous-${UUID.randomUUID()}")
        assertTrue(live.renameTo(previous))
        live.writeText("incomplete replacement")
        File(ctx.filesDir, "stock-restore-pending.json").writeText(JSONObject().put("previous", previous.name).toString())
        StockBackupManager.recoverInterruptedRestore(ctx)
        assertEquals("Arena", r.items(false).single().name)
        assertFalse(previous.exists())
    }
}
