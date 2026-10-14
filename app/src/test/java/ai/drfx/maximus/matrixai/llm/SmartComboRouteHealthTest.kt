package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.*
import org.junit.Test

class SmartComboRouteHealthTest {
    private fun offer(id: String) = SmartComboFreeCatalog.Offer(
        route = SmartComboPlanner.Route(
            id = id, provider = LlmProvider.GEMINI, modelId = "free-chat",
            verified = true, healthy = true,
            capabilities = setOf(ModelCapability.CHAT), contextWindowTokens = 8192,
            price = SmartComboPlanner.Price(0.0, 0.0, true),
            p95TimeToFirstTokenMs = null, quality = 75
        ),
        endpoint = "https://generativelanguage.googleapis.com",
        quotaScope = "test"
    )

    @Test fun dispatchedFailureCoolsOnlyOneRouteAndRestoresLater() {
        val health = SmartComboRouteHealth(baseCooldownMs = 1000, maxCooldownMs = 8000)
        val initial = listOf(offer("route-a"), offer("route-b"))
        assertEquals(2, health.decorate(initial, 100L).count { it.route.healthy })
        health.failed("route-a", 100L)
        val immediately = health.decorate(initial, 101L)
        assertFalse(immediately[0].route.healthy)
        assertTrue(immediately[1].route.healthy)
        assertEquals(999L, health.remainingCooldownMs("route-a", 101L))
        assertTrue(health.decorate(initial, 1100L)[0].route.healthy)
    }

    @Test fun failuresIncreaseCooldownAndSuccessClearsIt() {
        val tracker = SmartComboRouteHealth(baseCooldownMs = 1000, maxCooldownMs = 4000)
        tracker.failed("route-a", 0)
        assertEquals(1000L, tracker.remainingCooldownMs("route-a", 0))
        tracker.failed("route-a", 100)
        assertEquals(2000L, tracker.remainingCooldownMs("route-a", 100))
        tracker.failed("route-a", 200)
        tracker.failed("route-a", 300)
        assertEquals(4000L, tracker.remainingCooldownMs("route-a", 300))
        tracker.succeeded("route-a", 301)
        assertEquals(0L, tracker.remainingCooldownMs("route-a", 301))
    }

    @Test fun cannotFalselyChangeVerifiedPricingOrContext() {
        val tracker = SmartComboRouteHealth()
        val incoming = offer("free-1")
        tracker.failed("free-1", 200L)
        val next = tracker.decorate(listOf(incoming), 300L).single()
        assertEquals(incoming.route.price, next.route.price)
        assertEquals(incoming.route.contextWindowTokens, next.route.contextWindowTokens)
        assertEquals(incoming.route.capabilities, next.route.capabilities)
    }

    @Test fun realFirstTokenSamplesRequireEvidenceAndUseBoundedP95() {
        val tracker = SmartComboRouteHealth()
        val testOffer = offer("latency")
        tracker.observeFirstToken("latency", 120)
        tracker.observeFirstToken("latency", 240)
        assertNull(tracker.decorate(listOf(testOffer), 1000).single().route.p95TimeToFirstTokenMs)
        tracker.observeFirstToken("latency", 400)
        assertEquals(400L, tracker.decorate(listOf(testOffer), 1000).single()
            .route.p95TimeToFirstTokenMs)
        tracker.observeFirstToken("latency", -2)
        tracker.observeFirstToken("latency", 200_001)
        assertEquals(400L, tracker.decorate(listOf(testOffer), 1000).single()
            .route.p95TimeToFirstTokenMs)
        tracker.succeeded("latency", 1100)
        assertEquals(400L, tracker.decorate(listOf(testOffer), 1101).single()
            .route.p95TimeToFirstTokenMs)
    }

    @Test fun overflowsCannotWrapIntoAvailableState() {
        val health = SmartComboRouteHealth()
        health.failed("test", Long.MAX_VALUE - 1L)
        assertFalse(health.decorate(listOf(offer("test")), Long.MAX_VALUE - 1L).single().route.healthy)
    }
}
