package ai.drfx.maximus.matrixai.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineDiffTest {
    private val base = (1..10_000).joinToString("\n") { "line $it" }

    @Test fun insertionDeepInLargeFileIsOneAddedLine() {
        val edited = base.replace("line 8000\n", "line 8000\ninserted\n")
        val diff = LineDiff.compute(base, edited)
        assertEquals(1, diff.added)
        assertEquals(0, diff.removed)
        val added = diff.rows.single { it.kind == LineDiff.Kind.ADDED }
        assertEquals(8001, added.newLine)
        assertTrue(diff.rows.size < 12)
        assertTrue(diff.render().contains("+  8001  inserted"))
    }

    @Test fun scatteredEditsAreAlignedNotPositional() {
        val old = "a\nb\nc\nd\ne\nf"
        val new = "x\na\nb\nc\nd\nf\ny"
        val diff = LineDiff.compute(old, new, context = 0)
        assertEquals(2, diff.added)
        assertEquals(1, diff.removed)
        assertEquals(listOf("x", "e", "y"), diff.rows.filter { it.kind != LineDiff.Kind.GAP && it.kind != LineDiff.Kind.SAME }.map { it.text })
    }

    @Test fun identicalTextHasNoRows() {
        val diff = LineDiff.compute(base, base)
        assertEquals(0, diff.added + diff.removed)
        assertTrue(diff.rows.isEmpty())
        assertFalse(diff.truncated)
    }

    @Test fun hugeRewriteFallsBackAndTruncatesView() {
        val rewritten = (1..10_000).joinToString("\n") { "other $it" }
        val diff = LineDiff.compute(base, rewritten, maxRows = 500)
        assertEquals(10_000, diff.added)
        assertEquals(10_000, diff.removed)
        assertTrue(diff.truncated)
        assertEquals(500, diff.rows.size)
    }
}
