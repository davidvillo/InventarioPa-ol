package com.example.stockpaniol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StockIdentifiersTest {
    @Test fun matchingNamesUseFollowingLetterInsteadOfSharingPrefix() {
        assertEquals("BAL", chooseStockPrefix("Balde de albañil", emptySet()))
        assertEquals("BAI", chooseStockPrefix("Baliza", setOf("BAL")))
        assertEquals("BAZ", chooseStockPrefix("Baliza", setOf("BAL", "BAI")))
        assertEquals("BAN", chooseStockPrefix("Balinera", setOf("BAL", "BAI")))
    }

    @Test fun accentsSpacesAndShortNamesHaveStablePrefixes() {
        assertEquals("ARN", chooseStockPrefix("  Ár-nés  ", emptySet()))
        assertEquals("AXX", chooseStockPrefix("A", emptySet()))
        assertEquals("ART", chooseStockPrefix("123", emptySet()))
        val used = setOf("AAA", "AXX")
        assertFalse(chooseStockPrefix("AAA", used) in used)
    }

    @Test fun serialNumbersHaveAtLeastThreeDigitsWithoutLimitingStockTo999() {
        assertEquals("BAL 001", formatStockIdentifier("BAL", 1))
        assertEquals("BAL 010", formatStockIdentifier("BAL", 10))
        assertEquals("BAL 999", formatStockIdentifier("BAL", 999))
        assertEquals("BAL 1000", formatStockIdentifier("BAL", 1000))
    }
}
