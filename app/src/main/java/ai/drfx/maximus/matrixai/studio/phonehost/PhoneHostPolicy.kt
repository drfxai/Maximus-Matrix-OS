package ai.drfx.maximus.matrixai.studio.phonehost

/**
 * A deliberately small HTTP/1.1 read-only surface. The host never accepts
 * shell commands, uploaded source, privileged app actions or signing keys.
 */
internal object PhoneHostPolicy {
    const val PORT = 18767
    const val MAX_HEADER_BYTES = 8192
    const val MAX_EXPORT_BYTES = 8 * 1024 * 1024
    private val projectId = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")

    sealed interface Route {
        data object Capabilities : Route
        data object Projects : Route
        data class Manifest(val id: String) : Route
        data class Export(val id: String) : Route
    }

    fun route(method: String, requestTarget: String): Route? {
        if (method != "GET" || requestTarget.length > 160 ||
            '?' in requestTarget || '#' in requestTarget || '%' in requestTarget ||
            '\\' in requestTarget) return null
        if (requestTarget == "/v1/capabilities") return Route.Capabilities
        if (requestTarget == "/v1/projects") return Route.Projects
        val parts = requestTarget.split('/')
        if (parts.size != 5 || parts[0].isNotEmpty() ||
            parts[1] != "v1" || parts[2] != "projects" ||
            !projectId.matches(parts[3])) return null
        return when (parts[4]) {
            "manifest" -> Route.Manifest(parts[3])
            "export" -> Route.Export(parts[3])
            else -> null
        }
    }
}
