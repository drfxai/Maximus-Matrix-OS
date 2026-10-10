package ai.drfx.maximus.matrixai.llm

import java.security.MessageDigest

/** Process-local authenticated evidence expires; credential material is never retained as a cache key. */
internal class VerifiedCatalogCache(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private data class Entry(val expiresAt: Long, val models: List<ModelDescriptor>)
    private val entries = linkedMapOf<String, Entry>()
    private fun identity(provider: LlmProvider, endpoint: String, credential: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(credential.toByteArray(Charsets.UTF_8))
        return provider.name + "|" + endpoint + "|" + digest.joinToString("") { "%02x".format(it) }
    }
    @Synchronized fun get(provider: LlmProvider, endpoint: String, credential: String): List<ModelDescriptor>? {
        val key = identity(provider, endpoint, credential)
        val entry = entries[key] ?: return null
        if (entry.expiresAt <= now()) { entries.remove(key); return null }
        return entry.models
    }
    @Synchronized fun put(provider: LlmProvider, endpoint: String, credential: String, models: List<ModelDescriptor>) {
        entries.entries.removeAll { it.value.expiresAt <= now() }
        if (entries.size >= 32) entries.remove(entries.keys.first())
        entries[identity(provider, endpoint, credential)] = Entry(now() + TTL_MS, models.toList())
    }
    companion object { const val TTL_MS = 5 * 60 * 1000L }
}
