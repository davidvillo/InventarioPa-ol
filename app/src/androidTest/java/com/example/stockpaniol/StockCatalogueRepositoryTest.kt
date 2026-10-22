package com.example.stockpaniol

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StockCatalogueRepositoryTest {
    private val brand = StockCatalogueKind.BRAND
    private val company = StockCatalogueKind.COMPANY
    private fun withStock(block: (LocalStockRepository, android.content.Context) -> Unit) {
        val context = isolatedStockContext("catalogue_")
        try { LocalStockRepository(context).use { block(it, context) } }
        finally { context.deleteDatabase("stock_paniol.db") }
    }
    private fun entry(name: String = "Taladro") = StockEntry(0, name, 2.0, "unidades", "", "R1", "2026-10-15", "", "", "Proveedor",
        unitDetails = listOf(StockUnitEntry("Bosch", "T1"), StockUnitEntry("Makita", "T2")))

    @Test fun cataloguesHaveIndependentNamesAndValidateDuplicatesAndBlanks() = withStock { r, _ ->
        r.addStockOption(brand, "Zeta")
        r.addStockOption(brand, "Águila")
        r.addStockOption(company, "Zeta")
        assertEquals(listOf("Águila", "Zeta"), r.stockOptions(brand).map { it.name })
        assertTrue(runCatching { r.addStockOption(brand, " zeta ") }.isFailure)
        assertTrue(runCatching { r.addStockOption(company, " ") }.isFailure)
        val id = r.stockOptions(brand).first { it.name == "Zeta" }.id
        assertTrue(runCatching { r.updateStockOption(brand, id, "Águila") }.isFailure)
        assertEquals(listOf("Zeta"), r.stockOptions(company).map { it.name })
        assertTrue(matchesStockOption("Águila", "agui"))
    }

    @Test fun newValuesAreRememberedAndSettingsDoNotChangeStockOrPurchaseSnapshots() = withStock { r, context ->
        r.receive(true, entry())
        assertEquals(listOf("Bosch", "Makita"), r.stockOptions(brand).map { it.name })
        assertEquals(listOf("Proveedor"), r.stockOptions(company).map { it.name })
        val item = r.items(true).single()
        val purchases = r.purchases(true)
        val bosch = r.stockOptions(brand).first { it.name == "Bosch" }
        r.updateStockOption(brand, bosch.id, "Bosch Professional")
        val supplier = r.stockOptions(company).single()
        r.deleteStockOption(company, supplier.id)
        assertTrue(r.stockOptions(company).isEmpty())
        assertEquals(item, r.items(true).single())
        assertEquals(purchases, r.purchases(true))
        r.updateTool(item.id, ToolEdit(item.name, "Nueva nota", item.company, item.images,
            item.units.map { ToolUnitEdit(it.id, it.brand, it.identifier) },
            purchases.map { ToolPurchaseEdit(it.id, it.date, it.receipt, it.company) }))
        assertEquals(listOf("Bosch Professional", "Makita"), r.stockOptions(brand).map { it.name })
        assertTrue(r.stockOptions(company).isEmpty())
        r.addStockOption(company, "Proveedor")
        assertEquals(supplier.id, r.stockOptions(company).single().id)
        r.updateStockOption(brand, bosch.id, "Bosch")
        LocalStockRepository(context).use { reopened ->
            assertEquals(listOf("Bosch", "Makita"), reopened.stockOptions(brand).map { it.name })
            assertEquals(2.0, reopened.items(true).single().quantity, 0.0)
        }
    }

    @Test fun versionNineUpgradeSeedsExistingBrandsCompaniesAndRentalsWithoutChangingStock() = withStock { r, context ->
        r.receive(true, entry())
        r.receive(false, StockEntry(0, "Guantes", 1.0, "unidades", "", "", "2026-10-15", "", "Acme", "EPP SA"))
        r.receive(true, StockEntry(0, "Alquilada", 1.0, "unidades", "", "", "2026-10-15", "", "", "", rental = RentalEntry("Alquileres Norte")))
        val tools = r.items(true)
        val materials = r.items(false)
        removeStockCatalogueSchema(r.writableDatabase)
        r.writableDatabase.version = 9
        r.close()
        LocalStockRepository(context).use { reopened ->
            assertEquals(14, reopened.readableDatabase.version)
            assertEquals(tools, reopened.items(true))
            assertEquals(materials, reopened.items(false))
            assertEquals(setOf("Bosch", "Makita", "Acme"), reopened.stockOptions(brand).map { it.name }.toSet())
            assertEquals(setOf("Proveedor", "EPP SA", "Alquileres Norte"), reopened.stockOptions(company).map { it.name }.toSet())
        }
    }

    @Test fun invalidReceiptDoesNotAddSuggestionsOrStock() = withStock { r, _ ->
        val invalid = entry().copy(unitDetails = listOf(StockUnitEntry("Marca nueva", "X"), StockUnitEntry("Otra", "X")))
        assertTrue(runCatching { r.receive(true, invalid) }.isFailure)
        assertTrue(r.stockOptions(brand).isEmpty())
        assertTrue(r.stockOptions(company).isEmpty())
        assertTrue(r.items(true).isEmpty())
    }
}
