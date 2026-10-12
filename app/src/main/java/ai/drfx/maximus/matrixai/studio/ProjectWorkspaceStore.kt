package ai.drfx.maximus.matrixai.studio

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class StudioProject(val id: String, val name: String, val brief: String, val fileCount: Int)
data class StudioFile(val path: String, val length: Long)

/** App-private workspaces. No shell execution, implicit cloud upload, or deployment. */
class ProjectWorkspaceStore(context: Context) {
    private val root = File(context.filesDir, "studio/projects").apply { mkdirs() }

    fun projects(): List<StudioProject> = root.listFiles().orEmpty().filter { it.isDirectory }
        .mapNotNull { runCatching { summary(it) }.getOrNull() }.sortedBy { it.name.lowercase() }

    fun newProject(name: String): StudioProject {
        val safeName = validatedName(name)
        val dir = newDirectory(safeName)
        try {
            val namespace = "ai.drfx.generated." + safeName.lowercase().replace(Regex("[^a-z0-9]"), "").take(20)
            write(dir, "settings.gradle.kts",
                "pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\n" +
                "dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }\n" +
                "rootProject.name = \"" + safeName + "\"\ninclude(\":app\")\n")
            write(dir, "build.gradle.kts",
                "plugins {\n" +
                "    id(\"com.android.application\") version \"8.10.1\" apply false\n" +
                "    id(\"org.jetbrains.kotlin.android\") version \"2.2.0\" apply false\n" +
                "    id(\"org.jetbrains.kotlin.plugin.compose\") version \"2.2.0\" apply false\n}\n")
            write(dir, "gradle.properties", "org.gradle.jvmargs=-Xmx2048m\nandroid.useAndroidX=true\n")
            write(dir, "app/build.gradle.kts", """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                    id("org.jetbrains.kotlin.plugin.compose")
                }
                android {
                    namespace = "$namespace"
                    compileSdk = 35
                    defaultConfig {
                        applicationId = "$namespace"
                        minSdk = 26
                        targetSdk = 35
                        versionCode = 1
                        versionName = "1.0.0"
                    }
                    buildFeatures { compose = true }
                    compileOptions {
                        sourceCompatibility = JavaVersion.VERSION_17
                        targetCompatibility = JavaVersion.VERSION_17
                    }
                }
                dependencies {
                    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
                    implementation("androidx.activity:activity-compose:1.10.1")
                    implementation("androidx.compose.material3:material3")
                }
            """.trimIndent())
            write(dir, "app/src/main/AndroidManifest.xml", """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application android:label="$safeName" android:theme="@android:style/Theme.Material.Light.NoActionBar">
                        <activity android:name=".MainActivity" android:exported="true">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
            """.trimIndent())
            write(dir, "app/src/main/java/" + namespace.replace('.', '/') + "/MainActivity.kt", """
                package $namespace

                import android.os.Bundle
                import androidx.activity.ComponentActivity
                import androidx.activity.compose.setContent
                import androidx.compose.material3.MaterialTheme
                import androidx.compose.material3.Surface
                import androidx.compose.material3.Text

                class MainActivity : ComponentActivity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContent { MaterialTheme { Surface { Text("Welcome to $safeName") } } }
                    }
                }
            """.trimIndent())
            write(dir, "README.md", "# $safeName\n\nJetpack Compose starter project. No APK has been compiled or tested yet.\n")
            return summary(dir)
        } catch (t: Throwable) { dir.deleteRecursively(); throw t }
    }

