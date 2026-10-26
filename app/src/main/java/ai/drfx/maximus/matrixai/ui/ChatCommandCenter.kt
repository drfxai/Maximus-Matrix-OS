package ai.drfx.maximus.matrixai.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.llm.AgentDescriptor
import ai.drfx.maximus.matrixai.llm.ConnectionStatus
import ai.drfx.maximus.matrixai.llm.LlmProvider
import ai.drfx.maximus.matrixai.llm.LlmUiState
import ai.drfx.maximus.matrixai.llm.ModelCapability
import ai.drfx.maximus.matrixai.llm.ModelDescriptor
import ai.drfx.maximus.matrixai.llm.ModelProbeEvidence
import ai.drfx.maximus.matrixai.llm.ModelProbeOutcome
import ai.drfx.maximus.matrixai.llm.ProviderKeyDetector
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

/** Brand colour, icon and short label per provider, shared by the header and the switcher. */
internal object ProviderBrand {
    fun color(provider: LlmProvider): Color = when (provider) {
        LlmProvider.GEMINI -> Color(0xFF6EA8FF)
        LlmProvider.NVIDIA -> Color(0xFF76B900)
        LlmProvider.ROUTER_9_SMART, LlmProvider.ROUTER_9_COMBO -> Color(0xFFB57BFF)
        LlmProvider.OPENAI -> Color(0xFF10A37F)
        LlmProvider.ANTHROPIC -> Color(0xFFE38B5A)
        LlmProvider.OPENAI_COMPATIBLE -> Color(0xFF4FD1C5)
        LlmProvider.UNKNOWN -> MaximusReference.muted
    }

    fun icon(provider: LlmProvider): ImageVector = when (provider) {
        LlmProvider.GEMINI -> Icons.Default.AutoAwesome
        LlmProvider.NVIDIA -> Icons.Default.Memory
        LlmProvider.ROUTER_9_SMART -> Icons.Default.Hub
        LlmProvider.ROUTER_9_COMBO -> Icons.Default.AltRoute
        LlmProvider.OPENAI -> Icons.Default.Bolt
        LlmProvider.ANTHROPIC -> Icons.Default.Psychology
        LlmProvider.OPENAI_COMPATIBLE -> Icons.Default.Cable
        LlmProvider.UNKNOWN -> Icons.Default.HelpOutline
    }

    fun short(provider: LlmProvider): String = when (provider) {
        LlmProvider.GEMINI -> "Gemini"
        LlmProvider.NVIDIA -> "NVIDIA"
        LlmProvider.ROUTER_9_SMART -> "9Router"
        LlmProvider.ROUTER_9_COMBO -> "9Router combo"
        LlmProvider.OPENAI -> "OpenAI"
        LlmProvider.ANTHROPIC -> "Claude"
        LlmProvider.OPENAI_COMPATIBLE -> "Compatible"
        LlmProvider.UNKNOWN -> "Provider"
    }

    val selectable: List<LlmProvider> = LlmProvider.entries.filter { it != LlmProvider.UNKNOWN }
}

/**
 * The one-line status header of Chat: who is answering (provider, model, agent), a live token
 * counter while a reply streams and the exact context tokens left. Tapping it opens the switcher.
 */
