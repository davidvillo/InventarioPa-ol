package com.example.stockpaniol

import org.junit.Assert.*
import org.junit.Test

class StockErrorsTest {
    @Test fun repeatedErrorsHaveNewNoticesWithoutLosingTheValidationMessage() {
        val state = StockErrorState()
        state.value = "Error"
        val first = state.notice!!
        state.value = "Error"
        assertNotEquals(first, state.notice)
        state.dismiss(first)
        assertNotNull(state.notice)
        state.dismiss(state.notice!!)
        assertNull(state.notice)
        assertEquals("Error", state.value)
    }
}
