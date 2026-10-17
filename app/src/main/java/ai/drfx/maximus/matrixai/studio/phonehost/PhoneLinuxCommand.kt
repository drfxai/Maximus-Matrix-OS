package ai.drfx.maximus.matrixai.studio.phonehost

/** Manual local commands only. No agent, network API or model output calls this boundary. */
internal object PhoneLinuxCommand {
    const val MAX_CHARS = 4096
    const val MAX_OUTPUT = 32_768
    const val TIMEOUT_SECONDS = 900L
    fun validate(command: String): String {
        require(command.isNotBlank() && command.length <= MAX_CHARS)
        require(command.none { it == '\u0000' || (it.code < 32 && it !in "\n\r\t") })
        return command
    }
    /** Read chunks rather than readLine: a single unbounded output line must not exhaust RAM. */
    fun tail(previous: String, chunk: String): String = (previous + chunk).takeLast(MAX_OUTPUT)
}
