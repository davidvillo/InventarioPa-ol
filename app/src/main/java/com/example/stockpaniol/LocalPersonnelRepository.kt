package com.example.stockpaniol

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/** Persistence of people and their worksite catalogue is independent of inventory rules. */
class LocalPersonnelRepository(private val database: () -> SQLiteDatabase) : PersonnelRepository {
    companion object {
        fun migrate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE obras (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL COLLATE NOCASE UNIQUE, activo INTEGER NOT NULL DEFAULT 1 CHECK(activo IN (0,1)))")
            db.execSQL("ALTER TABLE personal ADD COLUMN obra_id INTEGER REFERENCES obras(id) ON DELETE RESTRICT")
            db.execSQL("ALTER TABLE personal ADD COLUMN activo INTEGER NOT NULL DEFAULT 1 CHECK(activo IN (0,1))")
            Worksite.defaults.forEach { name -> db.execSQL("INSERT INTO obras(nombre) VALUES (?)", arrayOf(name)) }
            db.rawQuery("SELECT id, obra FROM personal ORDER BY id", null).use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1).trim().ifBlank { "Otros" }
                    val worksiteId = db.rawQuery("SELECT id FROM obras WHERE nombre=? COLLATE NOCASE", arrayOf(name)).use { if (it.moveToFirst()) it.getLong(0) else null }
                        ?: db.insertOrThrow("obras", null, ContentValues().apply { put("nombre", name) })
                    db.update("personal", ContentValues().apply { put("obra_id", worksiteId); put("obra", name) }, "id=?", arrayOf(c.getLong(0).toString()))
                }
            }
        }
    }

    private fun transaction(action: (SQLiteDatabase) -> Unit) {
        val db = database()
        db.beginTransaction()
        try { action(db); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }

    override fun worksites(includeInactive: Boolean): List<Worksite> = database().rawQuery(
        "SELECT id, nombre, activo FROM obras ${if (includeInactive) "" else "WHERE activo=1"} ORDER BY id", null).use { c ->
        buildList { while (c.moveToNext()) add(Worksite(c.getLong(0), c.getString(1), c.getInt(2) == 1)) }
    }

    override fun personnel(includeInactive: Boolean): List<Worker> = database().rawQuery("""SELECT p.id, p.nombre, p.apellido,
        o.id, o.nombre, o.activo, p.apodo, p.activo FROM personal p JOIN obras o ON o.id=p.obra_id
        ${if (includeInactive) "" else "WHERE p.activo=1"} ORDER BY p.apellido COLLATE NOCASE, p.nombre COLLATE NOCASE, p.id""", null).use { c ->
        buildList { while (c.moveToNext()) add(Worker(c.getLong(0), c.getString(1), c.getString(2), Worksite(c.getLong(3), c.getString(4), c.getInt(5) == 1), c.getString(6), c.getInt(7) == 1)) }
    }

    private fun values(worker: Worker) = ContentValues().apply {
        put("nombre", worker.name); put("apellido", worker.surname); put("obra", worker.worksite)
        put("obra_id", worker.assignment.id); put("apodo", worker.nickname)
    }

    override fun addWorker(draft: WorkerDraft) = transaction { db ->
        draft.validate()
        val worksite = worksites().firstOrNull { it.id == draft.worksiteId } ?: error("Seleccioná una obra disponible.")
        val worker = Worker(0, "", "", worksite, "").edited(draft, worksite)
        db.insertOrThrow("personal", null, values(worker))
    }

    override fun addWorker(name: String, surname: String, worksite: String, nickname: String) = transaction { _ ->
        require(name.isNotBlank() && surname.isNotBlank() && worksite.isNotBlank()) { "Completá nombre, apellido y obra." }
        val site = worksites().firstOrNull { it.name.equals(worksite.trim(), true) }
            ?: run { addWorksite(worksite); worksites().first { it.name.equals(worksite.trim(), true) } }
        addWorker(WorkerDraft(name, surname, site.id, nickname))
    }

    override fun updateWorker(workerId: Long, draft: WorkerDraft) = transaction { db ->
        val worker = personnel().firstOrNull { it.id == workerId } ?: error("La persona ya no está disponible.")
        val worksite = worksites(true).firstOrNull { it.id == draft.worksiteId } ?: error("La obra ya no existe.")
        require(worksite.active || worker.assignment.id == worksite.id) { "Seleccioná una obra disponible." }
        val edited = worker.edited(draft, worksite)
        check(db.update("personal", values(edited), "id=? AND activo=1", arrayOf(workerId.toString())) == 1)
    }

    override fun deleteWorker(workerId: Long) = transaction { db ->
        require(personnel().any { it.id == workerId }) { "La persona ya no está disponible." }
        val hasHistory = listOf("movimientos_materiales", "movimientos_herramientas").any { table ->
            db.rawQuery("SELECT 1 FROM $table WHERE personal_id=? LIMIT 1", arrayOf(workerId.toString())).use { it.moveToFirst() }
        }
        if (hasHistory) db.update("personal", ContentValues().apply { put("activo", 0) }, "id=?", arrayOf(workerId.toString()))
        else db.delete("personal", "id=?", arrayOf(workerId.toString()))
    }

    override fun addWorksite(name: String) = transaction { db ->
        require(name.isNotBlank()) { "Ingresá el nombre de la obra." }
        val existing = worksites(true).firstOrNull { it.name.equals(name.trim(), true) }
        require(existing == null || !existing.active) { "Esa opción ya existe." }
        if (existing != null) db.update("obras", ContentValues().apply { put("activo", 1) }, "id=?", arrayOf(existing.id.toString()))
        else db.insertOrThrow("obras", null, ContentValues().apply { put("nombre", name.trim()) })
    }

    override fun updateWorksite(worksiteId: Long, name: String) = transaction { db ->
        require(name.isNotBlank()) { "Ingresá el nombre de la obra." }
        require(worksites().any { it.id == worksiteId }) { "La opción ya no está disponible." }
        require(worksites(true).none { it.id != worksiteId && it.name.equals(name.trim(), true) }) { "Esa opción ya existe." }
        db.update("obras", ContentValues().apply { put("nombre", name.trim()) }, "id=?", arrayOf(worksiteId.toString()))
        db.update("personal", ContentValues().apply { put("obra", name.trim()) }, "obra_id=?", arrayOf(worksiteId.toString()))
    }

    override fun deleteWorksite(worksiteId: Long) = transaction { db ->
        require(worksites().any { it.id == worksiteId }) { "La opción ya no está disponible." }
        val assigned = db.rawQuery("SELECT 1 FROM personal WHERE obra_id=? LIMIT 1", arrayOf(worksiteId.toString())).use { it.moveToFirst() }
        if (assigned) db.update("obras", ContentValues().apply { put("activo", 0) }, "id=?", arrayOf(worksiteId.toString()))
        else db.delete("obras", "id=?", arrayOf(worksiteId.toString()))
    }
}
