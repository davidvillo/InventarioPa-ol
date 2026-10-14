package com.example.stockpaniol

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StockImagePickerTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun cameraWritesFullImageToPrivateFileAndDatabaseReferenceSurvivesReopen() {
        val uri = StockImages.createCapture(context)
        val databaseContext = isolatedStockContext("camera_photo_")
        databaseContext.deleteDatabase("stock_paniol.db")
        try {
            val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLUE)
            context.contentResolver.openOutputStream(uri)!!.use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
            bitmap.recycle()
            LocalStockRepository(databaseContext).use { repository ->
                repository.receive(true, StockEntry(0, "Taladro", 1.0, "unidades", "", "", todayStockDate(), uri.toString(), "", ""))
            }
            LocalStockRepository(databaseContext).use { repository ->
                assertEquals(uri.toString(), repository.items(true).single().image)
                context.contentResolver.openInputStream(Uri.parse(repository.items(true).single().image))!!.use {
                    val restored = BitmapFactory.decodeStream(it)!!
                    assertEquals(64, restored.width)
                    assertEquals(48, restored.height)
                    restored.recycle()
                }
            }
        } finally {
            StockImages.discardCapture(context, uri)
            databaseContext.deleteDatabase("stock_paniol.db")
        }
    }

    @Test fun choosingCameraLaunchesCaptureWithOutputAndGrantsAndCancellationKeepsOldImage() {
        var launchedIntent: Intent? = null
        var selectedUri: Uri? = null
        var error = ""
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                    launchedIntent = contract.createIntent(context, input)
                    dispatchResult(requestCode, false)
                }
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                MaterialTheme {
                    StockImagePicker(onSelected = { selectedUri = it }, onError = { error = it }) { open ->
                        TextButton(onClick = open) { Text("Agregar imagen") }
                    }
                }
            }
        }
        compose.onNodeWithText("Agregar imagen").performClick()
        compose.onNodeWithText("Elegir de la galería").assertIsDisplayed()
        compose.onNodeWithText("Sacar foto con la cámara").performClick()
        compose.runOnIdle {
            val intent = launchedIntent!!
            assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, intent.action)
            assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            @Suppress("DEPRECATION") val uri = intent.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)!!
            assertEquals("${context.packageName}.images", uri.authority)
            assertNull(selectedUri)
            assertEquals("", error)
            assertTrue(runCatching { context.contentResolver.openInputStream(uri) }.isFailure)
        }
    }
}
