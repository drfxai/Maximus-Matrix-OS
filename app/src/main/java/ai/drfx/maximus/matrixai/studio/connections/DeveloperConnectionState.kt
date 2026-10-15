package ai.drfx.maximus.matrixai.studio.connections

/** UI session state contains no credentials. Revisions reject obsolete async results. */
internal data class DeveloperConnectionState(
    val revision: Long = 0,
    val credentialSaved: Boolean = false,
    val verified: Boolean = false,
    val accountId: String = "",
    val workerNames: List<String> = emptyList()
) {
    fun reset(saved: Boolean): DeveloperConnectionState =
        DeveloperConnectionState(revision = revision + 1, credentialSaved = saved)

    fun finishVerification(expectedRevision: Long): DeveloperConnectionState =
        if (revision == expectedRevision && credentialSaved) copy(verified = true) else this

    /** Every account selection removes results from the previous account. */
    fun selectAccount(id: String): DeveloperConnectionState =
        copy(revision = revision + 1, accountId = id, workerNames = emptyList())

    fun receiveWorkers(expectedRevision: Long, expectedAccount: String, names: List<String>): DeveloperConnectionState =
        if (revision == expectedRevision && accountId == expectedAccount && credentialSaved)
            copy(workerNames = names.toList()) else this
}
