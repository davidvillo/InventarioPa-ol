package com.example.stockpaniol

import java.text.Collator
import java.util.Locale

enum class StockColumn(val label: String, val width: Int, val numeric: Boolean = false) {
    ITEM("Ítem", 85, true),
    NAME("Nombre", 180),
    QUANTITY("Cantidad", 120, true),
    UNIT("Unidad", 110),
    NOTES("Observaciones", 240)
}

fun sortStock(items: List<StockItem>, column: StockColumn, descending: Boolean): List<StockItem> {
    val collator = Collator.getInstance(Locale.forLanguageTag("es-AR")).apply { strength = Collator.PRIMARY }
    val comparator = Comparator<StockItem> { a, b ->
        when (column) {
            StockColumn.ITEM -> a.id.compareTo(b.id)
            StockColumn.QUANTITY -> a.quantity.compareTo(b.quantity)
            StockColumn.NAME -> collator.compare(a.name, b.name)
            StockColumn.UNIT -> collator.compare(a.unit, b.unit)
            StockColumn.NOTES -> collator.compare(a.notes, b.notes)
        }
    }
    return items.sortedWith((if (descending) Comparator { a, b -> comparator.compare(b, a) } else comparator).thenBy { it.id })
}
