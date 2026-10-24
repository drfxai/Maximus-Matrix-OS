package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.*
import org.junit.Test

class SmartComboFreeExecutorTest {
    private fun offer(id: String, cost: Double = 0.0, endpoint: String = "https://openrouter.ai/api/v1") = SmartComboFreeCatalog.Offer(
        route = SmartComboPlanner.Route(
            id = "OPENAI_COMPATIBLE::$id",
            provider = LlmProvider.OPENAI_COMPATIBLE, modelId = id,
            verified = true, healthy = true,
            capabilities = setOf(ModelCapability.CHAT),
            contextWindowTokens = 32768,
            price = SmartComboPlanner.Price(cost, cost, verified = true),
            p95TimeToFirstTokenMs = null, quality = 75
        ),
        endpoint = endpoint,
        quotaScope = "account-one"
    )

    private fun prepare(executor: SmartComboFreeExecutor, id: String, offers: List<SmartComboFreeCatalog.Offer>) =
        executor.prepare(id, 300, 1000, setOf(ModelCapability.CHAT), offers,
            resolveCredential = { _, _ -> "token-for-unit-test-only" }, agentId = "general")

    @Test fun onlyExplicitVerifiedZeroPriceIsSelectedAndNeverReplayed() {
        val executor = SmartComboFreeExecutor()
        val first = prepare(executor, "a", listOf(offer("paid", 1.0), offer("free")))
        assertEquals("free", first?.config?.selectedModel)
        assertTrue(executor.dispatched(requireNotNull(first)))
        assertFalse(executor.dispatched(first))
        assertNull(prepare(executor, "a", listOf(offer("free"))))
        executor.completed(first, ChatUsage(inputTokens = 300, outputTokens = 90, totalTokens = 390))
        assertNull(prepare(executor, "a", listOf(offer("free"))))
    }

    @Test fun unknownOrUnapprovedCredentialsPreventDispatch() {
        val executor = SmartComboFreeExecutor()
        assertNull(executor.prepare("missing", 300, 1000, setOf(ModelCapability.CHAT),
            listOf(offer("free")), { _, _ -> "" }, "general"))
        assertNull(prepare(executor, "no-free", listOf(offer("paid", 0.10))))
    }

    @Test fun skipsMissingCredentialBeforeReservationAndSelectsAnotherVerifiedFreeRoute() {
        val executor = SmartComboFreeExecutor()
        val selected = executor.prepare(
            "usable", 300, 1000, setOf(ModelCapability.CHAT),
            listOf(
                offer("unconfigured", endpoint = "https://missing.example/v1"),
                offer("available", endpoint = "https://working.example/v1")
            ),
            resolveCredential = { _, endpoint ->
                if (endpoint == "https://working.example/v1") "owned-user-key" else ""
            },
            agentId = "general"
        )
        assertEquals("available", selected?.config?.selectedModel)
        assertTrue(executor.dispatched(requireNotNull(selected)))
    }

    @Test fun conflictingDuplicateRouteIdentifiersNeverBindWrongProviderEndpoint() {
        val executor = SmartComboFreeExecutor()
        val first = offer("same-id", endpoint = "https://legitimate.example/v1")
        val second = offer("same-id", endpoint = "https://unexpected.example/v1")
        assertNull(executor.prepare("duplicate", 300, 1000, setOf(ModelCapability.CHAT),
            listOf(first, second),
            resolveCredential = { _, _ -> "user-owned-test-secret" },
            agentId = "general"
        ))
        assertNotNull(prepare(executor, "duplicate", listOf(offer("different-id"))))
    }

    @Test fun unavailableAllCredentialsLeavesRequestIdReusable() {
        val executor = SmartComboFreeExecutor()
        assertNull(executor.prepare("one", 300, 1000, setOf(ModelCapability.CHAT),
            listOf(offer("no-key")), { _, _ -> "" }, "general"))
        assertNotNull(prepare(executor, "one", listOf(offer("with-key"))))
    }

