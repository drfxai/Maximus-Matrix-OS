package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pine Studio is a featured action INSIDE Work, not a new bottom-navigation destination.
 * The market preview shares the actual exchange-backed Pine chart implementation.
 * No preview/mock trading candles, script execution, or backtest results are invented.
 */
@Composable
internal fun PremiumPineWorkCard(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("premium_pine_work_card"),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0B1320),
        border = BorderStroke(1.5.dp, MaximusReference.gold),
        shadowElevation = 5.dp
    ) {
        Column(
            Modifier.background(
                Brush.linearGradient(
                    listOf(Color(0xFF29200D), Color(0xFF091A2B), Color(0xFF07111C))
                )
            ).padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color(0xFF051726),
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, MaximusReference.gold)
                ) {
                    Box(Modifier.size(49.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ShowChart, "Pine Studio",
                            Modifier.size(31.dp), tint = MaximusReference.gold)
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pine Studio", color = MaximusReference.white,
                            fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(7.dp))
                        Surface(shape = RoundedCornerShape(50),
                            color = Color(0xFF30230A),
                            border = BorderStroke(1.dp, MaximusReference.gold)) {
                            Text("AI", Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp, color = MaximusReference.gold)
                        }
                    }
                    Text("Build and test TradingView indicators with AI.",
                        fontSize = 11.sp, color = MaximusReference.muted,
                        maxLines = 2, lineHeight = 15.sp,
                        overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onOpen,
                    modifier = Modifier.testTag("premium_pine_open_icon")) {
                    Icon(Icons.Default.ChevronRight, "Open Pine Studio", tint = MaximusReference.white)
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PineFeatureChip("TradingView indicators", Icons.Default.Code)
                PineFeatureChip("200 candles", Icons.Default.CandlestickChart)
                PineFeatureChip("1m · 5m · 15m · 1h", Icons.Default.Schedule)
                PineFeatureChip("AI assist", Icons.Default.AutoAwesome, gold = true)
            }
            Surface(shape = RoundedCornerShape(11.dp),
                color = Color(0xFF041422),
                border = BorderStroke(1.dp, MaximusReference.stroke)) {
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    // Includes an honest loading/error state when the public market is unreachable.
                    PineLiveMarketChart(compact = true, previewOnly = true)
                }
            }
            ReferenceGoldButton(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth().testTag("open_pine_studio")            ) {
                Icon(Icons.Default.ShowChart, null, Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
                Text("Open Pine Studio", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.width(7.dp))
                Icon(Icons.Default.ChevronRight, null, Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun PineFeatureChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gold: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (gold) Color(0xFF2A200E) else Color(0xFF07182A),
        border = BorderStroke(1.dp,
            if (gold) MaximusReference.gold else MaximusReference.stroke)
    ) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(13.dp),
                tint = if (gold) MaximusReference.gold else MaximusReference.muted)
            Text(label, fontSize = 10.sp, maxLines = 1,
                color = if (gold) MaximusReference.gold else MaximusReference.muted)
        }
    }
}
