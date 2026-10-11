package ai.drfx.maximus.matrixai.llm

import org.junit.Assert.*
import org.junit.Test

class CredentialPolicyTest {
    @Test fun recognizableForeignKeysNeverPassFixedProviderValidation() {
        assertFalse(CredentialPolicy.compatible(LlmProvider.NVIDIA, "AIza-example"))
        assertFalse(CredentialPolicy.compatible(LlmProvider.OPENAI, "nvapi-example"))
        assertFalse(CredentialPolicy.compatible(LlmProvider.GEMINI, "sk-ant-example"))
        assertFalse(CredentialPolicy.compatible(LlmProvider.ANTHROPIC, "sk-proj-example"))
        assertTrue(CredentialPolicy.compatible(LlmProvider.GEMINI, "AIza-example"))
        assertTrue(CredentialPolicy.compatible(LlmProvider.ROUTER_9_SMART, "my-own-gateway-key"))
    }
    @Test(expected = IllegalArgumentException::class) fun foreignKeyRejectedBeforeNetwork() {
        CredentialPolicy.validate(LlmProvider.NVIDIA, "AIza-example")
    }
}
