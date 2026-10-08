package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ModuleItem(
    val name: String,
    val source: String,
    val purpose: String,
    val status: String,
    val accent: Color
)

@Composable
fun ModulesScreen(modifier: Modifier = Modifier) {
    val modules = listOf(
        ModuleItem("Maximus News Intelligence", "Forex Factory AI Agent", "Live market catalysts, macro analysis, Forex Factory calendar events & multi-asset sentiment.", "ACTIVE", Color(0xFFB388FF)),
        ModuleItem("Maximus AI Chart Vision", "Gemini 3.8 Multimodal Radar", "Screenshot chart ingestion, dynamic trendline detection, pattern recognition & trade setups.", "ACTIVE", Color(0xFF00E676)),
        ModuleItem("Maximus Control Plane", "AI Ops Hub", "Provider routing, policy, tools, observability and usage control.", "ACTIVE", Color(0xFF4C9EFF)),
        ModuleItem("Maximus Mission Control", "AI Ops Hub Mission Control", "Mission planning, execution paths, approvals and runtime events.", "ACTIVE", Color(0xFF35B98E)),
        ModuleItem("Maximus Intelligence Foundry", "DrFXAi Intelligence Foundry", "Ingestion, sanitization, metadata, classification and lineage.", "FOUNDATION", Color(0xFF299AAF)),
        ModuleItem("Maximus Trading Intelligence", "DrFXAi Trading Intelligence OS", "Indicator, strategy, Pine and market-research intelligence.", "FOUNDATION", Color(0xFFD09414)),
        ModuleItem("Maximus Research Engine", "DrFXAi Research OS", "Research missions, experiments, evidence and failure memory.", "FOUNDATION", Color(0xFF7D5DD1)),
        ModuleItem("Maximus Trading Genome", "DrFXAi Trading Genome", "Indicator DNA, Strategy DNA and reusable trading primitives.", "FOUNDATION", Color(0xFFD09414)),
        ModuleItem("Maximus Quant Lab", "DrFXAi Quant Lab", "OOS, walk-forward, stress, sensitivity and backtest architecture.", "REQUIRES DATA", Color(0xFF299AAF)),
        ModuleItem("Maximus Agent Factory", "DrFXAi Agent Factory", "Capability-gated agent registry, selection and lifecycle.", "ACTIVE", Color(0xFF35B98E))
    )
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("System Modules", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Legacy projects are internal engines of MAXIMUS MATRIX OS, not separate user-facing products.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
        items(modules) { module ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, module.accent.copy(alpha = .4f))
            ) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(module.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(module.status, color = module.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Migrated from: " + module.source,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp
                    )
                    Text(module.purpose, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}
