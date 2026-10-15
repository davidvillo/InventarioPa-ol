package com.example.stockpaniol

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

object MaterialTypes {
    const val CONSTRUCTION = "Material de construcción"
    const val PPE = "EPP"
    const val ELECTRICAL = "Material eléctrico"
    val all = listOf(CONSTRUCTION, PPE, ELECTRICAL)
}
data class StockMovement(val id: Long, val itemId: Long, val itemName: String, val quantity: Double, val unit: String,
    val date: String, val person: String, val workerId: Long?, val kind: String, val materialType: String, val notes: String,
    val parentId: Long?, val returned: Double, val returnDate: String, val purchaseId: Long?,
    val control: StockControl = StockControl.QUANTITY, val units: List<MovementUnit> = emptyList(), val wholeNumbers: Boolean = unit in listOf("unidades", "pares"),
    val auditProtected: Boolean = false) {
    val pending: Double get() = stockSum(quantity, -returned)
    val individuallyTracked: Boolean get() = control == StockControl.INDIVIDUAL
    val identitySummary: String get() = units.joinToString("; ") { if (kind == "Egreso") it.label else it.unit.label }
}
fun stockSum(a: Double, b: Double): Double = BigDecimal.valueOf(a).add(BigDecimal.valueOf(b)).toDouble()
fun todayStockDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
fun yesterdayStockDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    .format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time)
fun displayStockDate(date: String): String {
    if (date.isBlank()) return "—"
    val parsed = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }.parse(date)
    }.getOrNull() ?: return "—"
    val spanish = Locale.forLanguageTag("es-AR")
    return SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", spanish).format(parsed)
        .replaceFirstChar { it.titlecase(spanish) }
}
fun formatStockQuantity(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    .replace('.', java.text.DecimalFormatSymbols.getInstance().decimalSeparator)
fun validateMovementQuantity(quantity: Double, unit: String, wholeNumbers: Boolean = unit in listOf("unidades", "pares")) {
    require(quantity.isFinite() && quantity > 0) { "Ingresá una cantidad mayor a cero." }
    require(!wholeNumbers || quantity % 1.0 == 0.0) { "La cantidad de $unit debe ser un número entero." }
}
fun validateMovementDate(date: String) {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
    require(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && runCatching { format.parse(date) }.getOrNull() != null) { "Seleccioná una fecha válida." }
}
