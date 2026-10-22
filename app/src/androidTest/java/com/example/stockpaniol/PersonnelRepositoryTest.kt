package com.example.stockpaniol

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonnelRepositoryTest {
    private fun withRepository(block: (LocalStockRepository, android.content.Context) -> Unit) {
        val context = isolatedStockContext("personnel_test_")
        context.deleteDatabase("stock_paniol.db")
        try { LocalStockRepository(context).use { block(it, context) } }
        finally { context.deleteDatabase("stock_paniol.db") }
    }
    private fun rejects(action: () -> Unit) = assertTrue(runCatching(action).isFailure)
    private fun addPerson(r: LocalStockRepository) {
        r.addWorker(WorkerDraft(" Ana ", " Ruiz ", r.worksites().first().id, " Anita "))
    }
    private fun createLoan(r: LocalStockRepository): StockMovement {
        r.receive(false, StockEntry(0, "Cemento", 10.0, "unidades", "", "", todayStockDate(), "", "", ""))
        r.issue(false, r.items(false).single().id, 2.0, todayStockDate(), r.personnel().single().id, "Entrega")
        return r.movements(false).first { it.kind == "Egreso" }
    }

    @Test fun defaultsAndWorkerEditsPreserveIdentityOtherInstancesAndPersist() = withRepository { r, context ->
        assertEquals(Worksite.defaults, r.worksites().map { it.name })
        addPerson(r)
        val original = r.personnel().single()
        val secondSite = r.worksites().first { it.name == "519" }
        r.addWorker(WorkerDraft("Juan", "Pérez", secondSite.id, ""))
        val second = r.personnel().first { it.name == "Juan" }
        r.updateWorker(original.id, WorkerDraft("Ana María", "Ruiz", secondSite.id, "Ani"))
        val updated = r.personnel().first { it.id == original.id }
        assertEquals(original.id, updated.id)
        assertEquals("Ana María Ruiz (Ani)", updated.displayName)
        assertEquals(secondSite, updated.assignment)
        assertEquals("Ana Ruiz (Anita)", original.displayName)
        assertEquals(second, r.personnel().first { it.id == second.id })
        r.close()
        LocalStockRepository(context).use { reopened -> assertEquals(updated, reopened.personnel().first { it.id == original.id }) }
    }

    @Test fun deletingWorkerWithHistoryHidesNewIssuesButAllowsOriginalPendingReturn() = withRepository { r, _ ->
        addPerson(r)
        val original = r.personnel().single()
        val loan = createLoan(r)
        r.updateWorker(original.id, WorkerDraft("Ana María", "Ruiz", original.assignment.id, ""))
        assertEquals(original.displayName, r.movements(false).first { it.id == loan.id }.person)
        r.deleteWorker(original.id)
        assertTrue(r.personnel().isEmpty())
        assertFalse(r.personnel(true).single().active)
        rejects { r.issue(false, loan.itemId, 1.0, todayStockDate(), original.id, "") }
        r.returnStock(false, loan.id, 2.0, todayStockDate(), original.id, "Sobrante")
        assertEquals(0.0, r.movements(false).first { it.id == loan.id }.pending, 0.0)
        assertEquals(original.id, r.movements(false).first { it.kind == "Devolución" }.workerId)
        assertEquals(0, r.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
    }

    @Test fun unreferencedWorkerCanBeDeletedWithoutRemovingOthers() = withRepository { r, _ ->
        addPerson(r)
        val original = r.personnel().single()
        r.addWorker(WorkerDraft("Juan", "Pérez", original.assignment.id, ""))
        val second = r.personnel().first { it.name == "Juan" }
        r.deleteWorker(original.id)
        assertEquals(listOf(second), r.personnel(true))
        rejects { r.updateWorker(original.id, WorkerDraft("Otra", "Persona", original.assignment.id, "")) }
    }

    @Test fun worksiteRenameAndRemovalPreserveAssignmentsAndCanRestoreDropdownOption() = withRepository { r, _ ->
        addPerson(r)
        val worker = r.personnel().single()
        r.updateWorksite(worker.assignment.id, "513 Vereda norte")
        assertEquals("513 Vereda norte", r.personnel().single().worksite)
        r.deleteWorksite(worker.assignment.id)
        assertFalse(r.worksites().any { it.id == worker.assignment.id })
        assertEquals("513 Vereda norte", r.personnel().single().worksite)
        assertFalse(r.personnel().single().assignment.active)
        // Editing the person's name may preserve their withdrawn, existing assignment.
        r.updateWorker(worker.id, WorkerDraft("Ana María", "Ruiz", worker.assignment.id, ""))
        rejects { r.addWorker(WorkerDraft("Juan", "Pérez", worker.assignment.id, "")) }
        r.addWorksite("513 Vereda norte")
        assertTrue(r.worksites().any { it.id == worker.assignment.id })
        r.addWorksite("Obra nueva")
        val added = r.worksites().first { it.name == "Obra nueva" }
        r.deleteWorksite(added.id)
        assertFalse(r.worksites(true).any { it.id == added.id })
    }

    @Test fun invalidDataAndDuplicateOptionsLeavePeopleAndCatalogueUnchanged() = withRepository { r, _ ->
        addPerson(r)
        val before = r.personnel().single()
        val sites = r.worksites()
        rejects { r.addWorksite("  ") }
        rejects { r.addWorksite("513 vereda") }
        rejects { r.updateWorksite(sites[1].id, "513 Vereda") }
        rejects { r.updateWorker(before.id, WorkerDraft("", "Ruiz", before.assignment.id, "")) }
        rejects { r.addWorker(WorkerDraft("Juan", "Pérez", 999, "")) }
        assertEquals(listOf(before), r.personnel())
        assertEquals(sites, r.worksites())
    }

    @Test fun versionFourMigrationKeepsLegacyWorksitesWorkersAndMovements() = withRepository { r, context ->
        r.addWorker("Ana", "Ruiz", "Obra anterior", "Anita")
        val original = r.personnel().single()
        val loan = createLoan(r)
        val db = r.writableDatabase
        removeMeasurementSchema(db)
        db.execSQL("ALTER TABLE personal DROP COLUMN obra_id")
        db.execSQL("ALTER TABLE personal DROP COLUMN activo")
        db.execSQL("DROP TABLE obras")
        db.version = 4
        r.close()
        LocalStockRepository(context).use { migrated ->
            assertEquals(14, migrated.readableDatabase.version)
            assertEquals(original.id, migrated.personnel().single().id)
            assertEquals("Obra anterior", migrated.personnel().single().assignment.name)
            assertTrue(migrated.worksites().map { it.name }.containsAll(Worksite.defaults + "Obra anterior"))
            assertEquals(loan, migrated.movements(false).first { it.id == loan.id })
            assertEquals(0, migrated.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
        }
    }
}
