package com.example.stockpaniol

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolBundlePrincipalTest {
    private fun withStock(block: (LocalStockRepository, android.content.Context) -> Unit) {
        val ctx = isolatedStockContext("principal_")
        try { LocalStockRepository(ctx).use { block(it, ctx) } }
        finally { ctx.deleteDatabase("stock_paniol.db") }
    }
    private fun add(r: LocalStockRepository, name: String): StockItem {
        r.receive(true, StockEntry(0, name, 1.0, "unidades", "", "", "2026-10-21", "", "", ""))
        return r.items(true).first { it.name == name }
    }
    @Test fun principalPersistsValidatesMembershipAndCanBeChangedOrRemoved() = withStock { r, ctx ->
        val tools = listOf(add(r, "Martillo"), add(r, "Cincel"), add(r, "Caja"))
        val ids = tools.map { it.units.single().id }
        r.saveToolBundle(null, "Equipo", ids.take(2), ids[1])
        val b = r.toolBundles().single()
        assertEquals(ids[1], b.principalUnitId)
        LocalStockRepository(ctx).use { assertEquals(ids[1], it.toolBundles().single().principalUnitId) }
        assertTrue(runCatching { r.saveToolBundle(b.id, "Equipo", ids.take(2), ids[2]) }.isFailure)
        assertEquals(ids[1], r.toolBundles().single().principalUnitId)
        r.saveToolBundle(b.id, "Equipo editado", ids.take(2))
        assertEquals(ids[1], r.toolBundles().single().principalUnitId)
        r.saveToolBundle(b.id, "Equipo nuevo", listOf(ids[0], ids[2]), ids[2])
        assertEquals(ids[2], r.toolBundles().single().principalUnitId)
        assertTrue(runCatching { r.deleteTool(tools[2].id) }.isFailure)
        r.dissolveToolBundle(b.id)
        r.deleteTool(tools[2].id)
        assertEquals(0, r.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
    }
    @Test fun migrationFrom13AssignsPrincipalWithoutChangingMembershipOrStock() = withStock { r, ctx ->
        val ids = listOf(add(r, "Caja"), add(r, "Taladro")).map { it.units.single().id }
        r.saveToolBundle(null, "Conjunto anterior", ids)
        val db = r.writableDatabase
        db.setForeignKeyConstraintsEnabled(false)
        db.execSQL("CREATE TABLE conjuntos_13 (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL COLLATE NOCASE UNIQUE)")
        db.execSQL("INSERT INTO conjuntos_13(id, nombre) SELECT id, nombre FROM conjuntos_herramientas")
        db.execSQL("DROP TABLE conjuntos_herramientas")
        db.execSQL("ALTER TABLE conjuntos_13 RENAME TO conjuntos_herramientas")
        db.setForeignKeyConstraintsEnabled(true)
        r.writableDatabase.version = 13
        r.close()
        LocalStockRepository(ctx).use { upgraded ->
            assertEquals(14, upgraded.readableDatabase.version)
            assertEquals(ids.toSet(), upgraded.toolBundles().single().unitIds.toSet())
            assertEquals(ids.minOrNull(), upgraded.toolBundles().single().principalUnitId)
            assertTrue(upgraded.items(true).all { it.quantity == 1.0 })
            assertEquals(0, upgraded.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
        }
    }
}
