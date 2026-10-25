package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.*
import org.junit.Test

class SmartComboPlannerTest {
    private fun task(
        paid: Boolean = false,
        maxCostUsd: Double? = null,
        input: Int = 1200,
        output: Int = 800
    ) = SmartComboPlanner.Task(
        requiredCapabilities = setOf(ModelCapability.CHAT),
        estimatedInputTokens = input,
        reservedOutputTokens = output,
        paidFallbackApproved = paid,
        maxRequestCostUsd = maxCostUsd,
        nowEpochMs = 1_800_000_000_000L
    )

    private fun route(
        id: String,
        free: Boolean = true,
        quota: List<SmartComboPlanner.QuotaWindow> = emptyList(),
        capabilities: Set<ModelCapability> = setOf(ModelCapability.CHAT),
        window: Int? = 32_768,
        quality: Int = 85
    ) = SmartComboPlanner.Route(
        id = id,
        provider = LlmProvider.ROUTER_9_COMBO,
        modelId = id,
        verified = true,
        healthy = true,
        capabilities = capabilities,
        contextWindowTokens = window,
        price = SmartComboPlanner.Price(
            inputUsdPerMillion = if (free) 0.0 else 2.0,
            outputUsdPerMillion = if (free) 0.0 else 8.0,
            verified = true
        ),
        p95TimeToFirstTokenMs = 450,
        quality = quality,
        quota = quota
    )

    @Test fun prefersFreeQualifiedRouteAndRetainsStandby() {
        val choice = SmartComboPlanner.choose(
            task(paid = true, maxCostUsd = 0.02),
            listOf(route("paid", free = false, quality = 99), route("free"), route("backup", quality = 80))
        )
        assertEquals("free", choice.primary?.id)
        assertEquals("backup", choice.standby?.id)
    }

    @Test fun fractionalPublishedQuotaHeadroomRoundsUpLikeAdmissionLedger() {
        val at = task().nowEpochMs
        val low = SmartComboPlanner.QuotaWindow(
            kind = SmartComboPlanner.LimitKind.REQUESTS_PER_MINUTE,
            remaining = 2L,
            limit = 10L,
            observedAtEpochMs = at,
            confidence = SmartComboPlanner.Confidence.PROVIDER_VERIFIED
        )
        // 15% of 10 requests is 1.5, requiring 2 whole requests of guard.
        // Reserving one request from a balance of two leaves only one: reject.
        val rejected = SmartComboPlanner.choose(task(), listOf(route("low", quota = listOf(low))))
        assertNull(rejected.primary)
        assertTrue(rejected.rejectedReasons.getValue("low").contains("Quota nearing exhaustion"))

        // Three remaining requests leave two after reservation: exactly enough.
        val enough = low.copy(remaining = 3L)
        val accepted = SmartComboPlanner.choose(task(), listOf(route("enough", quota = listOf(enough))))
        assertEquals("enough", accepted.primary?.id)
    }

    @Test fun switchesBeforeConfirmedTokenWindowExhaustion() {
        val capacity = SmartComboPlanner.QuotaWindow(
            kind = SmartComboPlanner.LimitKind.TOTAL_TOKENS_PER_DAY,
            remaining = 3_000,
            limit = 30_000,
            observedAtEpochMs = task().nowEpochMs,
            confidence = SmartComboPlanner.Confidence.PROVIDER_VERIFIED
        )
        val choice = SmartComboPlanner.choose(task(), listOf(route("low", quota = listOf(capacity)), route("ready")))
        assertEquals("ready", choice.primary?.id)
        assertTrue(choice.rejectedReasons.getValue("low").contains("Quota nearing exhaustion"))
    }

    @Test fun noSilentPaidFallbackWithoutUserAuthorization() {
        val choice = SmartComboPlanner.choose(task(), listOf(route("paid", free = false)))
        assertNull(choice.primary)
        assertTrue(choice.rejectedReasons.getValue("paid").contains("explicit"))
    }

    @Test fun rejectsPaidRouteAboveApprovedCost() {
        val choice = SmartComboPlanner.choose(task(paid = true, maxCostUsd = 0.001), listOf(route("paid", free = false)))
        assertNull(choice.primary)
    }

    @Test fun rejectsUnverifiedPriceAndUnknownContext() {
        val unverified = route("price").copy(price = SmartComboPlanner.Price(0.0, 0.0, verified = false))
        val choice = SmartComboPlanner.choose(task(), listOf(unverified, route("context", window = null)))
        assertNull(choice.primary)
        assertEquals(2, choice.rejectedReasons.size)
    }

