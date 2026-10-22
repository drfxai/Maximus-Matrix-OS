package ai.drfx.maximus.matrixai.studio

/**
 * Line diff for review screens. Common prefix/suffix are trimmed first, then the changed middle is
 * diffed with Myers' algorithm, so an edit deep inside a 10k+ line file is shown where it happened
 * instead of as a whole-file rewrite. Pure text comparison; nothing is executed.
 */
object LineDiff {
    enum class Kind { SAME, ADDED, REMOVED, GAP }
    data class Row(val kind: Kind, val oldLine: Int?, val newLine: Int?, val text: String)
    data class Result(val rows: List<Row>, val added: Int, val removed: Int, val truncated: Boolean) {
        /** Unified-style text: `-`/`+`/space prefix plus the old or new line number. */
        fun render(): String = rows.joinToString("\n") { row ->
            when (row.kind) {
                Kind.GAP -> "@@ ${row.text} @@"
                Kind.ADDED -> "+${(row.newLine ?: 0).toString().padStart(6)}  ${row.text}"
                Kind.REMOVED -> "-${(row.oldLine ?: 0).toString().padStart(6)}  ${row.text}"
                Kind.SAME -> " ${(row.newLine ?: 0).toString().padStart(6)}  ${row.text}"
            }
        }
    }

    private const val MAX_EDIT_DISTANCE = 1_500

    fun compute(before: String, after: String, context: Int = 3, maxRows: Int = 5_000): Result {
        val old = before.lines()
        val new = after.lines()
        var prefix = 0
        while (prefix < old.size && prefix < new.size && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < old.size - prefix && suffix < new.size - prefix &&
            old[old.lastIndex - suffix] == new[new.lastIndex - suffix]) suffix++
        val a = old.subList(prefix, old.size - suffix)
        val b = new.subList(prefix, new.size - suffix)
        // Full script of edits over all lines: SAME rows for unchanged lines.
        val script = ArrayList<Row>()
        for (i in 0 until prefix) script += Row(Kind.SAME, i + 1, i + 1, old[i])
        val middle = myers(a, b)
        if (middle != null) {
            var oi = prefix
            var ni = prefix
            for (op in middle) when (op) {
                0 -> { script += Row(Kind.SAME, oi + 1, ni + 1, old[oi]); oi++; ni++ }
                -1 -> { script += Row(Kind.REMOVED, oi + 1, null, old[oi]); oi++ }
                else -> { script += Row(Kind.ADDED, null, ni + 1, new[ni]); ni++ }
            }
        } else {
            a.forEachIndexed { i, line -> script += Row(Kind.REMOVED, prefix + i + 1, null, line) }
            b.forEachIndexed { i, line -> script += Row(Kind.ADDED, null, prefix + i + 1, line) }
        }
        for (i in 0 until suffix) {
            val oi = old.size - suffix + i
            val ni = new.size - suffix + i
            script += Row(Kind.SAME, oi + 1, ni + 1, old[oi])
        }
        val added = script.count { it.kind == Kind.ADDED }
        val removed = script.count { it.kind == Kind.REMOVED }
        // Keep only changes plus [context] lines around them; collapse the rest into gaps.
        val keep = BooleanArray(script.size)
        script.forEachIndexed { index, row ->
            if (row.kind != Kind.SAME) for (k in maxOf(0, index - context)..minOf(script.lastIndex, index + context)) keep[k] = true
        }
        val rows = ArrayList<Row>()
        var skipped = 0
        var truncated = false
        for (index in script.indices) {
            if (!keep[index]) { skipped++; continue }
            if (skipped > 0) { rows += Row(Kind.GAP, null, null, "$skipped unchanged lines"); skipped = 0 }
            if (rows.size >= maxRows) { truncated = true; break }
            rows += script[index]
        }
        if (!truncated && skipped > 0 && rows.isNotEmpty()) rows += Row(Kind.GAP, null, null, "$skipped unchanged lines")
        return Result(rows, added, removed, truncated)
    }

    /** Myers O((N+M)D) edit script: 0 keep, -1 delete from a, 1 insert from b; null if D is too large. */
    private fun myers(a: List<String>, b: List<String>): IntArray? {
        val n = a.size
        val m = b.size
        if (n == 0) return IntArray(m) { 1 }
        if (m == 0) return IntArray(n) { -1 }
        val max = minOf(n + m, MAX_EDIT_DISTANCE)
        val offset = max + 1
        val v = IntArray(2 * max + 3)
        val trace = ArrayList<IntArray>()
        var found = -1
        loop@ for (d in 0..max) {
            // Only diagonals -d-1..d+1 are read when backtracking step d.
            trace += v.copyOfRange(offset - d - 1, offset + d + 2)
            var k = -d
            while (k <= d) {
                var x = if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) v[offset + k + 1] else v[offset + k - 1] + 1
                var y = x - k
                while (x < n && y < m && a[x] == b[y]) { x++; y++ }
                v[offset + k] = x
                if (x >= n && y >= m) { found = d; break@loop }
                k += 2
            }
        }
        if (found < 0) return null
        val ops = ArrayList<Int>(n + m)
        var x = n
        var y = m
        for (d in found downTo 1) {
            val vd = trace[d]
            val k = x - y
            val base = d + 1
            val prevK = if (k == -d || (k != d && vd[base + k - 1] < vd[base + k + 1])) k + 1 else k - 1
            val prevX = vd[base + prevK]
            val prevY = prevX - prevK
            while (x > prevX && y > prevY) { ops += 0; x--; y-- }
            if (prevK == k + 1) { ops += 1; y-- } else { ops += -1; x-- }
        }
        while (x > 0 && y > 0) { ops += 0; x--; y-- }
        ops.reverse()
        return ops.toIntArray()
    }
}
