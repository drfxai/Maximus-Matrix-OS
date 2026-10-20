package ai.drfx.maximus.matrixai.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.llm.*
import java.text.NumberFormat

private val comboCategories = listOf("All", "Code", "Vision", "Audio", "Reasoning", "Local")

/** Category labels never upgrade catalog metadata into verified runtime capabilities. */
internal fun smartComboCategoryMatch(model: ModelDescriptor, category: String): Boolean {
    val id = model.id.lowercase()
    return when (category) {
        "Code" -> ModelCapability.TOOLS in model.capabilities ||
            ModelCapability.STRUCTURED_OUTPUT in model.capabilities ||
            id.contains("code") || id.contains("coder")
        "Vision" -> ModelCapability.VISION in model.capabilities
        "Audio" -> id.contains("audio") || id.contains("whisper") || id.contains("speech")
        "Reasoning" -> ModelCapability.REASONING in model.capabilities
        "Local" -> false // never present a cloud model as installed locally
        else -> true
    }
}

/**
 * Live routing view: animation represents selected route state, not unverified
 * provider connectivity or invented quotas/performance numbers.
 */
@Composable
internal fun SmartComboFocusedScreen(
    viewModel: MatrixViewModel,
    onAdvanced: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.llmState.collectAsState()
    val metrics = state.tokenMetrics
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var usageExpanded by remember { mutableStateOf(false) }
    var choice by remember(state.provider, state.models, state.selectedModel) {
        mutableStateOf(state.selectedModel)
    }
    val filtered = remember(state.models, search, category) {
        state.models.filter {
            (it.id.contains(search, ignoreCase = true) ||
                it.displayName.contains(search, ignoreCase = true)) &&
                smartComboCategoryMatch(it, category)
        }
    }
    val verifiedRate = metrics.verifiedRateTokensRemaining.takeIf {
        it != null && metrics.rateProvider == state.provider &&
            metrics.rateModelId == state.selectedModel &&
            metrics.rateEvidenceAtEpochMs?.let { timestamp ->
                timestamp <= System.currentTimeMillis() &&
                    System.currentTimeMillis() - timestamp < 300_000L
            } == true
    }
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).testTag("smart_combo_focused_screen"),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                WorkspaceBrand()
                MaximusSectionHeader("Smart Combo", "Right model for every task.")
            }
            item {
                MaximusFramedCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, null, tint = MaximusReference.gold)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Smart routing", color = MaximusReference.white,
                                fontWeight = FontWeight.Bold)
                            Text("Automatically selects the best allowed model for your task",
                                fontSize = 11.sp, color = MaximusReference.muted)
                        }
                        MotionSwitch(state.smartComboEnabled, viewModel::setSmartComboEnabled,
                            enabled = !state.isGenerating)
                    }
                }
            }
            item {
                PremiumSmartComboHero(
                    provider = state.provider.displayName,
                    model = state.selectedModel,
                    active = state.smartComboEnabled && state.isGenerating,
                    status = smartComboStatusLabel(state),
                    contextUsed = metrics.contextUsagePercent,
                    contextLeft = if (state.selectedModel.isBlank()) "Unknown" else
                        NumberFormat.getIntegerInstance().format(metrics.remainingContextTokens),
                    verifiedRate = verifiedRate?.let { NumberFormat.getIntegerInstance().format(it) } ?: "Unverified",
                    freeOffers = state.smartComboOffers,
                    onRefresh = viewModel::refreshSmartCombo,
                    refreshEnabled = state.smartComboEnabled && !state.smartComboRefreshing && !state.isGenerating
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Choose model", modifier = Modifier.weight(1f),
                            fontSize = 19.sp,
                            color = MaximusReference.white, fontWeight = FontWeight.Bold)
                        Text("${state.models.size} catalog models", color = MaximusReference.muted,
                            fontSize = 10.sp)
                    }
                    OutlinedTextField(search, { search = it.take(120) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 51.dp),
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text("Search models, providers...") },
                        singleLine = true, shape = RoundedCornerShape(10.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        comboCategories.forEach { item ->
                            FilterChip(
                                selected = item == category, onClick = { category = item },
                                label = { Text(item, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaximusReference.gold,
                                    selectedLabelColor = Color.Black))
                        }
                    }
                    if (filtered.isEmpty()) {
                        Text(if (category == "Local")
                            "Select installed models in the Local Model Hub via Advanced."
                            else "No compatible catalog models. Check provider connection and refresh.",
                            color = MaximusReference.muted, fontSize = 11.sp)
                    }
                }
            }
            items(filtered.size) { index ->
                val model = filtered[index]
                Surface(
                    onClick = { choice = model.id },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (choice == model.id) Color(0xFF231E13) else MaximusReference.navy,
                    shape = RoundedCornerShape(11.dp),
                    border = BorderStroke(if (choice == model.id) 1.6.dp else 1.dp, if (choice == model.id)
                        MaximusReference.gold else MaximusReference.stroke)
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(when {
                            ModelCapability.VISION in model.capabilities -> Icons.Default.Image
                            ModelCapability.TOOLS in model.capabilities -> Icons.Default.Code
                            else -> Icons.Default.Memory
                        }, null, Modifier.size(23.dp), tint = if (choice == model.id)
                            MaximusReference.gold else MaximusReference.muted)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(model.displayName, color = MaximusReference.white,
                                fontSize = 13.sp, maxLines = 2,
                                fontWeight = FontWeight.SemiBold)
                            Text(
                                if (model.verified) "Provider catalog verified"
                                else "Catalog entry · runtime not verified",
                                color = MaximusReference.muted, fontSize = 10.sp)
                            val tags = model.capabilities.filter {
                                it in setOf(ModelCapability.VISION, ModelCapability.REASONING,
                                    ModelCapability.TOOLS, ModelCapability.LONG_CONTEXT)
                            }.take(3).joinToString(" · ") { it.name.lowercase().replace('_', ' ') }
                            if (tags.isNotBlank()) Text(tags, color = MaximusReference.muted,
                                fontSize = 9.sp)
                            val context = model.catalogContextTokens
                            Text("Context: " + (context?.let { NumberFormat.getIntegerInstance().format(it) }
                                ?: "Unverified") +
                                "  ·  Price: " +
                                (model.catalogFreeTextPrice?.let {
                                    if (it.isFree) "Verified zero catalog price"
                                    else if (it.verified) "Catalog price available"
                                    else "Unverified"
                                } ?: "Unknown"),
                                color = MaximusReference.muted, fontSize = 9.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(15.dp), tint = MaximusReference.muted)
                        RadioButton(selected = choice == model.id, onClick = { choice = model.id },
                            colors = RadioButtonDefaults.colors(selectedColor = MaximusReference.gold))
                    }
                }
            }
            item {
                Surface(
                    onClick = { usageExpanded = !usageExpanded },
                    modifier = Modifier.fillMaxWidth().testTag("smart_combo_usage_details"),
                    color = MaximusReference.navy,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaximusReference.stroke)
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DataUsage, null, Modifier.size(17.dp),
                            tint = MaximusReference.gold)
                        Spacer(Modifier.width(7.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Token and quota details", color = MaximusReference.white,
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("Context estimate · " +
                                (if (state.selectedModel.isBlank()) "Unknown"
                                    else NumberFormat.getIntegerInstance()
                                        .format(metrics.remainingContextTokens)) +
                                " · Rate tokens: " +
                                (verifiedRate?.let { NumberFormat.getIntegerInstance().format(it) }
                                    ?: "Unverified"),
                                color = MaximusReference.muted, fontSize = 10.sp,
                                maxLines = 1)
                        }
                        Icon(if (usageExpanded) Icons.Default.ExpandLess
                             else Icons.Default.ExpandMore,
                            if (usageExpanded) "Collapse quota details" else "Expand quota details",
                            modifier = Modifier.size(18.dp), tint = MaximusReference.muted)
                    }
                }
                if (usageExpanded) {
                    Spacer(Modifier.height(5.dp))
                    MaximusContextStrip(
                        usedFraction = metrics.contextUsagePercent,
                        remaining = if (state.selectedModel.isBlank()) "Unknown" else
                            NumberFormat.getIntegerInstance().format(metrics.remainingContextTokens) +
                                " (estimated)",
                        provider = state.provider.displayName,
                        model = state.selectedModel
                    )
                    Text("Verified provider rate tokens: " +
                            (verifiedRate?.let { NumberFormat.getIntegerInstance().format(it) }
                                ?: "Unverified") +
                            " · Context estimates are not billed quota.",
                        color = MaximusReference.muted, fontSize = 10.sp)
                }
            }
            item {
                Text("Models are grouped by catalog attributes, not guaranteed benchmark quality. Live availability requires provider authentication.",
                    color = MaximusReference.muted, fontSize = 10.sp)
                TextButton(onClick = onAdvanced, modifier = Modifier.fillMaxWidth()) {
                    Text("Advanced API · quotas · local models")
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
        Surface(color = Color.Black, border = BorderStroke(1.dp, MaximusReference.stroke)) {
            Button(onClick = { viewModel.selectModel(choice) },
                enabled = choice.isNotBlank() && !state.isGenerating &&
                    state.models.any { it.id == choice },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaximusReference.gold, contentColor = Color.Black),
                shape = RoundedCornerShape(11.dp)) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Use selected model", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SmartComboRouteDiagram(
    provider: String,
    selectedModel: String,
    active: Boolean,
    statusLabel: String
) {
    val transition = rememberInfiniteTransition(label = "smart-route-activity")
    val shimmer by transition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing),
            RepeatMode.Reverse),
        label = "smart-route-pulse"
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(provider, color = MaximusReference.white, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, maxLines = 1)
            Text(if (active) "Routing…" else statusLabel,
                color = MaximusReference.muted, fontSize = 10.sp, maxLines = 2)
        }
        WorkspaceCompanion(working = active, modifier = Modifier.size(51.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(selectedModel.ifBlank { "No model selected" },
                fontSize = 11.sp, maxLines = 2, color = MaximusReference.gold)
            Text("Current selection", fontSize = 9.sp, color = MaximusReference.muted)
        }
    }
    Canvas(Modifier.fillMaxWidth().height(15.dp)) {
        val path = Path().apply {
            moveTo(0f, size.height / 2)
            cubicTo(size.width * .25f, size.height * .05f,
                size.width * .6f, size.height * .95f, size.width, size.height / 2)
        }
        drawPath(path, MaximusReference.stroke, style =
            androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        drawCircle(MaximusReference.gold.copy(alpha = if (active) shimmer else .55f),
            radius = 5f, center = Offset(size.width * .5f, size.height / 2))
    }
}
