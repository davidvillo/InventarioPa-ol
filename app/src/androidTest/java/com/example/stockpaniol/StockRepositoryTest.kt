package com.example.stockpaniol

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import android.content.Context
import android.content.ContextWrapper

@RunWith(AndroidJUnit4::class)
class StockRepositoryTest {
    @Test fun persistsSeparateStocksAndAddsExistingQuantity() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val testContext = object : ContextWrapper(base) {
            override fun getDatabasePath(name: String) = base.getDatabasePath("test_$name")
            override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?) = base.openOrCreateDatabase("test_$name", mode, factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?, handler: android.database.DatabaseErrorHandler?) = base.openOrCreateDatabase("test_$name", mode, factory, handler)
        }
        base.deleteDatabase("test_stock_paniol.db")
        try {
            val repository = LocalStockRepository(testContext)
            fun entry(id: Long = 0, quantity: Double = 2.5) = StockEntry(id, "Arena", quantity, "kg", "Obra norte", "R-12/3", "2026-10-12", "", "", "")
            repository.receive(false, entry())
            val first = repository.items(false).single()
            repository.receive(false, entry(first.id, 3.0))
            assertEquals(5.5, repository.items(false).single().quantity, 0.0)
            assertTrue(repository.items(true).isEmpty())
            repository.receive(true, StockEntry(0, "Martillo", 1.0, "unidades", "Taller", "R-14", "2026-10-12", "content://photos/original", "Marca", "Proveedor"))
            val tool = repository.items(true).single()
            val material = repository.items(false).single()
            // IDs overlap between tables: changing a photo must affect only its own stock.
            assertEquals(material.id, tool.id)
            repository.updateImage(false, material.id, "content://photos/material")
            repository.updateImage(true, tool.id, "content://photos/tool")
            assertEquals(material.copy(image = "content://photos/material"), repository.items(false).single())
            assertEquals(tool.copy(image = "content://photos/tool"), repository.items(true).single())
            repository.updateImage(false, material.id, "")
            repository.stockName = "Obra norte"
            repository.close()
            val reopened = LocalStockRepository(testContext)
            assertEquals("Obra norte", reopened.stockName)
            assertEquals(5.5, reopened.items(false).single().quantity, 0.0)
            assertEquals(material.copy(image = ""), reopened.items(false).single())
            assertEquals(tool.copy(image = "content://photos/tool"), reopened.items(true).single())
            reopened.updateImage(true, tool.id, "")
            reopened.close()
            val afterRemoval = LocalStockRepository(testContext)
            assertEquals(tool.copy(image = ""), afterRemoval.items(true).single())
            assertEquals(3, afterRemoval.purchases(false).size + afterRemoval.purchases(true).size)
            try { afterRemoval.receive(false, entry(quantity = -1.0)); fail("Negative stock must be rejected") } catch (_: IllegalArgumentException) { }
            assertEquals(5.5, afterRemoval.items(false).single().quantity, 0.0)
            afterRemoval.close()
        } finally { base.deleteDatabase("test_stock_paniol.db") }
    }
}
