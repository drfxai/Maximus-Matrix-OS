package ai.drfx.maximus.matrixai.studio.phonehost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.drfx.maximus.matrixai.studio.ProjectWorkspaceStore
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import org.json.JSONObject
import java.io.File
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

    private fun post(path: String, token: String?, body: String): Pair<Int, ByteArray> {
        val connection = URL("http://127.0.0.1:18767" + path).openConnection() as HttpURLConnection
        connection.connectTimeout = 2000
        connection.readTimeout = 3000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token)
        return try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            code to (if (code < 400) connection.inputStream else connection.errorStream).use { it.readBytes() }
        } finally { connection.disconnect() }
    }

    @Test fun authenticatedRequestNeedsSeparatePhoneApprovalAndUsesCapturedSource() {
        val store = ProjectWorkspaceStore(context)
        val project = store.newProject("ImmutablePhoneJob")
        try {
            // Readiness DI is test-only: production requires current live tool probes.
            PhoneHarnessHttpServer(context, buildReady = { true }).use { server ->
                server.approveProject(project.id)
                val body = JSONObject().put("project_id", project.id).toString()
                assertEquals(401, post("/v1/build-jobs", null, body).first)
                val response = post("/v1/build-jobs", server.bearerToken, body)
                assertEquals(202, response.first)
                val id = JSONObject(String(response.second)).getString("job_id")
                val job = requireNotNull(server.jobs.get(id))
                var executions = 0
                assertEquals("awaiting_approval", job.phase)
                assertEquals(0, executions)
                assertThrows(IllegalStateException::class.java) { server.jobs.start(id) { executions++ } }
                assertEquals(404, post("/v1/build-jobs/$id/approve", server.bearerToken, "").first)
                val original = store.readFile(project.id, "README.md")
                store.saveFile(project.id, "README.md", "edited after request")
                server.jobs.approve(id, job.sha256)
                server.jobs.start(id) { claim ->
                    ZipInputStream(claim.archive.inputStream()).use { zip ->
                        var captured: String? = null
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            if (entry.name == "README.md") captured = zip.readBytes().toString(Charsets.UTF_8)
                        }
                        assertEquals(original, captured)
                    }
                    executions++ // Recording executor; emulator does not claim a native build.
                }
                assertEquals(1, executions)
                assertThrows(IllegalStateException::class.java) { server.jobs.start(id) { executions++ } }
                server.approveProject(null)
                assertNull(server.jobs.get(id))
                assertEquals(404, request("/v1/build-jobs/$id", server.bearerToken).first)
                assertEquals(1, executions)
            }
        } finally { File(context.filesDir, "studio/projects/" + project.id).deleteRecursively() }
    }

    @Test fun revocationDuringCaptureCannotLeaveAnApprovableJob() {
        val store = ProjectWorkspaceStore(context)
        val project = store.newProject("RevokedPhoneCapture")
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        try {
            PhoneHarnessHttpServer(context, buildReady = { true }, snapshotter = { id, output ->
                entered.countDown()
                check(release.await(3, java.util.concurrent.TimeUnit.SECONDS))
                store.exportZip(id, output)
            }).use { server ->
                server.approveProject(project.id)
                val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
                val request = Thread {
                    try { post("/v1/build-jobs", server.bearerToken,
                        JSONObject().put("project_id", project.id).toString()) }
                    catch (e: Throwable) { failure.set(e) }
                }.apply { start() }
                assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
                val revoke = Thread { server.approveProject(null) }.apply { start() }
                release.countDown()
                request.join(5000); revoke.join(5000)
                assertFalse(request.isAlive); assertFalse(revoke.isAlive)
                assertNull(failure.get())
                assertTrue(server.jobs.views().isEmpty())
            }
        } finally {
            release.countDown()
            File(context.filesDir, "studio/projects/" + project.id).deleteRecursively()
        }
    }

    @Test fun malformedOrUnverifiedBuildRequestsNeverReachApproval() {
        val store = ProjectWorkspaceStore(context)
        val project = store.newProject("RejectedPhoneJob")
        val body = JSONObject().put("project_id", project.id).toString()
        try {
            PhoneHarnessHttpServer(context, buildReady = { false }).use { server ->
                server.approveProject(project.id)
                assertEquals(409, post("/v1/build-jobs", server.bearerToken, body).first)
                assertTrue(server.jobs.views().isEmpty())
            }
            PhoneHarnessHttpServer(context, buildReady = { true }).use { server ->
                server.approveProject(project.id)
                for (invalid in listOf(body.dropLast(1) + ",\"task\":\"shell\"}",
                    "{\"project_id\":\"${project.id}\",\"project_id\":\"${project.id}\"}", "x".repeat(513))) {
                    assertEquals(400, post("/v1/build-jobs", server.bearerToken, invalid).first)
                    assertTrue(server.jobs.views().isEmpty())
                }
                val accepted = post("/v1/build-jobs", server.bearerToken, body)
                val id = JSONObject(String(accepted.second)).getString("job_id")
                assertEquals(200, post("/v1/build-jobs/$id/cancel", server.bearerToken, "").first)
                assertEquals("cancelled", server.jobs.get(id)!!.phase)
                assertThrows(IllegalStateException::class.java) {
                    server.jobs.approve(id, server.jobs.get(id)!!.sha256)
                }
            }
        } finally { File(context.filesDir, "studio/projects/" + project.id).deleteRecursively() }
    }

    @Test fun cancelledDirectServiceReservationCannotStartBuild() {
        val builder = PhoneOnDeviceApkBuilder(context)
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
        while (builder.state.value.busy && android.os.SystemClock.elapsedRealtime() < deadline) {
            android.os.SystemClock.sleep(50)
        }
        assertFalse(builder.state.value.busy)
        val phase = builder.state.value.phase
        val failure = assertThrows(IllegalStateException::class.java) {
            builder.buildForService("12345678-abcd-abcd-abcd-abcdefabcdef", "cancelled-direct") { false }
        }
        assertTrue(failure.message.orEmpty().contains("revoked"))
        assertEquals(phase, builder.state.value.phase)
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