    @Test fun failureBeforeDispatchAllowsNewRequestWithoutLeakingLocalQuota() {
        val executor = SmartComboFreeExecutor()
        val first = requireNotNull(prepare(executor, "first", listOf(offer("free"))))
        executor.failed(first)
        assertFalse(executor.dispatched(first))
        assertNotNull(prepare(executor, "second", listOf(offer("free"))))
    }
    @Test fun preDispatchAlternativeExcludesOriginalRouteAndUsesNewRequestId() {
        val executor = SmartComboFreeExecutor()
        val offers = listOf(offer("first"), offer("second"))
        val first = requireNotNull(prepare(executor, "original-request", listOf(offer("first"))))
        val alternate = executor.prepareAlternativeBeforeDispatch(
            first, "new-request", 300, 1000, setOf(ModelCapability.CHAT),
            offers, { _, _ -> "user-key" }, "general"
        )
        assertEquals("second", alternate?.config?.selectedModel)
        assertFalse(executor.dispatched(first))
        assertTrue(executor.dispatched(requireNotNull(alternate)))
        assertNull(executor.prepareAlternativeBeforeDispatch(
            first, "third-request", 300, 1000, setOf(ModelCapability.CHAT),
            offers, { _, _ -> "user-key" }, "general"
        ))
    }

    @Test fun dispatchedOrUnknownRequestsCannotRerouteOrReplay() {
        val executor = SmartComboFreeExecutor()
        val first = requireNotNull(prepare(executor, "sent", listOf(offer("first"))))
        assertTrue(executor.dispatched(first))
        assertNull(executor.prepareAlternativeBeforeDispatch(
            first, "retry", 300, 1000, setOf(ModelCapability.CHAT),
            listOf(offer("first"), offer("second")), { _, _ -> "owned-key" }, "general"
        ))
        executor.failed(first)
        assertEquals(SmartComboQuotaLedger.State.UNCERTAIN, executor.requestState("sent"))
        assertNull(executor.prepareAlternativeBeforeDispatch(
            first, "another", 300, 1000, setOf(ModelCapability.CHAT),
            listOf(offer("second")), { _, _ -> "owned-key" }, "general"
        ))
    }

    @Test fun alternativeWithSameRequestIdIsAlwaysRejectedWithoutReleasing() {
        val executor = SmartComboFreeExecutor()
        val first = requireNotNull(prepare(executor, "same", listOf(offer("first"))))
        assertNull(executor.prepareAlternativeBeforeDispatch(
            first, "same", 300, 1000, setOf(ModelCapability.CHAT),
            listOf(offer("second")), { _, _ -> "owned-key" }, "general"
        ))
        assertTrue(executor.dispatched(first))
    }


    @Test fun rejectsCatalogTimestampOverflowBeforeCredentialLookup() {
        val executor = SmartComboFreeExecutor()
        var credentialLookups = 0
        val result = executor.prepare(
            "overflow-evidence", 300, 1000, setOf(ModelCapability.CHAT),
            listOf(offer("overflow").copy(checkedAtEpochMs = Long.MIN_VALUE)),
            resolveCredential = { _, _ ->
                credentialLookups++
                "test-only-key"
            },
            agentId = "general", nowMs = Long.MAX_VALUE
        )
        assertNull(result)
        assertEquals(0, credentialLookups)
        assertNull(executor.requestState("overflow-evidence"))
    }

    @Test fun rejectsExpiredAndFutureCatalogEvidenceWithoutReservingRequestId() {
        val executor = SmartComboFreeExecutor()
        val now = 1_000_000L
        val stale = offer("stale").copy(checkedAtEpochMs = now - 600_001L)
        val future = offer("future").copy(checkedAtEpochMs = now + 1L)
        val boundary = offer("boundary").copy(checkedAtEpochMs = now - 600_000L)

        var credentialLookups = 0
        fun attempt(candidates: List<SmartComboFreeCatalog.Offer>) = executor.prepare(
            "freshness-check", 300, 1000, setOf(ModelCapability.CHAT), candidates,
            resolveCredential = { _, _ ->
                credentialLookups++
                "test-only-key"
            }, agentId = "general", nowMs = now
        )

        assertNull(attempt(listOf(stale)))
        assertNull(attempt(listOf(future)))
        // Rejected catalog evidence must never access a user-owned credential.
        assertEquals(0, credentialLookups)
        assertEquals("boundary", attempt(listOf(stale, future, boundary))?.config?.selectedModel)
        assertEquals(1, credentialLookups)
    }
}