@Composable
internal fun ChatLiveHeader(
    state: LlmUiState,
    sessionTitle: String,
    sendable: Boolean,
    onMenu: () -> Unit,
    onOpenSwitcher: () -> Unit,
    onOpenTokens: () -> Unit,
    onSearch: () -> Unit,
    onTools: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val request = state.activeRequest
    val generating = state.isGenerating
    val provider = request?.provider ?: state.provider
    val model = request?.modelId ?: state.selectedModel
    val brand by animateColorAsState(ProviderBrand.color(provider), tween(400), label = "provider-brand")
    val metrics = state.tokenMetrics
    val numbers = remember { NumberFormat.getNumberInstance(Locale.US) }
    val agentName = request?.agentName ?: state.supportedAgents.firstOrNull { it.id == state.selectedAgentId }?.name.orEmpty()
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenu, modifier = Modifier.size(40.dp).testTag("chat_sessions_button")) {
                Icon(Icons.Default.Menu, sessionTitle.ifBlank { "Conversations" })
            }
            Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpenSwitcher)
                .padding(horizontal = 4.dp, vertical = 4.dp).testTag("chat_model_switcher_open"),
                verticalAlignment = Alignment.CenterVertically) {
                ProviderOrb(provider, brand, generating, Modifier.size(34.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedContent(targetState = if (state.smartComboEnabled && request == null) "Smart Combo"
                            else shortModelName(model).ifBlank { "Choose a model" },
                            transitionSpec = { (fadeIn(tween(220)) + scaleIn(initialScale = .92f)) togetherWith fadeOut(tween(120)) },
                            label = "header-model") { label ->
                            Text(label, color = MaximusReference.white, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (state.smartComboEnabled) {
                            Spacer(Modifier.width(5.dp))
                            Text("AUTO", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.background(MaximusReference.gold, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                        Icon(Icons.Default.UnfoldMore, null, Modifier.size(15.dp), tint = MaximusReference.muted)
                    }
                    AnimatedContent(targetState = generating, label = "header-activity") { busy ->
                        if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                            PulseDot(brand)
                            Spacer(Modifier.width(5.dp))
                            Text(agentName.ifBlank { "Assistant" } + " is responding · " + ProviderBrand.short(provider),
                                color = brand, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else Text(
                            when {
                                state.smartComboEnabled -> (if (state.comboReadyModel.isNotBlank())
                                    "Ready: " + shortModelName(state.comboReadyModel) else "${state.smartComboOffers} free routes ready") +
                                    (if (state.comboStandbyModel.isNotBlank()) " · standby " + shortModelName(state.comboStandbyModel) else "")
                                !sendable && !state.hasSavedKey -> "Tap to connect ${ProviderBrand.short(state.provider)}"
                                !sendable -> statusLine(state)
                                else -> ProviderBrand.short(provider) + (if (agentName.isNotBlank()) " · $agentName" else "")
                            },
                            color = if (sendable) MaximusReference.muted else MaximusReference.orange,
                            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            TokenGauge(state, numbers, onOpenTokens)
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp).testTag("chat_header_more")) {
                    Icon(Icons.Default.MoreVert, "More chat actions")
                }
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Search conversation") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                        onClick = { menuOpen = false; onSearch() })
                    DropdownMenuItem(text = { Text("Tools, voice and REA") }, leadingIcon = { Icon(Icons.Default.Tune, null) },
                        onClick = { menuOpen = false; onTools() })
                    DropdownMenuItem(text = { Text("Models & providers") }, leadingIcon = { Icon(Icons.Default.SwapHoriz, null) },
                        onClick = { menuOpen = false; onOpenSwitcher() })
                    DropdownMenuItem(text = { Text("Advanced API settings") }, leadingIcon = { Icon(Icons.Default.Settings, null) },
                        onClick = { menuOpen = false; onSettings() })
                }
            }
        }
        ContextFlowBar(
            usedFraction = if (model.isBlank()) 0f else metrics.contextUsagePercent,
            color = brand,
            streaming = generating
        )
    }
}

private fun statusLine(state: LlmUiState): String = when (state.status) {
    ConnectionStatus.DETECTING -> "Loading models…"
    ConnectionStatus.VALIDATING -> "Testing model…"
    ConnectionStatus.ERROR -> "Connection problem · tap to fix"
    else -> if (state.models.isEmpty()) "Key saved · tap to load models" else "Choose a model"
}

@Composable
private fun ProviderOrb(provider: LlmProvider, brand: Color, working: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")
    val sweep by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "orb-sweep")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(brand.copy(alpha = .14f))
            if (working) drawArc(
                brush = Brush.sweepGradient(listOf(Color.Transparent, brand, Color.Transparent)),
                startAngle = sweep, sweepAngle = 300f, useCenter = false,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            else drawCircle(brand.copy(alpha = .55f), style = Stroke(width = 1.dp.toPx()))
        }
        Icon(ProviderBrand.icon(provider), null, Modifier.size(17.dp), tint = brand)
    }
}

@Composable
private fun PulseDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(.6f, 1.15f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse-scale")
    Box(Modifier.size(7.dp).graphicsLayer { scaleX = scale; scaleY = scale }.background(color, CircleShape))
}

/** Remaining context as an exact number, with a live output counter while a reply streams. */
@Composable
private fun TokenGauge(state: LlmUiState, numbers: NumberFormat, onClick: () -> Unit) {
    val metrics = state.tokenMetrics
    val known = state.selectedModel.isNotBlank() || state.activeRequest != null
    val remaining by animateIntAsState(metrics.remainingContextTokens.coerceAtLeast(0), tween(500), label = "ctx-left")
    val streamed by animateIntAsState(metrics.streamingOutputTokensEstimate, tween(220), label = "stream-tokens")
    val used = metrics.contextUsagePercent.coerceIn(0f, 1f)
    val warn = when {
        used > .85f -> Color(0xFFF0585B)
        used > .6f -> MaximusReference.orange
        else -> MaximusReference.green
    }
    Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
        .padding(horizontal = 6.dp, vertical = 4.dp).testTag("chat_token_gauge"),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            val sweep by animateFloatAsState(if (known) 1f - used else 0f, tween(500), label = "ctx-ring")
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(MaximusReference.stroke, style = Stroke(3.dp.toPx()))
                drawArc(warn, -90f, 360f * sweep, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Spacer(Modifier.width(5.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(if (known) numbers.format(remaining) else "—", color = MaximusReference.white, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
            AnimatedContent(targetState = metrics.streamingEstimateActive, label = "gauge-sub") { live ->
                if (live) Text("↓ ${numbers.format(streamed)} out", color = MaximusReference.green, fontSize = 9.sp, maxLines = 1)
                else Text(
                    metrics.verifiedRateTokensRemaining?.takeIf { metrics.rateModelId == state.selectedModel }
                        ?.let { "left · ${compactTokens(it)} TPM" }
                        ?: ("left · " + compactTokens(metrics.consumedSessionTokens) + " used"),
                    color = MaximusReference.muted, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

/** Thin context bar under the header; a light sweep runs across it while a reply streams. */
@Composable
private fun ContextFlowBar(usedFraction: Float, color: Color, streaming: Boolean) {
    val used by animateFloatAsState(usedFraction.coerceIn(0f, 1f), tween(600), label = "ctx-bar")
    val transition = rememberInfiniteTransition(label = "flow")
    val shift by transition.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "flow-shift")
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        drawRect(MaximusReference.stroke.copy(alpha = .55f))
        drawRect(Brush.horizontalGradient(listOf(color.copy(alpha = .7f), MaximusReference.gold)),
            size = size.copy(width = size.width * used.coerceAtLeast(.01f)))
        if (streaming) {
            val x = size.width * shift
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, color, Color.Transparent),
                startX = x - size.width * .18f, endX = x + size.width * .18f))
        }
    }
}

/**
 * Brief notice when Smart Combo moved a request to another free model; it fades out by itself.
 */
@Composable
internal fun ComboFailoverNotice(notice: String, modifier: Modifier = Modifier) {
    var visible by remember(notice) { mutableStateOf(notice.isNotBlank()) }
    LaunchedEffect(notice) { if (notice.isNotBlank()) { delay(7_000); visible = false } }
    AnimatedVisibility(visible, modifier = modifier, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaximusReference.gold.copy(alpha = .12f),
            border = BorderStroke(1.dp, MaximusReference.gold.copy(alpha = .45f)),
            modifier = Modifier.fillMaxWidth().testTag("combo_failover_notice")) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AltRoute, null, Modifier.size(16.dp), tint = MaximusReference.gold)
                Spacer(Modifier.width(7.dp))
                Text(notice, color = MaximusReference.white, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "Agent · model is writing" card with a live token counter, shown under the last message. */
@Composable
internal fun ChatTypingCard(state: LlmUiState) {
    val request = state.activeRequest
    val provider = request?.provider ?: state.provider
    val brand = ProviderBrand.color(provider)
    val numbers = remember { NumberFormat.getNumberInstance(Locale.US) }
    val tokens by animateIntAsState(state.tokenMetrics.streamingOutputTokensEstimate, tween(200), label = "typing-tokens")
    val started = request?.startedAtEpochMs
    var elapsed by remember(started) { mutableLongStateOf(0L) }
    LaunchedEffect(started) {
        while (started != null) { elapsed = (System.currentTimeMillis() - started).coerceAtLeast(0L); delay(250) }
    }
    val transition = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag("chat_typing_card")) {
        ProviderOrb(provider, brand, true, Modifier.size(30.dp))
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { index ->
                    val alpha by transition.animateFloat(.25f, 1f, infiniteRepeatable(tween(560, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse, StartOffset(index * 140)), label = "typing-dot-$index")
                    Box(Modifier.size(6.dp).graphicsLayer(alpha = alpha).background(brand, CircleShape))
                }
                Spacer(Modifier.width(4.dp))
                Text((request?.agentName ?: "Assistant") + " · " + shortModelName(request?.modelId ?: state.selectedModel),
                    color = MaximusReference.white, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Text(
                (if (tokens > 0) "${numbers.format(tokens)} tokens · " else "Thinking · ") +
                    String.format(Locale.US, "%.1fs", elapsed / 1000.0) +
                    (if (tokens > 0 && elapsed > 500) " · " + (tokens * 1000L / elapsed) + " tok/s" else ""),
                color = MaximusReference.muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

/**
 * Provider and model switcher: providers as animated cards, the current provider's models with
 * test status and capabilities, persona chips, and smart key entry for providers without a key.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelSwitcherSheet(
    state: LlmUiState,
    hasKey: (LlmProvider) -> Boolean,
    startWithKeyEntry: Boolean,
    onDismiss: () -> Unit,
    onSelectProvider: (LlmProvider) -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectAgent: (String) -> Unit,
    onConnectKey: (endpoint: String, key: String) -> Unit,
    onSmartCombo: (Boolean) -> Unit,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onAutoTest: (Boolean) -> Unit,
    onReloadModels: () -> Unit,
    onAdvanced: () -> Unit
) {
    var keyEntry by remember { mutableStateOf(startWithKeyEntry) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF040F1C)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Models & providers", color = MaximusReference.white, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(if (state.smartComboEnabled) "Smart Combo picks a free model and keeps a standby ready"
                        else "Pick who answers next", color = MaximusReference.muted, fontSize = 11.sp)
                }
                SmartComboPill(state.smartComboEnabled, onSmartCombo)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
                items(ProviderBrand.selectable) { provider ->
                    ProviderCard(provider, selected = provider == state.provider, keyed = hasKey(provider),
                        loading = provider == state.provider && state.status == ConnectionStatus.DETECTING,
                        onClick = {
                            if (provider != state.provider) onSelectProvider(provider)
                            keyEntry = !hasKey(provider)
                        })
                }
            }
            val needsKey = keyEntry || !state.hasSavedKey
            AnimatedContent(targetState = needsKey, label = "switcher-mode", modifier = Modifier.weight(1f)) { entering ->
                if (entering) Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmartKeyConnectCard(state, onConnectKey, onSelectModel)
                    if (state.hasSavedKey && state.models.isNotEmpty()) TextButton(onClick = { keyEntry = false }) {
                        Text("Show ${state.models.size} models", color = MaximusReference.gold)
                    }
                } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.supportedAgents.isNotEmpty()) PersonaChips(state.supportedAgents, state.selectedAgentId, onSelectAgent)
                    ModelCatalogList(state, onSelectModel, onScan, onCancelScan, onAutoTest, onReloadModels,
                        onReplaceKey = { keyEntry = true }, modifier = Modifier.weight(1f))
                }
            }
            TextButton(onClick = onAdvanced, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.Settings, null, Modifier.size(16.dp), tint = MaximusReference.muted)
                Spacer(Modifier.width(5.dp))
                Text("Endpoint, test and disconnect", color = MaximusReference.muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SmartComboPill(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val background by animateColorAsState(if (enabled) MaximusReference.gold else Color(0xFF0B2236), label = "combo-pill")
    Surface(onClick = { onChange(!enabled) }, shape = RoundedCornerShape(50), color = background,
        border = BorderStroke(1.dp, MaximusReference.gold.copy(alpha = .6f)), modifier = Modifier.testTag("switcher_smart_combo")) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bolt, null, Modifier.size(16.dp), tint = if (enabled) Color.Black else MaximusReference.gold)
            Spacer(Modifier.width(4.dp))
            Text("Smart Combo", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (enabled) Color.Black else MaximusReference.gold)
        }
    }
}

@Composable
private fun ProviderCard(provider: LlmProvider, selected: Boolean, keyed: Boolean, loading: Boolean, onClick: () -> Unit) {
    val brand = ProviderBrand.color(provider)
    val border by animateColorAsState(if (selected) brand else MaximusReference.stroke, tween(250), label = "card-border")
    val lift by animateFloatAsState(if (selected) 1f else .96f, spring(dampingRatio = .55f), label = "card-lift")
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp),
        color = if (selected) brand.copy(alpha = .12f) else Color(0xFF071A2C),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, border),
        modifier = Modifier.width(98.dp).graphicsLayer { scaleX = lift; scaleY = lift }
            .testTag("switcher_provider_" + provider.name.lowercase())) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(ProviderBrand.icon(provider), null, Modifier.size(20.dp), tint = brand)
                Spacer(Modifier.weight(1f))
                if (loading) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = brand)
                else Box(Modifier.size(8.dp).background(if (keyed) MaximusReference.green else MaximusReference.stroke, CircleShape))
            }
            Text(ProviderBrand.short(provider), color = MaximusReference.white, fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (keyed) "Key saved" else "Add key", color = if (keyed) MaximusReference.green else MaximusReference.muted,
                fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun PersonaChips(agents: List<AgentDescriptor>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("Agent", color = MaximusReference.muted, fontSize = 11.sp)
        agents.forEach { agent ->
            FilterChip(selected = agent.id == selected, onClick = { onSelect(agent.id) },
                label = { Text(agent.name, fontSize = 11.sp, maxLines = 1) },
                leadingIcon = if (agent.id == selected) { { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) } } else null,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaximusReference.gold.copy(alpha = .2f),
                    selectedLabelColor = MaximusReference.gold))
        }
    }
}

private enum class ModelFilter(val label: String) { RESPONSIVE("Responsive"), ALL("All"), VISION("Vision"), REASONING("Reasoning"), FREE("Free") }

@Composable
private fun ModelCatalogList(
    state: LlmUiState,
    onSelect: (String) -> Unit,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onAutoTest: (Boolean) -> Unit,
    onReload: () -> Unit,
    onReplaceKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    val evidence = state.modelProbeEvidence
    var query by remember { mutableStateOf("") }
    val anyPassing = state.models.any { evidence[it.id]?.outcome == ModelProbeOutcome.SUCCEEDED }
    var filter by remember(anyPassing) { mutableStateOf(if (anyPassing) ModelFilter.RESPONSIVE else ModelFilter.ALL) }
    val shown = remember(state.models, evidence, query, filter, state.selectedModel) {
        state.models.filter { model ->
            (query.isBlank() || model.id.contains(query.trim(), ignoreCase = true)) && when (filter) {
                ModelFilter.RESPONSIVE -> evidence[model.id]?.outcome == ModelProbeOutcome.SUCCEEDED || model.id == state.selectedModel
                ModelFilter.ALL -> true
                ModelFilter.VISION -> ModelCapability.VISION in model.capabilities
                ModelFilter.REASONING -> ModelCapability.REASONING in model.capabilities
                ModelFilter.FREE -> model.catalogFreeTextPrice?.isFree == true
            }
        }.sortedWith(compareBy<ModelDescriptor>({ it.id != state.selectedModel },
            { when (evidence[it.id]?.outcome) { ModelProbeOutcome.SUCCEEDED -> 0; null -> 1; ModelProbeOutcome.UNCERTAIN -> 2; ModelProbeOutcome.FAILED -> 3 } },
            { evidence[it.id]?.latencyMs ?: Long.MAX_VALUE }))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.models.isEmpty()) {
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF071A2C), border = BorderStroke(1.dp, MaximusReference.stroke)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.status == ConnectionStatus.DETECTING) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaximusReference.gold)
                        Spacer(Modifier.width(10.dp))
                        Text("Loading the models this key can use…", color = MaximusReference.muted, fontSize = 12.sp)
                    } else {
                        Text(if (state.status == ConnectionStatus.ERROR) state.statusMessage else "Models are not loaded yet.",
                            color = MaximusReference.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = onReload) { Text("Load models", color = MaximusReference.gold) }
                        if (state.status == ConnectionStatus.ERROR) TextButton(onClick = onReplaceKey) { Text("New key", color = MaximusReference.gold) }
                    }
                }
            }
            return@Column
        }
        ScanStrip(state, onScan, onCancelScan, onAutoTest)
        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search ${state.models.size} models", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("switcher_model_search"),
            shape = RoundedCornerShape(14.dp), textStyle = MaterialTheme.typography.bodyMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ModelFilter.entries.forEach { option ->
                FilterChip(selected = filter == option, onClick = { filter = option },
                    label = { Text(option.label, fontSize = 11.sp) })
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 8.dp)) {
            if (shown.isEmpty()) item {
                Text(if (filter == ModelFilter.RESPONSIVE) "No model has answered yet. Run the test or show all models."
                    else "No models match.", color = MaximusReference.muted, fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp))
            }
            items(shown, key = { it.id }) { model ->
                ModelRow(model, evidence[model.id], selected = model.id == state.selectedModel,
                    testing = state.modelScan.running && evidence[model.id] == null, onClick = { onSelect(model.id) })
            }
        }
    }
}

@Composable
private fun ScanStrip(state: LlmUiState, onScan: () -> Unit, onCancel: () -> Unit, onAutoTest: (Boolean) -> Unit) {
    val scan = state.modelScan
    val evidence = state.modelProbeEvidence
    val passed = state.models.count { evidence[it.id]?.outcome == ModelProbeOutcome.SUCCEEDED }
    val untested = state.models.count { evidence[it.id] == null }
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF071A2C), border = BorderStroke(1.dp, MaximusReference.stroke)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (scan.running) Icons.Default.Radar else Icons.Default.FactCheck, null, Modifier.size(17.dp),
                    tint = MaximusReference.gold)
                Spacer(Modifier.width(7.dp))
                Text(when {
                    scan.running -> "Testing ${scan.tested}/${scan.total} · ${scan.passed} answered"
                    passed > 0 -> "$passed of ${state.models.size} models answered"
                    else -> "${state.models.size} models listed · not tested yet"
                }, color = MaximusReference.white, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (scan.running) TextButton(onClick = onCancel) { Text("Stop", fontSize = 12.sp) }
                else if (untested > 0) TextButton(onClick = onScan, enabled = !state.isGenerating,
                    modifier = Modifier.testTag("switcher_scan")) { Text(if (passed == 0) "Test" else "Test more", fontSize = 12.sp, color = MaximusReference.gold) }
            }
            if (scan.running) LinearProgressIndicator(
                progress = { if (scan.total == 0) 0f else scan.tested / scan.total.toFloat() },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)), color = MaximusReference.gold,
                trackColor = MaximusReference.stroke)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-test new keys (one tiny “OK” request per model, may use quota)", color = MaximusReference.muted,
                    fontSize = 10.sp, lineHeight = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = state.autoTestModels, onCheckedChange = onAutoTest, modifier = Modifier.height(28.dp)
                    .graphicsLayer { scaleX = .8f; scaleY = .8f }.testTag("switcher_auto_test"))
            }
        }
    }
}

@Composable
private fun ModelRow(model: ModelDescriptor, evidence: ModelProbeEvidence?, selected: Boolean, testing: Boolean, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) MaximusReference.gold else MaximusReference.stroke.copy(alpha = .7f), label = "model-border")
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp),
        color = if (selected) MaximusReference.gold.copy(alpha = .08f) else Color(0xFF061626),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, border),
        modifier = Modifier.fillMaxWidth().testTag("switcher_model_row")) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(shortModelName(model.id), color = MaximusReference.white, fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val context = model.catalogContextTokens ?: model.contextWindowTokens
                    MiniTag(compactTokens(context.toLong()) + " ctx", MaximusReference.muted)
                    if (ModelCapability.VISION in model.capabilities) MiniTag("vision", Color(0xFF8FD3FF))
                    if (ModelCapability.REASONING in model.capabilities) MiniTag("reasoning", Color(0xFFB57BFF))
                    if (ModelCapability.TOOLS in model.capabilities) MiniTag("tools", MaximusReference.orange)
                    if (model.catalogFreeTextPrice?.isFree == true) MiniTag("free", MaximusReference.green)
                }
            }
            Spacer(Modifier.width(8.dp))
            ProbeBadge(evidence, testing)
            if (selected) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = MaximusReference.gold)
            }
        }
    }
}

@Composable
private fun MiniTag(text: String, color: Color) {
    Text(text, color = color, fontSize = 9.sp, maxLines = 1,
        modifier = Modifier.border(1.dp, color.copy(alpha = .35f), RoundedCornerShape(5.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
}

@Composable
private fun ProbeBadge(evidence: ModelProbeEvidence?, testing: Boolean) {
    when {
        evidence?.outcome == ModelProbeOutcome.SUCCEEDED -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bolt, null, Modifier.size(13.dp), tint = MaximusReference.green)
            Text(evidence.latencyMs?.let { if (it < 1000) "$it ms" else String.format(Locale.US, "%.1f s", it / 1000.0) } ?: "OK",
                color = MaximusReference.green, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        evidence?.outcome == ModelProbeOutcome.FAILED -> Text("✕ no reply", color = Color(0xFFF0585B), fontSize = 10.sp)
        evidence?.outcome == ModelProbeOutcome.UNCERTAIN -> Text("? busy", color = MaximusReference.orange, fontSize = 10.sp)
        testing -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = MaximusReference.gold)
        else -> Text("untested", color = MaximusReference.muted.copy(alpha = .7f), fontSize = 10.sp)
    }
}

private enum class KeyStep { RECOGNISE, VERIFY, LIST, TEST }

/**
 * Smart key entry. Pasting (or typing a recognisable key) connects automatically: the provider is
 * detected from the key, the key is verified by listing models, and the models are then tested so
 * only the ones that answer are offered first.
 */
@Composable
internal fun SmartKeyConnectCard(
    state: LlmUiState,
    onConnect: (endpoint: String, key: String) -> Unit,
    onSelectModel: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var key by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf(state.baseUrl.ifBlank { state.provider.defaultBaseUrl }) }
    var requested by remember { mutableStateOf("") }
    // A key for another service switches the provider mid-connect; keep that flow visible.
    // Only a manual provider change with nothing in flight starts the card over.
    LaunchedEffect(state.provider) {
        if (requested.isBlank()) endpoint = state.baseUrl.ifBlank { state.provider.defaultBaseUrl }
    }
    val detected = remember(key) { ProviderKeyDetector.detect(key) }
    val switching = remember(key, state.provider) { ProviderKeyDetector.mismatch(state.provider, key) }
    val needsEndpoint = state.provider.isNineRouter || state.provider == LlmProvider.OPENAI_COMPATIBLE ||
        switching?.provider == LlmProvider.OPENAI_COMPATIBLE || detected?.ambiguous == true
    LaunchedEffect(detected) {
        val suggested = switching?.baseUrl ?: detected?.takeIf { !it.ambiguous }?.baseUrl
        if (suggested != null && needsEndpoint) endpoint = suggested
    }
    fun connect(value: String = key) {
        val trimmed = value.trim()
        if (trimmed.isBlank() || trimmed == requested) return
        requested = trimmed
        onConnect(if (needsEndpoint) endpoint.trim() else "", trimmed)
    }
    // A recognisable, complete-looking key connects on its own after a short pause in typing.
    LaunchedEffect(key) {
        val candidate = key.trim()
        if (candidate.length >= 20 && ProviderKeyDetector.detect(candidate)?.ambiguous == false) {
            delay(700)
            connect(candidate)
        }
    }
    val active = requested.isNotBlank()
    val step = when {
        !active -> null
        state.status == ConnectionStatus.DETECTING -> KeyStep.VERIFY
        state.status == ConnectionStatus.ERROR -> KeyStep.VERIFY
        state.modelScan.running -> KeyStep.TEST
        state.models.isNotEmpty() -> if (state.modelScan.tested > 0 || !state.autoTestModels) KeyStep.TEST else KeyStep.LIST
        else -> KeyStep.VERIFY
    }
    val failed = active && state.status == ConnectionStatus.ERROR
    val finished = active && !state.modelScan.running && state.models.isNotEmpty() &&
        (state.modelScan.tested > 0 || !state.autoTestModels)
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF071A2C),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(ProviderBrand.color(state.provider), MaximusReference.gold.copy(alpha = .5f)))),
        modifier = Modifier.fillMaxWidth().testTag("smart_key_card")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, null, Modifier.size(18.dp), tint = MaximusReference.gold)
                Spacer(Modifier.width(7.dp))
                Text("Connect ${switching?.service ?: ProviderBrand.short(state.provider)}", color = MaximusReference.white,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("encrypted on device", color = MaximusReference.muted, fontSize = 10.sp)
            }
            OutlinedTextField(value = key, onValueChange = { key = it; if (it.isBlank()) requested = "" },
                singleLine = true, placeholder = { Text("Paste your API key", fontSize = 13.sp) },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = {
                        val clip = clipboard.getText()?.text.orEmpty().trim()
                        if (clip.isNotBlank()) { key = clip; connect(clip) }
                    }, modifier = Modifier.testTag("smart_key_paste")) { Icon(Icons.Default.ContentPaste, "Paste key") }
                },
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("smart_key_input"))
            if (key.isNotBlank()) KeyDetectionHint(key, state.provider)
            AnimatedVisibility(needsEndpoint, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                OutlinedTextField(value = endpoint, onValueChange = { endpoint = it; requested = "" }, singleLine = true,
                    label = { Text("Endpoint (https)") }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            }
            AnimatedVisibility(active, enter = expandVertically() + fadeIn()) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    StepRow("Key recognised" + (detected?.let { " · ${it.service}" } ?: ""), done = true, running = false, failed = false)
                    StepRow(if (failed) state.statusMessage else "Verified with the provider",
                        done = step != null && step > KeyStep.VERIFY, running = step == KeyStep.VERIFY && !failed, failed = failed)
                    StepRow(if (state.models.isEmpty()) "Listing chat models" else "${state.models.size} chat models listed",
                        done = step != null && step >= KeyStep.LIST && state.models.isNotEmpty(), running = false, failed = false)
                    if (state.autoTestModels) StepRow(
                        when {
                            state.modelScan.running -> "Testing responsiveness ${state.modelScan.tested}/${state.modelScan.total}"
                            state.modelScan.tested > 0 -> "${state.modelScan.passed} models answered"
                            else -> "Testing which models answer"
                        },
                        done = finished, running = state.modelScan.running || (step == KeyStep.LIST && !finished), failed = false)
                }
            }
            if (!active || failed) Button(onClick = { requested = ""; connect() }, enabled = key.isNotBlank() &&
                state.status != ConnectionStatus.DETECTING, modifier = Modifier.fillMaxWidth().testTag("api_connect_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaximusReference.gold, contentColor = Color.Black)) {
                Text(if (failed) "Try again" else "Connect & find working models", fontWeight = FontWeight.Bold)
            }
            val evidence = state.modelProbeEvidence
            val responsive = remember(state.models, evidence) {
                state.models.filter { evidence[it.id]?.outcome == ModelProbeOutcome.SUCCEEDED }
                    .sortedBy { evidence[it.id]?.latencyMs ?: Long.MAX_VALUE }
            }
            AnimatedVisibility(active && responsive.isNotEmpty(), enter = expandVertically() + fadeIn()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("api_connect_ok")) {
                    Text("Ready to use — fastest first", color = MaximusReference.green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    responsive.take(6).forEach { model ->
                        ModelRow(model, evidence[model.id], selected = model.id == state.selectedModel, testing = false,
                            onClick = { onSelectModel(model.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(label: String, done: Boolean, running: Boolean, failed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            when {
                failed -> Icon(Icons.Default.Error, null, Modifier.size(16.dp), tint = Color(0xFFF0585B))
                done -> {
                    val pop by animateFloatAsState(1f, spring(dampingRatio = .4f), label = "step-pop")
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp).graphicsLayer { scaleX = pop; scaleY = pop },
                        tint = MaximusReference.green)
                }
                running -> CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.5.dp, color = MaximusReference.gold)
                else -> Box(Modifier.size(8.dp).background(MaximusReference.stroke, CircleShape))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (failed) Color(0xFFF0585B) else if (done) MaximusReference.white else MaximusReference.muted,
            fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
