package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.ui.theme.AppThemeMode

@Composable
fun ControlHubScreen(
    viewModel: MatrixViewModel,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
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
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("API & Usage") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Modules") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Diagnostics") })
        }
        when (tab) {
            0 -> ApiControlScreen(viewModel, Modifier.weight(1f))
            1 -> ModulesScreen(Modifier.weight(1f))
            else -> DiagnosticsScreen(themeMode, onThemeModeChange, Modifier.weight(1f))
        }
    }
}
