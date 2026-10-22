package com.example.stockpaniol

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/** All entry points run inside one repository transaction, including linked loans and returns. */
internal object MovementDeletion {
    fun delete(repository: LocalStockRepository, db: SQLiteDatabase, tools: Boolean, movementId: Long) {
        val movement = repository.movements(tools).firstOrNull { it.id == movementId }
            ?: error("El movimiento ya no existe.")
        if (movementId < 0) {
            if (tools) deleteToolEvent(repository, db, movement) else deleteMaterialEvent(repository, db, movement)
            return
        }
        val stock = if (tools) "herramientas" else "materiales"
        val ledger = "movimientos_$stock"
        val links = "unidades_movimientos_$stock"
        val item = repository.items(tools).first { it.id == movement.itemId }
        if (tools && movement.kind == "Egreso") {
            // A loan row may include multiple sets; follow all connected tokens before changing anything.
            val tokens = ToolBundles.loanTokens(db)
            val ids = mutableSetOf(movement.id)
            do {
                val before = ids.size
                val groups = tokens.filterKeys { it.first in ids }.values.toSet()
                ids += tokens.filterValues { it in groups }.keys.map { it.first }
            } while (before != ids.size)
            ids.forEach { id -> db.delete("prestamos_conjuntos", "movimiento_id=?", arrayOf(id.toString())) }
            ids.forEach { id ->
                val loan = repository.movements(true).first { it.id == id }
                db.delete("eventos_herramientas", "prestamo_id=?", arrayOf(id.toString()))
                db.delete(ledger, "id=?", arrayOf(id.toString()))
                loan.units.forEach { rebuildToolState(db, it.unit.id) }
                syncQuantity(repository, db, true, loan.itemId)
            }
            return
        }
        if (movement.kind == "Ingreso" && item.individuallyTracked) {
            val ids = movement.units.map { it.unit.id }
            require(ids.size.toDouble() == movement.quantity) { "Este ingreso histórico no identifica sus unidades. Corregí sus cantidades antes de eliminarlo." }
            require(repository.movements(tools).none { it.id != movement.id && it.units.any { u -> u.unit.id in ids } }) {
                "El ingreso tiene movimientos posteriores. Eliminá primero los préstamos, devoluciones o cambios de estado de sus unidades."
            }
            db.delete(links, "movimiento_id=?", arrayOf(movement.id.toString()))
            ids.forEach { id -> removeUnit(db, tools, id) }
        }
        if (movement.kind == "Devolución") {
            val parent = repository.movements(tools).first { it.id == movement.parentId }
            if (item.individuallyTracked) {
                movement.units.forEach { returned ->
                    val unit = item.units.first { it.id == returned.unit.id }
                    require(unit.loanId == null && !unit.excludedByAdjustment) {
                        "Una unidad tiene un préstamo o una corrección posterior. Eliminá primero esa operación."
                    }
                    db.execSQL("UPDATE $links SET devuelta=0 WHERE movimiento_id=? AND unidad_id=?", arrayOf(parent.id, unit.id))
                }
            } else changeQuantity(db, stock, item, -movement.quantity)
            db.delete(ledger, "id=?", arrayOf(movement.id.toString()))
            val latest = db.rawQuery("SELECT MAX(fecha) FROM $ledger WHERE padre_id=?", arrayOf(parent.id.toString())).use {
                it.moveToFirst(); if (it.isNull(0)) "" else it.getString(0)
            }
            db.execSQL("UPDATE $ledger SET devuelto=?, fecha_devolucion=? WHERE id=?",
                arrayOf(stockSum(parent.returned, -movement.quantity), latest, parent.id))
        } else {
            if (!item.individuallyTracked) changeQuantity(db, stock, item,
                if (movement.kind == "Egreso") movement.pending else -movement.quantity)
            if (movement.kind == "Egreso") db.delete(ledger, "padre_id=?", arrayOf(movement.id.toString()))
            db.delete(ledger, "id=?", arrayOf(movement.id.toString()))
        }
        movement.purchaseId?.let { db.delete("compras_$stock", "id=?", arrayOf(it.toString())) }
        if (item.individuallyTracked) syncQuantity(repository, db, tools, item.id)
    }

    private fun changeQuantity(db: SQLiteDatabase, stock: String, item: StockItem, delta: Double) {
        val quantity = stockSum(item.quantity, delta)
        require(quantity.isFinite() && quantity >= 0) { "El movimiento ya fue utilizado por operaciones posteriores. Corregí o eliminá esas operaciones primero: el stock no puede quedar negativo." }
        db.execSQL("UPDATE $stock SET cantidad=? WHERE id=?", arrayOf(quantity, item.id))
    }

