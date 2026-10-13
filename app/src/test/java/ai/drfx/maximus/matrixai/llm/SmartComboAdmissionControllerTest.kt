package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.*
import org.junit.Test

class SmartComboAdmissionControllerTest {
    private val time = 1_800_000_000_000L
    private fun task(paid: Boolean = false) = SmartComboPlanner.Task(
        requiredCapabilities = setOf(ModelCapability.CHAT),
        estimatedInputTokens = 1000,
        reservedOutputTokens = 1000,
        nowEpochMs = time,
        paidFallbackApproved = paid,
        maxRequestCostUsd = if (paid) 0.05 else null
    )
    private fun route(id: String, paid: Boolean = false) = SmartComboPlanner.Route(
        id = id,
        provider = LlmProvider.ROUTER_9_SMART,
        modelId = id,
        verified = true,
        healthy = true,
        capabilities = setOf(ModelCapability.CHAT),
        contextWindowTokens = 32768,
        price = SmartComboPlanner.Price(
            if (paid) 1.0 else 0.0, if (paid) 2.0 else 0.0, verified = true
        ),
        p95TimeToFirstTokenMs = 400,
        quality = 80
    )
    private fun window(rem: Long) = SmartComboPlanner.QuotaWindow(
        kind = SmartComboPlanner.LimitKind.TOTAL_TOKENS_PER_DAY,
        remaining = rem,
        limit = 10_000,
        observedAtEpochMs = time,
        confidence = SmartComboPlanner.Confidence.PROVIDER_VERIFIED
    )

    @Test fun quotaExhaustionCausesSafePreflightFallbackWithoutDispatch() {
        val ledger = SmartComboQuotaLedger(now = { time })
        ledger.observe("shared-first", listOf(window(2100)))
        ledger.observe("independent-second", listOf(window(10_000)))
        val controller = SmartComboAdmissionController(ledger)
        val result = controller.prepare(
            "request-1", task(), listOf(route("a"), route("b")),
            mapOf("a" to "shared-first", "b" to "independent-second")
        )
        assertEquals("b", result.route?.id)
        assertNotNull(result.reasons["a"])
        assertEquals(SmartComboQuotaLedger.State.RESERVED, ledger.entry("request-1")?.state)
    }

    @Test fun neverFallsBackToPaidRouteWithoutApproval() {
        val ledger = SmartComboQuotaLedger(now = { time })
        val outcome = SmartComboAdmissionController(ledger).prepare(
            "request-2", task(), listOf(route("paid", paid = true)), mapOf("paid" to "scope")
        )
        assertNull(outcome.route)
        assertNull(ledger.entry("request-2"))
    }

    @Test fun standbyMustUseIndependentQuotaScope() {
        val ledger = SmartComboQuotaLedger(now = { time })
        val result = SmartComboAdmissionController(ledger).prepare(
            "request-3", task(), listOf(route("a"), route("b"), route("c")),
            mapOf("a" to "one", "b" to "one", "c" to "two")
        )
        assertEquals("a", result.route?.id)
        assertEquals("c", result.standby?.id)
    }

    @Test fun sameRequestNeverSwitchesRouteAfterAdmission() {
        val ledger = SmartComboQuotaLedger(now = { time })
        val controller = SmartComboAdmissionController(ledger)
        val routes = listOf(route("a"), route("b"))
        val scopes = mapOf("a" to "one", "b" to "two")
        assertEquals("a", controller.prepare("request-4", task(), routes, scopes).route?.id)
        assertNull(controller.prepare("request-4", task(), routes, scopes).route)
    }

    @Test fun missingSharedQuotaIdentityCannotBeGuessed() {
        val ledger = SmartComboQuotaLedger(now = { time })
        val result = SmartComboAdmissionController(ledger).prepare(
            "request-5", task(), listOf(route("a")), emptyMap()
        )
        assertNull(result.route)
        assertTrue(result.reasons.getValue("a").contains("scope"))
    }
}
