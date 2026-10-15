package ai.drfx.maximus.matrixai.studio.phonehost

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class PhoneBuildJobsTest {
    private val project = "12345678-abcd-abcd-abcd-abcdefabcdef"

    @Test fun requestCannotLaunchAndApprovalClaimsExactSnapshotOnlyOnce() = fixture { root ->
        var source = "approved source"
        var launches = 0
        PhoneBuildJobs(root, { _, output -> zip(output, source) }, {}).use { jobs ->
            val request = jobs.request(project)
            assertEquals("awaiting_approval", request.phase)
            assertEquals(0, launches)
            assertThrows(IllegalStateException::class.java) { jobs.start(request.id) { launches++ } }
            source = "editor changed after capture"
            jobs.approve(request.id, request.sha256)
            jobs.start(request.id) { claim ->
                assertEquals(request.sha256, PhoneBuildJobs.digest(claim.archive))
                launches++
            }
            assertEquals(1, launches)
            assertThrows(IllegalStateException::class.java) { jobs.start(request.id) { launches++ } }
            assertEquals(1, launches)
        }
    }

    @Test fun tamperingRevocationExpiryAndCloseInvalidateApproval() = fixture { root ->
        var time = 0L
        val jobs = PhoneBuildJobs(root, { _, out -> zip(out, "source") }, {}, { time })
        val first = jobs.request(project)
        File(root, first.id + "/source.zip").appendText("tampered")
        assertThrows(IllegalStateException::class.java) { jobs.approve(first.id, first.sha256) }
        jobs.cancel(first.id)
        val next = jobs.request(project)
        jobs.approve(next.id, next.sha256)
        jobs.revokeProject(project)
        assertThrows(IllegalStateException::class.java) { jobs.claim(next.id) }
        val expiring = jobs.request(project)
        time = 600_001
        assertEquals("expired", jobs.get(expiring.id)!!.phase)
        assertThrows(IllegalStateException::class.java) { jobs.approve(expiring.id, expiring.sha256) }
        jobs.close()
        assertThrows(IllegalStateException::class.java) { jobs.request(project) }
    }

    @Test fun cancellationTargetsOnlyItsOwnedJobAndRejectsParallelRequests() = fixture { root ->
        val cancelled = mutableListOf<String>()
        PhoneBuildJobs(root, { _, out -> zip(out, "source") }, { cancelled += it }).use { jobs ->
            val first = jobs.request(project)
            assertThrows(IllegalStateException::class.java) { jobs.request(project) }
            jobs.cancel(first.id)
            assertTrue(cancelled.isEmpty())
            val second = jobs.request(project)
            jobs.approve(second.id, second.sha256)
            jobs.start(second.id) {}
            jobs.cancel("unrelated")
            assertTrue(cancelled.isEmpty())
            jobs.cancel(second.id)
            assertEquals(listOf(second.id), cancelled)
        }
    }

    @Test fun unrelatedBuildStateCannotPublishArtifactOrLogs() = fixture { root ->
        PhoneBuildJobs(root, { _, out -> zip(out, "source") }, {}).use { jobs ->
            val request = jobs.request(project)
            jobs.approve(request.id, request.sha256)
            jobs.start(request.id) {}
            jobs.observe(request.id, PhoneBuildState(phase = "succeeded", projectId = project, logTail = "unrelated"))
            assertEquals("building", jobs.get(request.id)!!.phase)
            assertEquals("", jobs.get(request.id)!!.log)
            assertNull(jobs.artifact(request.id))
        }
    }

    @Test fun rejectsUnsafeSnapshotBeforeApproval() = fixture { root ->
        PhoneBuildJobs(root, { _, out -> ZipOutputStream(out).use {
            it.putNextEntry(ZipEntry("../outside")); it.write(1); it.closeEntry()
        } }, {}).use { jobs ->
            assertThrows(IllegalArgumentException::class.java) { jobs.request(project) }
            assertFalse(jobs.views().any { it.phase == "awaiting_approval" })
        }
    }

    @Test fun expandedSnapshotLimitRejectsCompressedBomb() = fixture { root ->
        PhoneBuildJobs(root, { _, out -> ZipOutputStream(out).use {
            it.putNextEntry(ZipEntry("oversized.txt"))
            it.write(ByteArray(4 * 1024 * 1024 + 1))
            it.closeEntry()
        } }, {}).use { jobs ->
            assertThrows(IllegalArgumentException::class.java) { jobs.request(project) }
            assertFalse(jobs.views().any { it.phase == "awaiting_approval" })
        }
    }

    @Test fun successfulJobOwnsArtifactCopyAndIgnoresGlobalReplacement() = fixture { root ->
        PhoneBuildJobs(File(root, "jobs"), { _, out -> zip(out, "source") }, {}).use { jobs ->
            val request = jobs.request(project)
            jobs.approve(request.id, request.sha256)
            jobs.start(request.id) {}
            val global = File(root, "global.apk").apply { writeBytes(ByteArray(2048) { 1 }) }
            val hash = PhoneBuildJobs.digest(global)
            jobs.observe(request.id, PhoneBuildState(phase = "succeeded", ownerJobId = request.id,
                projectId = project, apkPath = global.absolutePath, apkSha256 = hash))
            global.writeBytes(ByteArray(2048) { 2 })
            val owned = requireNotNull(jobs.artifact(request.id))
            assertNotEquals(global.canonicalPath, owned.canonicalPath)
            assertEquals(hash, PhoneBuildJobs.digest(owned))
            owned.appendText("tampered")
            assertNull(jobs.artifact(request.id))
        }
    }

    @Test fun concurrentClaimsCanLaunchOnlyOnce() = fixture { root ->
        PhoneBuildJobs(root, { _, out -> zip(out, "source") }, {}).use { jobs ->
            val request = jobs.request(project)
            jobs.approve(request.id, request.sha256)
            val count = java.util.concurrent.atomic.AtomicInteger()
            val gate = java.util.concurrent.CountDownLatch(1)
            val threads = List(2) { Thread {
                gate.await()
                runCatching { jobs.start(request.id) { count.incrementAndGet() } }
            }.apply { start() } }
            gate.countDown()
            threads.forEach { it.join(3000); assertFalse(it.isAlive) }
            assertEquals(1, count.get())
        }
    }

    @Test fun externalAuthorizationAndLockFreeInvalidationBlockClaimsBeforeCleanup() = fixture { root ->
        val authorized = java.util.concurrent.atomic.AtomicBoolean(true)
        PhoneBuildJobs(root, { _, out -> zip(out, "source") }, {}).use { jobs ->
            val request = jobs.request(project) { authorized.get() }
            jobs.approve(request.id, request.sha256)
            val claim = jobs.claim(request.id)
            assertTrue(claim.isValid())
            authorized.set(false)
            assertFalse(claim.isValid())
            jobs.cancel(request.id)
            authorized.set(true)
            val next = jobs.request(project) { authorized.get() }
            jobs.invalidateApprovals()
            assertThrows(IllegalStateException::class.java) { jobs.approve(next.id, next.sha256) }
        }
    }

    @Test fun invalidationDoesNotWaitForBulkCaptureLock() = fixture { root ->
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        PhoneBuildJobs(root, { _, out ->
            entered.countDown()
            check(release.await(3, java.util.concurrent.TimeUnit.SECONDS))
            zip(out, "source")
        }, {}).use { jobs ->
            val rejected = java.util.concurrent.atomic.AtomicBoolean(false)
            val capture = Thread {
                runCatching { jobs.request(project) }.onFailure { rejected.set(true) }
            }.apply { start() }
            try {
                assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
                val invalidated = java.util.concurrent.CountDownLatch(1)
                Thread { jobs.invalidateApprovals(); invalidated.countDown() }.start()
                assertTrue(invalidated.await(1, java.util.concurrent.TimeUnit.SECONDS))
            } finally { release.countDown(); capture.join(3000) }
            assertFalse(capture.isAlive)
            assertTrue(rejected.get())
            assertFalse(jobs.views().any { it.phase == "awaiting_approval" })
        }
    }

    @Test fun revocationDeletesCompletedArtifactAndLogs() = fixture { root ->
        PhoneBuildJobs(File(root, "jobs"), { _, out -> zip(out, "source") }, {}).use { jobs ->
            val request = jobs.request(project)
            jobs.approve(request.id, request.sha256)
            jobs.start(request.id) {}
            val source = File(root, "artifact.apk").apply { writeBytes(ByteArray(2048)) }
            jobs.observe(request.id, PhoneBuildState(phase = "succeeded", ownerJobId = request.id,
                projectId = project, apkPath = source.absolutePath, apkSha256 = PhoneBuildJobs.digest(source), logTail = "private log"))
            val artifact = requireNotNull(jobs.artifact(request.id))
            assertEquals("private log", jobs.get(request.id)!!.log)
            jobs.revokeProject(project)
            assertNull(jobs.get(request.id)); assertNull(jobs.artifact(request.id))
            assertFalse(artifact.exists())
        }
    }

    private fun zip(output: java.io.OutputStream, source: String) {
        ZipOutputStream(output).use { it.putNextEntry(ZipEntry("app/build.gradle.kts")); it.write(source.toByteArray()); it.closeEntry() }
    }
    private fun fixture(block: (File) -> Unit) {
        val root = Files.createTempDirectory("phone-jobs-test").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
}
