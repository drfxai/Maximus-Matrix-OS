package ai.drfx.maximus.matrixai.ui

/**
 * Small, allocation-light tokenizer for code display. It never parses or runs the code: it only
 * classifies characters into display spans per line, carrying block-comment and multi-line
 * string state from one line to the next so 15k-line files colour correctly while scrolling.
 */
internal enum class TokenKind { KEYWORD, TYPE, BUILTIN, STRING, NUMBER, COMMENT, FUNCTION, ANNOTATION, PREPROCESSOR, TAG, ATTRIBUTE, PROPERTY, CONSTANT }

internal data class TokenSpan(val start: Int, val end: Int, val kind: TokenKind)

/** State carried between lines. */
internal enum class LexState { NONE, BLOCK_COMMENT, TRIPLE_DOUBLE, TRIPLE_SINGLE, BACKTICK, MARKUP_COMMENT }

internal class LanguageSpec(
    val id: String,
    val keywords: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val builtins: Set<String> = emptySet(),
    val constants: Set<String> = setOf("true", "false", "null"),
    val lineComments: List<String> = listOf("//"),
    val blockComment: Pair<String, String>? = "/*" to "*/",
    val quotes: String = "\"'",
    val tripleQuotes: Boolean = false,
    val backtickStrings: Boolean = false,
    val annotations: Boolean = false,
    val preprocessor: Boolean = false,
    val capitalizedTypes: Boolean = false,
    val caseInsensitive: Boolean = false,
    val variablePrefix: Char? = null,
    val markup: Boolean = false,
    val keyValue: Boolean = false
)

internal object SyntaxSpecs {
    private fun words(text: String) = text.trim().split(Regex("\\s+")).toSet()

    private val cTypes = words("void char short int long float double signed unsigned bool size_t int8_t int16_t int32_t int64_t uint8_t uint16_t uint32_t uint64_t FILE wchar_t")
    private val cKeywords = words("if else for while do switch case default break continue return goto struct union enum typedef sizeof static const extern volatile register inline restrict auto")

