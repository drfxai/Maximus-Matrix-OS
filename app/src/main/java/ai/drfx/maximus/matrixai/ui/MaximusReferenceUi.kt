package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val remainingFraction = (1f - usedFraction.coerceIn(0f, 1f))
    val animatedRemaining by androidx.compose.animation.core.animateFloatAsState(
        targetValue = remainingFraction,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 450),
        label = "remaining-context")
    MaximusFramedCard(modifier, padding = if (compact) PaddingValues(horizontal = 9.dp, vertical = 7.dp)
        else PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (compact) {
                androidx.compose.material3.Icon(Icons.Default.AccountTree, null,
                    Modifier.size(18.dp), tint = MaximusReference.muted)
                Spacer(Modifier.width(7.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(provider, color = MaximusReference.white, fontWeight = FontWeight.SemiBold,
                    fontSize = if (compact) 11.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Provider", color = MaximusReference.muted, fontSize = 10.sp)
            }
            Box(Modifier.width(1.dp).height(if (compact) 28.dp else 30.dp).background(MaximusReference.stroke))
            Spacer(Modifier.width(if (compact) 6.dp else 12.dp))
            if (compact) {
                androidx.compose.material3.Icon(Icons.Default.ViewInAr, null,
                    Modifier.size(18.dp), tint = MaximusReference.muted)
                Spacer(Modifier.width(6.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(model.ifBlank { "Select a model" }, color = MaximusReference.white,
                    fontWeight = FontWeight.SemiBold, fontSize = if (compact) 11.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Model", color = MaximusReference.muted, fontSize = 10.sp)
            }
            if (compact) Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(17.dp), tint = MaximusReference.muted)
        }
        HorizontalDivider(color = MaximusReference.stroke)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Estimated context left · $remaining", fontSize = 11.sp, color = MaximusReference.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(10) { index ->
                        val filled = index < (animatedRemaining * 10f).toInt()
                        Box(Modifier.weight(1f).height(7.dp).background(
                            if (filled) lerp(MaximusReference.gold, Color(0xFFFF7139), index / 9f) else MaximusReference.stroke,
                            RoundedCornerShape(2.dp)))
                    }
                }
            }
            Spacer(Modifier.width(if (compact) 8.dp else 14.dp))
            Box(Modifier.size(if (compact) 37.dp else 43.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { animatedRemaining },
                    modifier = Modifier.size(if (compact) 34.dp else 39.dp),
                    color = MaximusReference.gold,
                    trackColor = MaximusReference.stroke,
                    strokeWidth = 5.dp
                )
                Text("${(animatedRemaining * 100).toInt()}%",
                    color = MaximusReference.white, fontSize = 9.sp,
                    fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(7.dp))
            Text("Estimated\ncontext", color = MaximusReference.muted, fontSize = 9.sp,
                lineHeight = 12.sp)
        }
    }
}

/** Shared gold action treatment from the reference; semantics and disabled state remain native. */
@Composable
internal fun ReferenceGoldButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 42.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaximusReference.gold,
        contentColor = Color(0xFF171307),
        border = BorderStroke(1.dp, Color(0xFFFFDE88))) {
        Row(Modifier.background(Brush.linearGradient(listOf(
            Color(0xFFFFDF83), Color(0xFFF8BD48), Color(0xFFFFD974))))
            .padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
internal fun ReferenceUnderlineTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            titles.forEachIndexed { index, title ->
                val active = index == selected
                Surface(onClick = { onSelect(index) }, modifier = Modifier.weight(1f), color = Color.Transparent) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.heightIn(min = 42.dp).padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
                            Text(title, color = if (active) MaximusReference.gold else MaximusReference.muted,
                                fontSize = 12.sp, maxLines = 1,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                        }
                        Box(Modifier.fillMaxWidth().height(2.dp).background(
                            if (active) MaximusReference.gold else Color.Transparent))
                    }
                }
            }
        }
        HorizontalDivider(color = MaximusReference.stroke, thickness = 1.dp)
    }
}
