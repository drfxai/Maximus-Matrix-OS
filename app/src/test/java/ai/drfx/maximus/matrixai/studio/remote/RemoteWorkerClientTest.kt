package ai.drfx.maximus.matrixai.studio.remote

import org.junit.Assert.*
import org.junit.Test

class RemoteWorkerClientTest {
    @Test fun endpointRequiresCleanHttpsOrigin() {
        assertEquals("https://worker.example.com", RemoteWorkerEndpointPolicy.normalize("https://WORKER.example.com/"))
        assertEquals("https://worker.example.com:8443", RemoteWorkerEndpointPolicy.normalize("https://worker.example.com:8443"))
        listOf("http://worker.example.com", "https://user:pass@worker.example.com",
            "https://worker.example.com/v1", "https://worker.example.com/?token=abc",
            "https://worker.example.com/#secret", "https://worker.example.com:0")
            .forEach { url ->
                assertThrows(Exception::class.java) { RemoteWorkerEndpointPolicy.normalize(url) }
            }
    }

    @Test fun discoveryRejectsMalformedOrMisleadingCapabilities() {
        val good = """{"protocol":"maximus-runtime","api_version":1,"worker_id":"0123456789abcdef0123456789abcdef","platform":"linux","connection":"ready","execution_enabled":false,"supported_operations":["capabilities.read"]}"""
        val parsed = RemoteWorkerClient.decode(good)
        assertEquals("linux", parsed.platform)
        assertFalse(parsed.executionEnabled)
        assertEquals(listOf("capabilities.read"), parsed.operations)
        assertThrows(Exception::class.java) {
            RemoteWorkerClient.decode(good.replace("\"api_version\":1", "\"api_version\":2"))
        }
        assertThrows(Exception::class.java) {
            RemoteWorkerClient.decode(good.replace("\"connection\":\"ready\"", "\"connection\":\"simulated\""))
        }
        assertThrows(Exception::class.java) {
            RemoteWorkerClient.decode(good.replace("\"platform\":\"linux\"", "\"platform\":\"fake\""))
        }
    }
}
