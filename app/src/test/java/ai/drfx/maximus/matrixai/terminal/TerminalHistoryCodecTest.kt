package ai.drfx.maximus.matrixai.terminal

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class TerminalHistoryCodecTest {
    @Test fun boundedRoundTripKeepsLatestThirty() {
        val all = (1..36).map { n ->
            TerminalHistoryEntry(UUID.randomUUID().toString(), n.toLong(),
                "/status-$n", "Verified event $n")
        }
        val restored = TerminalHistoryCodec.decode(TerminalHistoryCodec.encode(all))
        assertEquals(30, restored.size)
        assertEquals("/status-7", restored.first().command)
        assertEquals("/status-36", restored.last().command)
        assertEquals(36L, restored.last().timestampMs)
    }

    @Test fun sensitiveValuesAreRedactedBeforeEncryption() {
        val line = TerminalHistoryEntry(command = "Bearer abcdefghijklmnop",
            output = "api_key=super-secret-value")
        val json = TerminalHistoryCodec.encode(listOf(line))
        assertFalse(json.contains("abcdefghijklmnop"))
        assertFalse(json.contains("super-secret-value"))
        val restored = TerminalHistoryCodec.decode(json)
        assertTrue(restored.single().command.contains("[REDACTED]"))
        assertTrue(restored.single().output.contains("[REDACTED]"))
    }

    @Test fun invalidAndOversizedSessionsFailClosed() {
        assertThrows(Exception::class.java) { TerminalHistoryCodec.decode("untrusted") }
        assertThrows(Exception::class.java) {
            TerminalHistoryCodec.decode("""{"version":2,"entries":[]}""")
        }
        assertThrows(Exception::class.java) {
            TerminalHistoryCodec.decode("x".repeat(TerminalHistoryCodec.MAX_JSON_CHARS + 1))
        }
        val wrong = """{"version":1,"entries":[{"id":"no","timestamp":1,"command":"x","output":"y"}]}"""
        assertThrows(Exception::class.java) { TerminalHistoryCodec.decode(wrong) }
    }

    @Test fun noHistoryIsValid() {
        assertTrue(TerminalHistoryCodec.decode(TerminalHistoryCodec.encode(emptyList())).isEmpty())
    }
}
