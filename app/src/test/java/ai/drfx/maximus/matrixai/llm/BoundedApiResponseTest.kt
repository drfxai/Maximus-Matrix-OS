package ai.drfx.maximus.matrixai.llm

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class BoundedApiResponseTest {
    @Test fun responseReadsAndCloses() {
        var closed = false
        val input = object : ByteArrayInputStream("verified".toByteArray()) { override fun close() { closed = true; super.close() } }
        assertEquals("verified", BoundedApiResponse.read(input))
        assertTrue(closed)
        assertEquals("", BoundedApiResponse.read(null))
    }
    @Test(expected = IllegalArgumentException::class) fun oversizedResponseRejected() {
        BoundedApiResponse.read(ByteArrayInputStream(ByteArray(BoundedApiResponse.MAX_CHARACTERS + 1) { 65 }))
    }
}
