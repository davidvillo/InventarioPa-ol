package com.example.stockpaniol

data class Worksite(val id: Long, val name: String, val active: Boolean = true) {
    companion object {
        val defaults = listOf("513 Vereda", "513 Movimiento de suelo", "519", "Camionero", "Maquinista", "Administrativa", "Globalcia", "Otros")
    }
}

/** An immutable person instance keeps its identity when their details change. */
data class Worker(val id: Long, val name: String, val surname: String, val assignment: Worksite,
    val nickname: String, val active: Boolean = true) {
    val worksite: String get() = assignment.name
    val displayName: String get() = "$name $surname" + if (nickname.isBlank()) "" else " ($nickname)"
    val selectionLabel: String get() = "$displayName · $worksite" + if (active) "" else " (eliminado)"
    fun edited(draft: WorkerDraft, worksite: Worksite): Worker {
        draft.validate()
        require(draft.worksiteId == worksite.id)
        return copy(name = draft.name.trim(), surname = draft.surname.trim(), assignment = worksite, nickname = draft.nickname.trim())
    }
}

data class WorkerDraft(val name: String, val surname: String, val worksiteId: Long, val nickname: String) {
    fun validate() {
        require(name.isNotBlank() && surname.isNotBlank() && worksiteId > 0) { "Completá nombre, apellido y seleccioná una obra." }
    }
}

interface PersonnelRepository {
    fun personnel(includeInactive: Boolean = false): List<Worker>
    fun worksites(includeInactive: Boolean = false): List<Worksite>
    fun addWorker(draft: WorkerDraft)
    fun updateWorker(workerId: Long, draft: WorkerDraft)
    fun deleteWorker(workerId: Long)
    fun addWorksite(name: String)
    fun updateWorksite(worksiteId: Long, name: String)
    fun deleteWorksite(worksiteId: Long)
    /** Compatibility with existing local callers; the UI always chooses an existing worksite. */
    fun addWorker(name: String, surname: String, worksite: String, nickname: String)
}