    private fun syncQuantity(repository: LocalStockRepository, db: SQLiteDatabase, tools: Boolean, itemId: Long) {
        val item = repository.items(tools).first { it.id == itemId }
        db.execSQL("UPDATE ${if (tools) "herramientas" else "materiales"} SET cantidad=? WHERE id=?",
            arrayOf(item.availableUnits.size.toDouble(), itemId))
    }

    private fun removeUnit(db: SQLiteDatabase, tools: Boolean, unitId: Long) {
        val args = arrayOf(unitId.toString())
        if (tools) {
            val bundles = ToolBundles.all(db).filter { unitId in it.unitIds }
            bundles.forEach { bundle ->
                val remaining = bundle.unitIds - unitId
                if (remaining.size < 2) db.delete("conjuntos_herramientas", "id=?", arrayOf(bundle.id.toString()))
                else if (bundle.principalUnitId == unitId) db.execSQL("UPDATE conjuntos_herramientas SET principal_id=? WHERE id=?", arrayOf(remaining.first(), bundle.id))
            }
            db.delete("miembros_conjuntos", "unidad_id=?", args)
            db.delete("estado_herramientas", "unidad_id=?", args)
            db.delete("alquileres_herramientas", "unidad_id=?", args)
        } else db.delete("danos_materiales", "unidad_id=?", args)
        db.delete("unidades_${if (tools) "herramientas" else "materiales"}", "id=?", args)
    }

    private fun deleteToolEvent(repository: LocalStockRepository, db: SQLiteDatabase, event: StockMovement) {
        val unitId = event.units.single().unit.id
        val unit = repository.items(true).first { it.id == event.itemId }.units.first { it.id == unitId }
        if (event.kind == ToolRentals.INCOME) {
            require(repository.movements(true).none { it.id != event.id && it.units.any { u -> u.unit.id == unitId } }) {
                "El alquiler tiene operaciones posteriores. Eliminá primero sus préstamos, devoluciones y cambios de estado."
            }
            db.delete("eventos_herramientas", "id=?", arrayOf((-event.id).toString()))
            removeUnit(db, true, unitId)
        } else {
            if (event.kind in listOf("Devolución", "Devolución dañada")) {
                val parentId = requireNotNull(event.parentId)
                require(unit.loanId == null && unit.rental?.closed != true) {
                    "La herramienta volvió a prestarse o fue devuelta al proveedor. Eliminá primero esa operación posterior."
                }
                db.execSQL("UPDATE unidades_movimientos_herramientas SET devuelta=0 WHERE movimiento_id=? AND unidad_id=?", arrayOf(parentId, unitId))
                db.execSQL("UPDATE movimientos_herramientas SET devuelto=devuelto-1 WHERE id=?", arrayOf(parentId))
            }
            db.delete("eventos_herramientas", "id=?", arrayOf((-event.id).toString()))
            if (event.kind in listOf("Devolución", "Devolución dañada")) {
                db.execSQL("""UPDATE movimientos_herramientas SET fecha_devolucion=COALESCE(
                    (SELECT MAX(fecha) FROM eventos_herramientas WHERE prestamo_id=?), '') WHERE id=?""", arrayOf(event.parentId, event.parentId))
            }
            if (event.kind == ToolRentals.RETURN) db.execSQL("UPDATE alquileres_herramientas SET fecha_devolucion='', observaciones_devolucion='' WHERE unidad_id=?", arrayOf(unitId))
            rebuildToolState(db, unitId)
        }
        syncQuantity(repository, db, true, event.itemId)
    }

    private fun rebuildToolState(db: SQLiteDatabase, unitId: Long) {
        var condition = ToolCondition.OPERATIVE
        var damage = ""; var date = ""; var workshop = ""; var repairDate = ""
        db.rawQuery("SELECT tipo, fecha, observaciones, taller FROM eventos_herramientas WHERE unidad_id=? ORDER BY fecha, id", arrayOf(unitId.toString())).use { c ->
            while (c.moveToNext()) {
                val kind = c.getString(0)
                val action = ToolAction.entries.firstOrNull { it.movement == kind }
                if (action != null) { condition = action.destination; date = c.getString(1) }
                if (kind == "Devolución dañada") { condition = ToolCondition.DAMAGED; date = c.getString(1); damage = c.getString(2).substringAfter("Daño: ", c.getString(2)) }
                if (action == ToolAction.DAMAGE) damage = c.getString(2)
                if (action == ToolAction.SEND_REPAIR) { workshop = c.getString(3); repairDate = c.getString(1) }
            }
        }
        db.execSQL("INSERT OR REPLACE INTO estado_herramientas VALUES (?, ?, ?, ?, ?, ?)", arrayOf(unitId, condition.name, damage, date, workshop, repairDate))
    }

