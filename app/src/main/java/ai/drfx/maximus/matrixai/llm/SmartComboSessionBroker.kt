package ai.drfx.maximus.matrixai.llm

/** One process-wide quota ledger and chat/team dispatch owner across Activity ViewModels. */
internal object SmartComboSessionBroker {
    val executor = SmartComboFreeExecutor()
    private var owner: String? = null
    private var team = false
    private var cancelOwner: (() -> Unit)? = null
    @Synchronized fun acquire(id: String, team: Boolean = false): Boolean {
        if (owner != null) return false
        owner = id
        this.team = team
        return true
    }
    @Synchronized fun release(id: String) {
        if (owner == id) { owner = null; cancelOwner = null; team = false }
    }
    @Synchronized fun registerCancellation(id: String, cancel: () -> Unit) { if (owner == id) cancelOwner = cancel }
    fun cancelTeam() { val action = synchronized(this) { if (team) cancelOwner else null }; action?.invoke() }
    @Synchronized fun busy(): Boolean = owner != null
}
