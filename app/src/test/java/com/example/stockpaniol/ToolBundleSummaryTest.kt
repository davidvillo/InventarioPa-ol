package com.example.stockpaniol

import org.junit.Assert.*
import org.junit.Test

class ToolBundleSummaryTest {
    private fun item(id: Long, name: String, vararg units: Pair<Long, String>) = StockItem(id, name,
        units.size.toDouble(), "unidades", "", "", "", "", control = StockControl.INDIVIDUAL,
        units = units.map { InventoryUnit(it.first, id, it.second, "") })

    @Test fun principalListsAccessoriesAndEachAccessoryNamesOnlyItsPrincipal() {
        val items = listOf(item(1, "Taladro", 10L to "TAL 001"), item(2, "Batería", 20L to "BAT 001"), item(3, "Cargador", 30L to "CAR 001"))
        val links = toolBundleSummary(items, listOf(ToolBundle(1, "Equipo", listOf(10, 20, 30), 10)))
        assertEquals(listOf("con Batería · BAT 001", "con Cargador · CAR 001"), links[1])
        assertEquals(listOf("en Taladro · TAL 001"), links[2])
        assertEquals(listOf("en Taladro · TAL 001"), links[3])
    }
    @Test fun changingPrincipalReversesTheLinksAndDissolvingRemovesThem() {
        val items = listOf(item(1, "Taladro", 10L to "TAL 001"), item(2, "Batería", 20L to "BAT 001"))
        val links = toolBundleSummary(items, listOf(ToolBundle(1, "Equipo", listOf(10, 20), 20)))
        assertEquals(listOf("en Batería · BAT 001"), links[1])
        assertEquals(listOf("con Taladro · TAL 001"), links[2])
        assertTrue(toolBundleSummary(items, emptyList()).isEmpty())
    }
    @Test fun separateUnitsOfTheSameArticleKeepTheirOwnCodesAndSets() {
        val items = listOf(item(1, "Taladro", 10L to "TAL 001", 11L to "TAL 002"), item(2, "Batería", 20L to "BAT 001", 21L to "BAT 002"))
        val links = toolBundleSummary(items, listOf(ToolBundle(1, "Uno", listOf(10, 20), 10), ToolBundle(2, "Dos", listOf(11, 21), 11)))
        assertEquals(listOf("TAL 001: con Batería · BAT 001", "TAL 002: con Batería · BAT 002"), links[1])
        assertEquals(listOf("BAT 001: en Taladro · TAL 001", "BAT 002: en Taladro · TAL 002"), links[2])
    }
}