    private fun deleteMaterialEvent(repository: LocalStockRepository, db: SQLiteDatabase, event: StockMovement) {
        val item = repository.items(false).first { it.id == event.itemId }
        if (event.kind == MaterialDamage.CORRECTION) {
            val delta = correctionDelta(event.notes) ?: error("No se pudo interpretar la corrección histórica. No se modificaron los datos.")
            if (item.individuallyTracked) {
                val unit = item.units.first { it.id == event.units.single().unit.id }
                if (delta < 0) db.execSQL("UPDATE unidades_materiales SET descartada_ajuste=0 WHERE id=?", arrayOf(unit.id))
                else {
                    require(repository.movements(false).none { it.id != event.id && it.units.any { u -> u.unit.id == unit.id } }) {
                        "La unidad agregada tiene operaciones posteriores. Eliminá esas operaciones primero."
                    }
                    removeUnit(db, false, unit.id)
                }
            } else changeQuantity(db, "materiales", item, -delta)
        } else {
            val allEvents = repository.movements(false).filter { it.id < 0 && it.itemId == item.id }
            val damageRows = repository.damagedMaterials().filter { it.itemId == item.id }
            val linkedId = db.rawQuery("SELECT valor FROM configuracion WHERE clave=?", arrayOf("dano_evento_${-event.id}")).use {
                if (it.moveToFirst()) it.getString(0).toLongOrNull() else null
            }
            val candidates = damageRows.filter { row ->
                row.unitId == event.units.firstOrNull()?.unit?.id && row.quantity == event.quantity &&
                    (event.notes == row.notes || event.notes.startsWith("Daño: ${row.notes}\n"))
            }
            require(linkedId != null || candidates.size <= 1) {
                "Este registro histórico coincide con varios daños. Revisá los registros de daño antes de eliminarlo; no se modificaron los datos."
            }
            val damage = if (linkedId != null) damageRows.firstOrNull { it.id == linkedId } else candidates.singleOrNull()
            if (damage != null) {
                val related = allEvents.filter { e ->
                    val link = db.rawQuery("SELECT valor FROM configuracion WHERE clave=?", arrayOf("dano_evento_${-e.id}")).use {
                        if (it.moveToFirst()) it.getString(0).toLongOrNull() else null
                    }
                    if (link != null) link == damage.id else e.units.firstOrNull()?.unit?.id == damage.unitId && e.quantity == damage.quantity &&
                        (e.notes == damage.notes || e.notes.startsWith("Daño: ${damage.notes}\n"))
                }.filter { it.id != event.id }.sortedWith(compareBy<StockMovement> { it.date }.thenBy { -it.id })
                val last = related.lastOrNull()
                val destination = when (last?.kind) {
                    MaterialDamageAction.USE.movement -> "USABLE"
                    MaterialDamageAction.TRASH.movement -> "TRASH"
                    else -> "DAMAGED"
                }
                if (!item.individuallyTracked) {
                    val before = if (damage.state == "USABLE") damage.quantity else 0.0
                    val after = if (last == null || destination == "USABLE") damage.quantity else 0.0
                    changeQuantity(db, "materiales", item, stockSum(after, -before))
                }
                if (last == null) db.delete("danos_materiales", "id=?", arrayOf(damage.id.toString()))
                else db.execSQL("UPDATE danos_materiales SET estado=?, fecha=? WHERE id=?", arrayOf(destination, last.date, damage.id))
            }
        }
        db.delete("eventos_materiales", "id=?", arrayOf((-event.id).toString()))
        db.delete("configuracion", "clave=?", arrayOf("dano_evento_${-event.id}"))
        if (item.individuallyTracked) syncQuantity(repository, db, false, item.id)
    }

    internal fun correctionDelta(notes: String): Double? {
        val values = notes.substringAfter("Cantidad disponible corregida: ", "").split(" → ")
        if (values.size != 2) return null
        val before = parseStockThreshold(values[0]) ?: return null
        val after = parseStockThreshold(values[1].substringBefore(' ')) ?: return null
        return stockSum(after, -before)
    }
}
