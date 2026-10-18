package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A bounded positional preview; not a claim of a Git patch or a remote branch write. */
@Composable
internal fun WorkspaceDiffCard(before: String, after: String) {
    val rows = remember(before, after) {
        val old = before.lines(); val new = after.lines()
        buildList {
            repeat(maxOf(old.size, new.size).coerceAtMost(100)) { index ->
                if (old.getOrNull(index) == new.getOrNull(index)) add("  " + new[index])
                else {
                    old.getOrNull(index)?.let { add("− $it") }
                    new.getOrNull(index)?.let { add("+ $it") }
                }
            }
        }
    }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(10.dp)) {
            rows.forEach { row ->
                val color = when (row.firstOrNull()) {
                    '+' -> MaterialTheme.colorScheme.primary
                    '−' -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(row, color = color, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    lineHeight = 18.sp, softWrap = false,
                    modifier = Modifier.background(color.copy(alpha = if (row.startsWith("  ")) 0f else .08f)))
            }
        }
    }
}
