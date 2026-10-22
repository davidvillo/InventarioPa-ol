package com.example.stockpaniol

import androidx.compose.ui.graphics.Color

internal enum class StockAlert(val label: String, val color: Color) {
    AVAILABLE("Disponibilidad total", Color(0xFFC8E6C9)),
    LOANED("Hay unidades prestadas", Color(0xFFFFF59D)),
    LOW("Stock bajo o sin disponibilidad", Color(0xFFFFCC80)),
    EMPTY("Sin stock", Color(0xFFEF9A9A))
}

internal fun stockAlert(item: StockItem, tools: Boolean): StockAlert {
    if (!tools && !item.individuallyTracked) return when {
        item.quantity <= 0 -> StockAlert.EMPTY
        item.quantity <= item.stockThreshold -> StockAlert.LOW
        else -> StockAlert.AVAILABLE
    }
    val active = item.units.filter { it.inActiveStock }
    val loans = active.count { it.loanId != null }
    return when {
        item.quantity <= 0 && loans == 0 -> StockAlert.EMPTY
        item.quantity <= 0 -> StockAlert.LOW
        loans > 0 -> StockAlert.LOANED
        active.any { !it.available } -> StockAlert.LOW
        else -> StockAlert.AVAILABLE
    }
}

internal fun parseStockThreshold(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()
    ?.takeIf { it.isFinite() && it >= 0 }

internal fun validateStockThreshold(value: Double) {
    require(value.isFinite() && value >= 0) { "Ingresá un umbral igual o mayor a cero." }
}
