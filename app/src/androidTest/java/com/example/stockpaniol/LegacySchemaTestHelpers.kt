package com.example.stockpaniol

import android.database.sqlite.SQLiteDatabase

internal fun removeMeasurementSchema(db: SQLiteDatabase) {
    removeToolLifecycleSchema(db)
    for (stock in listOf("materiales", "herramientas")) {
        db.execSQL("ALTER TABLE $stock DROP COLUMN unidad_medida_id")
        db.execSQL("ALTER TABLE movimientos_$stock DROP COLUMN cantidad_entera")
    }
    db.execSQL("DROP TABLE unidades_medida")
}

internal fun removeToolLifecycleSchema(db: SQLiteDatabase) {
    removeToolRentalSchema(db)
    db.execSQL("DROP TABLE IF EXISTS eventos_herramientas")
    db.execSQL("DROP TABLE IF EXISTS estado_herramientas")
}

internal fun removeToolRentalSchema(db: SQLiteDatabase) {
    removeToolBundleSchema(db)
    db.execSQL("DROP TABLE IF EXISTS alquileres_herramientas")
}

internal fun removeToolBundleSchema(db: SQLiteDatabase) {
    removeStockCatalogueSchema(db)
    db.execSQL("DROP TABLE IF EXISTS prestamos_conjuntos")
    db.execSQL("DROP TABLE IF EXISTS miembros_conjuntos")
    db.execSQL("DROP TABLE IF EXISTS conjuntos_herramientas")
}

internal fun removeStockCatalogueSchema(db: SQLiteDatabase) {
    db.execSQL("DROP TABLE IF EXISTS opciones_stock")
}
