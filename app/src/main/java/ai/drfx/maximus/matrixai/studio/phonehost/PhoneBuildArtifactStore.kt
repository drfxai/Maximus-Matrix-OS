package ai.drfx.maximus.matrixai.studio.phonehost

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/** One app-private, durable Debug APK. No source, signing key or arbitrary paths in metadata. */
internal class PhoneBuildArtifactStore(private val root: File) {
    data class Artifact(val file: File, val projectId: String, val sha256: String, val packageName: String)

    @Synchronized fun save(source: File, projectId: String, packageName: String): Artifact {
        require(projectId.matches(PROJECT_ID) && packageName.matches(PACKAGE_NAME))
        require(source.isFile && source.length() in MIN_BYTES..MAX_BYTES)
        check(root.isDirectory || root.mkdirs())
        val generation = UUID.randomUUID().toString()
        val directory = File(root, generation).apply { check(mkdir()) }
        val pending = File(root, "current.pending")
        try {
            val apk = File(directory, "debug.apk")
            source.inputStream().use { input -> apk.outputStream().use { input.copyTo(it) } }
            check(apk.length() == source.length())
            val hash = digest(apk)
            pending.outputStream().use { raw ->
                DataOutputStream(raw).use { output ->
                    output.writeInt(1)
                    output.writeUTF(generation)
                    output.writeUTF(projectId)
                    output.writeUTF(hash)
                    output.writeUTF(packageName)
                }
            }
            Files.move(pending.toPath(), File(root, "current.bin").toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            // Only after publication may a previous success be removed.
            root.listFiles().orEmpty().filter { it.isDirectory && it.name != generation }
                .forEach { it.deleteRecursively() }
            return Artifact(apk, projectId, hash, packageName)
        } catch (e: Exception) {
            pending.delete()
            directory.deleteRecursively()
            throw e
        }
    }

    @Synchronized fun restore(): Artifact? = runCatching {
        val manifest = File(root, "current.bin")
        require(manifest.length() in 1..2048)
        DataInputStream(manifest.inputStream()).use { input ->
            require(input.readInt() == 1)
            val generation = input.readUTF()
            val project = input.readUTF()
            val hash = input.readUTF()
            val packageName = input.readUTF()
            require(generation.matches(PROJECT_ID) && project.matches(PROJECT_ID) &&
                hash.matches(Regex("[a-f0-9]{64}")) && packageName.matches(PACKAGE_NAME))
            require(input.read() == -1)
            val apk = File(root, "$generation/debug.apk")
            require(apk.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
            require(apk.isFile && apk.length() in MIN_BYTES..MAX_BYTES && digest(apk) == hash)
            Artifact(apk, project, hash, packageName)
        }
    }.getOrNull()

    companion object {
        private val PROJECT_ID = Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
        private val PACKAGE_NAME = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")
        private const val MIN_BYTES = 1024L
        private const val MAX_BYTES = 128L * 1024 * 1024
        private fun digest(file: File): String {
            val sha = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    sha.update(buffer, 0, count)
                }
            }
            return sha.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
