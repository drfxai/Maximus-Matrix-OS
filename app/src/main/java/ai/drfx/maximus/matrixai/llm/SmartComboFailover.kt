package ai.drfx.maximus.matrixai.llm

import java.io.IOException
import java.util.concurrent.CancellationException

/**
 * Automatic failover between verified zero-price Smart Combo routes.
 *
 * A route that fails, is too slow to produce its first token, or reports a rate limit/quota
 * BEFORE any reply text reached the user is settled (released or marked uncertain in the
 * ledger) and the same prompt is sent as a NEW request to a different free route. Only
 * zero-price routes are eligible, and chat completions have no external side effects, so the
 * worst case of an ambiguous first attempt is extra free-tier quota use, never a charge.
 *
 * A failure AFTER text was streamed is never replayed automatically: the partial reply is kept
 * and the user can explicitly continue on another route.
 */
internal object SmartComboFailover {
    /** Primary plus at most two automatic alternates per user message. */
    const val MAX_ROUTES = 3

    /** Streaming routes must start answering within this window, or the next route takes over. */
    const val FIRST_TOKEN_TIMEOUT_MS = 25_000L

    enum class Reason(val label: String) {
        SLOW("was too slow to start answering"),
        QUOTA("hit its rate limit or quota"),
        OUTAGE("is temporarily unavailable"),
        REJECTED("rejected the request"),
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
            else -> Reason.REJECTED
        }
        error is IOException -> Reason.NETWORK
        else -> Reason.EMPTY
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

    private fun shortId(model: String) = model.substringAfterLast('/').take(40)
}
