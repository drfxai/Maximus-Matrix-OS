package ai.drfx.maximus.matrixai.studio.phonehost

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

/** Shared production extractor; injectable host operations allow real pinned-bundle tests. */
internal class PhoneRuntimeArchiveExtractor(
    private val cancelled: () -> Boolean,
    private val chmod: (File, Int) -> Unit,
    private val symlink: (String, File) -> Unit
) {
    private fun member(path: String): String = PhoneRuntimePathPolicy.member(path)
    private fun linkTarget(guest: String, link: String): String =
        PhoneRuntimePathPolicy.linkTarget(guest, link)

    private fun destination(root: File, member: String, links: Map<String, String>): File {
        val safeRelative = PhoneRuntimePathPolicy.mappedRelative(member, links)
        val rootPath = root.absoluteFile.toPath().normalize()
        val path = rootPath.resolve(safeRelative).normalize()
        check(path != rootPath && path.startsWith(rootPath)) { "Escaping Linux staging root." }
        return path.toFile()
    }

    /**
     * Two sequential passes: validate bounds and discover symlinks, then extract.
     * Symlink parents are resolved in GUEST space; no Android-host absolute links
     * are traversed while writing data. No scripts are executed during install.
     */
    fun unpack(
        openArchive: () -> TarArchiveInputStream, root: File, links: MutableMap<String, String>, maximum: Long
    ) {
        val newLinks = linkedMapOf<String, String>()
        var seen = 0
        var expanded = 0L
        openArchive().use { stream ->
            while (true) {
                check(!cancelled()) { "Cancelled." }
                val e = stream.nextTarEntry ?: break
                val name = member(e.name)
                if (name.isEmpty()) continue
                check(++seen <= 250_000) { "Too many Linux files." }
                check(e.size in 0L..300_000_000L)
                expanded += e.size
                check(expanded <= maximum) { "Bundle decompressed beyond allowed size." }
                if (e.isSymbolicLink) {
                    linkTarget(name, e.linkName)
                    newLinks[name] = e.linkName
                } else check(e.isFile || e.isDirectory || e.isLink) {
                    "Unsupported Linux archive entry type."
                }
            }
        }
        links.putAll(newLinks)
        val hardlinks = mutableListOf<Pair<String, String>>()
        openArchive().use { stream ->
            var written = 0L
            while (true) {
                check(!cancelled()) { "Cancelled." }
                val e = stream.nextTarEntry ?: break
                val name = member(e.name)
                if (name.isEmpty() || e.isSymbolicLink) continue
                if (e.isLink) {
                    hardlinks += name to member(e.linkName)
                    continue
                }
                val path = destination(root, name, links)
                if (e.isDirectory) {
                    if (!Files.isSymbolicLink(path.toPath())) {
                        check(path.isDirectory || path.mkdirs())
                    }
                    continue
                }
                check(e.isFile)
                check(path.parentFile?.isDirectory == true || path.parentFile?.mkdirs() == true)
                if (Files.isSymbolicLink(path.toPath())) check(path.delete())
                FileOutputStream(path, false).use { output ->
                    var remain = e.size
                    val buffer = ByteArray(131072)
                    while (remain > 0) {
                        check(!cancelled()) { "Cancelled." }
                        val n = stream.read(buffer, 0, minOf(remain, buffer.size.toLong()).toInt())
                        check(n > 0) { "Truncated runtime bundle." }
                        output.write(buffer, 0, n)
                        remain -= n
                        written += n
                        check(written <= maximum) { "Runtime exceeded expanded size." }
                    }
                }
                chmod(path, e.mode and 511)
            }
        }
        for ((name, target) in newLinks) {
            check(!cancelled()) { "Cancelled." }
            val file = destination(root, name, links)
            file.parentFile?.mkdirs()
            if (file.exists() || Files.isSymbolicLink(file.toPath())) {
                check(!file.isDirectory || file.list().isNullOrEmpty()) {
                    "Conflicting runtime symlink."
                }
                check(file.delete())
            }
            symlink(target, file)
        }
        for ((name, original) in hardlinks) {
            val file = destination(root, name, links)
            val source = destination(root, original, links)
            check(source.isFile && !Files.isSymbolicLink(source.toPath()))
            file.parentFile?.mkdirs()
            if (file.exists()) check(file.delete())
            Files.createLink(file.toPath(), source.toPath())
        }
    }
}
