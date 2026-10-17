package com.example.stockpaniol

data class MovementDay(val date: String, val movements: List<StockMovement>)

/** Keep each day's chosen ordering while ensuring there is only one section per date. */
fun groupMovementDays(rows: List<StockMovement>, newestFirst: Boolean = true): List<MovementDay> {
    val grouped = rows.groupBy { it.date }
    val dates = if (newestFirst) grouped.keys.sortedDescending() else grouped.keys.sorted()
    return dates.map { MovementDay(it, grouped.getValue(it)) }
}
