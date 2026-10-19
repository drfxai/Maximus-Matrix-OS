package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.ui.theme.AppThemeMode
import ai.drfx.maximus.matrixai.ui.theme.AppLanguage
import ai.drfx.maximus.matrixai.R
import androidx.compose.ui.res.stringResource

@Composable
fun ControlHubScreen(
    viewModel: MatrixViewModel,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    selectedLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableIntStateOf(0) }
    if (tab == 0) {
        SmartComboFocusedScreen(viewModel, onAdvanced = { tab = 6 }, modifier = modifier)
    } else {
        Column(modifier.fillMaxSize()) {
            ScrollableTabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 8.dp
            ) {
                Tab(false, { tab = 0 }, text = { Text("Smart Combo") })
                Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.tab_modules)) })
                Tab(tab == 2, { tab = 2 }, text = { Text(stringResource(R.string.tab_telemetry)) })
                Tab(tab == 3, { tab = 3 }, text = { Text(stringResource(R.string.control_voice)) })
                Tab(tab == 4, { tab = 4 }, text = { Text(stringResource(R.string.control_models)) })
                Tab(tab == 5, { tab = 5 }, text = { Text(stringResource(R.string.tab_language)) })
                Tab(tab == 6, { tab = 6 }, text = { Text(stringResource(R.string.tab_api_usage)) })
            }
            when (tab) {
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
}
