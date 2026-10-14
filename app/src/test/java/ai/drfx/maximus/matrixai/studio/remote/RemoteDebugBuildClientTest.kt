package ai.drfx.maximus.matrixai.studio.remote

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RemoteDebugBuildClientTest {
    private val valid = """
        {"id":"0123456789abcdef0123456789abcdef",
         "project_id":"01234567-89ab-cdef-0123-456789abcdef",
         "state":"succeeded",
         "source_sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
         "apk_sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
         "error":"","signed":false}
    """.trimIndent()

    @Test fun successfulJobRequiresVerifiedChecksumsAndIsExplicitlyNotProductionSigned() {
        val job = RemoteDebugBuildClient.decodeJob(JSONObject(valid))
        assertEquals("succeeded", job.state)
        assertTrue(job.finished)
        assertFalse(job.signed)
        assertEquals(64, job.apkSha256.length)
    }

    @Test fun forgedProductionSigningClaimOrInvalidDigestIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RemoteDebugBuildClient.decodeJob(JSONObject(valid.replace("\"signed\":false", "\"signed\":true")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RemoteDebugBuildClient.decodeJob(JSONObject(valid.replace("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "bad")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RemoteDebugBuildClient.decodeJob(JSONObject(valid.replace("0123456789abcdef0123456789abcdef", "../secret")))
        }
    }

    @Test fun runningBuildNeverAppearsFinished() {
        val job = RemoteDebugBuildClient.decodeJob(JSONObject(
            valid.replace("\"state\":\"succeeded\"", "\"state\":\"running\"")
                .replace("\"apk_sha256\":\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\"",
                    "\"apk_sha256\":\"\"")))
        assertFalse(job.finished)
    }
}
