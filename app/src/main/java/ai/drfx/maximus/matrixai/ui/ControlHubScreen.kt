package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.ui.theme.AppThemeMode
import ai.drfx.maximus.matrixai.ui.theme.AppLanguage
import androidx.compose.ui.platform.testTag
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import ai.drfx.maximus.matrixai.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.llm.ConnectionStatus
import ai.drfx.maximus.matrixai.llm.LlmUiState

@Composable
fun ControlHubScreen(
    viewModel: MatrixViewModel,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    selectedLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
    onHub: () -> Unit = {}
) {
    var tab by remember { mutableIntStateOf(0) }
    BackHandler(enabled = tab != 0) { tab = 0 }
    val state by viewModel.llmState.collectAsState()
    // Most-used first: routing, then the API key and model setup that routing depends on.
    val sections = listOf(
        ControlSection("Smart Combo", Icons.Default.Bolt),
        ControlSection("API Control", Icons.Default.Key),
        ControlSection(stringResource(R.string.control_models), Icons.Default.Memory),
        ControlSection(stringResource(R.string.control_voice), Icons.Default.RecordVoiceOver),
        ControlSection(stringResource(R.string.tab_modules), Icons.Default.Extension),
        ControlSection(stringResource(R.string.tab_telemetry), Icons.Default.Insights),
        ControlSection(stringResource(R.string.tab_language), Icons.Default.Translate)
    )
    Column(modifier.fillMaxSize().testTag("control_hub_screen")) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onHub) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Control Center", style = MaterialTheme.typography.titleMedium,
                    color = MaximusReference.gold, fontWeight = FontWeight.Bold)
                Text(controlStatusLine(state), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("control_status_line"))
            }
            TextButton(onClick = onHub, modifier = Modifier.testTag("control_back_hub")) { Text("Hub") }
        }
        // Every section is reachable immediately, including from the focused Smart Combo page.
        ScrollableTabRow(selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaximusReference.gold, edgePadding = 8.dp,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }) {
            sections.forEachIndexed { index, section ->
                Tab(tab == index, { tab = index }, modifier = Modifier.testTag("control_section_$index"),
                    selectedContentColor = MaximusReference.gold,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(section.icon, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(section.label, maxLines = 1)
                        }
                    })
            }
        }
        when (tab) {
            0 -> SmartComboFocusedScreen(viewModel, onAdvanced = { tab = 1 }, modifier = Modifier.weight(1f))
            1 -> ApiControlScreen(viewModel, Modifier.weight(1f))
            2 -> LocalModelHubPanel(Modifier.weight(1f))
            3 -> ElevenLabsPanel(Modifier.weight(1f))
            4 -> ModulesScreen(Modifier.weight(1f))
            5 -> DiagnosticsScreen(viewModel, themeMode, onThemeModeChange, Modifier.weight(1f))
            else -> LanguageSettingsScreen(selected = selectedLanguage,
                onChange = onLanguageChange, modifier = Modifier.weight(1f))
        }
    }
}

private data class ControlSection(val label: String, val icon: ImageVector)

private fun controlStatusLine(state: LlmUiState): String = buildString {
    append(if (state.smartComboEnabled) "Smart Combo on" else "Direct")
    append(" · ").append(state.provider.displayName)
    append(" · ").append(state.selectedModel.ifBlank { "no model selected" })
    append(" · ").append(when (state.status) {
        ConnectionStatus.CONNECTED -> "connected"
        ConnectionStatus.DEGRADED -> "degraded"
        ConnectionStatus.DETECTING, ConnectionStatus.VALIDATING -> "checking…"
        ConnectionStatus.CONFIGURED -> "key saved"
        else -> "not connected"
    })
}
