package ai.drfx.maximus.matrixai.studio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Reference values of the local Pine interpreter: collections, user types and drawings. */
internal class PineArray(val items: ArrayList<Any?>) {
    override fun toString(): String = items.joinToString(", ", "[", "]") { PineText.of(it) }
}

internal class PineMap(val entries: LinkedHashMap<Any?, Any?>)

internal class PineMatrix(val rows: Int, val columns: Int, val cells: Array<Any?>) {
    fun index(row: Int, column: Int): Int {
        if (row !in 0 until rows || column !in 0 until columns)
            throw PineScriptEngine.PineException("Matrix index ($row, $column) is out of bounds ${rows}x$columns.")
        return row * columns + column
    }
}

/** An instance of a `type` declared in the script, or of a built-in like chart.point. */
internal class PineObject(val type: String, val fields: LinkedHashMap<String, Any?>) {
    fun copy() = PineObject(type, LinkedHashMap(fields))
}

internal class PineLabel(
    var x: Double, var y: Double, var text: String, var color: Long?, var textColor: Long?,
    var style: String, var size: String, var yloc: String, var tooltip: String
) { var deleted = false }

internal class PineLine(
    var x1: Double, var y1: Double, var x2: Double, var y2: Double, var color: Long?, var width: Double,
    var style: String, var extend: String
) { var deleted = false }

internal class PineBox(
    var left: Double, var top: Double, var right: Double, var bottom: Double, var border: Long?, var background: Long?,
    var text: String, var textColor: Long?, var extend: String
) { var deleted = false }

internal class PineTable(var position: String, val columns: Int, val rows: Int, var background: Long?, var frame: Long?) {
    val cells = HashMap<Pair<Int, Int>, PineScriptEngine.TableCell>()
    var deleted = false
}

internal class PineLinefill(val first: PineLine, val second: PineLine, var color: Long?) { var deleted = false }

/** Positional-or-named arguments after evaluation. */
internal class PineArgs(val positional: List<Any?>, val named: Map<String, Any?>) {
    fun has(index: Int, name: String) = named.containsKey(name) || (index >= 0 && index < positional.size)
    operator fun get(index: Int, name: String): Any? = if (named.containsKey(name)) named[name] else positional.getOrNull(index)
}

internal object PineText {
    fun of(value: Any?): String = when (value) {
        null -> "NaN"
        is Double -> if (value.isNaN()) "NaN" else if (value == Math.floor(value) && abs(value) < 1e15) value.toLong().toString()
            else trimNumber("%.8f".format(java.util.Locale.US, value))
        is Boolean -> value.toString()
        is PineScriptEngine.PineColor -> "#%08X".format((value.argb shl 8 or (value.argb ushr 24)) and 0xFFFFFFFFL)
        else -> value.toString()
    }

    private fun trimNumber(text: String) = if ('.' in text) text.trimEnd('0').trimEnd('.') else text

    /** str.tostring(value, format) with "#.##", "0.00", format.mintick/percent/volume. */
    fun format(value: Any?, pattern: String?): String {
        val number = value as? Double ?: return of(value)
        if (number.isNaN()) return "NaN"
        return when {
            pattern == null || pattern.isBlank() -> of(number)
            pattern == "format.mintick" -> "%.2f".format(java.util.Locale.US, number)
            pattern == "format.percent" -> "%.2f%%".format(java.util.Locale.US, number)
            pattern == "format.volume" -> compact(number)
            pattern == "format.inherit" -> of(number)
            pattern.any { it == '#' || it == '0' } -> runCatching {
                java.text.DecimalFormat(pattern, java.text.DecimalFormatSymbols(java.util.Locale.US))
                    .apply { roundingMode = java.math.RoundingMode.HALF_UP }.format(java.math.BigDecimal(number.toString()))
            }.getOrElse { of(number) }
            else -> of(number)
        }
    }

    private fun compact(value: Double): String {
        val a = abs(value)
        return when {
            a >= 1e9 -> "%.2fB".format(java.util.Locale.US, value / 1e9)
            a >= 1e6 -> "%.2fM".format(java.util.Locale.US, value / 1e6)
            a >= 1e3 -> "%.2fK".format(java.util.Locale.US, value / 1e3)
            else -> "%.2f".format(java.util.Locale.US, value)
        }
    }

