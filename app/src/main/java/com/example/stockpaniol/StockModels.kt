package com.example.stockpaniol

/** The article is a catalogue entry; an InventoryUnit is a physical object. */
enum class StockControl { QUANTITY, INDIVIDUAL }

sealed interface InventoryPolicy {
    fun validateReceipt(quantity: Double, unit: String, identifier: String, wholeNumbers: Boolean = unit in listOf("unidades", "pares"), counting: Boolean = unit == "unidades")
    fun validateSelection(quantity: Double, selectedIds: List<Long>)

    object ByQuantity : InventoryPolicy {
        override fun validateReceipt(quantity: Double, unit: String, identifier: String, wholeNumbers: Boolean, counting: Boolean) {
            validateMovementQuantity(quantity, unit, wholeNumbers)
            require(identifier.isBlank()) { "Los artículos por cantidad no llevan identificador individual." }
        }
        override fun validateSelection(quantity: Double, selectedIds: List<Long>) {
            require(selectedIds.isEmpty()) { "Este artículo se controla por cantidad." }
        }
    }

    object ByUnit : InventoryPolicy {
        override fun validateReceipt(quantity: Double, unit: String, identifier: String, wholeNumbers: Boolean, counting: Boolean) {
            validateMovementQuantity(quantity, unit, true)
            require(counting) { "Los artículos identificados se registran en unidades." }
            require(identifier.isBlank() || quantity == 1.0) { "Ingresá una unidad por cada identificador personalizado." }
        }
        override fun validateSelection(quantity: Double, selectedIds: List<Long>) {
            require(selectedIds.isNotEmpty() && selectedIds.distinct().size == selectedIds.size && quantity == selectedIds.size.toDouble()) {
                "Seleccioná exactamente las unidades que se entregan o devuelven."
            }
        }
    }
}

data class InventoryUnit(val id: Long, val itemId: Long, val identifier: String, val brand: String,
    val holder: String = "", val legacy: Boolean = false, val loanId: Long? = null) {
    val available: Boolean get() = loanId == null
    val label: String get() = listOf(brand, identifier).filter { it.isNotBlank() }.joinToString(" · ")
}

data class MovementUnit(val unit: InventoryUnit, val returned: Boolean) {
    val label: String get() = unit.label + if (returned) " · Devuelta" else ""
}

data class StockItem(val id: Long, val name: String, val quantity: Double, val unit: String,
    val notes: String, val image: String, val brand: String, val company: String,
    val materialType: String = MaterialTypes.CONSTRUCTION, val control: StockControl = StockControl.QUANTITY,
    val units: List<InventoryUnit> = emptyList(), val wholeNumbers: Boolean = unit in listOf("unidades", "pares")) {
    val individuallyTracked: Boolean get() = control == StockControl.INDIVIDUAL
    val policy: InventoryPolicy get() = if (individuallyTracked) InventoryPolicy.ByUnit else InventoryPolicy.ByQuantity
    val availableUnits: List<InventoryUnit> get() = units.filter { it.available }
    val identitySummary: String get() = units.joinToString("; ") { "${it.label} (${if (it.available) "En pañol" else it.holder})" }
}

data class StockEntry(val itemId: Long, val name: String, val quantity: Double, val unit: String,
    val notes: String, val receipt: String, val date: String, val image: String, val brand: String, val company: String,
    val materialType: String = MaterialTypes.CONSTRUCTION, val control: StockControl = StockControl.QUANTITY,
    val identifier: String = "")
