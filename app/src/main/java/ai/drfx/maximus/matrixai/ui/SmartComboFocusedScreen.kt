package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.llm.ConnectionStatus
import java.text.NumberFormat

/**
 * Model choices are drawn exclusively from actual provider discovery.
 * This does not imply an authenticated connection, free balance or remote execution.
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
    var filter by remember { mutableIntStateOf(0) }
    var choice by remember(state.provider, state.models, state.selectedModel) {
        mutableStateOf(state.selectedModel)
    }
    val modelOptions = state.models.filter { model ->
        model.displayName.contains(search, ignoreCase = true) ||
            model.id.contains(search, ignoreCase = true)
    }.filter { model ->
        when (filter) {
            1 -> model.capabilities.any { it.name.contains("CODE", ignoreCase = true) }
            2 -> model.capabilities.any { it.name.contains("VISION", ignoreCase = true) ||
                it.name.contains("IMAGE", ignoreCase = true) }
            3 -> false // local models are managed by the separate local model hub
            else -> true
        }
    }

    LazyColumn(modifier.fillMaxSize().testTag("smart_combo_focused_screen"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            WorkspaceBrand()
            MaximusSectionHeader(stringResource(R.string.dash_combo_title),
                "Right model for every task")
        }
        item {
            MaximusFramedCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, null, tint = MaximusReference.gold)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Smart routing", color = MaximusReference.white,
                            fontWeight = FontWeight.SemiBold)
                        Text(if (state.smartComboEnabled) "Free-first routing enabled"
                            else "Manual routing", color = MaximusReference.muted, fontSize = 11.sp)
                    }
                    MotionSwitch(state.smartComboEnabled, viewModel::setSmartComboEnabled,
                        enabled = !state.isGenerating)
                }
            }
        }
        item {
            MaximusFramedCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WorkspaceCompanion(working = state.isGenerating, modifier = Modifier.size(62.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (state.isGenerating) "Selecting a route..." else
                            smartComboStatusLabel(state), color = MaximusReference.white,
                            fontSize = 12.sp)
                        Text(state.statusMessage, color = MaximusReference.muted,
                            fontSize = 10.sp, maxLines = 2)
                    }
                }
                HorizontalDivider(color = MaximusReference.stroke)
                Text("Verified free offers: ${state.smartComboOffers} · Connected accounts: ${state.smartComboConfiguredProviders}",
                    fontSize = 11.sp, color = MaximusReference.muted)
                OutlinedButton(
                    onClick = viewModel::refreshSmartCombo,
                    enabled = state.smartComboEnabled && !state.smartComboRefreshing && !state.isGenerating,
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Refresh verified routes")
                }
            }
        }
        item {
            MaximusContextStrip(
                usedFraction = metrics.contextUsagePercent,
                remaining = if (state.selectedModel.isBlank()) "Unknown"
                    else NumberFormat.getIntegerInstance().format(metrics.remainingContextTokens),
                provider = state.provider.displayName,
                model = state.selectedModel
            )
        }
        item {
            MaximusFramedCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Choose model", Modifier.weight(1f), fontWeight = FontWeight.Bold,
                        color = MaximusReference.white)
                    Text("${state.models.size} discovered", color = MaximusReference.muted,
                        fontSize = 10.sp)
                }
                OutlinedTextField(
                    search, { search = it.take(120) }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search models...") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    shape = RoundedCornerShape(12.dp)
                )
                MaximusTabs(listOf("All", "Code", "Vision", "Local"), filter, { filter = it })
                if (modelOptions.isEmpty()) {
                    Text(if (filter == 3) "Local models are managed separately in the Local Model Hub."
                        else "No matching verified catalog entries. Connect and discover a provider in Advanced settings.",
                        color = MaximusReference.muted, fontSize = 11.sp)
                }
            }
        }
        items(modelOptions, key = { it.id }) { model ->
            Surface(
                onClick = { choice = model.id },
                shape = RoundedCornerShape(11.dp),
                color = MaximusReference.navy,
                border = BorderStroke(1.dp, if (choice == model.id)
                    MaximusReference.gold else MaximusReference.stroke)
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, null, tint = MaximusReference.muted)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(model.displayName, color = MaximusReference.white,
                            fontSize = 12.sp, maxLines = 1)
                        Text(if (model.verified) "Discovered · verified descriptor" else
                            "Catalog entry · runtime not verified",
                            color = MaximusReference.muted, fontSize = 10.sp)
                    }
                    RadioButton(selected = choice == model.id,
                        onClick = { choice = model.id })
                }
            }
        }
        item {
            Button(
                onClick = { viewModel.selectModel(choice) },
                enabled = !state.isGenerating && choice.isNotBlank() &&
                    state.models.any { it.id == choice },
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaximusReference.gold, contentColor = Color.Black),
                shape = RoundedCornerShape(11.dp)
            ) { Text("Use selected model", fontWeight = FontWeight.SemiBold) }
        }
        item {
            Text("Context is estimated. Provider credit is unknown unless a verified provider snapshot exists.",
                color = MaximusReference.muted, fontSize = 10.sp)
            TextButton(onClick = onAdvanced, modifier = Modifier.fillMaxWidth()) {
                Text("Advanced API, providers, quotas and local models")
                Icon(Icons.Default.ChevronRight, null)
            }
        }
    }
}
