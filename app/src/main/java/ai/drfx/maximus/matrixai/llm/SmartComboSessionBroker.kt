package ai.drfx.maximus.matrixai.llm

/** One process-wide quota ledger and chat/team dispatch owner across Activity ViewModels. */
internal object SmartComboSessionBroker {
    val executor = SmartComboFreeExecutor()
    private var durableExecutor: SmartComboFreeExecutor? = null
    private var journalPath: String? = null

    /** Lazy journal opening happens on the caller's inference IO dispatcher. */
    @Synchronized fun forStorage(file: java.io.File): SmartComboFreeExecutor {
        check(journalPath == null || journalPath == file.absolutePath) { "Conflicting request journal." }
        journalPath = file.absolutePath
        return durableExecutor ?: SmartComboFreeExecutor(SmartComboQuotaLedger(
            journal = SmartComboRequestJournal(file))).also { durableExecutor = it }
    }
    private var owner: String? = null
    private var team = false
    private var cancellationRequested = false
    private var cancellationDelivered = false
    private var cancelOwner: (() -> Unit)? = null
    @Synchronized fun acquire(id: String, team: Boolean = false): Boolean {
        if (owner != null) return false
        owner = id
        this.team = team
        cancellationRequested = false
        cancellationDelivered = false
        return true
    }
    @Synchronized fun release(id: String) {
        if (owner == id) {
            owner = null; cancelOwner = null; team = false
            cancellationRequested = false; cancellationDelivered = false
        }
    }
    fun registerCancellation(id: String, cancel: () -> Unit) {
        val action = synchronized(this) {
            if (owner != id || cancellationDelivered) return@synchronized null
            cancelOwner = cancel
            takeCancellation()
        }
        action?.invoke()
    }
    fun cancelTeam() {
        val action = synchronized(this) {
            if (!team || owner == null) return@synchronized null
            // Another ViewModel may cancel before this owner's coroutine is registered.
            cancellationRequested = true
            takeCancellation()
        }
        action?.invoke()
    }
    /** Called only under the broker lock. Invoke the returned callback outside it. */
    private fun takeCancellation(): (() -> Unit)? {
        if (!cancellationRequested || cancellationDelivered) return null
        val action = cancelOwner ?: return null
        cancellationDelivered = true
        cancelOwner = null
        return action
    }
    @Synchronized fun busy(): Boolean = owner != null
}
