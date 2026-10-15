package com.example.stockpaniol

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StockPhotoGalleryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun swipeShowsNextPhotoAndDeletionReenablesAddingBelowFive() {
        val original = (1..5).map { "content://photos/$it.jpg" }
        var photos by mutableStateOf(original)
        compose.setContent { MaterialTheme {
            StockPhotoGallery("Amoladora", photos, false, { photos = it }, { error(it) }, {})
        } }
        compose.onNodeWithText("Foto 1 de 5 · 5/5 fotos").assertIsDisplayed()
        compose.onNodeWithText("Agregar foto (5/5)").assertIsNotEnabled()
        compose.onNodeWithTag("Galería de fotos").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Foto 2 de 5 · 5/5 fotos").assertIsDisplayed()
        compose.onNodeWithContentDescription("Eliminar foto 2 de Amoladora").performClick()
        compose.waitForIdle()
        assertEquals(original.filterIndexed { index, _ -> index != 1 }, photos)
        compose.onNodeWithText("Foto 2 de 4 · 4/5 fotos").assertIsDisplayed()
        compose.onNodeWithText("Agregar foto (4/5)").assertIsEnabled()
        repeat(2) { compose.onNodeWithTag("Galería de fotos").performTouchInput { swipeLeft() }; compose.waitForIdle() }
        for (size in 4 downTo 1) {
            compose.onNodeWithText("Foto $size de $size · $size/5 fotos").assertIsDisplayed()
            compose.onNodeWithContentDescription("Eliminar foto $size de Amoladora").performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithText("Sin fotos (0/5)").assertIsDisplayed()
        compose.onNodeWithText("Agregar foto (0/5)").assertIsEnabled()
    }

    @Test fun simpleViewOpensSavedGalleryAndDeletingFirstPhotoUpdatesTheCover() {
        val context = isolatedStockContext("photo_gallery_ui_")
        context.deleteDatabase("stock_paniol.db")
        val photos = listOf("content://photos/bosch.jpg", "content://photos/wadfow.jpg")
        val repository = LocalStockRepository(context)
        try {
            repository.receive(true, StockEntry(0, "Amoladora", 2.0, "unidades", "", "", todayStockDate(), StockPhotos.encode(photos), "", ""))
            compose.setContent { MaterialTheme { StockApp(repository) } }
            compose.onNodeWithText("Herramientas").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Amoladora").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Sin imagen").performClick()
            compose.onNodeWithText("Foto 1 de 2 · 2/5 fotos").assertIsDisplayed()
            compose.onNodeWithContentDescription("Eliminar foto 1 de Amoladora").performClick()
            compose.waitUntil(10_000) { repository.items(true).single().images == photos.drop(1) }
            compose.waitForIdle()
            compose.onNodeWithText("Foto 1 de 1 · 1/5 fotos").assertIsDisplayed()
            assertEquals(photos[1], repository.items(true).single().images.first())
            compose.onNodeWithText("Cerrar").performClick()
            compose.waitForIdle()
        } finally { repository.close(); context.deleteDatabase("stock_paniol.db") }
    }
}
