package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.*
import org.junit.Test

class SmartComboFreeExecutorTest {
    private fun offer(id: String, cost: Double = 0.0) = SmartComboFreeCatalog.Offer(
        route = SmartComboPlanner.Route(
            id = "OPENAI_COMPATIBLE::$id",
            provider = LlmProvider.OPENAI_COMPATIBLE, modelId = id,
            verified = true, healthy = true,
            capabilities = setOf(ModelCapability.CHAT),
            contextWindowTokens = 32768,
            price = SmartComboPlanner.Price(cost, cost, verified = true),
            p95TimeToFirstTokenMs = null, quality = 75
        ),
        endpoint = "https://openrouter.ai/api/v1",
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

    @Test fun failureBeforeDispatchAllowsNewRequestWithoutLeakingLocalQuota() {
        val executor = SmartComboFreeExecutor()
        val first = requireNotNull(prepare(executor, "first", listOf(offer("free"))))
        executor.failed(first)
        assertFalse(executor.dispatched(first))
        assertNotNull(prepare(executor, "second", listOf(offer("free"))))
    }
}
