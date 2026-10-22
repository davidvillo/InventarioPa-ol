package com.example.stockpaniol

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IndividualInventoryTest {
    @Test fun automaticCodesUseNamePrefixesAndPersistIndependentCountersWithoutReusingDeletedCodes() = withRepository { r, context ->
        for (tools in listOf(true, false)) {
            val bucket = entry("", "", tools).copy(name = "Balde de albañil", quantity = 2.0)
            val beacon = bucket.copy(name = "Baliza", quantity = 1.0)
            r.receive(tools, bucket)
            r.receive(tools, beacon)
            assertEquals(listOf("BAL 001", "BAL 002"), r.items(tools).first { it.name == bucket.name }.units.map { it.identifier })
            assertEquals("BAI 001", r.items(tools).first { it.name == beacon.name }.units.single().identifier)
            r.receive(tools, bucket.copy(quantity = 1.0))
            val lastIncome = r.movements(tools).first { it.units.any { unit -> unit.unit.identifier == "BAL 003" } }
            r.deleteMovement(tools, lastIncome.id)
        }
        r.close()
        LocalStockRepository(context).use { reopened ->
            for (tools in listOf(true, false)) {
                reopened.receive(tools, entry("", "", tools).copy(name = "Balde de albañil"))
                reopened.receive(tools, entry("", "", tools).copy(name = "Baliza"))
                assertEquals(listOf("BAL 001", "BAL 002", "BAL 004"), reopened.items(tools).first { it.name == "Balde de albañil" }.units.map { it.identifier })
                assertEquals(listOf("BAI 001", "BAI 002"), reopened.items(tools).first { it.name == "Baliza" }.units.map { it.identifier })
            }
        }
    }

    @Test fun zeroQuantityReservesPrefixAndCustomCodesArePreservedAndSkipped() = withRepository { r, _ ->
        r.receive(true, entry("", "").copy(name = "Balde de albañil", quantity = 0.0))
        r.receive(true, entry("", "").copy(name = "Baliza"))
        assertEquals("BAI 001", r.items(true).first { it.name == "Baliza" }.units.single().identifier)
        r.receive(true, entry("", "").copy(name = "Balde de albañil", quantity = 3.0, unitDetails = listOf(
            StockUnitEntry(), StockUnitEntry(identifier = "BAL 001"), StockUnitEntry(identifier = "ETIQUETA"))))
        val bucket = r.items(true).first { it.name == "Balde de albañil" }
        assertEquals(setOf("BAL 001", "BAL 002", "ETIQUETA"), bucket.units.map { it.identifier }.toSet())
        r.identifyUnit(true, bucket.units.first { it.identifier == "BAL 002" }.id, "Marca", "EDITADO")
        r.receive(true, entry("", "").copy(name = "Balde de albañil"))
        assertEquals(setOf("BAL 001", "BAL 003", "ETIQUETA", "EDITADO"), r.items(true).first { it.id == bucket.id }.units.map { it.identifier }.toSet())
    }

    @Test fun zeroStockCreatesCatalogueWithoutPurchasesMovementsOrUnitsAndCanBeReplenished() = withRepository { r, context ->
        for (tools in listOf(true, false)) {
            r.receive(tools, entry("", "", tools).copy(quantity = 0.0))
            r.receive(tools, entry("", "", tools).copy(quantity = 0.0))
            assertEquals(0.0, r.items(tools).single().quantity, 0.0)
            assertTrue(r.items(tools).single().units.isEmpty())
            assertTrue(r.purchases(tools).isEmpty())
            assertTrue(r.movements(tools).isEmpty())
        }
        val bulk = StockEntry(0, "Arena", 0.0, "kg", "", "", date, "", "", "")
        r.receive(false, bulk)
        r.close()
        LocalStockRepository(context).use { reopened ->
            assertEquals(0.0, reopened.items(true).single().quantity, 0.0)
            assertEquals(2, reopened.items(false).size)
            reopened.receive(false, bulk.copy(quantity = 2.5))
            assertEquals(2.5, reopened.items(false).first { it.name == "Arena" }.quantity, 0.0)
            reopened.receive(true, entry("", ""))
            assertEquals(1, reopened.items(true).single().units.size)
        }
    }

    @Test fun batchReceiptStoresEachBrandAndOptionalIdentityAndRejectsDuplicatesAtomically() = withRepository { r, _ ->
        for (tools in listOf(true, false)) {
            val batch = entry("", "", tools).copy(quantity = 3.0, unitDetails = listOf(
                StockUnitEntry(), StockUnitEntry("Bosch", " ${if (tools) "H" else "M"}-2 "), StockUnitEntry("Otra", "SERIE-3")))
            r.receive(tools, batch)
            val item = r.items(tools).single()
            assertEquals(3.0, item.quantity, 0.0)
            assertEquals(3, item.units.map { it.identifier.lowercase() }.distinct().size)
            assertEquals("Bosch", item.units.first { it.identifier.endsWith("-2") }.brand)
            assertEquals("Otra", item.units.first { it.identifier == "SERIE-3" }.brand)
            assertEquals(1, r.purchases(tools).size)
            assertEquals(3, r.movements(tools).single().units.size)
            rejects { r.receive(tools, batch.copy(quantity = 2.0, unitDetails = listOf(StockUnitEntry(identifier = "NUEVO"), StockUnitEntry(identifier = " nuevo ")))) }
            rejects { r.receive(tools, batch.copy(quantity = 1.0, unitDetails = listOf(StockUnitEntry(identifier = "serie-3")))) }
            rejects { r.receive(tools, batch.copy(quantity = 2.0)) }
            assertEquals(item, r.items(tools).single())
            assertEquals(1, r.purchases(tools).size)
        }
    }

    private val date = "2026-10-13"
    private fun entry(brand: String, identifier: String, tools: Boolean = true) = StockEntry(
        0, if (tools) "Amoladora de 7 pulgadas" else "Arnés", 1.0, "unidades", "", "R-1", date,
        "", brand, "Proveedor", control = StockControl.INDIVIDUAL, identifier = identifier)
    private fun withRepository(block: (LocalStockRepository, android.content.Context) -> Unit) {
        val context = isolatedStockContext("individual_inventory_")
        context.deleteDatabase("stock_paniol.db")
        try { LocalStockRepository(context).use { block(it, context) } }
        finally { context.deleteDatabase("stock_paniol.db") }
    }
    private fun rejects(action: () -> Unit) = assertTrue(runCatching(action).isFailure)
    private fun workers(r: LocalStockRepository): Pair<Long, Long> {
        r.addWorker("Juan", "Pérez", "Norte", "")
        r.addWorker("Diego", "López", "Sur", "")
        val people = r.personnel()
        return people.first { it.name == "Juan" }.id to people.first { it.name == "Diego" }.id
    }

    @Test fun sameNameDifferentBrandsAreIssuedAndReturnedByExactIdentityAndPersist() = withRepository { r, context ->
        r.receive(true, entry("Bosch", "B-01"))
        r.receive(true, entry("Wadfow", "W-01"))
        val item = r.items(true).single()
        val bosch = item.units.first { it.brand == "Bosch" }
        val wadfow = item.units.first { it.brand == "Wadfow" }
        val (juan, diego) = workers(r)
        r.issue(true, item.id, 1.0, date, juan, "", listOf(bosch.id))
        r.issue(true, item.id, 1.0, date, diego, "", listOf(wadfow.id))
        val loans = r.movements(true).filter { it.kind == "Egreso" }
        val juanLoan = loans.first { it.workerId == juan }
        assertEquals("Bosch · B-01", juanLoan.identitySummary)
        assertEquals("Wadfow · W-01", loans.first { it.workerId == diego }.identitySummary)
        rejects { r.issue(true, item.id, 1.0, date, diego, "", listOf(bosch.id)) }
        rejects { r.returnStock(true, juanLoan.id, 1.0, date, null, "", listOf(wadfow.id)) }
        assertEquals(0.0, r.items(true).single().quantity, 0.0)
        r.returnStock(true, juanLoan.id, 1.0, date, null, "", listOf(bosch.id))
        rejects { r.returnStock(true, juanLoan.id, 1.0, date, null, "", listOf(bosch.id)) }
        r.close()
        LocalStockRepository(context).use { reopened ->
            val saved = reopened.items(true).single()
            assertEquals(1.0, saved.quantity, 0.0)
            assertEquals(listOf("B-01"), saved.availableUnits.map { it.identifier })
            assertEquals("Diego López", saved.units.first { it.id == wadfow.id }.holder)
            assertEquals(0, reopened.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
        }
    }

    @Test fun sameBrandGetsDistinctAutomaticCodesAndDuplicatesRollBackEntireReceipt() = withRepository { r, _ ->
        r.receive(true, entry("Bosch", ""))
        r.receive(true, entry("Bosch", ""))
        val before = r.items(true).single()
        assertEquals(2, before.units.map { it.identifier }.distinct().size)
        rejects { r.receive(true, entry("Otra", before.units.first().identifier.lowercase())) }
        assertEquals(before, r.items(true).single())
        assertEquals(2, r.purchases(true).size)
        assertEquals(2, r.movements(true).size)
    }

    @Test fun identifiedMaterialsCoexistWithBulkCementAndRestoreSelectedReturnOnDeletion() = withRepository { r, _ ->
        r.receive(false, entry("Marca", "A-01", false))
        r.receive(false, entry("Otra", "A-02", false))
        r.receive(false, StockEntry(0, "Cemento", 20.0, "unidades", "", "", date, "", "", ""))
        val harness = r.items(false).first { it.name == "Arnés" }
        val (juan, _) = workers(r)
        val unitIds = harness.units.map { it.id }
        r.issue(false, harness.id, 2.0, date, juan, "", unitIds)
        val loan = r.movements(false).first { it.kind == "Egreso" }
        r.returnStock(false, loan.id, 1.0, date, juan, "", unitIds.take(1))
        val returned = r.movements(false).first { it.kind == "Devolución" }
        assertEquals("Marca · A-01", returned.identitySummary)
        r.deleteMovement(false, returned.id)
        assertTrue(r.items(false).first { it.id == harness.id }.availableUnits.isEmpty())
        r.deleteMovement(false, loan.id)
        assertEquals(2, r.items(false).first { it.id == harness.id }.availableUnits.size)
        val cement = r.items(false).first { it.name == "Cemento" }
        assertFalse(cement.individuallyTracked)
        assertTrue(cement.units.isEmpty())
        r.issue(false, cement.id, 8.0, date, juan, "")
        assertEquals(12.0, r.items(false).first { it.id == cement.id }.quantity, 0.0)
    }

    @Test fun selectionRejectsMissingDuplicateAndForeignUnitsAndWrongQuantity() = withRepository { r, _ ->
        r.receive(true, entry("Bosch", "B-01"))
        r.receive(true, entry("Otra", "T-01").copy(name = "Taladro"))
        val item = r.items(true).first { it.name.startsWith("Amoladora") }
        val foreign = r.items(true).first { it.name == "Taladro" }.units.single()
        val (juan, _) = workers(r)
        rejects { r.issue(true, item.id, 1.0, date, juan, "") }
        rejects { r.issue(true, item.id, 2.0, date, juan, "", listOf(item.units.single().id, item.units.single().id)) }
        rejects { r.issue(true, item.id, 1.0, date, juan, "", listOf(foreign.id)) }
        rejects { r.issue(true, item.id, 2.0, date, juan, "", listOf(item.units.single().id)) }
        assertEquals(1.0, r.items(true).first { it.id == item.id }.quantity, 0.0)
        assertEquals(2, r.movements(true).size)
    }

    @Test fun deletingIncomeRemovesOnlyItsUnitsAndUsedUnitsKeepTheirHistory() = withRepository { r, _ ->
        r.receive(true, entry("Bosch", "B-01"))
        r.receive(true, entry("Wadfow", "W-01"))
        val firstIncome = r.movements(true).first { it.identitySummary == "Bosch · B-01" }
        r.deleteMovement(true, firstIncome.id)
        val item = r.items(true).single()
        assertEquals(listOf("W-01"), item.units.map { it.identifier })
        val (juan, _) = workers(r)
        r.issue(true, item.id, 1.0, date, juan, "", listOf(item.units.single().id))
        val loan = r.movements(true).first { it.kind == "Egreso" }
        r.returnStock(true, loan.id, 1.0, date, null, "", listOf(item.units.single().id))
        rejects { r.deleteMovement(true, r.movements(true).first { it.kind == "Ingreso" }.id) }
        assertEquals(1.0, r.items(true).single().quantity, 0.0)
        r.identifyUnit(true, item.units.single().id, "Wadfow", "NUEVO-01")
        assertEquals("NUEVO-01", r.items(true).single().units.single().identifier)
        assertEquals("Wadfow · W-01 · Devuelta", r.movements(true).first { it.kind == "Egreso" }.identitySummary)
    }

    @Test fun versionThreeMigrationPreservesPendingLoansWithoutInventingPhysicalIdentity() = withRepository { r, context ->
        r.receive(true, entry("Bosch", "B-01").copy(quantity = 4.0, identifier = ""))
        val item = r.items(true).single()
        val (juan, _) = workers(r)
        r.issue(true, item.id, 3.0, date, juan, "", item.units.take(3).map { it.id })
        val loan = r.movements(true).first { it.kind == "Egreso" }
        r.returnStock(true, loan.id, 1.0, date, null, "", item.units.take(1).map { it.id })
        // Recreate the v3 schema, retaining the old totals and history but no unit identities.
        val db = r.writableDatabase
        removeMeasurementSchema(db)
        db.execSQL("UPDATE movimientos_herramientas SET persona='' WHERE clase='Egreso'")
        for (stock in listOf("materiales", "herramientas")) {
            db.execSQL("DROP TABLE unidades_movimientos_$stock")
            db.execSQL("DROP TABLE unidades_$stock")
            db.execSQL("ALTER TABLE $stock DROP COLUMN control")
        }
        db.execSQL("ALTER TABLE personal DROP COLUMN obra_id")
        db.execSQL("ALTER TABLE personal DROP COLUMN activo")
        db.execSQL("DROP TABLE obras")
        db.version = 3
        r.close()
        LocalStockRepository(context).use { upgraded ->
            val saved = upgraded.items(true).single()
            assertEquals(14, upgraded.readableDatabase.version)
            assertEquals(2.0, saved.quantity, 0.0)
            assertEquals(4, saved.units.size)
            assertEquals(2, saved.availableUnits.size)
            assertTrue(saved.units.all { it.legacy })
            assertTrue(saved.units.filter { !it.available }.all { it.holder == "Préstamo histórico sin personal" })
            val pending = upgraded.movements(true).first { it.kind == "Egreso" }
            assertEquals(1.0, pending.returned, 0.0)
            assertEquals(2, pending.units.size)
            upgraded.returnStock(true, pending.id, 1.0, date, null, "", pending.units.take(1).map { it.unit.id })
            assertEquals(3, upgraded.items(true).single().availableUnits.size)
            assertEquals(3.0, upgraded.items(true).single().quantity, 0.0)
            assertEquals(0, upgraded.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
        }
    }
}
