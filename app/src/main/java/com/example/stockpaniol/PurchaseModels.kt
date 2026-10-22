package com.example.stockpaniol

import java.text.Collator
import java.util.Locale

data class StockPurchase(val id: Long, val itemId: Long, val itemName: String, val quantity: Double,
    val unit: String, val date: String, val company: String, val receipt: String, val identitySummary: String = "", val movementId: Long? = null)

enum class PurchaseColumn(val label: String, val width: Int) {
    DATE("Fecha", 320), ITEM("Artículo", 190), IDENTITY("Marca / identificador", 280), QUANTITY("Cantidad", 110), UNIT("Unidad", 100), COMPANY("Empresa", 200), RECEIPT("Remito", 160);
    fun text(purchase: StockPurchase): String = when (this) {
        DATE -> purchase.date; ITEM -> purchase.itemName; QUANTITY -> formatStockQuantity(purchase.quantity)
        UNIT -> stockUnitLabel(purchase.unit, purchase.quantity); COMPANY -> purchase.company; RECEIPT -> purchase.receipt
        IDENTITY -> purchase.identitySummary
    }
    fun display(purchase: StockPurchase): String = if (this == DATE) displayStockDate(purchase.date) else text(purchase).ifBlank { "—" }
    fun label(tools: Boolean): String = if (this == ITEM) { if (tools) "Herramienta" else "Material" } else label
}

fun sortPurchases(rows: List<StockPurchase>, column: PurchaseColumn, descending: Boolean): List<StockPurchase> {
    val collator = Collator.getInstance(Locale.forLanguageTag("es-AR")).apply { strength = Collator.PRIMARY }
    val comparator = Comparator<StockPurchase> { a, b ->
        if (column == PurchaseColumn.QUANTITY) a.quantity.compareTo(b.quantity) else collator.compare(column.text(a), column.text(b))
    }
    return rows.sortedWith((if (descending) Comparator { a, b -> comparator.compare(b, a) } else comparator).thenByDescending { it.id })
}
