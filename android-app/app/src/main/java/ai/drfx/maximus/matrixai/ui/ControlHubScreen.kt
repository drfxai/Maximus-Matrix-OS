package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun ControlHubScreen(viewModel: MatrixViewModel, modifier: Modifier = Modifier) {
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = tab,
            containerColor = Color(0xFF030707),
            contentColor = Color(0xFF5CF0BC)
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("API & Usage") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Modules") })
        }
        when (tab) {
            0 -> ApiControlScreen(viewModel, Modifier.weight(1f))
            else -> ModulesScreen(Modifier.weight(1f))
        }
    }
}
