package ai.drfx.maximus.matrixai.studio.phonehost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.drfx.maximus.matrixai.studio.ProjectWorkspaceStore
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the REAL Android local listener, not only the routing policy. */
@RunWith(AndroidJUnit4::class)
class PhoneHarnessIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun request(path: String, token: String? = null): Pair<Int, ByteArray> {
        val connection = URL("http://127.0.0.1:18767" + path)
            .openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 2000
        connection.readTimeout = 3000
        if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token)
        return try {
            val code = connection.responseCode
            val content = (if (code < 400) connection.inputStream else connection.errorStream)
                ?.use { it.readBytes() } ?: byteArrayOf()
            code to content
        } finally {
            connection.disconnect()
        }
    }

    @Test fun serverRejectsMissingAuthAndRequiresPerProjectSharing() {
        val project = ProjectWorkspaceStore(context).newProject("HostedWorkspace")
        PhoneHarnessHttpServer(context).use { server ->
            assertEquals(401, request("/v1/capabilities").first)
            val token = server.bearerToken
            val cap = request("/v1/capabilities", token)
            assertEquals(200, cap.first)
            val text = String(cap.second)
            assertTrue(text.contains("\"execution_enabled\":false"))
            assertTrue(text.contains("loopback_only"))

            assertEquals(200, request("/v1/projects", token).first)
            val path = "/v1/projects/${project.id}/manifest"
            assertEquals(403, request(path, token).first)
            server.approveProject(project.id)
            assertEquals(200, request(path, token).first)
            val archive = request("/v1/projects/${project.id}/export", token)
            assertEquals(200, archive.first)
            val found = mutableSetOf<String>()
            ZipInputStream(archive.second.inputStream()).use { zip ->
                while (true) {
                    val item = zip.nextEntry ?: break
                    found.add(item.name)
                    zip.closeEntry()
                }
            }
            assertTrue(found.contains("settings.gradle.kts"))
            server.approveProject(null)
            assertEquals(403, request(path, token).first)
            assertEquals(404, request("/v1/jobs", token).first)
        }
    }

    @Test fun wrongHostAndCrossSiteOriginAreNotAccepted() {
        PhoneHarnessHttpServer(context).use { server ->
            val token = server.bearerToken
            val conn = URL("http://127.0.0.1:18767/v1/projects")
                .openConnection() as HttpURLConnection
            try {
                conn.setRequestProperty("Authorization", "Bearer " + token)
                conn.setRequestProperty("Origin", "https://untrusted.example")
                assertEquals(403, conn.responseCode)
            } finally { conn.disconnect() }
        }
    }
}
