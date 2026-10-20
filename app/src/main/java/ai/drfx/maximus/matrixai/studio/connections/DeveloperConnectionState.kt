package ai.drfx.maximus.matrixai.studio.connections

/** UI session state contains no credentials. Revisions reject obsolete async results. */
internal data class DeveloperConnectionState(
    val revision: Long = 0,
    val credentialSaved: Boolean = false,
    val verified: Boolean = false,
    val accountId: String = "",
    val workerNames: List<String> = emptyList(),
    val verifiedAtEpochMs: Long = 0L,
    val verifiedIdentity: String = "",
    val workersObservedAtEpochMs: Long = 0L
) {
    fun reset(saved: Boolean): DeveloperConnectionState =
        DeveloperConnectionState(revision = revision + 1, credentialSaved = saved)

    fun finishVerification(expectedRevision: Long, observedAtEpochMs: Long = System.currentTimeMillis(),
        identity: String = ""): DeveloperConnectionState =
        if (revision == expectedRevision && credentialSaved && observedAtEpochMs > 0L)
            copy(verified = true, verifiedAtEpochMs = observedAtEpochMs,
                verifiedIdentity = identity.take(80)) else this

    /** Every account selection removes results from the previous account. */
    fun selectAccount(id: String): DeveloperConnectionState =
        copy(revision = revision + 1, accountId = id, workerNames = emptyList(), workersObservedAtEpochMs = 0L)

    fun receiveWorkers(expectedRevision: Long, expectedAccount: String, names: List<String>,
        observedAtEpochMs: Long = System.currentTimeMillis()): DeveloperConnectionState =
        if (revision == expectedRevision && accountId == expectedAccount && credentialSaved && observedAtEpochMs > 0L)
            copy(workerNames = names.toList(), workersObservedAtEpochMs = observedAtEpochMs) else this
}
