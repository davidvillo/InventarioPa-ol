package com.example.stockpaniol

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StockPhotosRepositoryTest {
    @Test fun galleriesAppendPersistAndRejectSixthPhotoWithoutChangingStock() {
        val context = isolatedStockContext("photo_gallery_")
        context.deleteDatabase("stock_paniol.db")
        val photos = (1..6).map { "content://photos/foto_$it.jpg" }
        fun entry(tools: Boolean, image: String) = StockEntry(0, if (tools) "Amoladora 7 pulgadas" else "Arnés", 1.0,
            "unidades", "", "", todayStockDate(), image, "", "", control = StockControl.INDIVIDUAL)
        try {
            LocalStockRepository(context).use { repository ->
                for (tools in listOf(true, false)) {
                    repository.receive(tools, entry(tools, photos[0]))
                    assertEquals(photos[0], repository.items(tools).single().image)
                    repository.receive(tools, entry(tools, StockPhotos.encode(photos.drop(1).take(4))))
                    val before = repository.items(tools).single()
                    assertEquals(photos.take(5), before.images)
                    assertTrue(runCatching { repository.receive(tools, entry(tools, photos[5])) }.isFailure)
                    assertEquals(before, repository.items(tools).single())
                    assertTrue(runCatching { repository.updateImages(tools, before.id, photos) }.isFailure)
                    assertEquals(before, repository.items(tools).single())
                }
            }
            LocalStockRepository(context).use { repository ->
                assertEquals(photos.take(5), repository.items(true).single().images)
                assertEquals(photos.take(5), repository.items(false).single().images)
                val toolId = repository.items(true).single().id
                repository.updateImages(true, toolId, photos.drop(1).take(4))
                assertEquals(photos.drop(1).take(4), repository.items(true).single().images)
                assertEquals(photos.take(5), repository.items(false).single().images)
                repository.updateImages(true, toolId, emptyList())
                assertEquals("", repository.items(true).single().image)
                assertTrue(repository.items(true).single().images.isEmpty())
            }
        } finally { context.deleteDatabase("stock_paniol.db") }
    }
}
