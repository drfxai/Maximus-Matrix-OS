package ai.drfx.maximus.matrixai.studio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PineStudioStoreTest {
    @Test fun starterIndicatorHasConfigurableSweepAndAlert() {
        val code = PineStudioStore.starterCode("Liquidity Sweep")
        assertTrue(code.startsWith("//@version=6"))
        assertTrue(code.contains("input.int(20"))
        assertTrue(code.contains("ta.highest(high[1], length)"))
        assertTrue(code.contains("alertcondition(sweep"))
    }

    @Test fun titleCannotBreakPineStringLiteral() {
        val code = PineStudioStore.starterCode("A\"B\\C\nD")
        assertTrue(code.contains("indicator(\"A'BC D\""))
        assertFalse(code.contains("indicator(\"A\"B"))
    }
}
