package com.example.stockpaniol

enum class MovementStatus(val label: String) {
    ALL("Todos"), LOANED("Prestadas"), RETURNED("Devueltas / en pañol"), IN_REPAIR("En reparación"),
    DAMAGED("Dañadas en pañol"), REPAIRED("Reparadas"), OTHER("Otros movimientos")
}

fun movementStatus(movement: StockMovement, currentUnits: Map<Long, InventoryUnit>): MovementStatus {
    if (movement.kind == "Egreso" && movement.pending > 0) return MovementStatus.LOANED
    when (movement.kind) {
        ToolAction.SEND_REPAIR.movement -> return MovementStatus.IN_REPAIR
        ToolAction.REPAIRED.movement -> return MovementStatus.REPAIRED
        ToolAction.DAMAGE.movement, ToolAction.UNREPAIRED.movement, ToolAction.RESTORE.movement,
        MaterialDamageAction.DAMAGE.movement, MaterialDamageAction.RESTORE.movement,
        MaterialDamageAction.USE.movement -> return MovementStatus.DAMAGED
    }
    val returned = movement.kind == "Egreso" && movement.returned > 0 ||
        movement.kind in listOf("Devolución", "Devolución dañada", MaterialDamageAction.USE.movement)
    if (!returned) return MovementStatus.OTHER
    val units = movement.units.map { currentUnits[it.unit.id] ?: it.unit }
    if (units.any { it.loanId != null || !it.inActiveStock }) return MovementStatus.OTHER
    if (units.any { it.condition == ToolCondition.IN_REPAIR }) return MovementStatus.IN_REPAIR
    if (units.any { it.condition == ToolCondition.DAMAGED || it.useDamaged }) return MovementStatus.DAMAGED
    if (movement.kind == "Devolución dañada" && units.isEmpty()) return MovementStatus.DAMAGED
    return MovementStatus.RETURNED
}

fun matchesMovementStatus(movement: StockMovement, currentUnits: Map<Long, InventoryUnit>, status: MovementStatus): Boolean =
    status == MovementStatus.ALL || movementStatus(movement, currentUnits) == status
