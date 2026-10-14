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
    Column(modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 8.dp
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.tab_api_usage)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.tab_modules)) })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.tab_telemetry)) })
            Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("ElevenLabs Voice") })
            Tab(selected = tab == 4, onClick = { tab = 4 }, text = { Text(stringResource(R.string.tab_language)) })
        }
        when (tab) {
            0 -> ApiControlScreen(viewModel, Modifier.weight(1f))
            1 -> ModulesScreen(Modifier.weight(1f))
            2 -> DiagnosticsScreen(viewModel, themeMode, onThemeModeChange, Modifier.weight(1f))
            3 -> ElevenLabsPanel(Modifier.weight(1f))
            else -> LanguageSettingsScreen(
                selected = selectedLanguage,
                onChange = onLanguageChange,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