    /** str.format("{0} and {1,number,#.##}", a, b). */
    fun formatString(template: String, values: List<Any?>): String =
        Regex("\\{(\\d+)(?:,number,([^}]*)|,number)?}").replace(template) { match ->
            val value = values.getOrNull(match.groupValues[1].toInt())
            val pattern = match.groupValues[2]
            if (pattern.isNotEmpty()) format(value, pattern)
            else if (match.value.contains(",number")) format(value, "#,##0.###")
            else of(value)
        }.replace("''", "'")
}

/** array.* and map.* built-ins, operating on evaluated arguments. */
internal object PineCollections {
    private const val MAX_SIZE = 100_000

    private fun number(value: Any?): Double = when (value) {
        is Double -> value
        is Boolean -> if (value) 1.0 else 0.0
        null -> Double.NaN
        else -> Double.NaN
    }

    private fun intOf(value: Any?, line: Int, what: String): Int {
        val d = number(value)
        if (d.isNaN()) throw PineScriptEngine.PineException("$what must be a number.", line)
        return d.toInt()
    }

    private fun arrayOf(value: Any?, fn: String, line: Int): PineArray =
        value as? PineArray ?: throw PineScriptEngine.PineException(
            if (value == null) "array.$fn() was called on an na array." else "array.$fn() needs an array.", line)

    private fun numbers(array: PineArray): List<Double> = array.items.map(::number).filter { !it.isNaN() }

    private fun checkIndex(array: PineArray, index: Int, line: Int, allowEnd: Boolean = false): Int {
        val size = array.items.size
        val i = if (index < 0) size + index else index
        if (i < 0 || i > size || (!allowEnd && i == size))
            throw PineScriptEngine.PineException("Index $index is out of bounds; the array size is $size.", line)
        return i
    }

    private fun grow(array: PineArray, line: Int) {
        if (array.items.size >= MAX_SIZE) throw PineScriptEngine.PineException("Array exceeds $MAX_SIZE elements.", line)
    }

    fun newArray(size: Any?, initial: Any?, line: Int): PineArray {
        val n = if (size == null) 0 else intOf(size, line, "Array size")
        if (n < 0 || n > MAX_SIZE) throw PineScriptEngine.PineException("Array size must be between 0 and $MAX_SIZE.", line)
        return PineArray(ArrayList<Any?>(n).apply { repeat(n) { add(initial) } })
    }

