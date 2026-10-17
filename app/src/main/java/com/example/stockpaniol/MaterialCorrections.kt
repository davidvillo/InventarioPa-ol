package com.example.stockpaniol

import android.database.sqlite.SQLiteDatabase

internal object MaterialCorrections {
    fun migrate(db: SQLiteDatabase) {
        val exists = db.rawQuery("PRAGMA table_info(unidades_materiales)", null).use { c ->
            var found = false
            while (c.moveToNext()) if (c.getString(1) == "descartada_ajuste") found = true
            found
        }
        if (!exists) db.execSQL("ALTER TABLE unidades_materiales ADD COLUMN descartada_ajuste INTEGER NOT NULL DEFAULT 0 CHECK(descartada_ajuste IN (0,1))")
    }
}
