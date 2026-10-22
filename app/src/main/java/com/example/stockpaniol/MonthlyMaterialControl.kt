package com.example.stockpaniol

import java.text.Collator
import java.util.Locale

internal fun hasGloveControl(item: StockItem): Boolean =
    item.name.trim().replace(Regex("\\s+"), " ").equals("Guantes moteados", ignoreCase = true)

internal data class MonthlyMaterialRow(val worker: Worker, val quantity: Double)

internal enum class MonthlyMaterialSort(val label: String) {
    PERSONAL_ASC("Personal A–Z"), PERSONAL_DESC("Personal Z–A"),
    QUANTITY_DESC("Más guantes primero"), QUANTITY_ASC("Menos guantes primero")
}

/** Count deliveries, rather than pending loans: returning a pair does not undo its receipt. */
internal fun monthlyMaterialRows(itemId: Long, month: String, workers: List<Worker>,
    movements: List<StockMovement>, query: String = "", sort: MonthlyMaterialSort = MonthlyMaterialSort.PERSONAL_ASC): List<MonthlyMaterialRow> {
    val totals = mutableMapOf<Long, Double>()
    movements.filter { it.itemId == itemId && it.kind == "Egreso" && it.date.take(7) == month }
        .forEach { movement -> movement.workerId?.let { id ->
            totals[id] = stockSum(totals[id] ?: 0.0, movement.quantity)
        } }
    val collator = Collator.getInstance(Locale.forLanguageTag("es-AR"))
    val alphabetical = Comparator<MonthlyMaterialRow> { a, b ->
        collator.compare("${a.worker.surname} ${a.worker.name}", "${b.worker.surname} ${b.worker.name}")
    }
    val order = when (sort) {
        MonthlyMaterialSort.PERSONAL_ASC -> alphabetical
        MonthlyMaterialSort.PERSONAL_DESC -> Comparator { a, b -> alphabetical.compare(b, a) }
        MonthlyMaterialSort.QUANTITY_DESC -> compareByDescending<MonthlyMaterialRow> { it.quantity }.then(alphabetical)
        MonthlyMaterialSort.QUANTITY_ASC -> compareBy<MonthlyMaterialRow> { it.quantity }.then(alphabetical)
    }
    return workers.filter { it.selectionLabel.contains(query.trim(), ignoreCase = true) }
        .map { MonthlyMaterialRow(it, totals[it.id] ?: 0.0) }
        .sortedWith(order)
}

internal fun monthlyMaterialAlert(quantity: Double): StockAlert = when {
    quantity >= 2 -> StockAlert.EMPTY
    quantity > 0 -> StockAlert.LOANED
    else -> StockAlert.AVAILABLE
}