    fun array(fn: String, a: PineArgs, line: Int): Any? {
        if (fn.startsWith("new")) return newArray(a[0, "size"], a[1, "initial_value"], line)
        if (fn == "from") return PineArray(ArrayList(a.positional))
        val array = arrayOf(a[0, "id"], fn, line)
        val items = array.items
        return when (fn) {
            "size" -> items.size.toDouble()
            "get" -> items[checkIndex(array, intOf(a[1, "index"], line, "Index"), line)]
            "set" -> { items[checkIndex(array, intOf(a[1, "index"], line, "Index"), line)] = a[2, "value"]; null }
            "push" -> { grow(array, line); items.add(a[1, "value"]); null }
            "unshift" -> { grow(array, line); items.add(0, a[1, "value"]); null }
            "insert" -> { grow(array, line); items.add(checkIndex(array, intOf(a[1, "index"], line, "Index"), line, allowEnd = true), a[2, "value"]); null }
            "pop" -> if (items.isEmpty()) throw PineScriptEngine.PineException("Cannot pop from an empty array.", line) else items.removeAt(items.lastIndex)
            "shift" -> if (items.isEmpty()) throw PineScriptEngine.PineException("Cannot shift an empty array.", line) else items.removeAt(0)
            "remove" -> items.removeAt(checkIndex(array, intOf(a[1, "index"], line, "Index"), line))
            "clear" -> { items.clear(); null }
            "first" -> items.firstOrNull() ?: if (items.isEmpty()) throw PineScriptEngine.PineException("array.first() on an empty array.", line) else null
            "last" -> items.lastOrNull() ?: if (items.isEmpty()) throw PineScriptEngine.PineException("array.last() on an empty array.", line) else null
            "includes" -> items.any { same(it, a[1, "value"]) }
            "indexof" -> items.indexOfFirst { same(it, a[1, "value"]) }.toDouble()
            "lastindexof" -> items.indexOfLast { same(it, a[1, "value"]) }.toDouble()
            "copy" -> PineArray(ArrayList(items))
            "slice" -> {
                val from = checkIndex(array, intOf(a[1, "index_from"], line, "Index"), line, allowEnd = true)
                val to = checkIndex(array, intOf(a[2, "index_to"], line, "Index"), line, allowEnd = true)
                PineArray(ArrayList(items.subList(min(from, to), max(from, to))))
            }
            "concat" -> { val other = arrayOf(a[1, "id2"], fn, line); items.addAll(other.items); array }
            "reverse" -> { items.reverse(); null }
            "fill" -> {
                val from = if (a.has(2, "index_from")) intOf(a[2, "index_from"], line, "Index") else 0
                val to = if (a.has(3, "index_to")) intOf(a[3, "index_to"], line, "Index") else items.size
                for (i in from.coerceAtLeast(0) until to.coerceAtMost(items.size)) items[i] = a[1, "value"]
                null
            }
            "join" -> items.joinToString((a[1, "separator"] as? String) ?: "") { PineText.of(it) }
            "sort" -> {
                val descending = (a[1, "order"] as? String)?.contains("descending") == true
                if (items.all { it is String }) items.sortBy { it as String } else items.sortBy { number(it).let { d -> if (d.isNaN()) Double.MAX_VALUE else d } }
                if (descending) items.reverse()
                null
            }
            "sort_indices" -> {
                val descending = (a[1, "order"] as? String)?.contains("descending") == true
                val order = items.indices.sortedBy { number(items[it]) }.let { if (descending) it.reversed() else it }
                PineArray(ArrayList(order.map { it.toDouble() }))
            }
            "sum" -> numbers(array).sum()
            "avg" -> numbers(array).takeIf { it.isNotEmpty() }?.average()
            "min" -> numbers(array).sorted().getOrNull(if (a.has(1, "nth")) intOf(a[1, "nth"], line, "nth") else 0)
            "max" -> numbers(array).sortedDescending().getOrNull(if (a.has(1, "nth")) intOf(a[1, "nth"], line, "nth") else 0)
            "range" -> numbers(array).takeIf { it.isNotEmpty() }?.let { it.max() - it.min() }
            "median" -> numbers(array).sorted().takeIf { it.isNotEmpty() }?.let { s ->
                if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2 }
            "mode" -> numbers(array).groupingBy { it }.eachCount().entries
                .sortedWith(compareBy({ -it.value }, { it.key })).firstOrNull()?.key
            "variance", "stdev" -> numbers(array).takeIf { it.isNotEmpty() }?.let { s ->
                val mean = s.average()
                val biased = (a[1, "biased"] as? Boolean) ?: true
                val v = s.sumOf { (it - mean) * (it - mean) } / (if (biased || s.size < 2) s.size else s.size - 1)
                if (fn == "stdev") sqrt(v) else v
            }
            "abs" -> PineArray(ArrayList(items.map { number(it).let { d -> if (d.isNaN()) null else abs(d) } }))
            "standardize" -> numbers(array).let { s ->
                val mean = s.average(); val sd = sqrt(s.sumOf { (it - mean) * (it - mean) } / s.size.coerceAtLeast(1))
                PineArray(ArrayList(items.map { number(it).let { d -> if (d.isNaN() || sd == 0.0) null else (d - mean) / sd } }))
            }
            "percentile_nearest_rank", "percentile_linear_interpolation" -> {
                val s = numbers(array).sorted()
                val p = number(a[1, "percentage"]).coerceIn(0.0, 100.0)
                if (s.isEmpty()) null else if (fn.endsWith("rank")) s[(Math.ceil(p / 100 * s.size).toInt() - 1).coerceIn(0, s.lastIndex)]
                else {
                    val pos = p / 100 * (s.size - 1); val lo = pos.toInt(); val hi = min(lo + 1, s.lastIndex)
                    s[lo] + (s[hi] - s[lo]) * (pos - lo)
                }
            }
            "percentrank" -> {
                val i = checkIndex(array, intOf(a[1, "index"], line, "Index"), line)
                val v = number(items[i]); val s = numbers(array)
                if (s.size < 2) null else (s.count { it <= v } - 1).toDouble() / (s.size - 1) * 100
            }
            "covariance" -> {
                val other = arrayOf(a[1, "id2"], fn, line)
                val x = items.map(::number); val y = other.items.map(::number)
                val n = min(x.size, y.size)
                if (n == 0) null else {
                    val mx = x.take(n).average(); val my = y.take(n).average()
                    (0 until n).sumOf { (x[it] - mx) * (y[it] - my) } / n
                }
            }
            "binary_search", "binary_search_leftmost", "binary_search_rightmost" -> {
                val target = number(a[1, "val"])
                val s = items.map(::number)
                var lo = 0; var hi = s.size - 1; var found = -1
                while (lo <= hi) {
                    val mid = (lo + hi) / 2
                    when {
                        s[mid] == target -> { found = mid; if (fn.endsWith("leftmost")) hi = mid - 1 else if (fn.endsWith("rightmost")) lo = mid + 1 else break }
                        s[mid] < target -> lo = mid + 1
                        else -> hi = mid - 1
                    }
                }
                when {
                    found >= 0 -> found.toDouble()
                    fn.endsWith("leftmost") -> lo.toDouble()
                    fn.endsWith("rightmost") -> hi.toDouble()
                    else -> -1.0
                }
            }
            "every" -> items.all { it == true || (it is Double && it != 0.0 && !it.isNaN()) }
            "some" -> items.any { it == true || (it is Double && it != 0.0 && !it.isNaN()) }
            else -> throw PineScriptEngine.PineException("array.$fn is not supported by the local preview.", line)
        }
    }

