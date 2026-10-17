package com.example.stockpaniol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MaterialEditDialog(itemId: Long, repository: StockRepository, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var item by remember { mutableStateOf<StockItem?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    var brand by rememberSaveable { mutableStateOf("") }
    var company by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(MaterialTypes.CONSTRUCTION) }
    var removed by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var date by rememberSaveable { mutableStateOf(todayStockDate()) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var expectedQuantity by rememberSaveable { mutableStateOf<Double?>(null) }
    var brands by remember { mutableStateOf(emptyList<StockCatalogueOption>()) }
    var companies by remember { mutableStateOf(emptyList<StockCatalogueOption>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(itemId) {
        runCatching { withContext(Dispatchers.IO) {
            Triple(repository.items(false).first { it.id == itemId }, repository.stockOptions(StockCatalogueKind.BRAND), repository.stockOptions(StockCatalogueKind.COMPANY))
        } }.onSuccess { (stock, brandOptions, companyOptions) ->
            item = stock; brands = brandOptions; companies = companyOptions
            if (!initialized) {
                name = stock.name; quantity = formatStockQuantity(stock.quantity); brand = stock.brand
                company = stock.company; notes = stock.notes; category = stock.materialType
                expectedQuantity = stock.quantity; initialized = true
            }
        }.onFailure { error = "No se pudo cargar el material." }
    }
    val amount = quantity.trim().replace(',', '.').toDoubleOrNull()
    val stock = item
    val removedCount = if (stock != null && amount != null && amount.isFinite()) maxOf(0.0, stockSum(stock.quantity, -amount)) else 0.0
    StockOverlay({ if (!busy) onDismiss() }) {
        Text("Editar material", style = MaterialTheme.typography.titleLarge)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (stock == null && error.isBlank()) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (stock != null) {
                OutlinedTextField(name, { name = it }, enabled = !busy, label = { Text("Nombre del material *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(quantity, { quantity = it; removed = emptyList() }, enabled = !busy,
                    label = { Text("Cantidad disponible en pañol (${stock.unit}) *") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = if (stock.wholeNumbers) KeyboardType.Number else KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Text("Actual: ${formatStockQuantity(stock.quantity)} ${stock.unit}. La corrección se guarda directamente y queda registrada en Movimientos de materiales.", style = MaterialTheme.typography.bodySmall)
                if (stock.individuallyTracked && removedCount > 0) {
                    Text("Elegí las ${formatStockQuantity(removedCount)} unidades sobrantes que se retiran del stock disponible.")
                    stock.availableUnits.forEach { unit ->
                        Row {
                            Checkbox(unit.id in removed, { checked -> removed = if (checked) removed + unit.id else removed - unit.id }, enabled = !busy)
                            Text(unit.label, Modifier.weight(1f))
                        }
                    }
                    Text("Las unidades retiradas conservan su historial. Los préstamos y materiales dañados no se modifican.", style = MaterialTheme.typography.bodySmall)
                }
                if (stock.individuallyTracked && amount != null && amount.isFinite() && amount > stock.quantity) Text("Se crearán ${formatStockQuantity(stockSum(amount, -stock.quantity))} unidades identificadas adicionales.", style = MaterialTheme.typography.bodySmall)
                if (amount != null && amount != stock.quantity) StockDateField("Fecha de corrección", date, !busy) { date = it }
                StockChoice("Tipo de material", category, MaterialTypes.all.map { it to it }, !busy) { category = it }
                StockCatalogueField(if (stock.individuallyTracked) "Marca para unidades agregadas" else "Marca", brand, brands, !busy) { brand = it }
                StockCatalogueField("Empresa", company, companies, !busy) { company = it }
                OutlinedTextField(notes, { notes = it }, enabled = !busy, label = { Text("Observaciones del material") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") }
            Button(enabled = !busy && stock != null, onClick = {
                if (amount == null) error = "Ingresá una cantidad válida."
                else {
                    busy = true
                    scope.launch {
                        val edit = MaterialEdit(name, amount, expectedQuantity ?: stock!!.quantity, notes, brand, company, category, removed, date)
                        val result = withContext(Dispatchers.IO) { runCatching { repository.updateMaterial(itemId, edit) } }
                        busy = false
                        result.onSuccess { onSaved() }.onFailure { error = it.message ?: "No se pudieron guardar los cambios." }
                    }
                }
            }) { Text(if (busy) "Guardando…" else "Guardar cambios del material") }
        }
    }
}
