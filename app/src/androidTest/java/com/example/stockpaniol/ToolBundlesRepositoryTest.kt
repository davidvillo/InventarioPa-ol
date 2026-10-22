package com.example.stockpaniol

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolBundlesRepositoryTest {
    private val date = "2026-10-15"
    private fun withStock(block: (LocalStockRepository, android.content.Context) -> Unit) {
        val context = isolatedStockContext("tool_bundles_")
        try { LocalStockRepository(context).use { block(it, context) } }
        finally { context.deleteDatabase("stock_paniol.db") }
    }
    private fun add(r: LocalStockRepository, name: String, count: Int = 1): StockItem {
        r.receive(true, StockEntry(0, name, count.toDouble(), "unidades", "", "", date, "", "", ""))
        return r.items(true).first { it.name == name }
    }
    private fun worker(r: LocalStockRepository): Long {
        r.addWorker("Ana", "Ruiz", "Obra", "")
        return r.personnel().single().id
    }
    private fun check(r: LocalStockRepository) {
        r.items(true).forEach { assertEquals(it.availableUnits.size.toDouble(), it.quantity, 0.0) }
        assertEquals(0, r.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
    }

    @Test fun threeToolSetIssuesAndReturnsFromAnyMemberAndCanBeDissolvedAfterReopen() = withStock { r, context ->
        val box = add(r, "Caja")
        val hammer = add(r, "Martillo demoledor")
        val chisel = add(r, "Cincel")
        val ids = listOf(box, hammer, chisel).map { it.units.single().id }
        r.saveToolBundle(null, "Caja de demolición", ids)
        val person = worker(r)
        r.issue(true, hammer.id, 1.0, date, person, "Trabajo", listOf(ids[1]))
        assertTrue(r.items(true).all { it.quantity == 0.0 })
        assertTrue(runCatching { r.dissolveToolBundle(r.toolBundles().single().id) }.isFailure)
        LocalStockRepository(context).use { reopened ->
            val loan = reopened.movements(true).first { it.kind == "Egreso" && it.itemId == chisel.id }
            assertEquals(3, reopened.toolLoanUnits(loan.id).size)
            reopened.returnStock(true, loan.id, 1.0, date, null, "", listOf(ids[2]))
            assertTrue(reopened.items(true).all { it.quantity == 1.0 })
            assertTrue(reopened.movements(true).filter { it.kind == "Egreso" }.all { it.pending == 0.0 })
            reopened.dissolveToolBundle(reopened.toolBundles().single().id)
            assertTrue(reopened.toolBundles().isEmpty())
            reopened.issue(true, hammer.id, 1.0, date, person, "Solo", listOf(ids[1]))
            assertEquals(1.0, reopened.items(true).first { it.id == chisel.id }.quantity, 0.0)
            check(reopened)
        }
    }

    @Test fun twoSetsWithSameArticleReturnOnlyTheSelectedSetAndRetainDamagePerUnit() = withStock { r, _ ->
        val hammers = add(r, "Martillo", 2)
        val chisels = add(r, "Cincel", 2)
        val person = worker(r)
        repeat(2) { index -> r.saveToolBundle(null, "Equipo $index", listOf(hammers.units[index].id, chisels.units[index].id)) }
        r.issue(true, hammers.id, 2.0, date, person, "", hammers.units.map { it.id })
        val loan = r.movements(true).first { it.kind == "Egreso" && it.itemId == hammers.id }
        r.returnStock(true, loan.id, 1.0, date, null, "", listOf(hammers.units[0].id), mapOf(chisels.units[0].id to "Punta rota"))
        assertEquals(1.0, r.items(true).first { it.id == hammers.id }.quantity, 0.0)
        assertEquals(0.0, r.items(true).first { it.id == chisels.id }.quantity, 0.0)
        assertEquals(ToolCondition.DAMAGED, r.items(true).first { it.id == chisels.id }.units[0].condition)
        assertEquals(1.0, r.movements(true).first { it.id == loan.id }.pending, 0.0)
        assertTrue(r.items(true).first { it.id == chisels.id }.units[1].loanId != null)
        r.returnStock(true, loan.id, 1.0, date, null, "", listOf(hammers.units[1].id))
        assertEquals(1.0, r.items(true).first { it.id == chisels.id }.quantity, 0.0)
        check(r)
    }

    @Test fun unavailableMemberBlocksWholeIssueAndInvalidReturnRollsBackAllMembers() = withStock { r, _ ->
        val hammer = add(r, "Martillo")
        val chisel = add(r, "Cincel")
        val ids = listOf(hammer.units.single().id, chisel.units.single().id)
        r.saveToolBundle(null, "Equipo", ids)
        val person = worker(r)
        r.changeToolState(ids[1], ToolAction.DAMAGE, date, "Roto")
        assertTrue(runCatching { r.issue(true, hammer.id, 1.0, date, person, "", listOf(ids[0])) }.isFailure)
        assertEquals(1.0, r.items(true).first { it.id == hammer.id }.quantity, 0.0)
        r.dissolveToolBundle(r.toolBundles().single().id)
        r.changeToolState(ids[1], ToolAction.SEND_REPAIR, date, "", "Taller")
        r.changeToolState(ids[1], ToolAction.REPAIRED, date, "")
        r.saveToolBundle(null, "Equipo reparado", ids)
        r.issue(true, hammer.id, 1.0, date, person, "", listOf(ids[0]))
        val loan = r.movements(true).first { it.kind == "Egreso" && it.itemId == hammer.id }
        assertTrue(runCatching { r.returnStock(true, loan.id, 1.0, date, null, "", listOf(ids[0]), mapOf(ids[1] to "")) }.isFailure)
        assertTrue(r.items(true).all { it.quantity == 0.0 })
        check(r)
    }

    @Test fun sameArticleMembersAreNotDuplicatedAndMembershipEditsPreserveStock() = withStock { r, _ ->
        val tools = add(r, "Destornillador", 3)
        val ids = tools.units.map { it.id }
        r.saveToolBundle(null, "Juego", ids.take(2))
        val bundle = r.toolBundles().single()
        assertTrue(runCatching { r.saveToolBundle(null, "Duplicado", ids.drop(1)) }.isFailure)
        r.saveToolBundle(bundle.id, "Juego completo", ids)
        assertEquals(3.0, r.items(true).single().quantity, 0.0)
        r.issue(true, tools.id, 1.0, date, worker(r), "", listOf(ids[0]))
        val loan = r.movements(true).first { it.kind == "Egreso" }
        assertEquals(3.0, loan.quantity, 0.0)
        r.returnStock(true, loan.id, 1.0, date, null, "", listOf(ids[0]))
        assertEquals(3.0, r.items(true).single().quantity, 0.0)
        check(r)
    }

    @Test fun upgradeFromVersionEightPreservesExistingStockAndPersonnelAreSortedBySpanishName() = withStock { r, context ->
        val item = add(r, "Taladro")
        r.addWorker("Zoe", "Alonso", "Obra", "")
        r.addWorker("Álvaro", "Zeta", "Obra", "")
        r.addWorker("ana", "Pérez", "Obra", "")
        assertEquals(listOf("Álvaro", "ana", "Zoe"), r.personnel().map { it.name })
        removeToolBundleSchema(r.writableDatabase)
        r.writableDatabase.version = 8
        r.close()
        LocalStockRepository(context).use { reopened ->
            assertEquals(14, reopened.readableDatabase.version)
            assertEquals(item, reopened.items(true).single())
            assertTrue(reopened.toolBundles().isEmpty())
            assertEquals(listOf("Álvaro", "ana", "Zoe"), reopened.personnel().map { it.name })
            check(reopened)
        }
    }
}
