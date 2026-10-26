package ai.drfx.maximus.matrixai.studio

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Local, bounded Pine Script interpreter for chart previews.
 *
 * It executes the script bar by bar against real exchange candles, the same execution
 * model TradingView uses, for a broad subset of Pine v4/v5/v6: inputs, `var`/`:=`,
 * history references, `if`/`for`/`while`/`switch`, user functions, tuples, the common
 * `ta.*`/`math.*` library, `plot*`, `hline`, `bgcolor`, `barcolor` and `strategy.*` orders.
 *
 * It also runs arrays, maps, matrices, user-defined types, methods, enums, and the label, line,
 * box, linefill and table drawing APIs. It is NOT TradingView: libraries, polylines and other
 * symbols or timeframes are skipped or approximated, and every skipped statement is reported in
 * [Output.diagnostics].
 * Source text is data only; nothing is executed outside this interpreter.
 */
object PineScriptEngine {
    const val MAX_SOURCE = LargeSource.MAX_CHARS
    const val MAX_BARS = 1_000
    private const val MAX_LOOP = 2_000
    // Scales with large scripts: a 10k+ line indicator runs thousands of statements per bar.
    private const val MAX_STEPS = 40_000_000L

    enum class Pane { PRICE, INDICATOR }
    enum class PlotStyle { LINE, STEP, HISTOGRAM, COLUMNS, CIRCLES, AREA, CROSS, MARKER_UP, MARKER_DOWN, MARKER }
    enum class Severity { INFO, WARNING, ERROR }

    data class Plot(
        val title: String,
        val values: List<Double?>,
        val style: PlotStyle,
        val pane: Pane,
        /** ARGB per bar; null where the script used na or no colour. */
        val colors: List<Long?>,
        val text: String = ""
    ) {
        val isMarker: Boolean get() = style == PlotStyle.MARKER || style == PlotStyle.MARKER_UP || style == PlotStyle.MARKER_DOWN
        val lastValue: Double? get() = values.lastOrNull { it != null }
        val defaultColor: Long? get() = colors.lastOrNull { it != null }
    }

    data class HLine(val price: Double, val title: String, val color: Long?, val pane: Pane)
    data class Diagnostic(val line: Int, val message: String, val severity: Severity)
    enum class SignalKind { LONG_ENTRY, SHORT_ENTRY, EXIT }
    data class Signal(val bar: Int, val kind: SignalKind, val label: String)
    data class Trade(
        val direction: Int, val entryBar: Int, val entryPrice: Double,
        val exitBar: Int?, val exitPrice: Double?, val label: String
    ) {
        val returnPercent: Double? get() = exitPrice?.let { (it - entryPrice) / entryPrice * 100.0 * direction }
    }
    data class Backtest(
        val trades: List<Trade>,
        val closedTrades: Int,
        val winRate: Double?,
        val netReturnPercent: Double,
        val maxDrawdownPercent: Double,
        val profitFactor: Double?
    )
    data class AlertHit(val title: String, val bars: List<Int>)

    /** Script drawings kept at the end of the run, x in bar indexes (may lie past the last bar). */
    sealed interface Drawing
    data class LabelDrawing(val x: Double, val y: Double, val text: String, val color: Long?, val textColor: Long?,
                            val style: String, val size: String) : Drawing
    data class LineDrawing(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val color: Long?,
                           val width: Double, val style: String, val extend: String) : Drawing
    data class BoxDrawing(val left: Double, val top: Double, val right: Double, val bottom: Double, val border: Long?,
                          val background: Long?, val text: String, val textColor: Long?, val extend: String) : Drawing
    data class LinefillDrawing(val first: LineDrawing, val second: LineDrawing, val color: Long?) : Drawing

    data class TableCell(val text: String, val background: Long?, val textColor: Long?, val textSize: String)
    data class Table(val position: String, val columns: Int, val rows: Int, val background: Long?, val frame: Long?,
                     val cells: Map<Pair<Int, Int>, TableCell>)

    /** fill() between two plots (by index in [Output.plots]) or two price levels. */
    data class FillEdge(val plot: Int?, val price: Double?)
    data class Fill(val upper: FillEdge, val lower: FillEdge, val colors: List<Long?>, val pane: Pane)

    data class Output(
        val title: String,
        val overlay: Boolean,
        val isStrategy: Boolean,
        val plots: List<Plot>,
        val hlines: List<HLine>,
        val backgrounds: List<Long?>,
        val barColors: List<Long?>,
        val signals: List<Signal>,
        val backtest: Backtest?,
        val alerts: List<AlertHit>,
        val diagnostics: List<Diagnostic>,
        val statements: Int,
        val skippedStatements: Int,
        val drawings: List<Drawing> = emptyList(),
        val fills: List<Fill> = emptyList(),
        val tables: List<Table> = emptyList(),
        /** Strategy equity per bar in percent of the starting capital (100 = flat); empty for indicators. */
        val equity: List<Double?> = emptyList()
    ) {
        val hasVisualOutput: Boolean get() = plots.isNotEmpty() || hlines.isNotEmpty() ||
            signals.isNotEmpty() || backgrounds.any { it != null } || barColors.any { it != null } ||
            drawings.isNotEmpty() || tables.isNotEmpty()
        val errors: List<Diagnostic> get() = diagnostics.filter { it.severity == Severity.ERROR }
    }

    class PineException(message: String, val line: Int = 0) : RuntimeException(message)

    fun run(source: String, candles: List<PineCandle>, symbol: String = "BTC-USD", timeframeSeconds: Int = 60): Output {
        if (source.length > MAX_SOURCE) throw PineException("Pine source exceeds ${LargeSource.MAX_LABEL}.")
        if (candles.size > MAX_BARS) throw PineException("Preview is limited to $MAX_BARS candles.")
        val program = Parser(Lexer.lines(source)).parseProgram()
        return Interpreter(program, candles, symbol, timeframeSeconds).execute()
    }

    /** Syntax-only check without market data, for instant feedback in editors. */
    fun check(source: String): List<Diagnostic> = try {
        if (source.length > MAX_SOURCE) listOf(Diagnostic(0, "Pine source exceeds ${LargeSource.MAX_LABEL}.", Severity.ERROR))
        else Parser(Lexer.lines(source)).parseProgram().diagnostics
    } catch (error: PineException) {
        listOf(Diagnostic(error.line, error.message ?: "Syntax error", Severity.ERROR))
    }

    // ---------------------------------------------------------------- lexer

    internal enum class T { NUM, STR, ID, OP, COLOR, EOL }
    internal data class Tok(val type: T, val text: String, val line: Int)
    internal data class LogicalLine(val indent: Int, val tokens: List<Tok>, val line: Int)

    internal object Lexer {
        private val operators = listOf(":=", "+=", "-=", "*=", "/=", "%=", "==", "!=", "<=", ">=", "=>",
            "=", "<", ">", "+", "-", "*", "/", "%", "?", ":", "(", ")", "[", "]", ",", ".")
        private val continuationEnd = setOf(",", "+", "-", "*", "/", "%", "?", ":", "=", ":=", "and", "or",
            "==", "!=", "<", ">", "<=", ">=", "(", "[", "not", "+=", "-=", "*=", "/=")

