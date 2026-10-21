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
    Column(modifier.fillMaxSize().testTag("control_hub_screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Control Center", Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, color = MaximusReference.gold)
            TextButton(onClick = onHub, modifier = Modifier.testTag("control_back_hub")) { Text("Hub") }
        }
        // Every section is reachable immediately, including from the focused Smart Combo page.
        ScrollableTabRow(selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary, edgePadding = 8.dp) {
            listOf("Smart Combo", stringResource(R.string.tab_modules), stringResource(R.string.tab_telemetry),
                stringResource(R.string.control_voice), stringResource(R.string.control_models),
                stringResource(R.string.tab_language), "API Control")
                .forEachIndexed { index, label ->
                    Tab(tab == index, { tab = index }, modifier = Modifier.testTag("control_section_$index"),
                        text = { Text(label) })
                }
        }
        when (tab) {
            0 -> SmartComboFocusedScreen(viewModel, onAdvanced = { tab = 6 }, modifier = Modifier.weight(1f))
            1 -> ModulesScreen(Modifier.weight(1f))
            2 -> DiagnosticsScreen(viewModel, themeMode, onThemeModeChange, Modifier.weight(1f))
            3 -> ElevenLabsPanel(Modifier.weight(1f))
            4 -> LocalModelHubPanel(Modifier.weight(1f))
            5 -> LanguageSettingsScreen(selected = selectedLanguage,
                onChange = onLanguageChange, modifier = Modifier.weight(1f))
            else -> ApiControlScreen(viewModel, Modifier.weight(1f))
        }
    }
}