    fun importZip(name: String, input: InputStream): StudioProject {
        val dir = newDirectory(validatedName(name))
        try {
            var entries = 0
            var totalBytes = 0L
            val buffer = ByteArray(8192)
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    require(entries <= 500) { "Archive exceeds 500 entries." }
                    val relative = safePath(entry.name)
                    if (relative.isNotEmpty()) {
                        val file = child(dir, relative)
                        if (entry.isDirectory) file.mkdirs() else {
                            file.parentFile?.mkdirs()
                            require(!file.exists()) { "Duplicate archive entry." }
                            file.outputStream().use { output ->
                                var fileBytes = 0L
                                while (true) {
                                    val n = zip.read(buffer)
                                    if (n < 0) break
                                    totalBytes += n
                                    fileBytes += n
                                    require(fileBytes <= 4L * 1024 * 1024 &&
                                        totalBytes <= 32L * 1024 * 1024) { "Archive is too large." }
                                    output.write(buffer, 0, n)
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            require(entries > 0) { "Empty archive." }
            return summary(dir)
        } catch (t: Throwable) { dir.deleteRecursively(); throw t }
    }

    /** Only anonymous public GitHub repositories. No arbitrary URL fetching or redirects. */
    fun importPublicGitHub(input: String): StudioProject {
        val m = Regex("^https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?/?$")
            .matchEntire(input.trim()) ?: error("Use https://github.com/owner/repository")
        val owner = m.groupValues[1]
        val repo = m.groupValues[2]
        require(owner != "." && owner != ".." && repo != "." && repo != "..")
        val metadata = JSONObject(readLimited(URL("https://api.github.com/repos/$owner/$repo"), 1_048_576)
            .toString(Charsets.UTF_8))
        val branch = URLEncoder.encode(metadata.getString("default_branch"), "UTF-8")
        val archive = readLimited(URL("https://codeload.github.com/$owner/$repo/zip/refs/heads/$branch"), 20 * 1024 * 1024)
        return importZip(repo, archive.inputStream())
    }

    fun listFiles(id: String): List<StudioFile> {
        val dir = projectDirectory(id)
        return contents(dir).map { StudioFile(it.relativeTo(dir).invariantSeparatorsPath, it.length()) }
    }

    fun readFile(id: String, path: String): String {
        val file = child(projectDirectory(id), safePath(path))
        require(file.isFile && file.length() <= 512 * 1024 && editable(file.name)) { "Only small text files are editable." }
        return file.readText(Charsets.UTF_8)
    }

    fun saveFile(id: String, path: String, text: String) {
        val file = child(projectDirectory(id), safePath(path))
        require(file.isFile && editable(file.name) && text.toByteArray().size <= 512 * 1024) {
            "Only existing text files up to 512 KB are editable."
        }
        file.writeText(text, Charsets.UTF_8)
    }

    fun saveBrief(id: String, brief: String) {
        require(brief.length <= 12_000)
        val file = File(projectDirectory(id), "studio.json")
        file.writeText(JSONObject(file.readText()).put("brief", brief).toString())
    }

    fun exportZip(id: String, output: OutputStream) {
        val dir = projectDirectory(id)
        ZipOutputStream(output.buffered()).use { zip ->
            contents(dir).forEach { file ->
                zip.putNextEntry(ZipEntry(file.relativeTo(dir).invariantSeparatorsPath))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun aiPlanPrompt(id: String): String {
        val project = summary(projectDirectory(id))
        val description = project.brief.ifBlank { "Propose the next development steps." }
        val names = listFiles(id).take(80).joinToString("\n") { it.path }
        return "Project: " + project.name + "\nObjective: " + description +
            "\nFile paths (no file contents or secrets were sent):\n" + names +
            "\nPrepare a concrete Android implementation plan, file-by-file changes, tests, and build steps." +
            "\nDo not claim code was executed, files were read, or APKs built." +
            "\nRequest approval before destructive or external actions."
    }

    private fun summary(dir: File): StudioProject {
        val json = JSONObject(File(dir, "studio.json").readText())
        return StudioProject(dir.name, json.getString("name"), json.optString("brief"), contents(dir).size)
    }

    private fun validatedName(input: String): String = input.trim().also {
        require(it.matches(Regex("[A-Za-z][A-Za-z0-9 _-]{1,48}"))) {
            "Name must start with an English letter and contain 2-49 safe characters."
        }
    }

    private fun newDirectory(name: String): File {
        val dir = File(root, UUID.randomUUID().toString())
        require(dir.mkdir()) { "Cannot create project." }
        File(dir, "studio.json").writeText(JSONObject().put("name", name).put("brief", "").toString())
        return dir
    }

    private fun projectDirectory(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        return File(root, id).also { require(it.isDirectory && File(it, "studio.json").isFile) }
    }

    private fun safePath(name: String): String {
        require(name.length <= 512 && !name.startsWith("/") && !name.contains('\\') && !name.contains(':') &&
            !name.contains('\u0000')) { "Unsafe archive path." }
        val text = name.trimEnd('/')
        if (text.isEmpty()) return ""
        require(text.split('/').none { it.isBlank() || it == "." || it == ".." }) { "Unsafe relative path." }
        return text
    }

    private fun child(dir: File, relative: String): File {
        require(relative.isNotEmpty())
        val file = File(dir, relative).canonicalFile
        require(file != dir.canonicalFile && file.toPath().startsWith(dir.canonicalFile.toPath()))
        return file
    }
    private fun write(dir: File, path: String, content: String) {
        val file = child(dir, path)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }
    private fun contents(dir: File): List<File> = dir.walkTopDown()
        .filter { it.isFile && it.name != "studio.json" &&
            it.canonicalFile.toPath().startsWith(dir.canonicalFile.toPath()) }
        .take(500).toList().sortedBy { it.relativeTo(dir).path }

    private fun editable(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in
        setOf("kt", "java", "xml", "json", "md", "txt", "kts", "gradle", "properties", "yaml",
            "yml", "html", "css", "js", "ts", "py", "sql", "toml", "sh")

    private fun readLimited(url: URL, max: Int): ByteArray {
        require(url.protocol == "https" && url.host in setOf("api.github.com", "codeload.github.com"))
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 12_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            require(connection.responseCode == 200) { "GitHub HTTP " + connection.responseCode }
            require(connection.contentLengthLong < 0 || connection.contentLengthLong <= max) { "Download exceeds limit." }
            connection.inputStream.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    require(out.size() + n <= max) { "Download exceeds limit." }
                    out.write(buffer, 0, n)
                }
                return out.toByteArray()
            }
        } finally { connection.disconnect() }
    }
}
