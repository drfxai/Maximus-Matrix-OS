package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MAXIMUS reference surfaces. Appearance is shared; values and actions always come
 * from actual ViewModels/stores. No preview data are injected into production state.
 */
internal object MaximusReference {
    val gold = Color(0xFFFFCC62)
    val navy = Color(0xFF061A2D)
    val elevated = Color(0xFF092239)
    val stroke = Color(0xFF17446D)
    val muted = Color(0xFFA7C2E8)
    val white = Color(0xFFF5F8FF)
    val green = Color(0xFF2AD49F)
    val orange = Color(0xFFFFA347)
}

@Composable
internal fun MaximusSectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = MaximusReference.white, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, color = MaximusReference.muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
internal fun MaximusFramedCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaximusReference.navy,
        border = BorderStroke(1.dp, MaximusReference.stroke)
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}

@Composable
internal fun MaximusTabs(
    titles: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        titles.forEachIndexed { index, title ->
            val active = selected == index
            Surface(
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = if (active) Color(0xFF1C170C) else MaximusReference.navy,
                border = BorderStroke(if (active) 1.3.dp else 1.dp,
                    if (active) MaximusReference.gold else MaximusReference.stroke)
            ) {
                Box(Modifier.heightIn(min = 40.dp).padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center) {
                    Text(title, color = if (active) MaximusReference.gold else MaximusReference.muted,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun MaximusContextStrip(
    usedFraction: Float,
    remaining: String,
    provider: String,
    model: String,
    modifier: Modifier = Modifier
) {
    MaximusFramedCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(provider, color = MaximusReference.white, fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp, maxLines = 1)
                Text("Provider", color = MaximusReference.muted, fontSize = 10.sp)
            }
            Box(Modifier.width(1.dp).height(30.dp).background(MaximusReference.stroke))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(model.ifBlank { "Select a model" }, color = MaximusReference.white,
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                Text("Model", color = MaximusReference.muted, fontSize = 10.sp)
            }
        }
        HorizontalDivider(color = MaximusReference.stroke)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Context left · $remaining", fontSize = 11.sp, color = MaximusReference.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(10) { index ->
                        val filled = index < (usedFraction.coerceIn(0f, 1f) * 10).toInt()
                        Box(Modifier.weight(1f).height(7.dp).background(
                            if (filled) MaximusReference.gold else MaximusReference.stroke,
                            RoundedCornerShape(2.dp)))
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            CircularProgressIndicator(
                progress = { usedFraction.coerceIn(0f, 1f) },
                modifier = Modifier.size(34.dp),
                color = MaximusReference.gold,
                trackColor = MaximusReference.stroke,
                strokeWidth = 5.dp
            )
            Spacer(Modifier.width(7.dp))
            Text("Estimated\ncontext", color = MaximusReference.muted, fontSize = 9.sp,
                lineHeight = 12.sp)
        }
    }
}
