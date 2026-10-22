package com.example.stockpaniol

import androidx.compose.ui.unit.*
import org.junit.Assert.*
import org.junit.Test

class StockErrorsTest {
    @Test fun errorIsCenteredInTheLowerScreenAwayFromTheBottomAndIndependentOfItsAnchor() {
        val position = StockErrorPosition.calculatePosition(IntRect(0, 0, 100, 100), IntSize(400, 800), LayoutDirection.Ltr, IntSize(300, 100))
        assertEquals(50, position.x)
        assertEquals(504, position.y)
        assertTrue(position.y > 400)
        assertTrue(position.y + 100 < 700)
    }

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