    val python = LanguageSpec("py",
        keywords = words("and as assert async await break class continue def del elif else except finally for from global if import in is lambda nonlocal not or pass raise return try while with yield match case"),
        types = words("int float str bool list dict set tuple bytes object complex frozenset bytearray"),
        builtins = words("print len range open input enumerate zip map filter sorted reversed sum min max abs round isinstance issubclass type super self cls hasattr getattr setattr any all iter next format repr"),
        constants = words("True False None"), lineComments = listOf("#"), blockComment = null, tripleQuotes = true,
        annotations = true, capitalizedTypes = true)
    val java = LanguageSpec("java",
        keywords = words("abstract assert break case catch class continue default do else enum extends final finally for if implements import instanceof interface native new package private protected public return static strictfp super switch synchronized this throw throws transient try volatile while var record sealed permits yield"),
        types = words("boolean byte char double float int long short void String Object Integer Long Double Boolean List Map Set ArrayList HashMap"),
        builtins = words("System out println printf Math"), annotations = true, capitalizedTypes = true)
    val kotlin = LanguageSpec("kt",
        keywords = words("as break class continue do else for fun if in interface is object package return super this throw try typealias val var when while by catch constructor finally get import init set where abstract annotation companion const data enum external final inline inner internal lateinit open operator out override private protected public reified sealed suspend tailrec vararg"),
        types = words("Int Long Short Byte Double Float Boolean Char String Unit Any Nothing List Map Set Array MutableList MutableMap"),
        builtins = words("println print listOf mapOf setOf mutableListOf mutableMapOf require check error lazy also apply let run with takeIf"),
        tripleQuotes = true, annotations = true, capitalizedTypes = true)
    val c = LanguageSpec("c", keywords = cKeywords, types = cTypes,
        builtins = words("printf scanf malloc calloc realloc free memcpy memset strlen strcpy strcmp fopen fclose fprintf puts gets NULL"),
        constants = words("true false NULL"), preprocessor = true)
    val cpp = LanguageSpec("cpp",
        keywords = cKeywords + words("class namespace template typename public private protected virtual override final new delete this throw try catch using operator friend explicit constexpr noexcept nullptr decltype mutable co_await co_return"),
        types = cTypes + words("string vector map unordered_map set pair shared_ptr unique_ptr std"),
        builtins = words("cout cin endl printf make_shared make_unique move"), constants = words("true false nullptr NULL"),
        preprocessor = true, capitalizedTypes = true)
    val csharp = LanguageSpec("cs",
        keywords = words("abstract as base break case catch class const continue default delegate do else enum event explicit extern finally fixed for foreach goto if implicit in interface internal is lock namespace new operator out override params private protected public readonly ref return sealed sizeof static struct switch this throw try typeof unchecked unsafe using virtual volatile while async await var record get set"),
        types = words("bool byte char decimal double float int long object sbyte short string uint ulong ushort void"),
        builtins = words("Console WriteLine Math List Dictionary Task"), annotations = false, preprocessor = true, capitalizedTypes = true)
    val javascript = LanguageSpec("js",
        keywords = words("async await break case catch class const continue debugger default delete do else export extends finally for from function if import in instanceof let new of return static super switch this throw try typeof var void while with yield"),
        types = words("Array Object String Number Boolean Promise Map Set Date RegExp Error JSON Math Symbol"),
        builtins = words("console log document window fetch setTimeout setInterval require module exports"),
        constants = words("true false null undefined NaN Infinity"), backtickStrings = true)
    val typescript = LanguageSpec("ts",
        keywords = javascript.keywords + words("interface type enum implements namespace declare abstract public private protected readonly as keyof infer is satisfies"),
        types = javascript.types + words("string number boolean any unknown never void object bigint Record Partial Readonly"),
        builtins = javascript.builtins, constants = javascript.constants, backtickStrings = true, annotations = true, capitalizedTypes = true)
    val rust = LanguageSpec("rs",
        keywords = words("as async await break const continue crate dyn else enum extern fn for if impl in let loop match mod move mut pub ref return self Self static struct super trait type unsafe use where while"),
        types = words("i8 i16 i32 i64 i128 isize u8 u16 u32 u64 u128 usize f32 f64 bool char str String Vec Option Result Box"),
        builtins = words("println print format vec panic Some None Ok Err"), quotes = "\"", capitalizedTypes = true)
    val go = LanguageSpec("go",
        keywords = words("break case chan const continue default defer else fallthrough for func go goto if import interface map package range return select struct switch type var"),
        types = words("bool byte complex64 complex128 error float32 float64 int int8 int16 int32 int64 rune string uint uint8 uint16 uint32 uint64 uintptr"),
        builtins = words("append cap close copy delete len make new panic print println recover fmt"), constants = words("true false nil iota"),
        backtickStrings = true)
    val swift = LanguageSpec("swift",
        keywords = words("class deinit enum extension func import init let protocol struct subscript typealias var break case continue default do else fallthrough for guard if in repeat return switch where while as catch is rethrows throw throws try async await some any private public internal fileprivate open static override mutating"),
        types = words("Int Double Float Bool String Character Array Dictionary Set Optional Any Void"),
        builtins = words("print"), constants = words("true false nil self"), annotations = true, tripleQuotes = true, capitalizedTypes = true)
    val dart = LanguageSpec("dart",
        keywords = words("abstract as assert async await break case catch class const continue default do else enum extends extension final finally for if implements import in is late library mixin new on part required return static super switch this throw try var while with yield"),
        types = words("int double num String bool List Map Set dynamic void Future Stream Object"),
        builtins = words("print"), annotations = true, tripleQuotes = true, capitalizedTypes = true)
    val ruby = LanguageSpec("rb",
        keywords = words("alias and begin break case class def defined? do else elsif end ensure for if in module next not or redo rescue retry return self super then undef unless until when while yield require attr_accessor"),
        builtins = words("puts print p require_relative lambda proc"), constants = words("true false nil"),
        lineComments = listOf("#"), blockComment = null, capitalizedTypes = true)
    val php = LanguageSpec("php",
        keywords = words("abstract and as break case catch class const continue declare default do echo else elseif empty extends final finally fn for foreach function global if implements include interface isset namespace new or print private protected public require return static switch throw trait try unset use var while yield"),
        builtins = words("array strlen count explode implode json_encode json_decode"), constants = words("true false null TRUE FALSE NULL"),
        lineComments = listOf("//", "#"), variablePrefix = '$')
    val shell = LanguageSpec("sh",
        keywords = words("if then else elif fi for while until do done case esac in function return local export readonly declare select break continue"),
        builtins = words("echo printf cd ls cat grep sed awk find mkdir rm cp mv chmod chown source exit set unset test read curl wget git sudo apt npm"),
        constants = emptySet(), lineComments = listOf("#"), blockComment = null, variablePrefix = '$')
    val powershell = LanguageSpec("ps1",
        keywords = words("begin break catch class continue data do dynamicparam else elseif end exit filter finally for foreach function if in param process return switch throw trap try until while"),
        constants = words("\$true \$false \$null"), lineComments = listOf("#"), blockComment = "<#" to "#>", variablePrefix = '$', caseInsensitive = true)
    val sql = LanguageSpec("sql",
        keywords = words("select from where and or not insert into values update set delete create table drop alter add index view join left right inner outer full on as group by order having limit offset union all distinct case when then else end primary key foreign references default null is in like between exists returning with"),
        types = words("int integer bigint smallint decimal numeric float real double varchar char text date time timestamp boolean serial json jsonb"),
        builtins = words("count sum avg min max coalesce now cast"), constants = words("true false null"),
        lineComments = listOf("--"), caseInsensitive = true)
    val lua = LanguageSpec("lua",
        keywords = words("and break do else elseif end for function goto if in local not or repeat return then until while"),
        builtins = words("print pairs ipairs require tostring tonumber type table string math"), constants = words("true false nil"),
        lineComments = listOf("--"), blockComment = "--[[" to "]]")
    val mql = LanguageSpec("mq",
        keywords = cKeywords + words("class public private protected virtual new delete this input extern sinput"),
        types = cTypes + words("string datetime color bool double int long ulong uchar ENUM_TIMEFRAMES MqlRates MqlTick"),
        builtins = words("OnInit OnDeinit OnTick OnCalculate OnTimer OrderSend iMA iRSI iATR CopyBuffer SymbolInfoDouble Print Alert Comment ObjectCreate"),
        preprocessor = true, capitalizedTypes = false)
    val css = LanguageSpec("css", constants = emptySet(), lineComments = emptyList(), keyValue = true,
        builtins = words("important media import keyframes font-face supports"))
    val json = LanguageSpec("json", lineComments = listOf("//"), quotes = "\"", keyValue = true)
    val yaml = LanguageSpec("yaml", constants = words("true false null yes no on off ~"), lineComments = listOf("#"),
        blockComment = null, keyValue = true)
    val toml = LanguageSpec("toml", lineComments = listOf("#"), blockComment = null, keyValue = true, tripleQuotes = true)
    val markup = LanguageSpec("html", constants = emptySet(), lineComments = emptyList(), blockComment = null, markup = true)
    val markdown = LanguageSpec("md", constants = emptySet(), lineComments = emptyList(), blockComment = null, quotes = "")
    val plain = LanguageSpec("txt", constants = emptySet(), lineComments = emptyList(), blockComment = null, quotes = "")
    /** Fallback for unlabelled code: common C-family words, both comment styles. */
    val generic = LanguageSpec("code",
        keywords = words("if else for while return function def class import from const let var fun val new public private static void int float double bool string true false null None"),
        lineComments = listOf("//", "#"))