    @Test fun capabilitiesAreHardConstraints() {
        val choice = SmartComboPlanner.choose(
            task().copy(requiredCapabilities = setOf(ModelCapability.CHAT, ModelCapability.VISION)),
            listOf(route("textOnly"), route("vision", capabilities = setOf(ModelCapability.CHAT, ModelCapability.VISION)))
        )
        assertEquals("vision", choice.primary?.id)
    }

    @Test fun unknownBalanceIsNotReportedAsConfirmed() {
        val unknown = SmartComboPlanner.QuotaWindow(
            kind = SmartComboPlanner.LimitKind.REQUESTS_PER_DAY,
            remaining = null,
            confidence = SmartComboPlanner.Confidence.UNKNOWN
        )
        val choice = SmartComboPlanner.choose(task(), listOf(route("unknown", quota = listOf(unknown))))
        assertEquals("unknown", choice.primary?.id)
        assertTrue(choice.requiresLiveQuotaConfirmation)
    }

    @Test fun cooldownForcesAlternativeProvider() {
        val cooling = route("cooling").copy(cooldownUntilEpochMs = task().nowEpochMs + 30_000)
        val choice = SmartComboPlanner.choose(task(), listOf(cooling, route("available")))
        assertEquals("available", choice.primary?.id)
    }

    @Test fun disallowsRoutesNotApprovedForUserData() {
        val choice = SmartComboPlanner.choose(task(), listOf(route("restricted").copy(approvedForUserData = false)))
        assertNull(choice.primary)
    }

    @Test fun verifiedFreeRouteDoesNotRequirePaidAccountCredit() {
        val credit = SmartComboPlanner.QuotaWindow(
            kind = SmartComboPlanner.LimitKind.DOLLAR_BALANCE_MICROS,
            remaining = 0L, limit = 1_000_000L,
            observedAtEpochMs = task().nowEpochMs,
            confidence = SmartComboPlanner.Confidence.PROVIDER_VERIFIED
        )
        assertEquals("free", SmartComboPlanner.choose(task(),
            listOf(route("free", quota = listOf(credit)))).primary?.id)
    }

    @Test fun confirmedDollarCreditAloneCannotConfirmFreeRouteQuota() {
        val credit = SmartComboPlanner.QuotaWindow(
            SmartComboPlanner.LimitKind.DOLLAR_BALANCE_MICROS,
            remaining = 0L, limit = 1_000_000L,
            observedAtEpochMs = task().nowEpochMs,
            confidence = SmartComboPlanner.Confidence.PROVIDER_VERIFIED
        )
        val choice = SmartComboPlanner.choose(task(), listOf(route("free", quota = listOf(credit))))
        assertEquals("free", choice.primary?.id)
        assertTrue(choice.requiresLiveQuotaConfirmation)
    }

    @Test fun confirmedRequestWindowResolvesFreeCreditOnlyConfirmation() {
        val at = task().nowEpochMs
        val credit = SmartComboPlanner.QuotaWindow(
            SmartComboPlanner.LimitKind.DOLLAR_BALANCE_MICROS,
            remaining = 0L, limit = 1_000_000L,
            observedAtEpochMs = at,
            confidence = SmartComboPlanner.Confidence.PROVIDER_VERIFIED
        )
        val requests = SmartComboPlanner.QuotaWindow(
            SmartComboPlanner.LimitKind.REQUESTS_PER_MINUTE,
            remaining = 100L, limit = 100L,
            observedAtEpochMs = at,
            confidence = SmartComboPlanner.Confidence.PROVIDER_VERIFIED
        )
        val choice = SmartComboPlanner.choose(task(), listOf(route("free", quota = listOf(credit, requests))))
        assertEquals("free", choice.primary?.id)
        assertFalse(choice.requiresLiveQuotaConfirmation)
    }

    @Test fun freeCreditExceptionNeverBypassesRequestWindowExhaustion() {
        val at = task().nowEpochMs
        val windows = listOf(
            SmartComboPlanner.QuotaWindow(SmartComboPlanner.LimitKind.DOLLAR_BALANCE_MICROS,
                0L, 1_000_000L, observedAtEpochMs = at,
                confidence = SmartComboPlanner.Confidence.PROVIDER_VERIFIED),
            SmartComboPlanner.QuotaWindow(SmartComboPlanner.LimitKind.REQUESTS_PER_MINUTE,
                0L, 30L, observedAtEpochMs = at,
                confidence = SmartComboPlanner.Confidence.PROVIDER_VERIFIED)
        )
        assertNull(SmartComboPlanner.choose(task(),
            listOf(route("free", quota = windows))).primary)
    }

