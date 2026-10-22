package com.example.stockpaniol

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StorageUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Before fun keepTestScreenAwake() {
        compose.activityRule.scenario.onActivity {
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }

    @Test fun freshInstallOffersRecoveryAndSettingsContainsStorage() {
        val app = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StockApplication
        app.repository.close()
        app.deleteDatabase(StockBackupManager.DATABASE)
        app.getSharedPreferences("stock_storage", 0).edit().clear().commit()
        compose.setContent { MaterialTheme { StockApp(app.repository) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Recuperar tus datos").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Buscar copia guardada").performClick()
        compose.onNodeWithTag("save-stock-backup").assertIsDisplayed()
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("☰").performClick()
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithText("Almacenamiento").performClick()
        compose.onNodeWithText("Elegir carpeta de copias").assertIsDisplayed()
        compose.onNodeWithTag("save-stock-backup").assertIsNotEnabled()
    }

    @Test fun storageReviewsCancelsAndConfirmsRestore() {
        val context = isolatedStockContext("backup_ui_${UUID.randomUUID()}_")
        val r = LocalStockRepository(context)
        val manager = StockBackupManager(context, r)
        val controller = StockStorageController(manager)
        val archive = File(context.filesDir, "stock_images/test-backup-${UUID.randomUUID()}.zip").apply { parentFile!!.mkdirs() }
        try {
            r.receive(false, StockEntry(0, "Arena", 2.0, "kg", "", "", "2026-10-21", "", "", ""))
            r.stockName = "Antes"
            manager.createArchive(archive)
            r.stockName = "Después"
            compose.setContent { MaterialTheme { StorageDialog(controller, {}) } }
            compose.onNodeWithText("Almacenamiento").assertIsDisplayed()
            compose.onNodeWithTag("save-stock-backup").assertIsNotEnabled()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.images", archive)
            controller.inspect(uri)
            compose.waitUntil(15_000) { controller.state.value.prepared != null }
            compose.onNodeWithText("Cancelar").performClick()
            assertEquals("Después", r.stockName)
            controller.inspect(uri)
            compose.waitUntil(15_000) { controller.state.value.prepared != null }
            compose.onNodeWithText("Restaurar esta copia").performClick()
            compose.waitUntil(15_000) { controller.state.value.restored == 1 }
            assertEquals("Antes", r.stockName)
            compose.onNodeWithTag("storage-result").assertTextContains("Copia restaurada", substring = true)
        } finally { r.close(); context.deleteDatabase(StockBackupManager.DATABASE); archive.delete() }
    }
}
