package com.example.stockpaniol

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolLifecycleRepositoryTest {
    private val date = "2026-10-14"
    private fun withRepository(block: (LocalStockRepository, android.content.Context) -> Unit) {
        val context = isolatedStockContext("tool_lifecycle_")
        context.deleteDatabase("stock_paniol.db")
        try { LocalStockRepository(context).use { block(it, context) } }
        finally { context.deleteDatabase("stock_paniol.db") }
    }
    private fun prepare(r: LocalStockRepository, count: Int = 2): StockItem {
        r.receive(true, StockEntry(0, "Amoladora", count.toDouble(), "unidades", "", "", date, "", "", ""))
        r.addWorker("Ana", "Ruiz", "Obra", "")
        val item = r.items(true).single()
        r.issue(true, item.id, count.toDouble(), date, r.personnel().single().id, "", item.units.map { it.id })
        return item
    }
    private fun checkStock(r: LocalStockRepository, quantity: Double) {
        val item = r.items(true).single()
        assertEquals(quantity, item.quantity, 0.0)
        assertEquals(quantity, item.availableUnits.size.toDouble(), 0.0)
        assertEquals(0, r.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
    }
    private fun rejects(action: () -> Unit) { assertTrue(runCatching(action).isFailure) }

    @Test fun damagedReturnRepairRetirementAndRestorationKeepStockAndFullHistoryAcrossReopen() = withRepository { r, context ->
        val item = prepare(r)
        val broken = item.units[0]
        val healthy = item.units[1]
        val loan = r.movements(true).first { it.kind == "Egreso" }
        r.returnStock(true, loan.id, 2.0, date, null, "Vuelven de obra", item.units.map { it.id }, mapOf(broken.id to "No enciende"))
        checkStock(r, 1.0)
        assertEquals(0.0, r.movements(true).first { it.id == loan.id }.pending, 0.0)
        assertEquals(ToolCondition.DAMAGED, r.items(true).single().units.first { it.id == broken.id }.condition)
        assertEquals(listOf(healthy.id), r.items(true).single().availableUnits.map { it.id })
        assertEquals(1, r.movements(true).count { it.kind == "Devolución dañada" })
        assertEquals(1, r.movements(true).count { it.kind == "Devolución" })
        r.changeToolState(broken.id, ToolAction.SEND_REPAIR, date, "Revisar motor", "Taller Norte")
        checkStock(r, 1.0)
        r.close()
        LocalStockRepository(context).use { reopened ->
            val saved = reopened.items(true).single().units.first { it.id == broken.id }
            assertEquals(ToolCondition.IN_REPAIR, saved.condition)
            assertEquals("Taller Norte", saved.repairPlace)
            assertEquals(date, saved.repairDate)
            assertEquals("No enciende", saved.damageNotes)
            reopened.changeToolState(broken.id, ToolAction.UNREPAIRED, date, "No hay repuesto")
            checkStock(reopened, 1.0)
            reopened.changeToolState(broken.id, ToolAction.SEND_REPAIR, date, "", "Taller Sur")
            reopened.changeToolState(broken.id, ToolAction.REPAIRED, date, "Motor reemplazado")
            checkStock(reopened, 2.0)
            reopened.changeToolState(broken.id, ToolAction.DAMAGE, date, "Se quebró la carcasa")
            checkStock(reopened, 1.0)
            reopened.changeToolState(broken.id, ToolAction.RETIRE, date, "Sin reparación")
            checkStock(reopened, 1.0)
            assertEquals(ToolCondition.RETIRED, reopened.items(true).single().units.first { it.id == broken.id }.condition)
            assertFalse(reopened.items(true).single().identitySummary.contains(broken.identifier))
            reopened.changeToolState(broken.id, ToolAction.RESTORE, date, "Revisar baja")
            checkStock(reopened, 1.0)
            val restored = reopened.items(true).single().units.first { it.id == broken.id }
            assertEquals(ToolCondition.DAMAGED, restored.condition)
            assertEquals(broken.identifier, restored.identifier)
            val history = reopened.movements(true)
            assertEquals(11, history.size)
            assertTrue(history.first { it.kind == "Envío a reparación" }.notes.contains("Taller Norte"))
            val damagedReturn = history.first { it.kind == "Devolución dañada" }
            assertTrue(damagedReturn.notes.contains("No enciende"))
            reopened.identifyUnit(true, broken.id, "Bosch", "NUEVA-ETIQUETA")
            assertEquals(broken.label, reopened.movements(true).first { it.id == damagedReturn.id }.identitySummary)
        }
    }

    @Test fun invalidTransitionsMissingDamageDetailsAndDeletingAuditHistoryAreRejectedAtomically() = withRepository { r, _ ->
        val item = prepare(r)
        val unit = item.units.first()
        val loan = r.movements(true).first { it.kind == "Egreso" }
        rejects { r.changeToolState(unit.id, ToolAction.DAMAGE, date, "Rota") }
        rejects { r.returnStock(true, loan.id, 1.0, date, null, "", listOf(unit.id), mapOf(unit.id to "")) }
        rejects { r.returnStock(true, loan.id, 1.0, date, null, "", listOf(unit.id), mapOf(item.units.last().id to "Rota")) }
        checkStock(r, 0.0)
        assertEquals(2, r.movements(true).size)
        r.returnStock(true, loan.id, 1.0, date, null, "", listOf(unit.id), mapOf(unit.id to "Disco bloqueado"))
        checkStock(r, 0.0)
        rejects { r.issue(true, item.id, 1.0, date, r.personnel().single().id, "", listOf(unit.id)) }
        rejects { r.changeToolState(unit.id, ToolAction.REPAIRED, date, "") }
        rejects { r.changeToolState(unit.id, ToolAction.SEND_REPAIR, date, "", "") }
        rejects { r.changeToolState(unit.id, ToolAction.SEND_REPAIR, "2026-10-13", "", "Taller") }
        rejects { r.deleteMovement(true, loan.id) }
        rejects { r.deleteMovement(true, r.movements(true).first { it.kind == "Devolución dañada" }.id) }
        rejects { r.deleteMovement(true, r.movements(true).first { it.kind == "Ingreso" }.id) }
        assertEquals(3, r.movements(true).size)
        checkStock(r, 0.0)
        r.changeToolState(unit.id, ToolAction.RETIRE, date, "Irreparable")
        rejects { r.changeToolState(unit.id, ToolAction.RESTORE, date, "") }
        assertEquals(ToolCondition.RETIRED, r.items(true).single().units.first { it.id == unit.id }.condition)
    }

    @Test fun versionSixMigrationPreservesLoanPhotosIdentityAndPartialReturn() = withRepository { r, context ->
        val item = prepare(r)
        val loan = r.movements(true).first { it.kind == "Egreso" }
        r.returnStock(true, loan.id, 1.0, date, null, "", listOf(item.units.first().id))
        r.updateImages(true, item.id, listOf("content://photos/bosch", "content://photos/wadfow"))
        val before = r.items(true).single()
        removeToolLifecycleSchema(r.writableDatabase)
        r.writableDatabase.version = 6
        r.close()
        LocalStockRepository(context).use { upgraded ->
            assertEquals(10, upgraded.readableDatabase.version)
            assertEquals(before, upgraded.items(true).single())
            assertEquals(1.0, upgraded.movements(true).first { it.id == loan.id }.pending, 0.0)
            upgraded.returnStock(true, loan.id, 1.0, date, null, "", listOf(item.units.last().id), mapOf(item.units.last().id to "Rota"))
            checkStock(upgraded, 1.0)
        }
    }
}