    @Test fun overflowedQuotaAgeNeverCountsAsFreshProviderEvidence() {
        val extreme = SmartComboPlanner.QuotaWindow(
            kind = SmartComboPlanner.LimitKind.REQUESTS_PER_MINUTE,
            remaining = 0L,
            limit = 10L,
            observedAtEpochMs = Long.MIN_VALUE + 1L,
            confidence = SmartComboPlanner.Confidence.PROVIDER_VERIFIED
        )
        val choice = SmartComboPlanner.choose(
            task().copy(nowEpochMs = Long.MAX_VALUE),
            listOf(route("extreme", quota = listOf(extreme)))
        )
        // Stale evidence is unknown, not proof of an exhausted current quota.
        assertEquals("extreme", choice.primary?.id)
        assertTrue(choice.requiresLiveQuotaConfirmation)
    }

    @Test fun inconsistentRemainingAboveLimitIsNotTrustedAsConfirmedQuota() {
        val inconsistent = SmartComboPlanner.QuotaWindow(
            kind = SmartComboPlanner.LimitKind.TOTAL_TOKENS_PER_DAY,
            remaining = 5_000L,
            limit = 100L,
            observedAtEpochMs = task().nowEpochMs,
            confidence = SmartComboPlanner.Confidence.PROVIDER_VERIFIED
        )
        val choice = SmartComboPlanner.choose(
            task(), listOf(route("inconsistent", quota = listOf(inconsistent)))
        )
        // Contradictory evidence must not be presented as a confirmed quota.
        // The route remains a candidate pending a live admission check.
        assertEquals("inconsistent", choice.primary?.id)
        assertTrue(choice.requiresLiveQuotaConfirmation)
    }

    @Test fun paidRouteStillRequiresRealCreditDespiteExplicitBudget() {
        val credit = SmartComboPlanner.QuotaWindow(
            SmartComboPlanner.LimitKind.DOLLAR_BALANCE_MICROS, 0L, 1_000_000L,
            observedAtEpochMs = task().nowEpochMs,
            confidence = SmartComboPlanner.Confidence.PROVIDER_VERIFIED)
        assertNull(SmartComboPlanner.choose(task(paid = true, maxCostUsd = 1.0),
            listOf(route("paid", free = false, quota = listOf(credit)))).primary)
    }

    @Test fun duplicateIdenticalRoutesCannotBecomeTheirOwnStandby() {
        val duplicate = route("same")
        val choice = SmartComboPlanner.choose(
            task(), listOf(duplicate, duplicate, route("backup", quality = 80))
        )
        assertEquals("same", choice.primary?.id)
        assertEquals("backup", choice.standby?.id)
        assertEquals(2, choice.eligible.size)
    }

    @Test fun conflictingRecordsForOneRouteIdFailClosed() {
        val allowed = route("collision")
        val restricted = allowed.copy(approvedForUserData = false)
        val choice = SmartComboPlanner.choose(
            task(), listOf(allowed, restricted, route("safe"))
        )
        assertEquals("safe", choice.primary?.id)
        assertNull(choice.standby)
        assertEquals("Conflicting route identity", choice.rejectedReasons["collision"])
    }

    @Test fun freshQuotaOutranksExpiredQuotaWhenFreeRoutesOtherwiseTie() {
        val at = task().nowEpochMs
        val fresh = SmartComboPlanner.QuotaWindow(
            SmartComboPlanner.LimitKind.REQUESTS_PER_MINUTE,
            remaining = 100L, limit = 100L, observedAtEpochMs = at,
            confidence = SmartComboPlanner.Confidence.PROVIDER_VERIFIED
        )
        val stale = fresh.copy(observedAtEpochMs = at - 120_000L)
        val choice = SmartComboPlanner.choose(task(), listOf(
            route("a-stale", quota = listOf(stale)),
            route("z-fresh", quota = listOf(fresh))
        ))
        assertEquals("z-fresh", choice.primary?.id)
        assertEquals("a-stale", choice.standby?.id)
        assertFalse(choice.requiresLiveQuotaConfirmation)
        assertTrue(SmartComboPlanner.choose(task(), listOf(
            route("a-stale", quota = listOf(stale))
        )).requiresLiveQuotaConfirmation)
    }

    @Test fun contradictoryQuotaIsPenalizedLikeUnknownEvidence() {
        val at = task().nowEpochMs
        val fresh = SmartComboPlanner.QuotaWindow(
            SmartComboPlanner.LimitKind.REQUESTS_PER_DAY,
            remaining = 100L, limit = 100L, observedAtEpochMs = at,
            confidence = SmartComboPlanner.Confidence.PROVIDER_VERIFIED
        )
        val contradictory = fresh.copy(remaining = 101L)
        val choice = SmartComboPlanner.choose(task(), listOf(
            route("a-contradictory", quota = listOf(contradictory)),
            route("z-confirmed", quota = listOf(fresh))
        ))
        assertEquals("z-confirmed", choice.primary?.id)
        assertEquals("a-contradictory", choice.standby?.id)
    }
}
