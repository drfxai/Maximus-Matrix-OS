package ai.drfx.maximus.matrixai.llm

import java.io.IOException
import java.util.concurrent.CancellationException

/**
 * Automatic failover between routes for one user message.
 *
 * A route that fails, is too slow to produce its first token, or reports a rate limit/quota
 * BEFORE any reply text reached the user is settled (released or marked uncertain in the
 * ledger) and the same prompt is sent as a NEW request to a different route: first other
 * verified zero-price routes, then, when the user allows it, models on the user's own saved
 * keys ([KeyedRoutes]). Chat completions have no external side effects, so the worst case of an
 * ambiguous first attempt is one extra request on the user's own account.
 *
 * A failure AFTER text was streamed is never replayed automatically: the partial reply is kept
 * and the user can explicitly continue on another route.
 */
internal object SmartComboFailover {
    /** Primary plus at most three automatic alternates per user message. */
    const val MAX_ROUTES = 4

    /** Streaming free routes must start answering within this window, or the next route takes over. */
    const val FIRST_TOKEN_TIMEOUT_MS = 25_000L

    /** Models on the user's own keys (often large or thinking models) get longer to start. */
    const val KEYED_FIRST_TOKEN_TIMEOUT_MS = 60_000L

    enum class Reason(val label: String) {
        SLOW("was too slow to start answering"),
        QUOTA("hit its rate limit or quota"),
        OUTAGE("is temporarily unavailable"),
        REJECTED("rejected the request"),
        DENIED("refused access with this key"),
        NETWORK("lost the connection"),
        EMPTY("returned no answer")
    }

    sealed class Decision {
        data class Switch(val reason: Reason) : Decision()
        data class Stop(val reason: Reason?, val partialReply: Boolean) : Decision()
    }

    /** Only streaming routes get a first-token watchdog; non-streaming replies arrive at the end. */
    fun watchesFirstToken(provider: LlmProvider): Boolean =
        provider != LlmProvider.GEMINI && provider != LlmProvider.ANTHROPIC

    fun classify(error: Throwable?, timedOut: Boolean): Reason = when {
        timedOut -> Reason.SLOW
        error is ProviderRequestException -> when (error.failure) {
            ProviderFailure.RATE_LIMIT_OR_QUOTA -> Reason.QUOTA
            ProviderFailure.SERVICE_OUTAGE -> Reason.OUTAGE
            ProviderFailure.AUTHENTICATION, ProviderFailure.FORBIDDEN -> Reason.DENIED
            else -> Reason.REJECTED
        }
        error is IOException -> Reason.NETWORK
        else -> Reason.EMPTY
    }

    /**
     * Failures that usually affect every model behind the same key or host, so the next route
     * should come from a different provider when one is available.
     */
    fun affectsWholeProvider(reason: Reason, error: Throwable?): Boolean = when (reason) {
        Reason.OUTAGE, Reason.NETWORK, Reason.QUOTA -> true
        Reason.DENIED -> (error as? ProviderRequestException)?.failure == ProviderFailure.AUTHENTICATION
        else -> false
    }

    /**
     * @param routesTried routes already used for this message, including the one that failed.
     * @param userCancelled the user stopped generation; never fail over.
     */
    fun decide(error: Throwable?, timedOut: Boolean, receivedText: Boolean, routesTried: Int,
        userCancelled: Boolean): Decision {
        if (userCancelled || (error is CancellationException && !timedOut)) return Decision.Stop(null, receivedText)
        val reason = classify(error, timedOut)
        if (receivedText) return Decision.Stop(reason, partialReply = true)
        if (routesTried >= MAX_ROUTES) return Decision.Stop(reason, partialReply = false)
        return Decision.Switch(reason)
    }

    fun notice(fromModel: String, toModel: String, reason: Reason): String =
        "Switched to ${shortId(toModel)}: ${shortId(fromModel)} ${reason.label}."

    /** Same notice, naming the provider when the conversation moved to another one. */
    fun notice(from: LlmProvider, fromModel: String, to: LlmProvider, toModel: String, reason: Reason): String =
        if (from == to) notice(fromModel, toModel, reason)
        else "Switched to ${shortId(toModel)} (${to.displayName}): ${shortId(fromModel)} ${reason.label}."

    private fun shortId(model: String) = model.substringAfterLast('/').take(40)
}