    fun forExtension(extension: String): LanguageSpec = when (extension) {
        "py" -> python; "java" -> java; "kt" -> kotlin; "c" -> c; "cpp" -> cpp; "cs" -> csharp
        "js" -> javascript; "ts" -> typescript; "rs" -> rust; "go" -> go; "swift" -> swift; "dart" -> dart
        "rb" -> ruby; "php" -> php; "sh" -> shell; "ps1" -> powershell; "sql" -> sql; "lua" -> lua
        "mq4", "mq5" -> mql; "css" -> css; "json" -> json; "yaml" -> yaml; "toml" -> toml
        "html" -> markup; "md" -> markdown; "txt" -> generic
        else -> generic
    }
}

internal object SyntaxTokenizer {
    private fun isIdentStart(c: Char) = c.isLetter() || c == '_' || c == '$'
    private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

    /** Tokenizes one line starting in [state]; returns spans and the state at the end of the line. */
    fun line(text: String, spec: LanguageSpec, state: LexState = LexState.NONE): Pair<List<TokenSpan>, LexState> {
        val spans = ArrayList<TokenSpan>()
        var i = 0
        var current = state
        val n = text.length
        if (n > 4_000) return spans to current

        fun closeMultiline(closer: String, kind: TokenKind): Boolean {
            val end = text.indexOf(closer, i)
            return if (end < 0) { spans += TokenSpan(i, n, kind); i = n; false }
            else { spans += TokenSpan(i, end + closer.length, kind); i = end + closer.length; true }
        }
        when (current) {
            LexState.BLOCK_COMMENT -> if (closeMultiline(spec.blockComment?.second ?: "*/", TokenKind.COMMENT)) current = LexState.NONE
            LexState.MARKUP_COMMENT -> if (closeMultiline("-->", TokenKind.COMMENT)) current = LexState.NONE
            LexState.TRIPLE_DOUBLE -> if (closeMultiline("\"\"\"", TokenKind.STRING)) current = LexState.NONE
            LexState.TRIPLE_SINGLE -> if (closeMultiline("'''", TokenKind.STRING)) current = LexState.NONE
            LexState.BACKTICK -> if (closeMultiline("`", TokenKind.STRING)) current = LexState.NONE
            LexState.NONE -> {}
        }
        if (spec.markup) return markupLine(text, i, spans, current)
        if (spec.id == "md") return markdownLine(text, spans) to LexState.NONE
        val firstNonSpace = text.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) n else it }
        while (i < n) {
            val c = text[i]
            if (c.isWhitespace()) { i++; continue }
            val lineComment = spec.lineComments.firstOrNull { text.startsWith(it, i) }
            val block = spec.blockComment
            when {
                block != null && text.startsWith(block.first, i) -> {
                    val end = text.indexOf(block.second, i + block.first.length)
                    if (end < 0) { spans += TokenSpan(i, n, TokenKind.COMMENT); i = n; current = LexState.BLOCK_COMMENT }
                    else { spans += TokenSpan(i, end + block.second.length, TokenKind.COMMENT); i = end + block.second.length }
                }
                lineComment != null && !(lineComment == "#" && spec.preprocessor && i == firstNonSpace) -> {
                    spans += TokenSpan(i, n, TokenKind.COMMENT); i = n
                }
                spec.preprocessor && c == '#' && i == firstNonSpace -> {
                    var end = i + 1
                    while (end < n && (text[end].isLetter())) end++
                    spans += TokenSpan(i, end, TokenKind.PREPROCESSOR)
                    val rest = text.substring(end).trimStart()
                    if (rest.startsWith("<") && rest.contains('>')) {
                        val start = text.indexOf('<', end)
                        spans += TokenSpan(start, text.indexOf('>', start) + 1, TokenKind.STRING)
                        i = text.indexOf('>', start) + 1
                    } else i = end
                }
                spec.tripleQuotes && (text.startsWith("\"\"\"", i) || text.startsWith("'''", i)) -> {
                    val delimiter = text.substring(i, i + 3)
                    val end = text.indexOf(delimiter, i + 3)
                    if (end < 0) {
                        spans += TokenSpan(i, n, TokenKind.STRING); i = n
                        current = if (delimiter == "\"\"\"") LexState.TRIPLE_DOUBLE else LexState.TRIPLE_SINGLE
                    } else { spans += TokenSpan(i, end + 3, TokenKind.STRING); i = end + 3 }
                }
                spec.backtickStrings && c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end < 0) { spans += TokenSpan(i, n, TokenKind.STRING); i = n; current = LexState.BACKTICK }
                    else { spans += TokenSpan(i, end + 1, TokenKind.STRING); i = end + 1 }
                }
                c in spec.quotes -> {
                    var end = i + 1
                    while (end < n && text[end] != c) { if (text[end] == '\\') end++; end++ }
                    end = minOf(end + 1, n)
                    // A quoted key followed by ':' is a property in JSON-like data.
                    val after = text.substring(end).trimStart()
                    spans += TokenSpan(i, end, if (spec.keyValue && after.startsWith(":")) TokenKind.PROPERTY else TokenKind.STRING)
                    i = end
                }
                c.isDigit() || (c == '.' && i + 1 < n && text[i + 1].isDigit()) -> {
                    if (i > 0 && isIdentPart(text[i - 1])) { i++; continue }
                    var end = i + 1
                    while (end < n && (text[end].isLetterOrDigit() || text[end] == '.' || text[end] == '_')) end++
                    spans += TokenSpan(i, end, TokenKind.NUMBER); i = end
                }
                spec.annotations && c == '@' && i + 1 < n && isIdentStart(text[i + 1]) -> {
                    var end = i + 1
                    while (end < n && (isIdentPart(text[end]) || text[end] == '.')) end++
                    spans += TokenSpan(i, end, TokenKind.ANNOTATION); i = end
                }
                spec.variablePrefix == c && i + 1 < n && (isIdentStart(text[i + 1]) || text[i + 1] == '{') -> {
                    var end = i + 1
                    if (text[end] == '{') { end = text.indexOf('}', end).let { if (it < 0) n else it + 1 } }
                    else while (end < n && isIdentPart(text[end])) end++
                    spans += TokenSpan(i, end, TokenKind.CONSTANT); i = end
                }
                isIdentStart(c) -> {
                    var end = i + 1
                    while (end < n && (isIdentPart(text[end]) || (spec.id == "rb" && text[end] == '?'))) end++
                    if (spec.id == "css") while (end < n && (isIdentPart(text[end]) || text[end] == '-')) end++
                    val word = text.substring(i, end)
                    val key = if (spec.caseInsensitive) word.lowercase() else word
                    var next = end
                    while (next < n && text[next] == ' ') next++
                    val kind = when {
                        spec.keyValue && next < n && text[next] == ':' && (spec.id != "css" || i == firstNonSpace) -> TokenKind.PROPERTY
                        key in spec.keywords -> TokenKind.KEYWORD
                        key in spec.constants -> TokenKind.CONSTANT
                        key in spec.types -> TokenKind.TYPE
                        key in spec.builtins -> TokenKind.BUILTIN
                        next < n && text[next] == '(' -> TokenKind.FUNCTION
                        spec.capitalizedTypes && word.first().isUpperCase() && word.any { it.isLowerCase() } -> TokenKind.TYPE
                        word.length > 1 && word.all { it.isUpperCase() || it == '_' || it.isDigit() } && word.any { it.isLetter() } -> TokenKind.CONSTANT
                        else -> null
                    }
                    if (kind != null) spans += TokenSpan(i, end, kind)
                    i = end
                }
                else -> i++
            }
        }
        return spans to current
    }

    private fun markupLine(text: String, from: Int, spans: ArrayList<TokenSpan>, state: LexState): Pair<List<TokenSpan>, LexState> {
        var i = from
        var current = state
        val n = text.length
        while (i < n) {
            if (current == LexState.MARKUP_COMMENT) {
                val end = text.indexOf("-->", i)
                if (end < 0) { spans += TokenSpan(i, n, TokenKind.COMMENT); return spans to current }
                spans += TokenSpan(i, end + 3, TokenKind.COMMENT); i = end + 3; current = LexState.NONE
                continue
            }
            if (text.startsWith("<!--", i)) { current = LexState.MARKUP_COMMENT; continue }
            if (text[i] == '<') {
                var end = i + 1
                if (end < n && (text[end] == '/' || text[end] == '!' || text[end] == '?')) end++
                val nameStart = end
                while (end < n && (text[end].isLetterOrDigit() || text[end] in "-:_")) end++
                if (end > nameStart) spans += TokenSpan(nameStart, end, TokenKind.TAG)
                i = end
                // Attributes until the tag closes on this line.
                while (i < n && text[i] != '>') {
                    val c = text[i]
                    when {
                        c == '"' || c == '\'' -> {
                            val close = text.indexOf(c, i + 1).let { if (it < 0) n - 1 else it }
                            spans += TokenSpan(i, close + 1, TokenKind.STRING); i = close + 1
                        }
                        c.isLetter() -> {
                            var e = i
                            while (e < n && (text[e].isLetterOrDigit() || text[e] in "-:_@")) e++
                            spans += TokenSpan(i, e, TokenKind.ATTRIBUTE); i = e
                        }
                        else -> i++
                    }
                }
                i++
                continue
            }
            if (text[i] == '&') {
                val end = text.indexOf(';', i)
                if (end in (i + 1)..(i + 10)) { spans += TokenSpan(i, end + 1, TokenKind.CONSTANT); i = end + 1; continue }
            }
            i++
        }
        return spans to current
    }

    private fun markdownLine(text: String, spans: ArrayList<TokenSpan>): List<TokenSpan> {
        val trimmed = text.trimStart()
        val offset = text.length - trimmed.length
        when {
            trimmed.startsWith("#") -> spans += TokenSpan(offset, text.length, TokenKind.KEYWORD)
            trimmed.startsWith(">") -> spans += TokenSpan(offset, text.length, TokenKind.COMMENT)
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.matches(Regex("\\d+\\. .*")) ->
                spans += TokenSpan(offset, offset + trimmed.indexOf(' ') + 1, TokenKind.BUILTIN)
        }
        Regex("`[^`]+`").findAll(text).forEach { spans += TokenSpan(it.range.first, it.range.last + 1, TokenKind.STRING) }
        Regex("\\*\\*[^*]+\\*\\*").findAll(text).forEach { spans += TokenSpan(it.range.first, it.range.last + 1, TokenKind.TYPE) }
        return spans
    }

    /** Line-start states for a whole source in one pass, so any visible line can be coloured alone. */
    fun lineStates(source: String, spec: LanguageSpec): ByteArray {
        val lines = source.split('\n')
        val states = ByteArray(lines.size)
        var state = LexState.NONE
        val multiline = spec.blockComment != null || spec.tripleQuotes || spec.backtickStrings || spec.markup
        for (index in lines.indices) {
            states[index] = state.ordinal.toByte()
            if (multiline) state = line(lines[index], spec, state).second
        }
        return states
    }
}
