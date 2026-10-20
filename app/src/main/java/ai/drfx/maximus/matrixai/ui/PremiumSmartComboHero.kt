package ai.drfx.maximus.matrixai.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Premium routing diagram. Only real routing state and sourced metrics are presented. */
@Composable
internal fun PremiumSmartComboHero(
    provider: String,
    model: String,
    active: Boolean,
    status: String,
    contextUsed: Float,
    contextLeft: String,
    verifiedRate: String,
    freeOffers: Int,
    onRefresh: () -> Unit,
    refreshEnabled: Boolean
) {
    val remaining = (1f - contextUsed.coerceIn(0f, 1f))
    val ring by animateFloatAsState(remaining, animationSpec = tween(550), label = "combo-context-ring")
    val motion = rememberInfiniteTransition(label = "premium-route-animation")
    val pulse by motion.animateFloat(
        initialValue = .30f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(880), RepeatMode.Reverse),
        label = "route-active-pulse"
    )
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("premium_combo_route_card"),
        shape = RoundedCornerShape(15.dp),
        color = Color(0xFF09121F),
        border = BorderStroke(1.5.dp, MaximusReference.gold),
        shadowElevation = 4.dp
    ) {
        Column(
            Modifier.background(Brush.linearGradient(listOf(
                Color(0xFF2C210D), Color(0xFF07192A), Color(0xFF1C170E))))
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WorkspaceCompanion(working = active, modifier = Modifier.size(91.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (active) "Selecting a route…" else "Smart Combo",
                        color = MaximusReference.white,
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(if (active) "Analyzing your request and finding the best model…"
                        else status,
                        color = MaximusReference.muted, fontSize = 11.sp,
                        lineHeight = 16.sp, maxLines = 3)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        repeat(7) { i ->
                            Box(Modifier.size(if (i < 5) 8.dp else 6.dp)
                                .background(
                                    if (active && i < 5) MaximusReference.gold.copy(
                                        alpha = if (i % 2 == 0) pulse else .9f)
                                    else if (!active && i == 0) MaximusReference.gold
                                    else MaximusReference.stroke,
                                    RoundedCornerShape(50)))
                        }
                    }
                }
            }
            HorizontalDivider(color = MaximusReference.stroke)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountTree, null, Modifier.size(23.dp), tint = MaximusReference.white)
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(provider, color = MaximusReference.white,
                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Provider", color = MaximusReference.muted, fontSize = 10.sp)
                }
                Box(Modifier.width(1.dp).height(38.dp).background(MaximusReference.stroke))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ViewInAr, null, Modifier.size(22.dp), tint = MaximusReference.white)
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(model.ifBlank { "Select model" }, color = MaximusReference.white,
                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Model", color = MaximusReference.muted, fontSize = 10.sp)
                }
            }
            HorizontalDivider(color = MaximusReference.stroke)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Estimated context left · $contextLeft",
                        color = MaximusReference.muted, fontSize = 10.sp, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(12) { idx ->
                            Box(Modifier.weight(1f).height(8.dp).background(
                                if (idx < (ring * 12f).toInt())
                                    MaximusReference.gold else MaximusReference.stroke,
                                RoundedCornerShape(3.dp)))
                        }
                    }
                    Text("Rate tokens: $verifiedRate · Free offers: $freeOffers",
                        color = MaximusReference.muted, fontSize = 10.sp, maxLines = 1)
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { ring },
                        modifier = Modifier.size(46.dp),
                        color = MaximusReference.gold,
                        trackColor = MaximusReference.stroke,
                        strokeWidth = 5.dp)
                    Text("${(remaining * 100).toInt()}%", color = MaximusReference.white,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
                Text("Estimated\ncontext", color = MaximusReference.muted,
                    fontSize = 9.sp, lineHeight = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, null, Modifier.size(15.dp), tint = MaximusReference.muted)
                Spacer(Modifier.width(6.dp))
                Text("Provider credit: Unknown unless reported by API",
                    modifier = Modifier.weight(1f), color = MaximusReference.muted,
                    fontSize = 10.sp, maxLines = 1)
                IconButton(onClick = onRefresh, enabled = refreshEnabled,
                    modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Refresh, "Refresh real provider routes",
                        Modifier.size(17.dp), tint = MaximusReference.gold)
                }
            }
        }
    }
}
