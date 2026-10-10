package ai.drfx.maximus.matrixai.llm

import java.io.InputStream

internal object BoundedApiResponse {
    const val MAX_CHARACTERS = 4 * 1024 * 1024
    fun read(stream: InputStream?): String {
        if (stream == null) return ""
        return stream.bufferedReader().use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(4096)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                require(result.length + count <= MAX_CHARACTERS) { "Provider response exceeds the safe response limit." }
                result.append(buffer, 0, count)
            }
            result.toString()
        }
    }
}
