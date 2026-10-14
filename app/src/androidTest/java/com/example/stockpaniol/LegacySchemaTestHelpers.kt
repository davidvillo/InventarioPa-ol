package com.example.stockpaniol

import android.database.sqlite.SQLiteDatabase

internal fun removeMeasurementSchema(db: SQLiteDatabase) {
    for (stock in listOf("materiales", "herramientas")) {
        db.execSQL("ALTER TABLE $stock DROP COLUMN unidad_medida_id")
        db.execSQL("ALTER TABLE movimientos_$stock DROP COLUMN cantidad_entera")
    }
    db.execSQL("DROP TABLE unidades_medida")
}