    fun map(fn: String, a: PineArgs, line: Int): Any? {
        if (fn == "new") return PineMap(LinkedHashMap())
        val map = a[0, "id"] as? PineMap ?: throw PineScriptEngine.PineException("map.$fn() needs a map.", line)
        val entries = map.entries
        return when (fn) {
            "put" -> {
                if (!entries.containsKey(key(a[1, "key"])) && entries.size >= 50_000)
                    throw PineScriptEngine.PineException("Map exceeds 50000 keys.", line)
                entries.put(key(a[1, "key"]), a[2, "value"])
            }
            "get" -> entries[key(a[1, "key"])]
            "contains" -> entries.containsKey(key(a[1, "key"]))
            "remove" -> entries.remove(key(a[1, "key"]))
            "size" -> entries.size.toDouble()
            "keys" -> PineArray(ArrayList(entries.keys))
            "values" -> PineArray(ArrayList(entries.values))
            "clear" -> { entries.clear(); null }
            "copy" -> PineMap(LinkedHashMap(entries))
            "put_all" -> { (a[1, "id2"] as? PineMap)?.let { entries.putAll(it.entries) }; null }
            else -> throw PineScriptEngine.PineException("map.$fn is not supported by the local preview.", line)
        }
    }

    fun matrix(fn: String, a: PineArgs, line: Int): Any? {
        if (fn == "new") {
            val rows = intOf(a[0, "rows"] ?: 0.0, line, "Rows"); val columns = intOf(a[1, "columns"] ?: 0.0, line, "Columns")
            if (rows < 0 || columns < 0 || rows.toLong() * columns > MAX_SIZE)
                throw PineScriptEngine.PineException("Matrix size must stay within $MAX_SIZE cells.", line)
            return PineMatrix(rows, columns, Array(rows * columns) { a[2, "initial_value"] })
        }
        val m = a[0, "id"] as? PineMatrix ?: throw PineScriptEngine.PineException("matrix.$fn() needs a matrix.", line)
        return when (fn) {
            "get" -> m.cells[m.index(intOf(a[1, "row"], line, "Row"), intOf(a[2, "column"], line, "Column"))]
            "set" -> { m.cells[m.index(intOf(a[1, "row"], line, "Row"), intOf(a[2, "column"], line, "Column"))] = a[3, "value"]; null }
            "rows" -> m.rows.toDouble()
            "columns" -> m.columns.toDouble()
            "elements_count" -> (m.rows * m.columns).toDouble()
            "row" -> { val r = intOf(a[1, "row"], line, "Row"); PineArray(ArrayList((0 until m.columns).map { m.cells[m.index(r, it)] })) }
            "col" -> { val c = intOf(a[1, "column"], line, "Column"); PineArray(ArrayList((0 until m.rows).map { m.cells[m.index(it, c)] })) }
            "fill" -> { m.cells.fill(a[1, "value"]); null }
            "sum" -> m.cells.sumOf { number(it).takeIf { d -> !d.isNaN() } ?: 0.0 }
            "avg" -> m.cells.map(::number).filter { !it.isNaN() }.takeIf { it.isNotEmpty() }?.average()
            "max" -> m.cells.map(::number).filter { !it.isNaN() }.maxOrNull()
            "min" -> m.cells.map(::number).filter { !it.isNaN() }.minOrNull()
            "copy" -> PineMatrix(m.rows, m.columns, m.cells.copyOf())
            else -> throw PineScriptEngine.PineException("matrix.$fn is not supported by the local preview.", line)
        }
    }

    private fun key(value: Any?): Any? = if (value is Double && value == Math.floor(value)) value else value

    private fun same(a: Any?, b: Any?): Boolean =
        if (a is Double || b is Double) number(a) == number(b) else a == b
}
