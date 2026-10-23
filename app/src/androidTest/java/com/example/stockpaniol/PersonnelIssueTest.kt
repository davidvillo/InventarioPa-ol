package com.example.stockpaniol

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonnelIssueTest {
    private fun withStock(test: (LocalStockRepository, Long) -> Unit) {
        val context = isolatedStockContext("personnel_delivery_")
        val repository = LocalStockRepository(context)
        try {
            repository.addMeasurement("bolsas", true)
            repository.addWorker("Juan", "Pérez", "Otros", "")
            listOf("Sierra circular", "Amoladora").forEach { name ->
                repository.receive(true, StockEntry(0, name, 1.0, "unidades", "", "", todayStockDate(), "", "", ""))
            }
            repository.receive(false, StockEntry(0, "Cemento", 5.0, "bolsas", "", "", todayStockDate(), "", "", ""))
            test(repository, repository.personnel().single().id)
        } finally { repository.close(); context.deleteDatabase("stock_paniol.db") }
    }

    private fun delivery(repository: LocalStockRepository, amount: Double = 1.0) =
        repository.items(true).map { StockIssueLine(true, it.id, 1.0, listOf(it.availableUnits.single().id)) } +
            repository.items(false).map { StockIssueLine(false, it.id, amount) }

    @Test fun mixedDeliveryCreatesTwoToolRowsAndOneMaterialRowForTheSamePerson() = withStock { repository, worker ->
        repository.issueForPersonnel(worker, todayStockDate(), "Entrega de Juan", delivery(repository))
        val tools = repository.movements(true).filter { it.kind == "Egreso" }
        val materials = repository.movements(false).filter { it.kind == "Egreso" }
        assertEquals(2, tools.size)
        assertEquals(1, materials.size)
        (tools + materials).forEach { assertEquals(worker, it.workerId); assertEquals("Entrega de Juan", it.notes) }
        assertTrue(repository.items(true).all { it.quantity == 0.0 })
        assertEquals(4.0, repository.items(false).single().quantity, 0.0)
        repository.close()
        assertEquals(2, repository.movements(true).count { it.kind == "Egreso" })
        assertEquals(1, repository.movements(false).count { it.kind == "Egreso" })
    }

    @Test fun aFailureInTheLastMaterialRollsBackEveryToolAndMovement() = withStock { repository, worker ->
        assertTrue(runCatching { repository.issueForPersonnel(worker, todayStockDate(), "", delivery(repository, 6.0)) }.isFailure)
        assertTrue(repository.items(true).all { it.quantity == 1.0 && it.availableUnits.size == 1 })
        assertEquals(5.0, repository.items(false).single().quantity, 0.0)
        assertFalse(repository.movements(true).any { it.kind == "Egreso" })
        assertFalse(repository.movements(false).any { it.kind == "Egreso" })
    }

    @Test fun bundledToolsAreOnlyIssuedOnceEvenWhenBothAreIncluded() = withStock { repository, worker ->
        val units = repository.items(true).flatMap { it.availableUnits }.map { it.id }
        repository.saveToolBundle(null, "Equipo de corte", units, units.first())
        repository.issueForPersonnel(worker, todayStockDate(), "", delivery(repository))
        val movements = repository.movements(true).filter { it.kind == "Egreso" }
        assertEquals(2, movements.size)
        assertTrue(movements.all { it.quantity == 1.0 && it.units.size == 1 })
        repository.deleteMovement(true, movements.first().id)
        assertFalse(repository.movements(true).any { it.kind == "Egreso" })
        assertTrue(repository.items(true).all { it.quantity == 1.0 })
    }

    @Test fun emptyDeliveryOrMissingPersonnelNeverChangesStock() = withStock { repository, worker ->
        assertTrue(runCatching { repository.issueForPersonnel(worker, todayStockDate(), "", emptyList()) }.isFailure)
        assertTrue(runCatching { repository.issueForPersonnel(0L, todayStockDate(), "", delivery(repository)) }.isFailure)
        assertFalse(repository.movements(true).any { it.kind == "Egreso" })
        assertFalse(repository.movements(false).any { it.kind == "Egreso" })
    }
}
