package com.example.stockpaniol

import java.util.Locale

private val singularStockUnits = mapOf("unidades" to "unidad", "pares" to "par", "litros" to "litro",
    "lts" to "litro", "metros" to "metro", "mts" to "metro", "kilogramos" to "kilogramo",
    "kilos" to "kilo", "gramos" to "gramo", "toneladas" to "tonelada", "bidones" to "bidón",
    "galones" to "galón", "arneses" to "arnés", "lápices" to "lápiz", "paquetes" to "paquete",
    "envases" to "envase", "cables" to "cable", "papeles" to "papel", "materiales" to "material")
private val invariantStockUnits = setOf("kg", "g", "mg", "m", "cm", "mm", "m²", "m³", "l", "ml", "mes", "gas")

/** Display grammar never changes catalogue names, measurement rules or historical quantities. */
fun stockUnitLabel(unit: String, quantity: Double): String {
    if (quantity != 1.0) return unit
    val parts = unit.trim().split(" de ", limit = 2)
    val words = parts.first().split(Regex("\\s+"))
    val result = words.map { word ->
        val lower = word.lowercase(Locale.ROOT)
        singularStockUnits[lower] ?: when {
            lower in invariantStockUnits -> word
            lower.endsWith("iones") -> word.dropLast(5) + "ión"
            lower.endsWith("ones") -> word.dropLast(4) + "ón"
            lower.endsWith("ores") -> word.dropLast(2)
            lower.endsWith("ades") || lower.endsWith("udes") -> word.dropLast(2)
            lower.endsWith("os") || lower.endsWith("as") -> word.dropLast(1)
            else -> word
        }
    }.joinToString(" ") + if (parts.size == 2) " de ${parts[1]}" else ""
    return if (unit.firstOrNull()?.isUpperCase() == true) result.replaceFirstChar { it.uppercase() } else result
}

fun formatStockAmount(quantity: Double, unit: String): String = "${formatStockQuantity(quantity)} ${stockUnitLabel(unit, quantity)}"

/** Keep the complete article; the filter restricts visibility, never its available stock count. */
internal fun stockLoanedToday(items: List<StockItem>, movements: List<StockMovement>, date: String): List<StockItem> {
    val ids = movements.filter { it.kind == "Egreso" && it.date == date && it.pending > 0.0 }.map { it.itemId }.toSet()
    return items.filter { it.id in ids }
}
