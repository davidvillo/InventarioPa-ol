package com.example.stockpaniol

/** The article is a catalogue entry; an InventoryUnit is a physical object. */
enum class StockControl { QUANTITY, INDIVIDUAL }

fun validateStockReceiptQuantity(quantity: Double, unit: String, wholeNumbers: Boolean) {
    require(quantity.isFinite() && quantity >= 0) { "Ingresá una cantidad igual o mayor a cero." }
    require(!wholeNumbers || quantity % 1.0 == 0.0) { "La cantidad de $unit debe ser un número entero." }
}

sealed interface InventoryPolicy {
    fun validateReceipt(quantity: Double, unit: String, identifier: String, wholeNumbers: Boolean = unit in listOf("unidades", "pares"), counting: Boolean = unit in listOf("unidades", "pares"))
    fun validateSelection(quantity: Double, selectedIds: List<Long>)

    object ByQuantity : InventoryPolicy {
        override fun validateReceipt(quantity: Double, unit: String, identifier: String, wholeNumbers: Boolean, counting: Boolean) {
            validateStockReceiptQuantity(quantity, unit, wholeNumbers)
            require(identifier.isBlank()) { "Los artículos por cantidad no llevan identificador individual." }
        }
        override fun validateSelection(quantity: Double, selectedIds: List<Long>) {
            require(selectedIds.isEmpty()) { "Este artículo se controla por cantidad." }
        }
    }

    object ByUnit : InventoryPolicy {
        override fun validateReceipt(quantity: Double, unit: String, identifier: String, wholeNumbers: Boolean, counting: Boolean) {
            validateStockReceiptQuantity(quantity, unit, true)
            require(quantity <= Int.MAX_VALUE) { "La cantidad de unidades es demasiado grande." }
            require(counting) { "Los artículos identificados requieren unidades o pares habilitados para su control." }
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
    val holder: String = "", val legacy: Boolean = false, val loanId: Long? = null,
    val condition: ToolCondition = ToolCondition.OPERATIVE, val damageNotes: String = "", val statusDate: String = "",
    val repairPlace: String = "", val repairDate: String = "", val rental: ToolRental? = null,
    val bundleId: Long? = null, val bundleName: String = "", val useDamaged: Boolean = false,
    val excludedByAdjustment: Boolean = false) {
    val available: Boolean get() = !excludedByAdjustment && loanId == null && condition == ToolCondition.OPERATIVE && rental?.closed != true
    val inActiveStock: Boolean get() = !excludedByAdjustment && condition != ToolCondition.RETIRED && rental?.closed != true
    val statusLabel: String get() = if (excludedByAdjustment) "Retirada por corrección de cantidad" else if (rental?.closed == true) "Alquiler devuelto al proveedor" else if (loanId != null) "Prestada a $holder" else if (useDamaged) "Disponible · utilizar dañado" else condition.label
    val label: String get() = (listOf(brand, identifier) + listOfNotNull(rental?.let { "Alquilada: ${it.supplier}" }, bundleName.takeIf { it.isNotBlank() }?.let { "Conjunto: $it" })).filter { it.isNotBlank() }.joinToString(" · ")
}

data class MovementUnit(val unit: InventoryUnit, val returned: Boolean) {
    val label: String get() = unit.label + if (returned) " · Devuelta" else ""
}

data class StockItem(val id: Long, val name: String, val quantity: Double, val unit: String,
    val notes: String, val image: String, val brand: String, val company: String,
    val materialType: String = MaterialTypes.CONSTRUCTION, val control: StockControl = StockControl.QUANTITY,
    val units: List<InventoryUnit> = emptyList(), val wholeNumbers: Boolean = unit in listOf("unidades", "pares")) {
    val individuallyTracked: Boolean get() = control == StockControl.INDIVIDUAL
    val images: List<String> get() = StockPhotos.decode(image)
    val policy: InventoryPolicy get() = if (individuallyTracked) InventoryPolicy.ByUnit else InventoryPolicy.ByQuantity
    val availableUnits: List<InventoryUnit> get() = units.filter { it.available }
    val identitySummary: String get() = units.filter { it.inActiveStock }.joinToString("; ") { "${it.label} (${it.statusLabel})" }
}

data class StockUnitEntry(val brand: String = "", val identifier: String = "")

data class ToolUnitEdit(val id: Long, val brand: String, val identifier: String)
data class ToolPurchaseEdit(val id: Long, val date: String, val receipt: String, val company: String)

data class MaterialEdit(val name: String, val quantity: Double, val expectedQuantity: Double,
    val notes: String, val brand: String, val company: String, val materialType: String,
    val removedUnitIds: List<Long> = emptyList(), val date: String = todayStockDate())
data class ToolEdit(val name: String, val notes: String, val company: String, val images: List<String>,
    val units: List<ToolUnitEdit>, val purchases: List<ToolPurchaseEdit>)

data class StockEntry(val itemId: Long, val name: String, val quantity: Double, val unit: String,
    val notes: String, val receipt: String, val date: String, val image: String, val brand: String, val company: String,
    val materialType: String = MaterialTypes.CONSTRUCTION, val control: StockControl = StockControl.QUANTITY,
    val identifier: String = "", val unitDetails: List<StockUnitEntry> = emptyList(), val rental: RentalEntry? = null)
