package ai.drfx.maximus.matrixai.studio.memory

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HindsightClientTest {
    @Test fun bankIdentifiersAreStrictlyValidated() {
        assertTrue(HindsightClient.validBankId("matrix_project_6d454eca0abc"))
        assertFalse(HindsightClient.validBankId("../other/bank"))
        assertFalse(HindsightClient.validBankId("my bank"))
        assertFalse(HindsightClient.validBankId("MiXeD"))
        assertFalse(HindsightClient.validBankId("a"))
    }

    @Test fun retainSendsOnlyApprovedContentAndContext() {
        val content = "Verified source revision abc123. A network retry is not safe after dispatch."
        val obj = HindsightClient.retentionPayload(content, "verified-project-checkpoint")
        val items = obj.getJSONArray("items")
        assertEquals(1, items.length())
        assertEquals(content, items.getJSONObject(0).getString("content"))
        assertEquals("verified-project-checkpoint", items.getJSONObject(0).getString("context"))
        assertFalse(obj.toString().contains("apiKey"))
    }

    @Test fun credentialsAreNotPermittedInSharedMemories() {
        assertFalse(HindsightMemoryPolicy.mayShare("password = verySecret123"))
        assertFalse(HindsightMemoryPolicy.mayShare("API_KEY: xyTest"))
        assertFalse(HindsightMemoryPolicy.mayShare("-----BEGIN PRIVATE KEY-----"))
        assertTrue(HindsightMemoryPolicy.mayShare("Gradle JVM smoke test passed on validated commit."))
    }

    @Test fun recallIsBoundedAndParsesHistoricalNotes() {
        val data = JSONObject().put("results", JSONArray().put(
            JSONObject().put("id", "mem_1").put("type", "experience")
                .put("text", "A prior JSON parser emitted an invalid token.")
        ))
        val parsed = HindsightClient.parseRecall("myproject01", data)
        assertEquals("myproject01", parsed.bankId)
        assertEquals(1, parsed.entries.size)
        assertEquals("experience", parsed.entries.single().kind)
        assertEquals("low", HindsightClient.recallPayload("Next task?").getString("budget"))
        assertEquals(1200, HindsightClient.recallPayload("Next task?").getInt("max_tokens"))
    }
}
