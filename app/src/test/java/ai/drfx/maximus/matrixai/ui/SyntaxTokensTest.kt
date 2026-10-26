package ai.drfx.maximus.matrixai.ui

import org.junit.Assert.*
import org.junit.Test

class SyntaxTokensTest {
    private fun kinds(text: String, spec: LanguageSpec): Map<String, TokenKind> =
        SyntaxTokenizer.line(text, spec).first.associate { text.substring(it.start, it.end) to it.kind }

    @Test fun pythonKeywordsStringsAndComments() {
        val tokens = kinds("def run(x): return \"ok\"  # done", SyntaxSpecs.python)
        assertEquals(TokenKind.KEYWORD, tokens["def"])
        assertEquals(TokenKind.KEYWORD, tokens["return"])
        assertEquals(TokenKind.STRING, tokens["\"ok\""])
        assertEquals(TokenKind.COMMENT, tokens["# done"])
    }

    @Test fun blockCommentCarriesAcrossLines() {
        val (_, state) = SyntaxTokenizer.line("int a = 1; /* start", SyntaxSpecs.c)
        assertEquals(LexState.BLOCK_COMMENT, state)
        val (spans, after) = SyntaxTokenizer.line("still comment */ return 0;", SyntaxSpecs.c, state)
        assertEquals(TokenKind.COMMENT, spans.first().kind)
        assertEquals(0, spans.first().start)
        assertEquals(LexState.NONE, after)
    }

    @Test fun javaAnnotationsAndNumbers() {
        val tokens = kinds("@Override public int size() { return 42; }", SyntaxSpecs.java)
        assertEquals(TokenKind.ANNOTATION, tokens["@Override"])
        assertEquals(TokenKind.NUMBER, tokens["42"])
    }

    @Test fun spansNeverOverlapOrLeaveTheLine() {
        val line = "x = f'{a}' + 'b' # c /* d */ 0x1F"
        listOf(SyntaxSpecs.python, SyntaxSpecs.c, SyntaxSpecs.javascript).forEach { spec ->
            var end = 0
            SyntaxTokenizer.line(line, spec).first.forEach {
                assertTrue(it.start >= end && it.end <= line.length && it.start < it.end)
                end = it.end
            }
        }
    }
}