        fun tokenize(text: String, line: Int): List<Tok> {
            val out = mutableListOf<Tok>()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    c == ' ' || c == '\t' || c == '\r' -> i++
                    c == '/' && i + 1 < text.length && text[i + 1] == '/' -> i = text.length
                    c.isDigit() || (c == '.' && i + 1 < text.length && text[i + 1].isDigit()) -> {
                        val start = i
                        while (i < text.length && (text[i].isDigit() || text[i] == '.')) i++
                        if (i < text.length && (text[i] == 'e' || text[i] == 'E')) {
                            i++
                            if (i < text.length && (text[i] == '+' || text[i] == '-')) i++
                            while (i < text.length && text[i].isDigit()) i++
                        }
                        out += Tok(T.NUM, text.substring(start, i), line)
                    }
                    c == '"' || c == '\'' -> {
                        val quote = c
                        val sb = StringBuilder()
                        i++
                        while (i < text.length && text[i] != quote) {
                            if (text[i] == '\\' && i + 1 < text.length) {
                                i++
                                sb.append(when (text[i]) { 'n' -> '\n'; 't' -> '\t'; else -> text[i] })
                            } else sb.append(text[i])
                            i++
                        }
                        if (i >= text.length) throw PineException("Unterminated string literal.", line)
                        i++
                        out += Tok(T.STR, sb.toString(), line)
                    }
                    c == '#' -> {
                        val start = i
                        i++
                        while (i < text.length && text[i].isLetterOrDigit()) i++
                        out += Tok(T.COLOR, text.substring(start, i), line)
                    }
                    c.isLetter() || c == '_' -> {
                        val start = i
                        while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                        out += Tok(T.ID, text.substring(start, i), line)
                    }
                    else -> {
                        val op = operators.firstOrNull { text.startsWith(it, i) }
                            ?: throw PineException("Unexpected character '$c'.", line)
                        out += Tok(T.OP, op, line)
                        i += op.length
                    }
                }
            }
            return out
        }

        private fun indentOf(raw: String): Int {
            var n = 0
            for (ch in raw) { if (ch == ' ') n++ else if (ch == '\t') n += 4 else break }
            return n
        }

        fun lines(source: String): List<LogicalLine> {
            val raw = source.replace("\r\n", "\n").split('\n')
            val result = mutableListOf<LogicalLine>()
            var pending: MutableList<Tok>? = null
            var pendingIndent = 0
            var pendingLine = 0
            var depth = 0
            for ((index, text) in raw.withIndex()) {
                val lineNo = index + 1
                val tokens = tokenize(text, lineNo)
                if (tokens.isEmpty()) continue
                val indent = indentOf(text)
                val current = pending
                val startsWithContinuation = tokens.first().let {
                    it.type == T.OP && it.text in setOf("?", ":", "+", "*", "/", ")", "]", ",") ||
                        it.type == T.ID && it.text in setOf("and", "or")
                }
                val joins = current != null && (depth > 0 ||
                    current.last().text in continuationEnd && current.last().type != T.STR ||
                    (indent > pendingIndent && indent % 4 != 0) ||
                    (indent > pendingIndent && startsWithContinuation))
                if (joins) {
                    current!!.addAll(tokens)
                } else {
                    current?.let { result += LogicalLine(pendingIndent, it, pendingLine) }
                    pending = tokens.toMutableList()
                    pendingIndent = indent
                    pendingLine = lineNo
                    depth = 0
                }
                for (t in tokens) if (t.type == T.OP) {
                    if (t.text == "(" || t.text == "[") depth++
                    if (t.text == ")" || t.text == "]") depth--
                }
            }
            pending?.let { result += LogicalLine(pendingIndent, it, pendingLine) }
            return result
        }
    }

    // ---------------------------------------------------------------- AST

    internal sealed class Expr { var id: Int = 0; var line: Int = 0 }
    internal class Num(val value: Double) : Expr()
    internal class Str(val value: String) : Expr()
    internal class ColorLit(val argb: Long) : Expr()
    internal class Name(val name: String) : Expr()
    internal class Call(val callee: String, val args: List<Expr>, val named: Map<String, Expr>) : Expr()
    internal class Index(val target: Expr, val offset: Expr) : Expr()
    internal class Unary(val op: String, val operand: Expr) : Expr()
    internal class Binary(val op: String, val left: Expr, val right: Expr) : Expr()
    internal class Ternary(val cond: Expr, val yes: Expr, val no: Expr) : Expr()
    internal class Member(val target: Expr, val name: String) : Expr()
    internal class MethodCall(val target: Expr, val name: String, val args: List<Expr>, val named: Map<String, Expr>) : Expr()
    internal class TupleLit(val items: List<Expr>) : Expr()
    internal class SwitchExpr(val subject: Expr?, val cases: List<Pair<Expr?, List<Stmt>>>) : Expr()
    internal class IfExpr(val branches: List<Pair<Expr, List<Stmt>>>, val otherwise: List<Stmt>?) : Expr()

    internal sealed class Stmt { var line: Int = 0 }
    internal class Declare(val name: String, val value: Expr, val persistent: Boolean) : Stmt()
    internal class Assign(val name: String, val op: String, val value: Expr) : Stmt()
    internal class TupleDeclare(val names: List<String>, val value: Expr) : Stmt()
    internal class ExprStmt(val expr: Expr) : Stmt()
    internal class IfStmt(val branches: List<Pair<Expr, List<Stmt>>>, val otherwise: List<Stmt>?) : Stmt()
    internal class ForStmt(val variable: String, val from: Expr, val to: Expr, val step: Expr?, val body: List<Stmt>) : Stmt()
    internal class ForInStmt(val indexVar: String?, val valueVar: String, val source: Expr, val body: List<Stmt>) : Stmt()
    internal class FieldAssign(val path: List<String>, val op: String, val value: Expr) : Stmt()
    internal class TypeDecl(val name: String) : Stmt()
    internal class TypeDef(val name: String, val fields: List<Pair<String, Expr?>>, val isEnum: Boolean)
    internal class WhileStmt(val cond: Expr, val body: List<Stmt>) : Stmt()
    internal class FuncDef(val name: String, val params: List<Pair<String, Expr?>>, val body: List<Stmt>) : Stmt()
    internal class Control(val kind: String) : Stmt()
    internal class Skipped(val reason: String) : Stmt()

    internal class Program(val statements: List<Stmt>, val functions: Map<String, FuncDef>,
        val diagnostics: List<Diagnostic>, val nodeCount: Int, val types: Map<String, TypeDef> = emptyMap())

    // ---------------------------------------------------------------- parser

    private val typeWords = setOf("int", "float", "bool", "color", "string", "line", "label", "box", "table",
        "series", "simple", "const", "linefill", "polyline", "chart.point", "array", "matrix", "map")

    internal class Parser(private val lines: List<LogicalLine>) {
        private var cursor = 0
        private var nodes = 0
        private val diagnostics = mutableListOf<Diagnostic>()
        private val functions = linkedMapOf<String, FuncDef>()
        private val types = linkedMapOf<String, TypeDef>()

        fun parseProgram(): Program {
            val body = parseBlock(-1)
            return Program(body, functions, diagnostics, nodes, types)
        }

        private fun parseBlock(parentIndent: Int): List<Stmt> {
            val out = mutableListOf<Stmt>()
            if (cursor >= lines.size) return out
            val indent = lines[cursor].indent
            if (indent <= parentIndent) return out
            while (cursor < lines.size && lines[cursor].indent == indent) {
                val line = lines[cursor]
                cursor++
                val stmt = try {
                    parseStatement(line, indent)
                } catch (error: PineException) {
                    // Skip the whole nested block of an unparseable statement.
                    while (cursor < lines.size && lines[cursor].indent > indent) cursor++
                    diagnostics += Diagnostic(line.line, error.message ?: "Syntax error", Severity.ERROR)
                    Skipped(error.message ?: "Syntax error")
                }
                stmt.line = line.line
                out += stmt
            }
            if (cursor < lines.size && lines[cursor].indent > indent) {
                val bad = lines[cursor]
                diagnostics += Diagnostic(bad.line, "Unexpected indentation.", Severity.ERROR)
                while (cursor < lines.size && lines[cursor].indent > indent) cursor++
            }
            return out
        }

        private fun parseStatement(line: LogicalLine, indent: Int): Stmt {
            val s = Stream(line.tokens, line.line)
            val first = s.peek()
            if (first.type == T.ID) when (first.text) {
                "indicator", "study", "strategy", "library" -> if (s.peekAt(1)?.text == "(") return ExprStmt(parseExpr(s).also { s.end() })
                "export", "method" -> if (s.peekAt(1)?.type == T.ID) {
                    // `export f(x) =>` and `method m(Type self, ...) =>` define ordinary functions here.
                    return parseStatement(LogicalLine(line.indent, line.tokens.drop(1), line.line), indent)
                }
                "type", "enum" -> if (s.peekAt(1)?.type == T.ID && line.tokens.size == 2) {
                    val name = line.tokens[1].text
                    types[name] = TypeDef(name, parseFields(indent, first.text == "enum"), first.text == "enum")
                    return TypeDecl(name)
                }
                "import" -> if (s.peekAt(1)?.type == T.ID) {
                    diagnostics += Diagnostic(line.line, "Libraries are not available in the local preview; calls into '" +
                        line.tokens.drop(1).joinToString("") { it.text }.substringBefore(" as") + "' will be skipped.", Severity.WARNING)
                    return Skipped("import")
                }
                "if" -> {
                    s.next()
                    return parseIfChain(parseExpr(s).also { s.end() }, indent).let { IfStmt(it.first, it.second) }
                }
                "for" -> {
                    s.next()
                    if (s.peek().text == "[" ) {
                        s.next()
                        val index = s.expectId(); s.expect(","); val value = s.expectId(); s.expect("]")
                        if (s.next().text != "in") throw PineException("Expected 'in' in for loop.", line.line)
                        val source = parseExpr(s); s.end()
                        return ForInStmt(index, value, source, parseBlock(indent))
                    }
                    if (s.peekAt(1)?.text == "in") {
                        val value = s.expectId(); s.next()
                        val source = parseExpr(s); s.end()
                        return ForInStmt(null, value, source, parseBlock(indent))
                    }
                    val variable = s.expectId()
                    s.expect("=")
                    val from = parseExpr(s)
                    if (s.next().text != "to") throw PineException("Expected 'to' in for loop.", line.line)
                    val to = parseExpr(s)
                    val step = if (s.peekOrNull()?.text == "by") { s.next(); parseExpr(s) } else null
                    s.end()
                    return ForStmt(variable, from, to, step, parseBlock(indent))
                }
                "while" -> {
                    s.next()
                    val cond = parseExpr(s)
                    s.end()
                    return WhileStmt(cond, parseBlock(indent))
                }
                "break", "continue" -> { s.next(); s.end(); return Control(first.text) }
                "switch" -> return ExprStmt(parseValue(s, indent))
            }
            // Function definition: name(params) => ...
            if (first.type == T.ID && s.peekAt(1)?.text == "(" && isFunctionDef(line.tokens)) {
                val name = s.next().text
                s.expect("(")
                val params = mutableListOf<Pair<String, Expr?>>()
                while (s.peek().text != ")") {
                    skipParamType(s)
                    val pname = s.expectId()
                    val default = if (s.peekOrNull()?.text == "=") { s.next(); parseExpr(s) } else null
                    params += pname to default
                    if (s.peek().text == ",") s.next()
                }
                s.expect(")")
                s.expect("=>")
                val body = if (s.atEnd()) parseBlock(indent) else listOf(ExprStmt(parseExpr(s).also { s.end() }))
                val def = FuncDef(name, params, body)
                functions[name] = def
                return def
            }
            // Tuple declaration: [a, b] = expr
            if (first.text == "[" && first.type == T.OP && line.tokens.any { it.text == "=" } && isTupleDecl(line.tokens)) {
                s.next()
                val names = mutableListOf<String>()
                while (s.peek().text != "]") {
                    names += s.expectId()
                    if (s.peek().text == ",") s.next()
                }
                s.expect("]")
                s.expect("=")
                return TupleDeclare(names, parseValue(s, indent))
            }
            // obj.field := value, obj.a.b += value
            if (first.type == T.ID && s.peekAt(1)?.text == "." ) {
                var k = 0
                val path = mutableListOf<String>()
                while (k < line.tokens.size && line.tokens[k].type == T.ID) {
                    path += line.tokens[k].text
                    if (line.tokens.getOrNull(k + 1)?.text == ".") k += 2 else { k++; break }
                }
                val op = line.tokens.getOrNull(k)?.text
                if (path.size >= 2 && op in setOf(":=", "+=", "-=", "*=", "/=", "%=") ) {
                    repeat(k + 1) { s.next() }
                    return FieldAssign(path, op!!, parseValue(s, indent))
                }
            }
            // Declarations with optional var/varip and type keywords.
            var persistent = false
            var position = 0
            val toks = line.tokens
            if (toks[0].type == T.ID && (toks[0].text == "var" || toks[0].text == "varip")) { persistent = true; position = 1 }
            position = skipType(toks, position)
            if (position < toks.size - 1 && toks[position].type == T.ID && toks[position + 1].type == T.OP &&
                toks[position + 1].text in setOf("=", ":=", "+=", "-=", "*=", "/=", "%=")) {
                repeat(position) { s.next() }
                val name = s.next().text
                val op = s.next().text
                val value = parseValue(s, indent)
                return if (op == "=") Declare(name, value, persistent) else Assign(name, op, value)
            }
            val expr = parseExpr(s)
            s.end()
            return ExprStmt(expr)
        }

        private fun skipParamType(s: Stream) {
            while (s.peek().type == T.ID) {
                val next = s.peekAt(1)
                when {
                    next?.type == T.ID -> s.next()
                    next?.text == "<" -> { s.next(); while (s.peek().text != ">") s.next(); s.next() }
                    next?.text == "." && s.peekAt(2)?.type == T.ID && s.peekAt(3)?.type == T.ID -> { s.next(); s.next() }
                    else -> return
                }
            }
        }

        /** Field lines of a `type` (or the members of an `enum`). */
        private fun parseFields(indent: Int, isEnum: Boolean): List<Pair<String, Expr?>> {
            val fields = mutableListOf<Pair<String, Expr?>>()
            if (cursor >= lines.size || lines[cursor].indent <= indent) return fields
            val fieldIndent = lines[cursor].indent
            while (cursor < lines.size && lines[cursor].indent >= fieldIndent) {
                val line = lines[cursor]
                cursor++
                if (line.indent > fieldIndent) continue
                val toks = line.tokens.dropWhile { it.text == "varip" }
                val equals = toks.indexOfFirst { it.text == "=" }
                val nameIndex = if (equals >= 0) equals - 1 else toks.lastIndex
                val name = toks.getOrNull(nameIndex)?.takeIf { it.type == T.ID }?.text ?: continue
                val default = if (equals >= 0 && equals < toks.lastIndex) {
                    val s = Stream(toks.subList(equals + 1, toks.size), line.line)
                    runCatching { parseExpr(s).also { s.end() } }.getOrNull()
                } else null
                fields += name to (if (isEnum) (default ?: mark(Str(name), line.line)) else default)
            }
            return fields
        }

        private fun skipType(toks: List<Tok>, start: Int): Int {
            var position = start
            // A user-defined type name directly before the variable name, e.g. `var Pivot p = ...`.
            if (position < toks.size - 2 && toks[position].type == T.ID && toks[position].text in types &&
                toks[position + 1].type == T.ID) return position + 1
            while (position < toks.size - 2 && toks[position].type == T.ID && (toks[position].text in typeWords ||
                    (toks[position + 1].text == "<" && toks[position].text in setOf("array", "matrix", "map")))) {
                position++
                // array<float> style generics
                if (position < toks.size && toks[position].text == "<") {
                    while (position < toks.size && toks[position].text != ">") position++
                    position++
                }
                if (position < toks.size && toks[position].text == ".") position += 2
            }
            return position
        }

        /** Value of a declaration: an expression, or a block-bodied switch / if expression. */
        private fun parseValue(s: Stream, indent: Int): Expr {
            val head = s.peek()
            if (head.type == T.ID && head.text == "switch") {
                s.next()
                val subject = if (s.atEnd()) null else parseExpr(s)
                s.end()
                return mark(parseSwitchBody(subject, indent), head.line)
            }
            if (head.type == T.ID && head.text == "if") {
                s.next()
                val cond = parseExpr(s)
                s.end()
                val (branches, otherwise) = parseIfChain(cond, indent)
                return mark(IfExpr(branches, otherwise), head.line)
            }
            return parseExpr(s).also { s.end() }
        }

        private fun parseSwitchBody(subject: Expr?, indent: Int): SwitchExpr {
            val cases = mutableListOf<Pair<Expr?, List<Stmt>>>()
            if (cursor >= lines.size || lines[cursor].indent <= indent) return SwitchExpr(subject, cases)
            val caseIndent = lines[cursor].indent
            while (cursor < lines.size && lines[cursor].indent == caseIndent) {
                val line = lines[cursor]
                cursor++
                val s = Stream(line.tokens, line.line)
                val key = if (s.peek().text == "=>") null else parseExpr(s)
                s.expect("=>")
                val body = if (s.atEnd()) parseBlock(caseIndent)
                else {
                    val stmt = statementFromStream(s, caseIndent)
                    stmt.line = line.line
                    listOf(stmt)
                }
                cases += key to body
            }
            return SwitchExpr(subject, cases)
        }

        private fun statementFromStream(s: Stream, indent: Int): Stmt {
            val rest = s.remaining()
            val line = LogicalLine(indent, rest, s.line)
            return parseStatement(line, indent)
        }

        private fun parseIfChain(cond: Expr, indent: Int): Pair<List<Pair<Expr, List<Stmt>>>, List<Stmt>?> {
            val branches = mutableListOf(cond to parseBlock(indent))
            var otherwise: List<Stmt>? = null
            while (cursor < lines.size && lines[cursor].indent == indent &&
                lines[cursor].tokens.first().let { it.type == T.ID && it.text == "else" }) {
                val line = lines[cursor]
                cursor++
                val s = Stream(line.tokens, line.line)
                s.next()
                if (!s.atEnd() && s.peek().text == "if") {
                    s.next()
                    val c = parseExpr(s)
                    s.end()
                    branches += c to parseBlock(indent)
                } else {
                    s.end()
                    otherwise = parseBlock(indent)
                    break
                }
            }
            return branches to otherwise
        }

        private fun isFunctionDef(tokens: List<Tok>): Boolean {
            var depth = 0
            for ((i, t) in tokens.withIndex()) {
                if (t.text == "(") depth++
                if (t.text == ")") { depth--; if (depth == 0) return tokens.getOrNull(i + 1)?.text == "=>" }
            }
            return false
        }

        private fun isTupleDecl(tokens: List<Tok>): Boolean {
            val close = tokens.indexOfFirst { it.text == "]" }
            return close > 0 && tokens.getOrNull(close + 1)?.text == "=" &&
                tokens.subList(1, close).all { it.type == T.ID || it.text == "," }
        }

        private fun <E : Expr> mark(e: E, line: Int): E { e.id = ++nodes; e.line = line; return e }

        fun parseArguments(s: Stream, name: String, line: Int) = parseArgumentsImpl(s, name, line)

        // Precedence climbing.
        fun parseExpr(s: Stream): Expr = parseTernary(s)

        private fun parseTernary(s: Stream): Expr {
            val cond = parseOr(s)
            if (s.peekOrNull()?.text == "?") {
                val line = s.next().line
                val yes = parseTernary(s)
                s.expect(":")
                val no = parseTernary(s)
                return mark(Ternary(cond, yes, no), line)
            }
            return cond
        }

        private fun parseOr(s: Stream): Expr {
            var left = parseAnd(s)
            while (s.peekOrNull()?.let { it.type == T.ID && it.text == "or" } == true) {
                val line = s.next().line
                left = mark(Binary("or", left, parseAnd(s)), line)
            }
            return left
        }

        private fun parseAnd(s: Stream): Expr {
            var left = parseEquality(s)
            while (s.peekOrNull()?.let { it.type == T.ID && it.text == "and" } == true) {
                val line = s.next().line
                left = mark(Binary("and", left, parseEquality(s)), line)
            }
            return left
        }

        private fun parseEquality(s: Stream): Expr {
            var left = parseComparison(s)
            while (s.peekOrNull()?.let { it.type == T.OP && it.text in setOf("==", "!=") } == true) {
                val t = s.next()
                left = mark(Binary(t.text, left, parseComparison(s)), t.line)
            }
            return left
        }

        private fun parseComparison(s: Stream): Expr {
            var left = parseAdditive(s)
            while (s.peekOrNull()?.let { it.type == T.OP && it.text in setOf("<", ">", "<=", ">=") } == true) {
                val t = s.next()
                left = mark(Binary(t.text, left, parseAdditive(s)), t.line)
            }
            return left
        }

        private fun parseAdditive(s: Stream): Expr {
            var left = parseMultiplicative(s)
            while (s.peekOrNull()?.let { it.type == T.OP && it.text in setOf("+", "-") } == true) {
                val t = s.next()
                left = mark(Binary(t.text, left, parseMultiplicative(s)), t.line)
            }
            return left
        }

        private fun parseMultiplicative(s: Stream): Expr {
            var left = parseUnary(s)
            while (s.peekOrNull()?.let { it.type == T.OP && it.text in setOf("*", "/", "%") } == true) {
                val t = s.next()
                left = mark(Binary(t.text, left, parseUnary(s)), t.line)
            }
            return left
        }

        private fun parseUnary(s: Stream): Expr {
            val t = s.peek()
            if (t.type == T.OP && (t.text == "-" || t.text == "+")) { s.next(); return mark(Unary(t.text, parseUnary(s)), t.line) }
            if (t.type == T.ID && t.text == "not") { s.next(); return mark(Unary("not", parseUnary(s)), t.line) }
            return parsePostfix(s)
        }

        private fun parsePostfix(s: Stream): Expr {
            var expr = parsePrimary(s)
            while (true) {
                val t = s.peekOrNull() ?: break
                if (t.type == T.OP && t.text == "[") {
                    s.next()
                    val offset = parseExpr(s)
                    s.expect("]")
                    expr = mark(Index(expr, offset), t.line)
                } else if (t.type == T.OP && t.text == "." && s.peekAt(1)?.type == T.ID) {
                    s.next()
                    val name = s.next().text
                    expr = if (s.peekOrNull()?.text == "(") {
                        s.next()
                        val (args, named) = parseArguments(s, name, t.line)
                        mark(MethodCall(expr, name, args, named), t.line)
                    } else mark(Member(expr, name), t.line)
                } else break
            }
            return expr
        }

        private fun parsePrimary(s: Stream): Expr {
            val t = s.next()
            return when (t.type) {
                T.NUM -> mark(Num(t.text.toDoubleOrNull() ?: throw PineException("Invalid number ${t.text}.", t.line)), t.line)
                T.STR -> mark(Str(t.text), t.line)
                T.COLOR -> mark(ColorLit(parseHexColor(t.text) ?: throw PineException("Invalid colour ${t.text}.", t.line)), t.line)
                T.ID -> {
                    var name = t.text
                    while (s.peekOrNull()?.text == "." && s.peekAt(1)?.type == T.ID) {
                        s.next()
                        name += "." + s.next().text
                    }
                    // Generic constructors: array.new<float>(...), map.new<string, float>(), matrix.new<int>(...)
                    if (s.peekOrNull()?.text == "<" && (name.endsWith(".new") || name == "array.from")) {
                        var depth = 0
                        while (true) {
                            val g = s.next()
                            if (g.text == "<") depth++
                            if (g.text == ">") { depth--; if (depth == 0) break }
                        }
                    }
                    if (s.peekOrNull()?.text == "(") {
                        s.next()
                        val (args, named) = parseArguments(s, name, t.line)
                        mark(Call(name, args, named), t.line)
                    } else mark(Name(name), t.line)
                }
                T.OP -> when (t.text) {
                    "(" -> parseExpr(s).also { s.expect(")") }
                    "[" -> {
                        val items = mutableListOf<Expr>()
                        while (s.peek().text != "]") {
                            items += parseExpr(s)
                            if (s.peek().text == ",") s.next()
                        }
                        s.expect("]")
                        mark(TupleLit(items), t.line)
                    }
                    else -> throw PineException("Unexpected '${t.text}'.", t.line)
                }
                T.EOL -> throw PineException("Unexpected end of line.", t.line)
            }
        }
    }

    private fun Parser.parseArgumentsImpl(s: Stream, name: String, line: Int): Pair<List<Expr>, Map<String, Expr>> {
        val args = mutableListOf<Expr>()
        val named = linkedMapOf<String, Expr>()
        while (s.peek().text != ")") {
            if (s.peek().type == T.ID && s.peekAt(1)?.text == "=") {
                val key = s.next().text
                s.next()
                named[key] = parseExpr(s)
            } else args += parseExpr(s)
            if (s.peek().text == ",") s.next() else if (s.peek().text != ")")
                throw PineException("Expected ',' or ')' in call to $name.", line)
        }
        s.expect(")")
        return args to named
    }

    internal class Stream(private val tokens: List<Tok>, val line: Int) {
        private var i = 0
        fun atEnd() = i >= tokens.size
        fun peek(): Tok = tokens.getOrElse(i) { Tok(T.EOL, "", line) }
        fun peekOrNull(): Tok? = tokens.getOrNull(i)
        fun peekAt(offset: Int): Tok? = tokens.getOrNull(i + offset)
        fun next(): Tok = tokens.getOrNull(i++) ?: throw PineException("Unexpected end of line.", line)
        fun expect(text: String) { val t = next(); if (t.text != text) throw PineException("Expected '$text' but found '${t.text}'.", t.line) }
        fun expectId(): String { val t = next(); if (t.type != T.ID) throw PineException("Expected a name but found '${t.text}'.", t.line); return t.text }
        fun end() { if (!atEnd()) throw PineException("Unexpected '${peek().text}'.", peek().line) }
        fun remaining(): List<Tok> = tokens.subList(i, tokens.size).also { i = tokens.size }
    }

    // ---------------------------------------------------------------- values

    /** Colour value as 0xAARRGGBB. */
    data class PineColor(val argb: Long)
    private class TupleValue(val items: List<Any?>)
    private class PlotRef(val key: String)
    private class HLineRef(val price: Double)
    private object Unsupported

    internal fun parseHexColor(text: String): Long? {
        val hex = text.removePrefix("#")
        return when (hex.length) {
            6 -> hex.toLongOrNull(16)?.let { 0xFF000000L or it }
            8 -> hex.toLongOrNull(16)?.let { rgba -> ((rgba and 0xFF) shl 24) or (rgba ushr 8) }
            else -> null
        }
    }

    private val namedColors = mapOf(
        "aqua" to 0xFF00BCD4L, "black" to 0xFF363A45L, "blue" to 0xFF2962FFL, "fuchsia" to 0xFFE040FBL,
        "gray" to 0xFF787B86L, "green" to 0xFF4CAF50L, "lime" to 0xFF00E676L, "maroon" to 0xFF880E4FL,
        "navy" to 0xFF311B92L, "olive" to 0xFF808000L, "orange" to 0xFFFF9800L, "purple" to 0xFF9C27B0L,
        "red" to 0xFFFF5252L, "silver" to 0xFFB2B5BEL, "teal" to 0xFF00897BL, "white" to 0xFFFFFFFFL,
        "yellow" to 0xFFFFEB3BL
    )

    private val legacyFunctions = setOf("sma", "ema", "rma", "wma", "vwma", "hma", "alma", "rsi", "atr", "stdev",
        "variance", "dev", "highest", "lowest", "highestbars", "lowestbars", "change", "mom", "roc", "crossover",
        "crossunder", "cross", "cum", "macd", "bb", "bbw", "kc", "stoch", "cci", "mfi", "pivothigh", "pivotlow",
        "valuewhen", "barssince", "rising", "falling", "linreg", "sar", "supertrend", "dmi", "wpr", "tsi", "cmo",
        "median", "percentrank", "swma", "falling", "correlation")
    private val legacyMath = setOf("abs", "max", "min", "sqrt", "pow", "log", "log10", "exp", "round", "floor",
        "ceil", "sign", "avg", "sum", "acos", "asin", "atan", "cos", "sin", "tan", "todegrees", "toradians")

    // ---------------------------------------------------------------- interpreter

    private class BreakSignal : RuntimeException(null, null, false, false)
    private class ContinueSignal : RuntimeException(null, null, false, false)

    private class Variable(size: Int, val persistent: Boolean) {
        val values = arrayOfNulls<Any?>(size)
        var initialized = false
    }

    private class Scope(val parent: Scope?) {
        val vars = HashMap<String, Variable>()
        fun find(name: String): Variable? = vars[name] ?: parent?.find(name)
    }

    private class PlotState(val title: String, val style: PlotStyle, val pane: Pane, size: Int, val text: String, val offset: Int = 0) {
        val values = arrayOfNulls<Double>(size)
        val colors = arrayOfNulls<Long>(size)
    }

    private class Order(val id: String, val direction: Int, val qtyPercent: Double = 100.0)
    private class ExitRule(val fromEntry: String?, val stop: Double?, val limit: Double?)

    private class Interpreter(
        private val program: Program,
        private val candles: List<PineCandle>,
        private val symbol: String,
        private val timeframeSeconds: Int
    ) {
        private val n = candles.size
        private var bar = 0
        private var steps = 0L
        private val global = Scope(null)
        private val functionScopes = HashMap<String, Scope>()
        private val nodeHistory = HashMap<String, Array<Any?>>()
        private val state = HashMap<String, Any>()
        private val plots = LinkedHashMap<String, PlotState>()
        private val hlines = mutableListOf<HLine>()
        private val hlineKeys = HashSet<String>()
        private val backgrounds = arrayOfNulls<Long>(n)
        private val barColors = arrayOfNulls<Long>(n)
        private val signals = mutableListOf<Signal>()
        private val alerts = LinkedHashMap<String, MutableList<Int>>()
        private val diagnostics = program.diagnostics.toMutableList()
        private val reported = HashSet<String>()
        private val disabled = HashSet<Stmt>()
        private var title = "Pine script"
        private var overlay = false
        private var declared = false
        private var isStrategy = false
        private var callPath = ""
        private val pendingOrders = mutableListOf<Order>()
        private var pendingClose: String? = null
        private val exitRules = mutableListOf<ExitRule>()
        private var position = 0
        private var positionEntry = 0.0
        private var positionBar = 0
        private var positionLabel = ""
        private val trades = mutableListOf<Trade>()
        private var realizedEquity = 100.0
        private val equityCurve = arrayOfNulls<Double>(n)
        private val labels = ArrayList<PineLabel>()
        private val drawnLines = ArrayList<PineLine>()
        private val boxes = ArrayList<PineBox>()
        private val linefills = ArrayList<PineLinefill>()
        private val tables = ArrayList<PineTable>()
        private val fills = LinkedHashMap<String, Triple<Any?, Any?, Array<Long?>>>()
        private var maxLabels = 50
        private var maxLines = 50
        private var maxBoxes = 50
        private val namespaces = setOf("ta", "math", "color", "str", "array", "map", "matrix", "label", "line", "box",
            "table", "linefill", "polyline", "chart", "strategy", "request", "input", "ticker", "timeframe", "syminfo",
            "barstate", "runtime", "log", "plot", "hline", "shape", "location", "size", "xloc", "yloc", "extend",
            "position", "text", "font", "format", "display", "session", "currency", "order", "alert", "dayofweek",
            "barmerge", "scale", "adjustment")

        fun execute(): Output {
            val countStatements = countStatements(program.statements)
            for (b in 0 until n) {
                bar = b
                fillOrders()
                for (variable in global.vars.values) if (variable.persistent && b > 0) variable.values[b] = variable.values[b - 1]
                for (scope in functionScopes.values) for (variable in scope.vars.values)
                    if (variable.persistent && b > 0) variable.values[b] = variable.values[b - 1]
                runBlock(program.statements, global, topLevel = true)
                if (isStrategy) equityCurve[b] = realizedEquity *
                    (if (position != 0 && positionEntry > 0) 1 + (candles[b].close - positionEntry) / positionEntry * position else 1.0)
            }
            if (!declared) diagnostics.add(0, Diagnostic(0,
                "No indicator() or strategy() declaration; previewed as a separate pane.", Severity.INFO))
            val pane = if (overlay) Pane.PRICE else Pane.INDICATOR
            val outPlots = plots.values.map { p ->
                Plot(p.title, shift(p.values.toList(), p.offset), p.style, p.pane, shift(p.colors.toList(), p.offset), p.text)
            }
            val backtest = if (isStrategy || trades.isNotEmpty()) buildBacktest() else null
            val plotKeys = plots.keys.toList()
            fun edge(ref: Any?): FillEdge? = when (ref) {
                is PlotRef -> plotKeys.indexOf(ref.key).takeIf { it >= 0 }?.let { FillEdge(it, null) }
                is HLineRef -> FillEdge(null, ref.price)
                else -> null
            }
            val outFills = fills.values.mapNotNull { (a, b, colors) ->
                val upper = edge(a) ?: return@mapNotNull null
                val lower = edge(b) ?: return@mapNotNull null
                val fillPane = upper.plot?.let { outPlots[it].pane } ?: lower.plot?.let { outPlots[it].pane } ?: pane
                Fill(upper, lower, colors.toList(), fillPane)
            }
            fun lineOut(l: PineLine) = LineDrawing(l.x1, l.y1, l.x2, l.y2, l.color, l.width, l.style, l.extend)
            val drawings = buildList<Drawing> {
                linefills.filter { !it.deleted && !it.first.deleted && !it.second.deleted }
                    .forEach { add(LinefillDrawing(lineOut(it.first), lineOut(it.second), it.color)) }
                boxes.filter { !it.deleted }.forEach { add(BoxDrawing(it.left, it.top, it.right, it.bottom, it.border, it.background, it.text, it.textColor, it.extend)) }
                drawnLines.filter { !it.deleted }.forEach { add(lineOut(it)) }
                labels.filter { !it.deleted }.forEach { add(LabelDrawing(it.x, labelPrice(it), it.text, it.color, it.textColor, it.style, it.size)) }
            }
            val outTables = tables.filter { !it.deleted }.map { Table(it.position, it.columns, it.rows, it.background, it.frame, HashMap(it.cells)) }
            return Output(title, overlay, isStrategy, outPlots, hlines.map { it.copy(pane = if (it.pane == Pane.PRICE) Pane.PRICE else pane) },
                backgrounds.toList(), barColors.toList(), signals, backtest,
                alerts.map { AlertHit(it.key, it.value) }, diagnostics.sortedBy { it.line },
                countStatements, disabled.size, drawings, outFills, outTables,
                if (isStrategy) equityCurve.toList() else emptyList())
        }

        private fun labelPrice(label: PineLabel): Double {
            val index = label.x.toInt().coerceIn(0, n - 1)
            return when {
                label.yloc.contains("abovebar") -> candles[index].high
                label.yloc.contains("belowbar") -> candles[index].low
                else -> label.y
            }
        }

        /** Pine's `offset=` moves a series right (positive) or left (negative) by whole bars. */
        private fun <V> shift(values: List<V?>, offset: Int): List<V?> =
            if (offset == 0) values else List(values.size) { i -> values.getOrNull(i - offset) }

        private fun offsetOf(e: Call, scope: Scope): Int =
            if (e.named.containsKey("offset")) num(eval(e.named.getValue("offset"), scope)).let { if (it.isNaN()) 0 else it.toInt().coerceIn(-500, 500) } else 0

        private fun countStatements(list: List<Stmt>): Int = list.sumOf { stmt ->
            1 + when (stmt) {
                is IfStmt -> stmt.branches.sumOf { countStatements(it.second) } + countStatements(stmt.otherwise.orEmpty())
                is ForStmt -> countStatements(stmt.body)
                is ForInStmt -> countStatements(stmt.body)
                is WhileStmt -> countStatements(stmt.body)
                else -> 0
            }
        }

        private fun note(line: Int, message: String, severity: Severity = Severity.WARNING) {
            if (reported.add("$line|$message")) diagnostics += Diagnostic(line, message, severity)
        }

        private fun tick() {
            if (++steps > MAX_STEPS) throw PineException("Script exceeded the local execution budget.")
        }

        // ------------------------------------------------ statements

        private fun runBlock(list: List<Stmt>, scope: Scope, topLevel: Boolean = false): Any? {
            var last: Any? = null
            for (stmt in list) {
                if (stmt in disabled) continue
                try {
                    last = runStatement(stmt, scope)
                } catch (signal: BreakSignal) { throw signal
                } catch (signal: ContinueSignal) { throw signal
                } catch (error: PineException) {
                    if (error.message?.contains("execution budget") == true) throw error
                    disabled += stmt
                    note(if (error.line > 0) error.line else stmt.line, error.message ?: "Statement failed", Severity.ERROR)
                    last = null
                } catch (error: ArithmeticException) {
                    disabled += stmt
                    note(stmt.line, "Arithmetic error: ${error.message}", Severity.ERROR)
                }
            }
            return last
        }

        private fun runStatement(stmt: Stmt, scope: Scope): Any? {
            tick()
            return when (stmt) {
                is Declare -> {
                    val existing = scope.vars[stmt.name]
                    val variable = existing ?: Variable(n, stmt.persistent).also { scope.vars[stmt.name] = it }
                    if (stmt.persistent) {
                        if (!variable.initialized) { variable.values[bar] = eval(stmt.value, scope); variable.initialized = true }
                    } else variable.values[bar] = eval(stmt.value, scope)
                    variable.values[bar]
                }
                is Assign -> {
                    val variable = scope.find(stmt.name) ?: throw PineException("'${stmt.name}' is assigned before it is declared.", stmt.line)
                    val value = eval(stmt.value, scope)
                    variable.values[bar] = when (stmt.op) {
                        ":=" -> value
                        else -> arithmetic(stmt.op.dropLast(1), variable.values[bar], value)
                    }
                    variable.values[bar]
                }
                is TupleDeclare -> {
                    val value = eval(stmt.value, scope)
                    val items = (value as? TupleValue)?.items ?: throw PineException("Right side does not return a tuple.", stmt.line)
                    stmt.names.forEachIndexed { i, name ->
                        if (name == "_") return@forEachIndexed
                        val variable = scope.vars.getOrPut(name) { Variable(n, false) }
                        variable.values[bar] = items.getOrNull(i)
                    }
                    value
                }
                is ExprStmt -> eval(stmt.expr, scope)
                is IfStmt -> {
                    for ((cond, body) in stmt.branches) {
                        if (truthy(eval(cond, scope))) return runBlock(body, Scope(scope))
                    }
                    stmt.otherwise?.let { runBlock(it, Scope(scope)) }
                }
                is ForStmt -> {
                    val from = num(eval(stmt.from, scope))
                    val to = num(eval(stmt.to, scope))
                    if (from.isNaN() || to.isNaN()) return null
                    val step = stmt.step?.let { abs(num(eval(it, scope))) }?.takeIf { it > 0 && !it.isNaN() } ?: 1.0
                    val direction = if (to >= from) 1.0 else -1.0
                    val loopScope = Scope(scope)
                    val counter = Variable(n, false)
                    loopScope.vars[stmt.variable] = counter
                    var i = from
                    var iterations = 0
                    var last: Any? = null
                    while (if (direction > 0) i <= to else i >= to) {
                        if (++iterations > MAX_LOOP) throw PineException("Loop exceeded $MAX_LOOP iterations.", stmt.line)
                        counter.values[bar] = i
                        try { last = runBlock(stmt.body, loopScope) }
                        catch (_: BreakSignal) { break }
                        catch (_: ContinueSignal) { }
                        i += step * direction
                    }
                    last
                }
                is WhileStmt -> {
                    var iterations = 0
                    var last: Any? = null
                    while (truthy(eval(stmt.cond, scope))) {
                        if (++iterations > MAX_LOOP) throw PineException("Loop exceeded $MAX_LOOP iterations.", stmt.line)
                        try { last = runBlock(stmt.body, Scope(scope)) }
                        catch (_: BreakSignal) { break }
                        catch (_: ContinueSignal) { }
                    }
                    last
                }
                is ForInStmt -> {
                    val source = eval(stmt.source, scope)
                    val items: List<Pair<Any?, Any?>> = when (source) {
                        is PineArray -> source.items.toList().mapIndexed { i, v -> i.toDouble() to v }
                        is PineMap -> source.entries.entries.map { it.key to it.value }
                        is PineMatrix -> (0 until source.rows).map { r -> r.toDouble() to
                            PineArray(ArrayList((0 until source.columns).map { source.cells[r * source.columns + it] })) }
                        null -> emptyList()
                        else -> throw PineException("for...in needs an array, map or matrix.", stmt.line)
                    }
                    val loopScope = Scope(scope)
                    val valueVar = Variable(n, false).also { loopScope.vars[stmt.valueVar] = it }
                    val indexVar = stmt.indexVar?.let { name -> Variable(n, false).also { loopScope.vars[name] = it } }
                    var last: Any? = null
                    for ((k, v) in items) {
                        indexVar?.values?.set(bar, k)
                        valueVar.values[bar] = v
                        try { last = runBlock(stmt.body, loopScope) }
                        catch (_: BreakSignal) { break }
                        catch (_: ContinueSignal) { }
                    }
                    last
                }
                is FieldAssign -> {
                    val root = scope.find(stmt.path[0]) ?: throw PineException("'${stmt.path[0]}' is assigned before it is declared.", stmt.line)
                    var target: Any? = root.values[bar]
                    for (field in stmt.path.subList(1, stmt.path.size - 1)) target = fieldOf(target, field, stmt.line)
                    val obj = target as? PineObject ?: throw PineException("'${stmt.path.dropLast(1).joinToString(".")}' is na or not an object.", stmt.line)
                    val field = stmt.path.last()
                    if (!obj.fields.containsKey(field)) throw PineException("${obj.type} has no field '$field'.", stmt.line)
                    val value = eval(stmt.value, scope)
                    obj.fields[field] = if (stmt.op == ":=") value else arithmetic(stmt.op.dropLast(1), obj.fields[field], value)
                    obj.fields[field]
                }
                is TypeDecl -> null
                is Control -> if (stmt.kind == "break") throw BreakSignal() else throw ContinueSignal()
                is FuncDef -> null
                is Skipped -> null
            }
        }

        // ------------------------------------------------ expressions

        private fun key(e: Expr) = if (callPath.isEmpty()) e.id.toString() else callPath + "/" + e.id

        private fun eval(e: Expr, scope: Scope): Any? {
            tick()
            return when (e) {
                is Num -> e.value
                is Str -> e.value
                is ColorLit -> PineColor(e.argb)
                is Name -> lookup(e.name, scope, e.line)
                is Unary -> {
                    val v = eval(e.operand, scope)
                    when (e.op) {
                        "not" -> !truthy(v)
                        "-" -> -num(v)
                        else -> num(v)
                    }
                }
                is Binary -> when (e.op) {
                    "and" -> truthy(eval(e.left, scope)) && truthy(eval(e.right, scope))
                    "or" -> truthy(eval(e.left, scope)) || truthy(eval(e.right, scope))
                    else -> binary(e.op, eval(e.left, scope), eval(e.right, scope))
                }
                is Ternary -> if (truthy(eval(e.cond, scope))) eval(e.yes, scope) else eval(e.no, scope)
                is TupleLit -> TupleValue(e.items.map { eval(it, scope) })
                is Member -> fieldOf(eval(e.target, scope), e.name, e.line)
                is MethodCall -> invokeMethod(eval(e.target, scope), e.name, e.args, e.named, scope, e.line, e.id)
                is Index -> {
                    val offset = num(eval(e.offset, scope))
                    if (offset.isNaN() || offset < 0) throw PineException("History offset must be a non-negative number.", e.line)
                    val back = offset.toInt()
                    val target = e.target
                    if (target is Name) {
                        builtinSeries(target.name)?.let { return seriesAt(it, bar - back) }
                        scope.find(target.name)?.let { return if (bar - back >= 0) it.values[bar - back] else null }
                        throw PineException("Unknown series '${target.name}'.", e.line)
                    }
                    val history = nodeHistory.getOrPut(key(e)) { arrayOfNulls(n) }
                    history[bar] = eval(target, scope)
                    if (bar - back >= 0) history[bar - back] else null
                }
                is Call -> call(e, scope)
                is SwitchExpr -> {
                    val subject = e.subject?.let { eval(it, scope) }
                    for ((caseKey, body) in e.cases) {
                        val matches = when {
                            caseKey == null -> true
                            e.subject == null -> truthy(eval(caseKey, scope))
                            else -> equalsValue(subject, eval(caseKey, scope))
                        }
                        if (matches) return runBlock(body, Scope(scope))
                    }
                    null
                }
                is IfExpr -> {
                    for ((cond, body) in e.branches) if (truthy(eval(cond, scope))) return runBlock(body, Scope(scope))
                    e.otherwise?.let { runBlock(it, Scope(scope)) }
                }
            }
        }

        private fun builtinSeries(name: String): String? = when (name) {
            "open", "high", "low", "close", "volume", "hl2", "hlc3", "ohlc4", "hlcc4", "time", "time_close", "bar_index" -> name
            else -> null
        }

        private fun seriesAt(name: String, index: Int): Double? {
            if (index < 0 || index >= n) return null
            val c = candles[index]
            return when (name) {
                "open" -> c.open
                "high" -> c.high
                "low" -> c.low
                "close" -> c.close
                "volume" -> c.volume
                "hl2" -> (c.high + c.low) / 2
                "hlc3" -> (c.high + c.low + c.close) / 3
                "ohlc4" -> (c.open + c.high + c.low + c.close) / 4
                "hlcc4" -> (c.high + c.low + 2 * c.close) / 4
                "time" -> c.epochSeconds * 1000.0
                "time_close" -> (c.epochSeconds + timeframeSeconds) * 1000.0
                "bar_index" -> index.toDouble()
                else -> null
            }
        }

        private fun lookup(name: String, scope: Scope, line: Int): Any? {
            scope.find(name)?.let { return it.values[bar] }
            if ('.' in name) {
                val parts = name.split('.')
                scope.find(parts[0])?.let { root ->
                    var value: Any? = root.values[bar]
                    for (field in parts.drop(1)) value = fieldOf(value, field, line)
                    return value
                }
                program.types[parts[0]]?.takeIf { it.isEnum && parts.size == 2 }?.let { return name }
            }
            builtinSeries(name)?.let { return seriesAt(it, bar) }
            val c = candles[bar]
            val dt = Instant.ofEpochSecond(c.epochSeconds).atZone(ZoneOffset.UTC)
            return when (name) {
                "na" -> null
                "true" -> true
                "false" -> false
                "last_bar_index" -> (n - 1).toDouble()
                "last_bar_time" -> candles.last().epochSeconds * 1000.0
                "barstate.isfirst" -> bar == 0
                "barstate.islast", "barstate.isrealtime", "barstate.islastconfirmedhistory" -> bar == n - 1
                "barstate.isconfirmed", "barstate.ishistory" -> true
                "barstate.isnew" -> true
                "year" -> dt.year.toDouble()
                "month" -> dt.monthValue.toDouble()
                "dayofmonth" -> dt.dayOfMonth.toDouble()
                "dayofweek" -> (dt.dayOfWeek.value % 7 + 1).toDouble()
                "hour" -> dt.hour.toDouble()
                "minute" -> dt.minute.toDouble()
                "weekofyear" -> dt.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR).toDouble()
                "syminfo.ticker", "syminfo.tickerid", "syminfo.description" -> symbol
                "syminfo.basecurrency" -> symbol.substringBefore('-')
                "syminfo.currency" -> symbol.substringAfter('-', "USD")
                "syminfo.mintick" -> 0.01
                "syminfo.pointvalue" -> 1.0
                "syminfo.type" -> "crypto"
                "syminfo.session" -> "24x7"
                "syminfo.timezone" -> "Etc/UTC"
                "timeframe.period" -> timeframeLabel()
                "timeframe.multiplier" -> (if (timeframeSeconds >= 86400) timeframeSeconds / 86400 else timeframeSeconds / 60).toDouble()
                "timeframe.isintraday", "timeframe.isminutes" -> timeframeSeconds < 86400
                "timeframe.isdaily" -> timeframeSeconds == 86400
                "timeframe.isweekly", "timeframe.ismonthly", "timeframe.isseconds", "timeframe.isticks" -> false
                "timeframe.in_seconds" -> timeframeSeconds.toDouble()
                "math.pi" -> Math.PI
                "math.e" -> Math.E
                "math.phi" -> 1.618033988749895
                "ta.tr" -> trueRange(bar, false)
                "ta.obv", "obv" -> obv()
                "ta.vwap", "vwap" -> vwap("ta.vwap", seriesAt("hlc3", bar))
                "ta.accdist", "accdist" -> accdist()
                "ta.pvt", "pvt" -> volumeIndicator("pvt")
                "ta.nvi", "nvi" -> volumeIndicator("nvi")
                "ta.pvi", "pvi" -> volumeIndicator("pvi")
                "ta.wad", "wad" -> volumeIndicator("wad")
                "ta.iii", "iii" -> volumeIndicator("iii")
                "ta.wvad", "wvad" -> volumeIndicator("wvad")
                "strategy.position_size" -> position.toDouble()
                "strategy.position_avg_price" -> if (position != 0) positionEntry else null
                "strategy.long" -> "long"
                "strategy.short" -> "short"
                "strategy.opentrades" -> if (position != 0) 1.0 else 0.0
                "strategy.closedtrades" -> trades.count { it.exitBar != null }.toDouble()
                "strategy.equity", "strategy.initial_capital" -> 100_000.0
                "strategy.netprofit" -> 0.0
                "strategy.fixed", "strategy.cash", "strategy.percent_of_equity" -> name
                else -> {
                    if (name.startsWith("color.")) namedColors[name.removePrefix("color.")]?.let { return PineColor(it) }
                    namedColors[name]?.let { return PineColor(it) }
                    if (name.startsWith("shape.") || name.startsWith("location.") || name.startsWith("size.") ||
                        name.startsWith("plot.style") || name.startsWith("hline.style") || name.startsWith("display.") ||
                        name.startsWith("extend.") || name.startsWith("position.") || name.startsWith("xloc.") ||
                        name.startsWith("yloc.") || name.startsWith("label.style") || name.startsWith("line.style") ||
                        name.startsWith("text.") || name.startsWith("font.") || name.startsWith("format.") ||
                        name.startsWith("scale.") || name.startsWith("alert.freq") || name.startsWith("barmerge.") ||
                        name.startsWith("currency.") || name.startsWith("order.") || name.startsWith("adjustment.") ||
                        name.startsWith("session.") || name.startsWith("dayofweek.") || name.startsWith("input.") ||
                        name.startsWith("strategy.commission") || name.startsWith("strategy.oca") || name.startsWith("strategy.direction"))
                        return name
                    if (name in setOf("integer", "float", "bool", "string", "source", "resolution", "session", "symbol"))
                        return name
                    throw PineException("Unknown identifier '$name'.", line)
                }
            }
        }

        private fun timeframeLabel(): String = when {
            timeframeSeconds % 86400 == 0 -> "${timeframeSeconds / 86400}D".let { if (it == "1D") "D" else it }
            else -> (timeframeSeconds / 60).toString()
        }

        // ------------------------------------------------ calls

        private fun argument(e: Call, index: Int, name: String, scope: Scope): Any? {
            e.named[name]?.let { return eval(it, scope) }
            return e.args.getOrNull(index)?.let { eval(it, scope) }
        }
        private fun has(e: Call, index: Int, name: String) = e.named.containsKey(name) || (index >= 0 && e.args.size > index)
        private fun numArg(e: Call, index: Int, name: String, scope: Scope, default: Double = Double.NaN): Double =
            if (has(e, index, name)) num(argument(e, index, name, scope)) else default
        private fun intArg(e: Call, index: Int, name: String, scope: Scope, default: Int? = null): Int {
            val v = if (has(e, index, name)) num(argument(e, index, name, scope)) else default?.toDouble() ?: Double.NaN
            if (v.isNaN()) throw PineException("Missing length argument '$name'.", e.line)
            val i = v.toInt()
            if (i < 0 || i > 5000) throw PineException("'$name' must be between 0 and 5000.", e.line)
            return i
        }
        private fun stateFor(e: Call): CallState = state.getOrPut(key(e)) { CallState(n) } as CallState

        private fun call(e: Call, scope: Scope): Any? {
            var name = e.callee
            if (name == "study") name = "indicator"
            if (name == "security") name = "request.security"
            if (name in legacyFunctions && program.functions[name] == null) name = "ta.$name"
            if (name in legacyMath && program.functions[name] == null) name = "math.$name"
            if (name == "input" ) return input(e, scope)
            program.functions[name]?.let { return callUser(it, e, scope) }
            if ('.' in name) {
                val head = name.substringBefore('.')
                val rest = name.substringAfter('.')
                if (head !in namespaces) {
                    scope.find(head)?.let { root ->
                        var receiver: Any? = root.values[bar]
                        val parts = rest.split('.')
                        for (field in parts.dropLast(1)) receiver = fieldOf(receiver, field, e.line)
                        return invokeMethod(receiver, parts.last(), e.args, e.named, scope, e.line, e.id)
                    }
                    program.types[head]?.let { type ->
                        return when (rest) {
                            "new" -> construct(type, e, scope)
                            "copy" -> (argument(e, 0, "object", scope) as? PineObject)?.copy()
                            else -> throw PineException("${type.name}.$rest is not defined.", e.line)
                        }
                    }
                }
            }
            if (name.startsWith("input.")) return input(e, scope)
            if (name.startsWith("ta.")) return technical(name.removePrefix("ta."), e, scope)
            if (name.startsWith("math.")) return mathFn(name.removePrefix("math."), e, scope)
            if (name.startsWith("color.")) return colorFn(name.removePrefix("color."), e, scope)
            if (name.startsWith("str.") || name == "tostring") return strFn(name.removePrefix("str."), e, scope)
            if (name.startsWith("strategy")) return strategyFn(name, e, scope)
            if (name.startsWith("label.") || name.startsWith("line.") || name.startsWith("box.") ||
                name.startsWith("table.") || name.startsWith("linefill.") || name.startsWith("chart.point."))
                return drawing(name, evalArgs(e, scope), e.line)
            if (name.startsWith("polyline.")) {
                note(e.line, "polyline drawings are not drawn in the local preview.", Severity.INFO)
                return null
            }
            if (name.startsWith("array.")) return PineCollections.array(name.removePrefix("array."), evalArgs(e, scope), e.line)
            if (name.startsWith("map.")) return PineCollections.map(name.removePrefix("map."), evalArgs(e, scope), e.line)
            if (name.startsWith("matrix.")) return PineCollections.matrix(name.removePrefix("matrix."), evalArgs(e, scope), e.line)
            return when (name) {
                "indicator", "strategy" -> {
                    declared = true
                    isStrategy = name == "strategy"
                    title = (argument(e, 0, "title", scope) as? String)?.take(80) ?: title
                    overlay = truthy(argument(e, 1, "overlay", scope) ?: false)
                    fun limit(key: String) = if (has(e, -1, key)) num(argument(e, -1, key, scope)).toInt().coerceIn(1, 500) else 50
                    maxLabels = limit("max_labels_count"); maxLines = limit("max_lines_count"); maxBoxes = limit("max_boxes_count")
                    null
                }
                "library" -> throw PineException("Libraries cannot be previewed on a chart.", e.line)
                "plot" -> plot(e, scope)
                "plotshape", "plotchar" -> plotShape(e, scope, name == "plotchar")
                "plotarrow" -> plotArrow(e, scope)
                "plotcandle", "plotbar" -> { note(e.line, "$name is not drawn in the local preview.", Severity.INFO); null }
                "hline" -> {
                    val price = num(argument(e, 0, "price", scope))
                    val k = key(e)
                    if (!price.isNaN() && hlineKeys.add(k)) hlines += HLine(price,
                        (argument(e, 1, "title", scope) as? String) ?: "", colorOf(argument(e, 2, "color", scope)), Pane.INDICATOR)
                    if (price.isNaN()) null else HLineRef(price)
                }
                "bgcolor" -> { colorOf(argument(e, 0, "color", scope))?.let { backgrounds[bar] = withMinimumTransparency(it) }; null }
                "barcolor" -> { barColors[bar] = colorOf(argument(e, 0, "color", scope)); null }
                "fill" -> {
                    val a = argument(e, 0, "hline1", scope) ?: argument(e, 0, "plot1", scope)
                    val b = argument(e, 1, "hline2", scope) ?: argument(e, 1, "plot2", scope)
                    val gradient = has(e, -1, "top_color") || e.args.size >= 6
                    val color = if (gradient) colorOf(argument(e, 4, "top_color", scope))?.let { top ->
                        colorOf(argument(e, 5, "bottom_color", scope))?.let { lerpColor(top, it, 0.5) } ?: top }
                        else colorOf(argument(e, 2, "color", scope)) ?: if (has(e, 2, "color")) null else 0x332962FFL
                    if (a != null && b != null) {
                        val entry = fills.getOrPut(key(e)) { Triple(a, b, arrayOfNulls(n)) }
                        entry.third[bar] = color?.let(::withMinimumTransparency)
                    }
                    null
                }
                "alertcondition" -> {
                    val title = (argument(e, 1, "title", scope) as? String) ?: "Alert"
                    val hits = alerts.getOrPut(title) { mutableListOf() }
                    if (truthy(argument(e, 0, "condition", scope))) hits += bar
                    null
                }
                "alert", "runtime.error", "log.info", "log.warning", "log.error", "max_bars_back" -> null
                "na" -> isNa(argument(e, 0, "x", scope))
                "nz" -> argument(e, 0, "source", scope).let { if (isNa(it)) (if (has(e, 1, "replacement")) argument(e, 1, "replacement", scope) else 0.0) else it }
                "fixnan" -> {
                    val st = stateFor(e)
                    val v = num(argument(e, 0, "source", scope))
                    if (!v.isNaN()) st.last = v
                    st.last.takeIf { !it.isNaN() }
                }
                "int" -> num(argument(e, 0, "x", scope)).let { if (it.isNaN()) null else it.toLong().toDouble() }
                "float" -> argument(e, 0, "x", scope)
                "bool" -> truthy(argument(e, 0, "x", scope))
                "string" -> argument(e, 0, "x", scope)?.toString()
                "timestamp" -> timestamp(e, scope)
                "request.security" -> {
                    note(e.line, "request.security() is evaluated on the chart timeframe and symbol in the preview.", Severity.WARNING)
                    argument(e, 2, "expression", scope)
                }
                "request.security_lower_tf", "request.financial", "request.quandl", "request.dividends",
                "request.earnings", "request.splits", "request.economic", "request.seed" ->
                    throw PineException("$name is not available in the local preview.", e.line)
                "time" -> seriesAt("time", bar)
                "dayofweek" -> lookup("dayofweek", scope, e.line)
                "ticker.new", "ticker.modify", "ticker.heikinashi", "ticker.standard" -> symbol
                else -> throw PineException("Function '$name' is not supported by the local preview.", e.line)
            }
        }

        private fun callUser(def: FuncDef, e: Call, caller: Scope): Any? =
            invokeUser(def, e.args.map { eval(it, caller) }, e, caller)

        private fun invokeUser(def: FuncDef, positional: List<Any?>, e: Call, caller: Scope): Any? {
            if (callPath.count { it == '/' } > 24) throw PineException("Function recursion is too deep.", e.line)
            val values = def.params.mapIndexed { i, (pname, default) ->
                when {
                    e.named.containsKey(pname) -> eval(e.named.getValue(pname), caller)
                    i < positional.size -> positional[i]
                    default != null -> eval(default, caller)
                    else -> null
                }
            }
            val previous = callPath
            callPath = if (previous.isEmpty()) "f${e.id}" else "$previous/f${e.id}"
            try {
                val scope = functionScopes.getOrPut(callPath) { Scope(global) }
                def.params.forEachIndexed { i, (pname, _) ->
                    val variable = scope.vars.getOrPut(pname) { Variable(n, false) }
                    variable.values[bar] = values[i]
                }
                // Local (non-persistent) declarations are recreated every bar; persistent ones carry over.
                return runBlock(def.body, scope)
            } finally {
                callPath = previous
            }
        }

        private fun input(e: Call, scope: Scope): Any? {
            val value = argument(e, 0, "defval", scope)
            if (e.callee == "input.source" || (e.callee == "input" && e.named["type"]?.let { (it as? Name)?.name } == "input.source"))
                return value
            return value
        }

        private fun timestamp(e: Call, scope: Scope): Double? {
            val numbers = e.args.map { eval(it, scope) }.filterIsInstance<Double>()
            if (numbers.size < 3) return null
            val dt = java.time.ZonedDateTime.of(numbers[0].toInt(), numbers[1].toInt(), numbers[2].toInt(),
                numbers.getOrElse(3) { 0.0 }.toInt(), numbers.getOrElse(4) { 0.0 }.toInt(),
                numbers.getOrElse(5) { 0.0 }.toInt(), 0, ZoneOffset.UTC)
            return dt.toInstant().toEpochMilli().toDouble()
        }

        // ------------------------------------------------ output functions

        private fun plot(e: Call, scope: Scope): Any? {
            val value = argument(e, 0, "series", scope)
            val styleName = (argument(e, 4, "style", scope) as? String).orEmpty()
            val style = when {
                styleName.contains("histogram") -> PlotStyle.HISTOGRAM
                styleName.contains("columns") -> PlotStyle.COLUMNS
                styleName.contains("circles") -> PlotStyle.CIRCLES
                styleName.contains("cross") -> PlotStyle.CROSS
                styleName.contains("area") -> PlotStyle.AREA
                styleName.contains("step") -> PlotStyle.STEP
                else -> PlotStyle.LINE
            }
            val k = key(e)
            val title = (argument(e, 1, "title", scope) as? String)?.take(60)
            val st = plots.getOrPut(k) { PlotState(title ?: "Plot ${plots.size + 1}", style, if (overlay) Pane.PRICE else Pane.INDICATOR, n, "", offsetOf(e, scope)) }
            val v = when (value) { is Boolean -> if (value) 1.0 else 0.0; else -> num(value) }
            st.values[bar] = v.takeIf { it.isFinite() }
            st.colors[bar] = colorOf(argument(e, 2, "color", scope)) ?: if (has(e, 2, "color")) null else defaultPalette(plots.size - 1)
            return PlotRef(k)
        }

        private fun plotShape(e: Call, scope: Scope, isChar: Boolean): Any? {
            val condition = argument(e, 0, "series", scope)
            val title = (argument(e, 1, "title", scope) as? String)?.take(60)
            val styleName = if (isChar) "" else (argument(e, 2, "style", scope) as? String).orEmpty()
            val location = (argument(e, if (isChar) 3 else 3, "location", scope) as? String) ?: "location.abovebar"
            val text = ((argument(e, -1, "text", scope) as? String) ?: if (isChar) (argument(e, 2, "char", scope) as? String).orEmpty() else "").take(12)
            val down = styleName.contains("down") || (!styleName.contains("up") && location.contains("above"))
            val style = if (styleName.contains("up") || styleName.contains("down") || location.contains("above") || location.contains("below"))
                (if (down) PlotStyle.MARKER_DOWN else PlotStyle.MARKER_UP) else PlotStyle.MARKER
            val k = key(e)
            val st = plots.getOrPut(k) { PlotState(title ?: "Signal ${plots.size + 1}", style,
                if (location.contains("absolute") && !overlay) Pane.INDICATOR else Pane.PRICE, n, text, offsetOf(e, scope)) }
            val active = when (condition) {
                is Boolean -> condition
                is Double -> !condition.isNaN() && (location.contains("absolute") || condition != 0.0)
                null -> false
                else -> true
            }
            val c = candles[bar]
            st.values[bar] = if (!active) null else when {
                location.contains("belowbar") || location.contains("bottom") -> c.low
                location.contains("absolute") -> (condition as? Double)?.takeIf { it.isFinite() } ?: c.close
                else -> c.high
            }
            st.colors[bar] = colorOf(argument(e, 4, "color", scope)) ?: 0xFF2962FFL
            return null
        }

        private fun plotArrow(e: Call, scope: Scope): Any? {
            val v = num(argument(e, 0, "series", scope))
            val k = key(e)
            val title = (argument(e, 1, "title", scope) as? String) ?: "Arrows"
            val up = plots.getOrPut("$k+") { PlotState("$title ▲", PlotStyle.MARKER_UP, Pane.PRICE, n, "") }
            val down = plots.getOrPut("$k-") { PlotState("$title ▼", PlotStyle.MARKER_DOWN, Pane.PRICE, n, "") }
            up.values[bar] = if (v > 0) candles[bar].low else null
            down.values[bar] = if (v < 0) candles[bar].high else null
            up.colors[bar] = colorOf(argument(e, 2, "colorup", scope)) ?: 0xFF4CAF50L
            down.colors[bar] = colorOf(argument(e, 3, "colordown", scope)) ?: 0xFFFF5252L
            return null
        }

        // ------------------------------------------------ strategy simulation

        private fun strategyFn(name: String, e: Call, scope: Scope): Any? {
            if (name == "strategy") return call(Call("indicator", e.args, e.named).also { it.id = e.id; it.line = e.line }, scope).also { isStrategy = true }
            isStrategy = true
            when (name) {
                "strategy.entry", "strategy.order" -> {
                    if (has(e, -1, "when") && !truthy(argument(e, -1, "when", scope))) return null
                    val id = argument(e, 0, "id", scope)?.toString() ?: "Entry"
                    val dir = argument(e, 1, "direction", scope)?.toString().orEmpty()
                    val direction = if (dir.contains("short") || dir == "false") -1 else 1
                    pendingOrders += Order(id, direction)
                    signals += Signal(bar, if (direction > 0) SignalKind.LONG_ENTRY else SignalKind.SHORT_ENTRY, id)
                }
                "strategy.close" -> {
                    if (has(e, -1, "when") && !truthy(argument(e, -1, "when", scope))) return null
                    pendingClose = argument(e, 0, "id", scope)?.toString() ?: "*"
                    if (position != 0) signals += Signal(bar, SignalKind.EXIT, pendingClose ?: "Close")
                }
                "strategy.close_all" -> {
                    if (has(e, -1, "when") && !truthy(argument(e, -1, "when", scope))) return null
                    pendingClose = "*"
                    if (position != 0) signals += Signal(bar, SignalKind.EXIT, "Close all")
                }
                "strategy.exit" -> {
                    val from = argument(e, 1, "from_entry", scope)?.toString()
                    var stop = num(argument(e, -1, "stop", scope))
                    var limit = num(argument(e, -1, "limit", scope))
                    val lossTicks = num(argument(e, -1, "loss", scope))
                    val profitTicks = num(argument(e, -1, "profit", scope))
                    if (position != 0) {
                        if (stop.isNaN() && !lossTicks.isNaN()) stop = positionEntry - position * lossTicks * 0.01
                        if (limit.isNaN() && !profitTicks.isNaN()) limit = positionEntry + position * profitTicks * 0.01
                    }
                    exitRules.removeAll { it.fromEntry == from }
                    exitRules += ExitRule(from, stop.takeIf { !it.isNaN() }, limit.takeIf { !it.isNaN() })
                }
                "strategy.cancel", "strategy.cancel_all" -> pendingOrders.clear()
                "strategy.risk.allow_entry_in", "strategy.risk.max_drawdown", "strategy.risk.max_intraday_loss",
                "strategy.risk.max_cons_loss_days", "strategy.risk.max_intraday_filled_orders", "strategy.risk.max_position_size" -> Unit
                "strategy.closedtrades.profit", "strategy.closedtrades.entry_price", "strategy.closedtrades.exit_price",
                "strategy.opentrades.entry_price", "strategy.opentrades.profit" -> return null
                else -> throw PineException("$name is not supported by the local preview.", e.line)
            }
            return null
        }

        /** Orders placed on bar N fill at the open of bar N+1, TradingView's default behaviour. */
        private fun fillOrders() {
            val c = candles[bar]
            if (position != 0 && bar > positionBar) {
                for (rule in exitRules) {
                    if (rule.fromEntry != null && rule.fromEntry != positionLabel) continue
                    val stopHit = rule.stop?.let { if (position > 0) c.low <= it else c.high >= it } == true
                    val limitHit = rule.limit?.let { if (position > 0) c.high >= it else c.low <= it } == true
                    if (stopHit || limitHit) {
                        val price = if (stopHit) (if (position > 0) min(c.open, rule.stop!!) else max(c.open, rule.stop!!))
                            else (if (position > 0) max(c.open, rule.limit!!) else min(c.open, rule.limit!!))
                        closePosition(bar, price)
                        signals += Signal(bar, SignalKind.EXIT, if (stopHit) "Stop" else "Target")
                        break
                    }
                }
            }
            if (bar == 0) { pendingOrders.clear(); pendingClose = null; return }
            pendingClose?.let { id ->
                if (position != 0 && (id == "*" || id == positionLabel)) closePosition(bar, c.open)
                pendingClose = null
            }
            val order = pendingOrders.lastOrNull()
            pendingOrders.clear()
            if (order != null && order.direction != position) {
                if (position != 0) closePosition(bar, c.open)
                position = order.direction
                positionEntry = c.open
                positionBar = bar
                positionLabel = order.id
            }
        }

        private fun closePosition(at: Int, price: Double) {
            trades += Trade(position, positionBar, positionEntry, at, price, positionLabel)
            if (positionEntry > 0) realizedEquity *= 1 + (price - positionEntry) / positionEntry * position
            position = 0
            exitRules.clear()
        }

        private fun buildBacktest(): Backtest {
            val all = trades.toMutableList()
            if (position != 0) all += Trade(position, positionBar, positionEntry, null, null, positionLabel)
            val closed = all.filter { it.exitPrice != null }
            val returns = closed.mapNotNull { it.returnPercent }
            var equity = 1.0
            var peak = 1.0
            var drawdown = 0.0
            for (r in returns) {
                equity *= 1 + r / 100.0
                peak = max(peak, equity)
                drawdown = max(drawdown, (peak - equity) / peak * 100.0)
            }
            val gains = returns.filter { it > 0 }.sum()
            val losses = -returns.filter { it < 0 }.sum()
            return Backtest(all, closed.size,
                if (closed.isEmpty()) null else returns.count { it > 0 } * 100.0 / closed.size,
                (equity - 1.0) * 100.0, drawdown, if (losses > 0) gains / losses else null)
        }

        // ------------------------------------------------ library: ta.*

        private fun src(e: Call, index: Int, name: String, scope: Scope): Double = num(argument(e, index, name, scope))

        private fun technical(fn: String, e: Call, scope: Scope): Any? {
            val st = stateFor(e)
            return when (fn) {
                "sma" -> { st.push(bar, src(e, 0, "source", scope)); st.window(bar, intArg(e, 1, "length", scope)).let { w -> w?.average() } }
                "ema" -> {
                    val v = src(e, 0, "source", scope)
                    val alpha = 2.0 / (intArg(e, 1, "length", scope) + 1)
                    recursive(st, v) { prev -> if (prev.isNaN()) v else alpha * v + (1 - alpha) * prev }
                }
                "rma" -> rma(st, src(e, 0, "source", scope), intArg(e, 1, "length", scope))
                "wma" -> { st.push(bar, src(e, 0, "source", scope)); wma(st.window(bar, intArg(e, 1, "length", scope))) }
                "vwma" -> {
                    val len = intArg(e, 1, "length", scope)
                    val v = src(e, 0, "source", scope)
                    st.push(bar, v * candles[bar].volume)
                    st.push2(bar, candles[bar].volume)
                    val a = st.window(bar, len) ?: return null
                    val b = st.window2(bar, len) ?: return null
                    if (b.sum() == 0.0) null else a.sum() / b.sum()
                }
                "hma" -> {
                    val len = intArg(e, 1, "length", scope)
                    st.push(bar, src(e, 0, "source", scope))
                    val half = wma(st.window(bar, max(1, len / 2)))
                    val full = wma(st.window(bar, len))
                    st.push2(bar, if (half == null || full == null) Double.NaN else 2 * half - full)
                    wma(st.window2(bar, max(1, sqrt(len.toDouble()).toInt())))
                }
                "alma" -> {
                    val len = intArg(e, 1, "length", scope)
                    val offset = numArg(e, 2, "offset", scope, 0.85)
                    val sigma = numArg(e, 3, "sigma", scope, 6.0)
                    st.push(bar, src(e, 0, "source", scope))
                    val w = st.window(bar, len) ?: return null
                    val m = offset * (len - 1)
                    val s = len / sigma
                    var norm = 0.0
                    var sum = 0.0
                    for (i in 0 until len) {
                        val weight = exp(-((i - m) * (i - m)) / (2 * s * s))
                        norm += weight
                        sum += w[i] * weight
                    }
                    sum / norm
                }
                "swma" -> { st.push(bar, src(e, 0, "source", scope)); st.window(bar, 4)?.let { (it[0] + 2 * it[1] + 2 * it[2] + it[3]) / 6 } }
                "stdev", "dev", "variance" -> {
                    st.push(bar, src(e, 0, "source", scope))
                    val w = st.window(bar, intArg(e, 1, "length", scope)) ?: return null
                    val mean = w.average()
                    when (fn) {
                        "dev" -> w.sumOf { abs(it - mean) } / w.size
                        "variance" -> w.sumOf { (it - mean) * (it - mean) } / w.size
                        else -> sqrt(w.sumOf { (it - mean) * (it - mean) } / w.size)
                    }
                }
                "highest", "lowest", "highestbars", "lowestbars" -> {
                    val single = e.args.size + e.named.size == 1
                    val len = if (single) intArg(e, 0, "length", scope) else intArg(e, 1, "length", scope)
                    val v = if (single) seriesAt(if (fn.startsWith("highest")) "high" else "low", bar) ?: Double.NaN
                        else src(e, 0, "source", scope)
                    st.push(bar, v)
                    val w = st.window(bar, len) ?: return null
                    val best = if (fn.startsWith("highest")) w.maxOrNull()!! else w.minOrNull()!!
                    if (fn.endsWith("bars")) -(w.size - 1 - w.indices.last { w[it] == best }).toDouble() else best
                }
                "range" -> { st.push(bar, src(e, 0, "source", scope)); st.window(bar, intArg(e, 1, "length", scope))?.let { it.max() - it.min() } }
                "change" -> {
                    val v = argument(e, 0, "source", scope)
                    val len = if (has(e, 1, "length")) intArg(e, 1, "length", scope) else 1
                    if (v is Boolean) {
                        st.pushAny(bar, v)
                        val prev = st.anyAt(bar - len)
                        if (prev == null) null else v != prev
                    } else {
                        st.push(bar, num(v))
                        st.at(bar - len).let { p -> if (p.isNaN()) null else num(v) - p }
                    }
                }
                "mom" -> { val v = src(e, 0, "source", scope); st.push(bar, v); st.at(bar - intArg(e, 1, "length", scope)).let { if (it.isNaN()) null else v - it } }
                "roc" -> { val v = src(e, 0, "source", scope); st.push(bar, v); st.at(bar - intArg(e, 1, "length", scope)).let { if (it.isNaN() || it == 0.0) null else 100 * (v - it) / it } }
                "rsi" -> {
                    val v = src(e, 0, "source", scope)
                    val len = intArg(e, 1, "length", scope)
                    st.push(bar, v)
                    val prev = st.at(bar - 1)
                    val up = if (prev.isNaN()) Double.NaN else max(v - prev, 0.0)
                    val down = if (prev.isNaN()) Double.NaN else max(prev - v, 0.0)
                    val u = rma(st.child("u"), up, len)
                    val d = rma(st.child("d"), down, len)
                    when {
                        u == null || d == null -> null
                        d == 0.0 -> 100.0
                        u == 0.0 -> 0.0
                        else -> 100 - 100 / (1 + u / d)
                    }
                }
                "atr" -> rma(st, trueRange(bar, true) ?: Double.NaN, intArg(e, 0, "length", scope))
                "tr" -> trueRange(bar, truthy(argument(e, 0, "handle_na", scope) ?: false))
                "cci" -> {
                    val v = src(e, 0, "source", scope)
                    st.push(bar, v)
                    val w = st.window(bar, intArg(e, 1, "length", scope)) ?: return null
                    val mean = w.average()
                    val dev = w.sumOf { abs(it - mean) } / w.size
                    if (dev == 0.0) 0.0 else (v - mean) / (0.015 * dev)
                }
                "mfi" -> {
                    val v = src(e, 0, "source", scope)
                    val len = intArg(e, 1, "length", scope)
                    st.push(bar, v)
                    val prev = st.at(bar - 1)
                    val flow = v * candles[bar].volume
                    st.push2(bar, if (prev.isNaN()) Double.NaN else if (v > prev) flow else 0.0)
                    st.child("neg").push(bar, if (prev.isNaN()) Double.NaN else if (v < prev) flow else 0.0)
                    val pos = st.window2(bar, len)?.sum() ?: return null
                    val neg = st.child("neg").window(bar, len)?.sum() ?: return null
                    if (neg == 0.0) 100.0 else 100 - 100 / (1 + pos / neg)
                }
                "wpr" -> {
                    val len = intArg(e, 0, "length", scope)
                    st.push(bar, candles[bar].high); st.push2(bar, candles[bar].low)
                    val h = st.window(bar, len)?.max() ?: return null
                    val l = st.window2(bar, len)?.min() ?: return null
                    if (h == l) null else 100 * (candles[bar].close - h) / (h - l)
                }
                "stoch" -> {
                    val v = src(e, 0, "source", scope)
                    st.push(bar, num(argument(e, 1, "high", scope)))
                    st.push2(bar, num(argument(e, 2, "low", scope)))
                    val len = intArg(e, 3, "length", scope)
                    val h = st.window(bar, len)?.max() ?: return null
                    val l = st.window2(bar, len)?.min() ?: return null
                    if (h == l) null else 100 * (v - l) / (h - l)
                }
                "macd" -> {
                    val v = src(e, 0, "source", scope)
                    val fast = intArg(e, 1, "fastlen", scope)
                    val slow = intArg(e, 2, "slowlen", scope)
                    val signal = intArg(e, 3, "siglen", scope)
                    val f = emaStep(st.child("f"), v, fast)
                    val s = emaStep(st.child("s"), v, slow)
                    val macd = if (f.isNaN() || s.isNaN()) Double.NaN else f - s
                    val sig = emaStep(st.child("g"), macd, signal)
                    TupleValue(listOf(macd.orNull(), sig.orNull(), if (macd.isNaN() || sig.isNaN()) null else macd - sig))
                }
                "bb", "bbw", "kc", "kcw" -> {
                    val v = src(e, 0, "source", scope)
                    val len = intArg(e, 1, "length", scope)
                    val mult = numArg(e, 2, "mult", scope)
                    val (mid, width) = if (fn.startsWith("bb")) {
                        st.push(bar, v)
                        val w = st.window(bar, len) ?: return if (fn == "bb") TupleValue(listOf(null, null, null)) else null
                        val mean = w.average()
                        mean to mult * sqrt(w.sumOf { (it - mean) * (it - mean) } / w.size)
                    } else {
                        val mid = emaStep(st.child("m"), v, len)
                        val range = rma(st.child("r"), trueRange(bar, true) ?: Double.NaN, len) ?: Double.NaN
                        mid to mult * range
                    }
                    when (fn) {
                        "bb", "kc" -> if (mid.isNaN() || width.isNaN()) TupleValue(listOf(null, null, null))
                            else TupleValue(listOf(mid, mid + width, mid - width))
                        else -> if (mid == 0.0 || mid.isNaN() || width.isNaN()) null else 2 * width / mid
                    }
                }
                "supertrend" -> {
                    val factor = numArg(e, 0, "factor", scope)
                    val period = intArg(e, 1, "atrPeriod", scope)
                    val atr = rma(st.child("atr"), trueRange(bar, true) ?: Double.NaN, period)
                    val c = candles[bar]
                    if (atr == null) return TupleValue(listOf(null, null))
                    val hl2 = (c.high + c.low) / 2
                    var upper = hl2 + factor * atr
                    var lower = hl2 - factor * atr
                    val prevUpper = st.extra["upper"] ?: Double.NaN
                    val prevLower = st.extra["lower"] ?: Double.NaN
                    val prevClose = seriesAt("close", bar - 1) ?: Double.NaN
                    lower = if (!prevLower.isNaN() && (lower > prevLower || prevClose < prevLower)) lower else if (prevLower.isNaN()) lower else prevLower
                    upper = if (!prevUpper.isNaN() && (upper < prevUpper || prevClose > prevUpper)) upper else if (prevUpper.isNaN()) upper else prevUpper
                    val prevSt = st.extra["st"] ?: Double.NaN
                    val direction = when {
                        prevSt.isNaN() -> 1.0
                        prevSt == prevUpper -> if (c.close > upper) -1.0 else 1.0
                        else -> if (c.close < lower) 1.0 else -1.0
                    }
                    val value = if (direction < 0) lower else upper
                    st.extra["upper"] = upper; st.extra["lower"] = lower; st.extra["st"] = value
                    TupleValue(listOf(value, direction))
                }
                "dmi" -> {
                    val diLen = intArg(e, 0, "diLength", scope)
                    val adxLen = intArg(e, 1, "adxSmoothing", scope)
                    val c = candles[bar]
                    val p = candles.getOrNull(bar - 1)
                    val up = if (p == null) Double.NaN else c.high - p.high
                    val down = if (p == null) Double.NaN else p.low - c.low
                    val plusDm = if (up.isNaN()) Double.NaN else if (up > down && up > 0) up else 0.0
                    val minusDm = if (down.isNaN()) Double.NaN else if (down > up && down > 0) down else 0.0
                    val tr = rma(st.child("tr"), trueRange(bar, false) ?: Double.NaN, diLen)
                    val plus = rma(st.child("p"), plusDm, diLen)?.let { if (tr == null || tr == 0.0) null else 100 * it / tr }
                    val minus = rma(st.child("m"), minusDm, diLen)?.let { if (tr == null || tr == 0.0) null else 100 * it / tr }
                    val dx = if (plus == null || minus == null || plus + minus == 0.0) Double.NaN else 100 * abs(plus - minus) / (plus + minus)
                    TupleValue(listOf(plus, minus, rma(st.child("adx"), dx, adxLen)))
                }
                "crossover", "crossunder", "cross" -> {
                    val a = num(argument(e, 0, "source1", scope))
                    val b = num(argument(e, 1, "source2", scope))
                    val pa = st.at(bar - 1); val pb = st.at2(bar - 1)
                    st.push(bar, a); st.push2(bar, b)
                    if (a.isNaN() || b.isNaN() || pa.isNaN() || pb.isNaN()) false else when (fn) {
                        "crossover" -> a > b && pa <= pb
                        "crossunder" -> a < b && pa >= pb
                        else -> (a > b && pa <= pb) || (a < b && pa >= pb)
                    }
                }
                "rising", "falling" -> {
                    val v = src(e, 0, "source", scope)
                    val len = intArg(e, 1, "length", scope)
                    st.push(bar, v)
                    val w = st.window(bar, len + 1) ?: return false
                    (0 until len).all { i -> if (fn == "rising") w[len] > w[i] else w[len] < w[i] }
                }
                "cum" -> { val v = src(e, 0, "source", scope); val prev = st.extra["sum"] ?: 0.0; val sum = prev + (if (v.isNaN()) 0.0 else v); st.extra["sum"] = sum; sum }
                "valuewhen" -> {
                    val cond = truthy(argument(e, 0, "condition", scope))
                    val v = argument(e, 1, "source", scope)
                    val occurrence = if (has(e, 2, "occurrence")) intArg(e, 2, "occurrence", scope) else 0
                    @Suppress("UNCHECKED_CAST")
                    val list = st.extraAny.getOrPut("list") { mutableListOf<Any?>() } as MutableList<Any?>
                    if (cond) list.add(0, v)
                    if (list.size > 64) list.removeAt(list.lastIndex)
                    list.getOrNull(occurrence)
                }
                "barssince" -> {
                    if (truthy(argument(e, 0, "condition", scope))) st.extra["last"] = bar.toDouble()
                    st.extra["last"]?.let { bar - it }
                }
                "pivothigh", "pivotlow" -> {
                    val twoArgs = e.args.size + e.named.size == 2
                    val left = intArg(e, if (twoArgs) 0 else 1, "leftbars", scope)
                    val right = intArg(e, if (twoArgs) 1 else 2, "rightbars", scope)
                    val v = if (twoArgs) seriesAt(if (fn == "pivothigh") "high" else "low", bar) ?: Double.NaN
                        else src(e, 0, "source", scope)
                    st.push(bar, v)
                    val w = st.window(bar, left + right + 1) ?: return null
                    val center = w[left]
                    val ok = w.withIndex().all { (i, x) -> i == left || (if (fn == "pivothigh") x < center else x > center) }
                    if (ok) center else null
                }
                "linreg" -> {
                    val len = intArg(e, 1, "length", scope)
                    val offset = if (has(e, 2, "offset")) intArg(e, 2, "offset", scope) else 0
                    st.push(bar, src(e, 0, "source", scope))
                    val w = st.window(bar, len) ?: return null
                    val xMean = (len - 1) / 2.0
                    val yMean = w.average()
                    var num = 0.0; var den = 0.0
                    w.forEachIndexed { i, y -> num += (i - xMean) * (y - yMean); den += (i - xMean) * (i - xMean) }
                    val slope = if (den == 0.0) 0.0 else num / den
                    val intercept = yMean - slope * xMean
                    intercept + slope * (len - 1 - offset)
                }
                "median" -> { st.push(bar, src(e, 0, "source", scope)); st.window(bar, intArg(e, 1, "length", scope))?.sorted()?.let { s -> if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2 } }
                "percentrank" -> {
                    val v = src(e, 0, "source", scope)
                    st.push(bar, v)
                    val len = intArg(e, 1, "length", scope)
                    val w = st.window(bar, len + 1) ?: return null
                    w.dropLast(1).count { it <= v } * 100.0 / len
                }
                "correlation" -> {
                    val len = intArg(e, 2, "length", scope)
                    st.push(bar, src(e, 0, "source1", scope)); st.push2(bar, src(e, 1, "source2", scope))
                    val a = st.window(bar, len) ?: return null
                    val b = st.window2(bar, len) ?: return null
                    val ma = a.average(); val mb = b.average()
                    var cov = 0.0; var va = 0.0; var vb = 0.0
                    for (i in a.indices) { cov += (a[i] - ma) * (b[i] - mb); va += (a[i] - ma).pow(2); vb += (b[i] - mb).pow(2) }
                    if (va == 0.0 || vb == 0.0) null else cov / sqrt(va * vb)
                }
                "cmo" -> {
                    val v = src(e, 0, "source", scope)
                    val len = intArg(e, 1, "length", scope)
                    st.push(bar, v)
                    val prev = st.at(bar - 1)
                    st.push2(bar, if (prev.isNaN()) Double.NaN else v - prev)
                    val w = st.window2(bar, len) ?: return null
                    val up = w.filter { it > 0 }.sum(); val down = -w.filter { it < 0 }.sum()
                    if (up + down == 0.0) 0.0 else 100 * (up - down) / (up + down)
                }
                "tsi" -> {
                    val v = src(e, 0, "source", scope)
                    val short = intArg(e, 1, "short_length", scope)
                    val long = intArg(e, 2, "long_length", scope)
                    st.push(bar, v)
                    val prev = st.at(bar - 1)
                    val m = if (prev.isNaN()) Double.NaN else v - prev
                    val a = emaStep(st.child("a2"), emaStep(st.child("a1"), m, long), short)
                    val b = emaStep(st.child("b2"), emaStep(st.child("b1"), abs(m), long), short)
                    if (a.isNaN() || b.isNaN() || b == 0.0) null else a / b
                }
                "sar" -> {
                    note(e.line, "ta.sar is approximated in the local preview.", Severity.INFO)
                    val start = numArg(e, 0, "start", scope); val inc = numArg(e, 1, "inc", scope); val maximum = numArg(e, 2, "max", scope)
                    val c = candles[bar]
                    if (bar == 0) { st.extra["sar"] = c.low; st.extra["ep"] = c.high; st.extra["af"] = start; st.extra["up"] = 1.0; return null }
                    var sar = st.extra["sar"]!!; var ep = st.extra["ep"]!!; var af = st.extra["af"]!!; var up = st.extra["up"]!! > 0
                    sar += af * (ep - sar)
                    if (up) {
                        if (c.low < sar) { up = false; sar = ep; ep = c.low; af = start }
                        else if (c.high > ep) { ep = c.high; af = min(af + inc, maximum) }
                    } else {
                        if (c.high > sar) { up = true; sar = ep; ep = c.high; af = start }
                        else if (c.low < ep) { ep = c.low; af = min(af + inc, maximum) }
                    }
                    st.extra["sar"] = sar; st.extra["ep"] = ep; st.extra["af"] = af; st.extra["up"] = if (up) 1.0 else -1.0
                    sar
                }
                "vwap" -> vwap(key(e), src(e, 0, "source", scope))
                "obv" -> obv()
                else -> throw PineException("ta.$fn is not supported by the local preview.", e.line)
            }
        }

        private fun Double.orNull(): Double? = if (isNaN()) null else this

        private fun recursive(st: CallState, v: Double, step: (Double) -> Double): Double? {
            if (v.isNaN()) return st.out.getOrNull(bar - 1)?.takeIf { !it.isNaN() }?.also { st.out[bar] = it }
            val prev = if (bar > 0) st.out[bar - 1] else Double.NaN
            val next = step(prev)
            st.out[bar] = next
            return next.orNull()
        }

        private fun emaStep(st: CallState, v: Double, len: Int): Double {
            if (st.lastBar == bar) return st.out[bar]
            st.lastBar = bar
            val alpha = 2.0 / (len + 1)
            val prev = if (bar > 0) st.out[bar - 1] else Double.NaN
            st.out[bar] = when {
                v.isNaN() -> prev
                prev.isNaN() -> v
                else -> alpha * v + (1 - alpha) * prev
            }
            return st.out[bar]
        }

        /** Pine's ta.rma: seeded with the SMA of the first `length` values. */
        private fun rma(st: CallState, v: Double, len: Int): Double? {
            if (len <= 0) return null
            st.push(bar, v)
            val prev = if (bar > 0) st.out[bar - 1] else Double.NaN
            val value = if (prev.isNaN()) st.window(bar, len)?.average() ?: Double.NaN
                else if (v.isNaN()) prev else (v + (len - 1) * prev) / len
            st.out[bar] = value
            return value.orNull()
        }

        private fun wma(w: DoubleArray?): Double? {
            w ?: return null
            var norm = 0.0
            var sum = 0.0
            for (i in w.indices) { val weight = (i + 1).toDouble(); norm += weight; sum += w[i] * weight }
            return sum / norm
        }

        private fun trueRange(index: Int, handleNa: Boolean): Double? {
            val c = candles[index]
            val prev = candles.getOrNull(index - 1) ?: return if (handleNa) c.high - c.low else null
            return max(c.high - c.low, max(abs(c.high - prev.close), abs(c.low - prev.close)))
        }

        private fun obv(): Double {
            val st = state.getOrPut("obv") { CallState(n) } as CallState
            if (st.lastBar != bar) {
                st.lastBar = bar
                val prev = if (bar > 0) st.out[bar - 1] else 0.0
                val c = candles[bar]
                val p = candles.getOrNull(bar - 1)
                st.out[bar] = prev + when { p == null -> 0.0; c.close > p.close -> c.volume; c.close < p.close -> -c.volume; else -> 0.0 }
            }
            return st.out[bar]
        }

        private fun accdist(): Double {
            val st = state.getOrPut("accdist") { CallState(n) } as CallState
            if (st.lastBar != bar) {
                st.lastBar = bar
                val c = candles[bar]
                val mfm = if (c.high == c.low) 0.0 else ((c.close - c.low) - (c.high - c.close)) / (c.high - c.low)
                st.out[bar] = (if (bar > 0) st.out[bar - 1] else 0.0) + mfm * c.volume
            }
            return st.out[bar]
        }

        /** Volume-based built-ins that Pine exposes as plain variables, computed per the Pine reference definitions. */
        private fun volumeIndicator(kind: String): Double {
            val st = state.getOrPut(kind) { CallState(n) } as CallState
            if (st.lastBar != bar) {
                st.lastBar = bar
                val c = candles[bar]
                val p = candles.getOrNull(bar - 1)
                val range = c.high - c.low
                val prev = if (bar > 0) st.out[bar - 1] else if (kind == "nvi" || kind == "pvi") 1.0 else 0.0
                st.out[bar] = when (kind) {
                    "pvt" -> if (p == null || p.close == 0.0) prev else prev + (c.close - p.close) / p.close * c.volume
                    "nvi" -> if (p != null && p.close != 0.0 && c.volume < p.volume) prev + prev * (c.close - p.close) / p.close else prev
                    "pvi" -> if (p != null && p.close != 0.0 && c.volume > p.volume) prev + prev * (c.close - p.close) / p.close else prev
                    "wad" -> if (p == null) prev else {
                        val trueHigh = maxOf(c.high, p.close)
                        val trueLow = minOf(c.low, p.close)
                        prev + when {
                            c.close > p.close -> c.close - trueLow
                            c.close < p.close -> c.close - trueHigh
                            else -> 0.0
                        }
                    }
                    "iii" -> if (range == 0.0 || c.volume == 0.0) prev else prev + (2 * c.close - c.high - c.low) / (range * c.volume)
                    else -> if (range == 0.0) 0.0 else (c.close - c.open) / range * c.volume
                }
            }
            return st.out[bar]
        }

        /** Session VWAP anchored to each UTC day. */
        private fun vwap(stateKey: String, value: Double?): Double? {
            val st = state.getOrPut("vwap|$stateKey") { CallState(n) } as CallState
            if (st.lastBar == bar) return st.out[bar].orNull()
            st.lastBar = bar
            val c = candles[bar]
            val day = c.epochSeconds / 86400
            if ((st.extra["day"] ?: -1.0) != day.toDouble()) { st.extra["pv"] = 0.0; st.extra["v"] = 0.0; st.extra["day"] = day.toDouble() }
            val v = value ?: return null
            st.extra["pv"] = st.extra["pv"]!! + v * c.volume
            st.extra["v"] = st.extra["v"]!! + c.volume
            st.out[bar] = if (st.extra["v"]!! == 0.0) Double.NaN else st.extra["pv"]!! / st.extra["v"]!!
            return st.out[bar].orNull()
        }

        // ------------------------------------------------ library: math, color, str

        private fun mathFn(fn: String, e: Call, scope: Scope): Any? {
            fun a(i: Int) = num(argument(e, i, listOf("number", "base", "number2", "number3")[i.coerceAtMost(3)], scope))
            val values = { e.args.map { num(eval(it, scope)) } }
            return when (fn) {
                "abs" -> abs(a(0))
                "max" -> values().let { v -> if (v.any { it.isNaN() }) Double.NaN else v.maxOrNull() }
                "min" -> values().let { v -> if (v.any { it.isNaN() }) Double.NaN else v.minOrNull() }
                "avg" -> values().average()
                "sqrt" -> sqrt(a(0))
                "pow" -> num(argument(e, 0, "base", scope)).pow(num(argument(e, 1, "exponent", scope)))
                "log" -> ln(a(0))
                "log10" -> log10(a(0))
                "exp" -> exp(a(0))
                "round" -> {
                    val v = a(0)
                    if (has(e, 1, "precision")) { val p = 10.0.pow(num(argument(e, 1, "precision", scope))); (v * p).roundToLong() / p }
                    else if (v.isNaN()) Double.NaN else v.roundToLong().toDouble()
                }
                "round_to_mintick" -> (a(0) * 100).roundToLong() / 100.0
                "floor" -> floor(a(0))
                "ceil" -> ceil(a(0))
                "sign" -> sign(a(0))
                "sin" -> kotlin.math.sin(a(0)); "cos" -> kotlin.math.cos(a(0)); "tan" -> kotlin.math.tan(a(0))
                "asin" -> kotlin.math.asin(a(0)); "acos" -> kotlin.math.acos(a(0)); "atan" -> kotlin.math.atan(a(0))
                "todegrees" -> Math.toDegrees(a(0)); "toradians" -> Math.toRadians(a(0))
                "sum" -> {
                    val st = stateFor(e)
                    st.push(bar, num(argument(e, 0, "source", scope)))
                    st.window(bar, intArg(e, 1, "length", scope))?.sum()
                }
                "random" -> 0.5
                else -> throw PineException("math.$fn is not supported by the local preview.", e.line)
            }
        }

        private fun colorFn(fn: String, e: Call, scope: Scope): Any? = when (fn) {
            "new" -> {
                val base = colorOf(argument(e, 0, "color", scope)) ?: return null
                val transp = num(argument(e, 1, "transp", scope)).let { if (it.isNaN()) 0.0 else it.coerceIn(0.0, 100.0) }
                PineColor((base and 0x00FFFFFFL) or (((100 - transp) / 100 * 255).roundToLong() shl 24))
            }
            "rgb" -> {
                val r = num(argument(e, 0, "red", scope)); val g = num(argument(e, 1, "green", scope)); val b = num(argument(e, 2, "blue", scope))
                val t = if (has(e, 3, "transp")) num(argument(e, 3, "transp", scope)) else 0.0
                if (listOf(r, g, b).any { it.isNaN() }) null
                else PineColor((((100 - t.coerceIn(0.0, 100.0)) / 100 * 255).roundToLong() shl 24) or
                    (r.toLong().coerceIn(0, 255) shl 16) or (g.toLong().coerceIn(0, 255) shl 8) or b.toLong().coerceIn(0, 255))
            }
            "from_gradient" -> {
                val v = num(argument(e, 0, "value", scope)); val lo = num(argument(e, 1, "bottom_value", scope)); val hi = num(argument(e, 2, "top_value", scope))
                val a = colorOf(argument(e, 3, "bottom_color", scope)); val b = colorOf(argument(e, 4, "top_color", scope))
                if (a == null || b == null) null else {
                    val t = if (hi == lo || v.isNaN()) 0.5 else ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0)
                    PineColor(lerpColor(a, b, t))
                }
            }
            "r" -> colorOf(argument(e, 0, "color", scope))?.let { ((it shr 16) and 0xFF).toDouble() }
            "g" -> colorOf(argument(e, 0, "color", scope))?.let { ((it shr 8) and 0xFF).toDouble() }
            "b" -> colorOf(argument(e, 0, "color", scope))?.let { (it and 0xFF).toDouble() }
            "t" -> colorOf(argument(e, 0, "color", scope))?.let { 100 - ((it shr 24) and 0xFF) * 100.0 / 255 }
            else -> throw PineException("color.$fn is not supported by the local preview.", e.line)
        }

        private fun strFn(fn: String, e: Call, scope: Scope): Any? = when (fn) {
            "tostring" -> PineText.format(argument(e, 0, "value", scope), argument(e, 1, "format", scope) as? String)
            "format" -> PineText.formatString(argument(e, 0, "formatString", scope)?.toString().orEmpty(),
                e.args.drop(1).map { eval(it, scope) })
            "split" -> PineArray(ArrayList(argument(e, 0, "string", scope)?.toString().orEmpty()
                .split(argument(e, 1, "separator", scope)?.toString().orEmpty()).let { parts ->
                    if (argument(e, 1, "separator", scope)?.toString().isNullOrEmpty()) parts.filter { it.isNotEmpty() } else parts }))
            "pos" -> argument(e, 0, "source", scope)?.toString()?.indexOf(argument(e, 1, "str", scope)?.toString().orEmpty())?.takeIf { it >= 0 }?.toDouble()
            "trim" -> argument(e, 0, "source", scope)?.toString()?.trim()
            "repeat" -> argument(e, 0, "source", scope)?.toString()?.let { text ->
                List(num(argument(e, 1, "repeat", scope)).toInt().coerceIn(0, 1000)) { text }
                    .joinToString(argument(e, 2, "separator", scope)?.toString().orEmpty()) }
            "match" -> argument(e, 0, "source", scope)?.toString()?.let { text ->
                runCatching { Regex(argument(e, 1, "regex", scope)?.toString().orEmpty()).find(text)?.value }.getOrNull().orEmpty() }
            "length" -> argument(e, 0, "string", scope)?.toString()?.length?.toDouble()
            "contains" -> argument(e, 0, "source", scope)?.toString()?.contains(argument(e, 1, "str", scope)?.toString().orEmpty())
            "upper" -> argument(e, 0, "source", scope)?.toString()?.uppercase()
            "lower" -> argument(e, 0, "source", scope)?.toString()?.lowercase()
            "tonumber" -> argument(e, 0, "string", scope)?.toString()?.toDoubleOrNull()
            "replace_all", "replace" -> argument(e, 0, "source", scope)?.toString()?.replace(argument(e, 1, "target", scope)?.toString().orEmpty(), argument(e, 2, "replacement", scope)?.toString().orEmpty())
            "startswith" -> argument(e, 0, "source", scope)?.toString()?.startsWith(argument(e, 1, "str", scope)?.toString().orEmpty())
            "endswith" -> argument(e, 0, "source", scope)?.toString()?.endsWith(argument(e, 1, "str", scope)?.toString().orEmpty())
            "substring" -> argument(e, 0, "source", scope)?.toString()?.let { text ->
                val begin = num(argument(e, 1, "begin_pos", scope)).toInt().coerceIn(0, text.length)
                val end = if (has(e, 2, "end_pos")) num(argument(e, 2, "end_pos", scope)).toInt().coerceIn(begin, text.length) else text.length
                text.substring(begin, end)
            }
            else -> null
        }

        // ------------------------------------------------ objects, methods and drawings

        private fun evalArgs(e: Call, scope: Scope, receiver: Any? = Unit): PineArgs {
            val positional = ArrayList<Any?>()
            if (receiver != Unit) positional += receiver
            e.args.forEach { positional += eval(it, scope) }
            return PineArgs(positional, e.named.mapValues { eval(it.value, scope) })
        }

        private fun fieldOf(value: Any?, field: String, line: Int): Any? = when (value) {
            null -> null
            is PineObject -> if (value.fields.containsKey(field)) value.fields[field]
                else throw PineException("${value.type} has no field '$field'.", line)
            is PineLabel, is PineLine, is PineBox -> throw PineException("Use getters such as line.get_x1() to read drawing fields.", line)
            else -> throw PineException("Cannot read '.$field' from this value.", line)
        }

        private fun construct(type: TypeDef, e: Call, scope: Scope): PineObject {
            if (type.isEnum) throw PineException("Enums have no constructor.", e.line)
            val fields = LinkedHashMap<String, Any?>()
            type.fields.forEachIndexed { i, (field, default) ->
                fields[field] = when {
                    e.named.containsKey(field) -> eval(e.named.getValue(field), scope)
                    i < e.args.size -> eval(e.args[i], scope)
                    default != null -> eval(default, scope)
                    else -> null
                }
            }
            return PineObject(type.name, fields)
        }

        /** `receiver.method(args)`: built-in methods of collections and drawings, or a user `method`. */
        private fun invokeMethod(receiver: Any?, method: String, args: List<Expr>, named: Map<String, Expr>, scope: Scope,
                                 line: Int, id: Int): Any? {
            val call = Call(method, args, named).also { it.id = id; it.line = line }
            program.functions[method]?.let { def ->
                if (def.params.isNotEmpty() && (receiver is PineObject || receiver !is PineArray && receiver !is PineMap &&
                        receiver !is PineMatrix && receiver !is PineLabel && receiver !is PineLine && receiver !is PineBox && receiver !is PineTable)) {
                    return invokeUser(def, listOf(receiver) + args.map { eval(it, scope) }, call, scope)
                }
            }
            val evaluated = { evalArgs(call, scope, receiver) }
            return when (receiver) {
                is PineArray -> PineCollections.array(method, evaluated(), line)
                is PineMap -> PineCollections.map(method, evaluated(), line)
                is PineMatrix -> PineCollections.matrix(method, evaluated(), line)
                is PineLabel -> drawing("label.$method", evaluated(), line)
                is PineLine -> drawing("line.$method", evaluated(), line)
                is PineBox -> drawing("box.$method", evaluated(), line)
                is PineTable -> drawing("table.$method", evaluated(), line)
                is PineLinefill -> drawing("linefill.$method", evaluated(), line)
                is PineObject -> when (method) {
                    "copy" -> receiver.copy()
                    else -> program.functions[method]?.let { invokeUser(it, listOf(receiver) + args.map { a -> eval(a, scope) }, call, scope) }
                        ?: throw PineException("${receiver.type} has no method '$method'.", line)
                }
                null -> program.functions[method]?.let { invokeUser(it, listOf(null) + args.map { a -> eval(a, scope) }, call, scope) }
                else -> throw PineException("'.$method()' cannot be called on this value.", line)
            }
        }

        private fun barOfTime(millis: Double): Double {
            if (millis.isNaN() || n == 0) return Double.NaN
            val seconds = (millis / 1000).toLong()
            var lo = 0; var hi = n - 1
            if (seconds > candles[hi].epochSeconds) return hi + (seconds - candles[hi].epochSeconds).toDouble() / timeframeSeconds
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (candles[mid].epochSeconds <= seconds) lo = mid else hi = mid - 1
            }
            return lo.toDouble()
        }

        private fun x(value: Any?, xloc: Any?): Double {
            val v = num(value)
            return if ((xloc as? String)?.contains("bar_time") == true) barOfTime(v) else v
        }

        private fun <T> keep(list: ArrayList<T>, item: T, limit: Int, deleted: (T) -> Boolean, delete: (T) -> Unit): T {
            list += item
            val alive = list.filter { !deleted(it) }
            if (alive.size > limit) alive.take(alive.size - limit).forEach(delete)
            if (list.size > limit * 4) list.removeAll { deleted(it) }
            return item
        }

        private fun point(value: Any?): PineObject? = (value as? PineObject)?.takeIf { it.type == "chart.point" }

        private fun pointX(p: PineObject, xloc: Any?): Double =
            if ((xloc as? String)?.contains("bar_time") == true) barOfTime(num(p.fields["time"])) else num(p.fields["index"])

        private fun drawing(name: String, a: PineArgs, line: Int): Any? {
            val (kind, fn) = name.substringBeforeLast('.') to name.substringAfterLast('.')
            when (kind) {
                "chart.point" -> return when (fn) {
                    "new" -> PineObject("chart.point", linkedMapOf("time" to a[0, "time"], "index" to a[1, "index"], "price" to a[2, "price"]))
                    "from_index" -> PineObject("chart.point", linkedMapOf("time" to seriesAt("time", num(a[0, "index"]).toInt()),
                        "index" to a[0, "index"], "price" to a[1, "price"]))
                    "from_time" -> PineObject("chart.point", linkedMapOf("time" to a[0, "time"], "index" to barOfTime(num(a[0, "time"])), "price" to a[1, "price"]))
                    "now" -> PineObject("chart.point", linkedMapOf("time" to seriesAt("time", bar), "index" to bar.toDouble(), "price" to (a[0, "price"] ?: seriesAt("close", bar))))
                    "copy" -> point(a[0, "id"])?.copy()
                    else -> throw PineException("$name is not supported by the local preview.", line)
                }
                "label" -> {
                    if (fn == "new") {
                        val p = point(a[0, "point"])
                        val shift = if (p != null) 1 else 0
                        val xloc = a[3 - shift, "xloc"]
                        val label = PineLabel(
                            x = if (p != null) pointX(p, xloc) else x(a[0, "x"], xloc),
                            y = if (p != null) num(p.fields["price"]) else num(a[1, "y"]),
                            text = a[2 - shift, "text"]?.let(PineText::of).orEmpty(),
                            color = colorOf(a[5 - shift, "color"]) ?: if (a.has(5 - shift, "color")) null else 0xFF2962FFL,
                            textColor = colorOf(a[7 - shift, "textcolor"]) ?: 0xFFFFFFFFL,
                            style = (a[6 - shift, "style"] as? String) ?: "label.style_label_down",
                            size = (a[8 - shift, "size"] as? String) ?: "size.normal",
                            yloc = (a[4 - shift, "yloc"] as? String) ?: "yloc.price",
                            tooltip = a[10 - shift, "tooltip"]?.toString().orEmpty())
                        return keep(labels, label, maxLabels, { it.deleted }) { it.deleted = true }
                    }
                    if (fn == "all") return PineArray(ArrayList(labels.filter { !it.deleted }))
                    val label = a[0, "id"] as? PineLabel ?: return null
                    when (fn) {
                        "delete" -> label.deleted = true
                        "copy" -> return keep(labels, PineLabel(label.x, label.y, label.text, label.color, label.textColor, label.style,
                            label.size, label.yloc, label.tooltip), maxLabels, { it.deleted }) { it.deleted = true }
                        "set_x" -> label.x = num(a[1, "x"])
                        "set_y" -> label.y = num(a[1, "y"])
                        "set_xy" -> { label.x = num(a[1, "x"]); label.y = num(a[2, "y"]) }
                        "set_point" -> point(a[1, "point"])?.let { label.x = num(it.fields["index"]); label.y = num(it.fields["price"]) }
                        "set_text" -> label.text = a[1, "text"]?.let(PineText::of).orEmpty()
                        "set_color" -> label.color = colorOf(a[1, "color"])
                        "set_textcolor" -> label.textColor = colorOf(a[1, "textcolor"])
                        "set_style" -> label.style = (a[1, "style"] as? String) ?: label.style
                        "set_size" -> label.size = (a[1, "size"] as? String) ?: label.size
                        "set_yloc" -> label.yloc = (a[1, "yloc"] as? String) ?: label.yloc
                        "set_xloc" -> { label.x = x(a[1, "x"], a[2, "xloc"]) }
                        "set_tooltip", "set_textalign", "set_text_font_family", "set_text_formatting" -> Unit
                        "get_x" -> return label.x
                        "get_y" -> return label.y
                        "get_text" -> return label.text
                        else -> throw PineException("$name is not supported by the local preview.", line)
                    }
                    return null
                }
                "line" -> {
                    if (fn == "new") {
                        val p1 = point(a[0, "first_point"])
                        val drawn = if (p1 != null) {
                            val p2 = point(a[1, "second_point"]) ?: throw PineException("line.new() needs two chart points.", line)
                            val xloc = a[2, "xloc"]
                            PineLine(pointX(p1, xloc), num(p1.fields["price"]), pointX(p2, xloc), num(p2.fields["price"]),
                                colorOf(a[4, "color"]) ?: 0xFF2962FFL, num(a[6, "width"]).takeIf { !it.isNaN() } ?: 1.0,
                                (a[5, "style"] as? String) ?: "line.style_solid", (a[3, "extend"] as? String) ?: "extend.none")
                        } else {
                            val xloc = a[4, "xloc"]
                            PineLine(x(a[0, "x1"], xloc), num(a[1, "y1"]), x(a[2, "x2"], xloc), num(a[3, "y2"]),
                                colorOf(a[6, "color"]) ?: 0xFF2962FFL, num(a[8, "width"]).takeIf { !it.isNaN() } ?: 1.0,
                                (a[7, "style"] as? String) ?: "line.style_solid", (a[5, "extend"] as? String) ?: "extend.none")
                        }
                        return keep(drawnLines, drawn, maxLines, { it.deleted }) { it.deleted = true }
                    }
                    if (fn == "all") return PineArray(ArrayList(drawnLines.filter { !it.deleted }))
                    val l = a[0, "id"] as? PineLine ?: return null
                    when (fn) {
                        "delete" -> l.deleted = true
                        "copy" -> return keep(drawnLines, PineLine(l.x1, l.y1, l.x2, l.y2, l.color, l.width, l.style, l.extend),
                            maxLines, { it.deleted }) { it.deleted = true }
                        "set_x1" -> l.x1 = num(a[1, "x"])
                        "set_x2" -> l.x2 = num(a[1, "x"])
                        "set_y1" -> l.y1 = num(a[1, "y"])
                        "set_y2" -> l.y2 = num(a[1, "y"])
                        "set_xy1" -> { l.x1 = num(a[1, "x"]); l.y1 = num(a[2, "y"]) }
                        "set_xy2" -> { l.x2 = num(a[1, "x"]); l.y2 = num(a[2, "y"]) }
                        "set_first_point" -> point(a[1, "point"])?.let { l.x1 = num(it.fields["index"]); l.y1 = num(it.fields["price"]) }
                        "set_second_point" -> point(a[1, "point"])?.let { l.x2 = num(it.fields["index"]); l.y2 = num(it.fields["price"]) }
                        "set_color" -> l.color = colorOf(a[1, "color"])
                        "set_width" -> l.width = num(a[1, "width"]).takeIf { !it.isNaN() } ?: l.width
                        "set_style" -> l.style = (a[1, "style"] as? String) ?: l.style
                        "set_extend" -> l.extend = (a[1, "extend"] as? String) ?: l.extend
                        "set_xloc" -> { l.x1 = x(a[1, "x1"], a[3, "xloc"]); l.x2 = x(a[2, "x2"], a[3, "xloc"]) }
                        "get_x1" -> return l.x1
                        "get_x2" -> return l.x2
                        "get_y1" -> return l.y1
                        "get_y2" -> return l.y2
                        "get_price" -> {
                            val at = num(a[1, "x"])
                            return if (l.x2 == l.x1) l.y1 else l.y1 + (l.y2 - l.y1) * (at - l.x1) / (l.x2 - l.x1)
                        }
                        else -> throw PineException("$name is not supported by the local preview.", line)
                    }
                    return null
                }
                "box" -> {
                    if (fn == "new") {
                        val p1 = point(a[0, "top_left"])
                        val shift = if (p1 != null) 2 else 0
                        val xloc = a[8 - shift, "xloc"]
                        val drawn = if (p1 != null) {
                            val p2 = point(a[1, "bottom_right"]) ?: throw PineException("box.new() needs two chart points.", line)
                            PineBox(pointX(p1, xloc), num(p1.fields["price"]), pointX(p2, xloc), num(p2.fields["price"]),
                                0L, 0L, "", null, "extend.none")
                        } else PineBox(x(a[0, "left"], xloc), num(a[1, "top"]), x(a[2, "right"], xloc), num(a[3, "bottom"]),
                            0L, 0L, "", null, "extend.none")
                        drawn.border = colorOf(a[4 - shift, "border_color"]) ?: if (a.has(4 - shift, "border_color")) null else 0xFF2962FFL
                        drawn.extend = (a[7 - shift, "extend"] as? String) ?: "extend.none"
                        drawn.background = colorOf(a[9 - shift, "bgcolor"]) ?: if (a.has(9 - shift, "bgcolor")) null else 0x332962FFL
                        drawn.text = a[10 - shift, "text"]?.let(PineText::of).orEmpty()
                        drawn.textColor = colorOf(a[12 - shift, "text_color"]) ?: 0xFFFFFFFFL
                        return keep(boxes, drawn, maxBoxes, { it.deleted }) { it.deleted = true }
                    }
                    if (fn == "all") return PineArray(ArrayList(boxes.filter { !it.deleted }))
                    val b = a[0, "id"] as? PineBox ?: return null
                    when (fn) {
                        "delete" -> b.deleted = true
                        "copy" -> return keep(boxes, PineBox(b.left, b.top, b.right, b.bottom, b.border, b.background, b.text,
                            b.textColor, b.extend), maxBoxes, { it.deleted }) { it.deleted = true }
                        "set_left" -> b.left = num(a[1, "left"])
                        "set_right" -> b.right = num(a[1, "right"])
                        "set_top" -> b.top = num(a[1, "top"])
                        "set_bottom" -> b.bottom = num(a[1, "bottom"])
                        "set_lefttop" -> { b.left = num(a[1, "left"]); b.top = num(a[2, "top"]) }
                        "set_rightbottom" -> { b.right = num(a[1, "right"]); b.bottom = num(a[2, "bottom"]) }
                        "set_top_left_point" -> point(a[1, "point"])?.let { b.left = num(it.fields["index"]); b.top = num(it.fields["price"]) }
                        "set_bottom_right_point" -> point(a[1, "point"])?.let { b.right = num(it.fields["index"]); b.bottom = num(it.fields["price"]) }
                        "set_border_color" -> b.border = colorOf(a[1, "color"])
                        "set_bgcolor" -> b.background = colorOf(a[1, "color"])
                        "set_text" -> b.text = a[1, "text"]?.let(PineText::of).orEmpty()
                        "set_text_color" -> b.textColor = colorOf(a[1, "text_color"])
                        "set_extend" -> b.extend = (a[1, "extend"] as? String) ?: b.extend
                        "set_border_width", "set_border_style", "set_text_size", "set_text_halign", "set_text_valign",
                        "set_text_wrap", "set_text_font_family", "set_xloc", "set_text_formatting" -> Unit
                        "get_left" -> return b.left
                        "get_right" -> return b.right
                        "get_top" -> return b.top
                        "get_bottom" -> return b.bottom
                        else -> throw PineException("$name is not supported by the local preview.", line)
                    }
                    return null
                }
                "linefill" -> {
                    if (fn == "new") {
                        val first = a[0, "line1"] as? PineLine ?: return null
                        val second = a[1, "line2"] as? PineLine ?: return null
                        return PineLinefill(first, second, colorOf(a[2, "color"])?.let(::withMinimumTransparency)).also { linefills += it }
                    }
                    val f = a[0, "id"] as? PineLinefill ?: return null
                    when (fn) {
                        "delete" -> f.deleted = true
                        "set_color" -> f.color = colorOf(a[1, "color"])?.let(::withMinimumTransparency)
                        "get_line1" -> return f.first
                        "get_line2" -> return f.second
                        else -> throw PineException("$name is not supported by the local preview.", line)
                    }
                    return null
                }
                "table" -> {
                    if (fn == "new") {
                        val columns = num(a[1, "columns"]).toInt().coerceIn(1, 100)
                        val rows = num(a[2, "rows"]).toInt().coerceIn(1, 100)
                        tables.forEach { if (!it.deleted && tables.size > 8) it.deleted = true }
                        return PineTable((a[0, "position"] as? String) ?: "position.top_right", columns, rows,
                            colorOf(a[3, "bgcolor"]), colorOf(a[4, "frame_color"])).also { tables += it }
                    }
                    val t = a[0, "table_id"] as? PineTable ?: return null
                    fun cellAt(): Pair<Int, Int> = num(a[1, "column"]).toInt() to num(a[2, "row"]).toInt()
                    fun update(change: (TableCell) -> TableCell) {
                        val at = cellAt()
                        t.cells[at] = change(t.cells[at] ?: TableCell("", null, null, "size.normal"))
                    }
                    when (fn) {
                        "cell" -> {
                            val at = cellAt()
                            if (at.first in 0 until t.columns && at.second in 0 until t.rows)
                                t.cells[at] = TableCell(a[3, "text"]?.let(PineText::of).orEmpty(), colorOf(a[10, "bgcolor"]),
                                    colorOf(a[6, "text_color"]) ?: 0xFFFFFFFFL, (a[9, "text_size"] as? String) ?: "size.normal")
                        }
                        "cell_set_text" -> update { it.copy(text = a[3, "text"]?.let(PineText::of).orEmpty()) }
                        "cell_set_bgcolor" -> update { it.copy(background = colorOf(a[3, "bgcolor"])) }
                        "cell_set_text_color" -> update { it.copy(textColor = colorOf(a[3, "text_color"])) }
                        "cell_set_text_size" -> update { it.copy(textSize = (a[3, "text_size"] as? String) ?: it.textSize) }
                        "clear" -> t.cells.clear()
                        "delete" -> t.deleted = true
                        "set_bgcolor" -> t.background = colorOf(a[1, "bgcolor"])
                        "set_frame_color" -> t.frame = colorOf(a[1, "frame_color"])
                        "set_position" -> t.position = (a[1, "position"] as? String) ?: t.position
                        "merge_cells", "cell_set_width", "cell_set_height", "cell_set_text_halign", "cell_set_text_valign",
                        "cell_set_tooltip", "cell_set_text_font_family", "set_border_color", "set_border_width",
                        "set_frame_width", "cell_set_text_formatting" -> Unit
                        else -> throw PineException("$name is not supported by the local preview.", line)
                    }
                    return null
                }
            }
            throw PineException("$name is not supported by the local preview.", line)
        }

        // ------------------------------------------------ primitive helpers

        private fun colorOf(v: Any?): Long? = when (v) {
            is PineColor -> v.argb
            is String -> namedColors[v.removePrefix("color.")]
            else -> null
        }

        private fun withMinimumTransparency(argb: Long): Long {
            val alpha = (argb ushr 24) and 0xFF
            return if (alpha > 0x66) (argb and 0x00FFFFFFL) or (0x40L shl 24) else argb
        }

        private fun lerpColor(a: Long, b: Long, t: Double): Long {
            fun ch(c: Long, s: Int) = ((c shr s) and 0xFF).toDouble()
            fun mix(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).toLong().coerceIn(0, 255) shl s
            return mix(24) or mix(16) or mix(8) or mix(0)
        }

        private fun defaultPalette(index: Int): Long = listOf(0xFF2962FFL, 0xFFFF9800L, 0xFFE040FBL, 0xFF00BCD4L,
            0xFF4CAF50L, 0xFFFFEB3BL, 0xFFFF5252L)[index.coerceAtLeast(0) % 7]

        private fun isNa(v: Any?): Boolean = v == null || (v is Double && v.isNaN())

        private fun truthy(v: Any?): Boolean = when (v) {
            null -> false
            is Boolean -> v
            is Double -> !v.isNaN() && v != 0.0
            is String -> v.isNotEmpty()
            else -> true
        }

        private fun num(v: Any?): Double = when (v) {
            null -> Double.NaN
            is Double -> v
            is Boolean -> if (v) 1.0 else 0.0
            is Int -> v.toDouble()
            is Long -> v.toDouble()
            is TupleValue -> throw PineException("A tuple cannot be used as a number; destructure it with [a, b] = ...")
            is String -> v.toDoubleOrNull() ?: throw PineException("Expected a number but found \"$v\".")
            is PineColor -> throw PineException("Expected a number but found a colour.")
            else -> Double.NaN
        }

        private fun equalsValue(a: Any?, b: Any?): Boolean = when {
            a is Double || b is Double -> { val x = num(a); val y = num(b); !x.isNaN() && !y.isNaN() && x == y }
            else -> a == b
        }

        private fun binary(op: String, a: Any?, b: Any?): Any? = when (op) {
            "==" -> equalsValue(a, b)
            "!=" -> !equalsValue(a, b) && !(isNa(a) || isNa(b))
            "<", ">", "<=", ">=" -> {
                val x = num(a); val y = num(b)
                if (x.isNaN() || y.isNaN()) false else when (op) { "<" -> x < y; ">" -> x > y; "<=" -> x <= y; else -> x >= y }
            }
            "+" -> if (a is String || b is String) (a?.toString() ?: "NaN") + (b?.toString() ?: "NaN") else arithmetic(op, a, b)
            else -> arithmetic(op, a, b)
        }

        private fun arithmetic(op: String, a: Any?, b: Any?): Any? {
            if (op == "+" && (a is String || b is String)) return (a?.toString() ?: "") + (b?.toString() ?: "")
            val x = num(a); val y = num(b)
            if (x.isNaN() || y.isNaN()) return Double.NaN
            return when (op) {
                "+" -> x + y
                "-" -> x - y
                "*" -> x * y
                "/" -> if (y == 0.0) Double.NaN else x / y
                "%" -> if (y == 0.0) Double.NaN else x % y
                else -> throw PineException("Unknown operator $op")
            }
        }
    }

    /** Per-call-site history buffers. */
    private class CallState(private val size: Int) {
        val values = DoubleArray(size) { Double.NaN }
        val values2 = DoubleArray(size) { Double.NaN }
        val out = DoubleArray(size) { Double.NaN }
        val anyValues = arrayOfNulls<Any?>(size)
        val extra = HashMap<String, Double>()
        val extraAny = HashMap<String, Any>()
        var last = Double.NaN
        var lastBar = -1
        private val children = HashMap<String, CallState>()
        fun child(name: String) = children.getOrPut(name) { CallState(size) }
        fun push(bar: Int, v: Double) { values[bar] = v }
        fun push2(bar: Int, v: Double) { values2[bar] = v }
        fun pushAny(bar: Int, v: Any?) { anyValues[bar] = v }
        fun at(index: Int) = if (index in 0 until size) values[index] else Double.NaN
        fun at2(index: Int) = if (index in 0 until size) values2[index] else Double.NaN
        fun anyAt(index: Int) = if (index in 0 until size) anyValues[index] else null
        fun window(bar: Int, len: Int): DoubleArray? = windowOf(values, bar, len)
        fun window2(bar: Int, len: Int): DoubleArray? = windowOf(values2, bar, len)
        private fun windowOf(source: DoubleArray, bar: Int, len: Int): DoubleArray? {
            if (len <= 0 || bar - len + 1 < 0) return null
            val w = source.copyOfRange(bar - len + 1, bar + 1)
            return if (w.any { it.isNaN() }) null else w
        }
    }
}
