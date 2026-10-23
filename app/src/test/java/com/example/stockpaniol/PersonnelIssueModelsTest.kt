package com.example.stockpaniol

import org.junit.Assert.*
import org.junit.Test

class PersonnelIssueModelsTest {
    private val saw = StockItem(1, "Sierra", 1.0, "unidades", "", "", "", "", control = StockControl.INDIVIDUAL,
        units = listOf(InventoryUnit(11, 1, "S1", "", bundleId = 5)))
    private val grinder = StockItem(2, "Amoladora", 2.0, "unidades", "", "", "", "", control = StockControl.INDIVIDUAL,
        units = listOf(InventoryUnit(21, 2, "A1", "", bundleId = 5), InventoryUnit(22, 2, "A2", "")))

    @Test fun addingABundleIncludesItsCompanionsOnceAndKeepsSeparateMaterialIds() {
        val lines = expandPersonnelIssueLines(listOf(StockIssueLine(true, 1, 1.0, listOf(11)),
            StockIssueLine(true, 2, 1.0, listOf(21)), StockIssueLine(false, 1, 3.0)), listOf(saw, grinder))
        assertEquals(3, lines.size)
        assertEquals(listOf(21L), lines.first { it.tools && it.itemId == 2L }.unitIds)
        assertEquals(3.0, lines.first { !it.tools }.quantity, 0.0)
    }

    @Test fun removingABundleKeepsUnrelatedUnitsAndMaterials() {
        val lines = expandPersonnelIssueLines(listOf(StockIssueLine(true, 1, 1.0, listOf(11)),
            StockIssueLine(true, 2, 1.0, listOf(22)), StockIssueLine(false, 1, 2.0)), listOf(saw, grinder))
        val remaining = removePersonnelIssueLine(lines, lines.first { it.itemId == 1L && it.tools }, listOf(saw, grinder))
        assertEquals(2, remaining.size)
        assertEquals(listOf(22L), remaining.first { it.tools }.unitIds)
        assertEquals(2.0, remaining.first { !it.tools }.quantity, 0.0)
    }

    @Test fun refreshingStockDoesNotSilentlyDropAnUnavailableSelection() {
        val selected = StockIssueLine(true, 1, 1.0, listOf(11))
        assertEquals(listOf(selected), expandPersonnelIssueLines(listOf(selected), emptyList()))
    }

    @Test fun suggestionsMatchAccentsAndAnyOrderOfNameSurnameAndNickname() {
        assertTrue(personnelIssueMatches("Juan Pérez (Toto) · Obra", "perez juan"))
        assertTrue(personnelIssueMatches("Juan Pérez (Toto) · Obra", "TÓTO"))
        assertFalse(personnelIssueMatches("Juan Pérez · Obra", "María"))
    }
}
