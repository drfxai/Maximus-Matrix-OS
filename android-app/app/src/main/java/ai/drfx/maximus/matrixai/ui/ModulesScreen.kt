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

private data class ModuleItem(val name: String, val source: String, val purpose: String, val status: String, val color: Color)

@Composable
fun ModulesScreen(modifier: Modifier = Modifier) {
    val modules = listOf(
        ModuleItem("Maximus Control Plane", "AI Ops Hub", "Provider routing, policy, tools, observability and usage control.", "ACTIVE", Color(0xFF4C9EFF)),
        ModuleItem("Maximus Mission Control", "AI Ops Hub Mission Control", "Mission planning, execution paths, approvals and runtime events.", "ACTIVE", Color(0xFF5CF0BC)),
        ModuleItem("Maximus Intelligence Foundry", "DrFXAi Intelligence Foundry", "Ingestion, sanitization, metadata, classification and lineage.", "FOUNDATION", Color(0xFF6AC7D8)),
        ModuleItem("Maximus Trading Intelligence", "DrFXAi Trading Intelligence OS", "Indicator, strategy, Pine and market-research intelligence.", "FOUNDATION", Color(0xFFF3B735)),
        ModuleItem("Maximus Research Engine", "DrFXAi Research OS", "Research missions, experiments, evidence and failure memory.", "FOUNDATION", Color(0xFF9B72FF)),
        ModuleItem("Maximus Trading Genome", "DrFXAi Trading Genome", "Indicator DNA, Strategy DNA and reusable trading primitives.", "FOUNDATION", Color(0xFFF3B735)),
        ModuleItem("Maximus Quant Lab", "DrFXAi Quant Lab", "OOS, walk-forward, stress, sensitivity and backtest architecture.", "REQUIRES DATA", Color(0xFF6AC7D8)),
        ModuleItem("Maximus Agent Factory", "DrFXAi Agent Factory", "Capability-gated agent registry, selection and lifecycle.", "ACTIVE", Color(0xFF5CF0BC))
    )
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("System Modules", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("Legacy projects are internal engines of MAXIMUS MATRIX OS, not separate user-facing products.", color = Color(0xFF87A29B), fontSize = 12.sp, lineHeight = 17.sp)
        }
        items(modules) { module ->
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF071110), border = BorderStroke(1.dp, module.color.copy(alpha = .35f))) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(module.name, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(module.status, color = module.color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Migrated from: " + module.source, color = Color(0xFF718883), fontSize = 9.sp)
                    Text(module.purpose, color = Color(0xFFB8CBC5), fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}
