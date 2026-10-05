package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.drfx.maximus.matrixai.ui.theme.AppThemeMode

private enum class Destination(val label: String) {
    MATRIX("Matrix"),
    CHAT("Chat"),
    AGENTS("Agents"),
    DATA("Data"),
    CONTROL("Control")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MatrixScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: MatrixViewModel = viewModel()
) {
    var destination by remember { mutableStateOf(Destination.MATRIX) }
    val llm by viewModel.llmState.collectAsState()
    val status by viewModel.status.collectAsState()
    val isImeVisible = WindowInsets.isImeVisible

    Scaffold(
        // The root owns IME padding and consumes it before any destination is laid out.
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (destination != Destination.MATRIX && !isImeVisible) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "MAXIMUS AI",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                "MAXIMUS MATRIX OS · V1.0.1",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                letterSpacing = .7.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                status,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (llm.selectedModel.isBlank()) "NO MODEL" else llm.selectedModel.take(20),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!isImeVisible) {
                NavigationBar(
                    modifier = Modifier,
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = destination == Destination.MATRIX,
                        onClick = { destination = Destination.MATRIX },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text(Destination.MATRIX.label, fontSize = 9.sp) }
                    )
                    NavigationBarItem(
                        selected = destination == Destination.CHAT,
                        onClick = { destination = Destination.CHAT },
                        icon = { Icon(Icons.AutoMirrored.Filled.Chat, null) },
                        label = { Text(Destination.CHAT.label, fontSize = 9.sp) }
                    )
                    NavigationBarItem(
                        selected = destination == Destination.AGENTS,
                        onClick = { destination = Destination.AGENTS },
                        icon = { Icon(Icons.Default.Person, null) },
                        label = { Text(Destination.AGENTS.label, fontSize = 9.sp) }
                    )
                    NavigationBarItem(
                        selected = destination == Destination.DATA,
                        onClick = { destination = Destination.DATA },
                        icon = { Icon(Icons.Default.Storage, null) },
                        label = { Text(Destination.DATA.label, fontSize = 9.sp) }
                    )
                    NavigationBarItem(
                        selected = destination == Destination.CONTROL,
                        onClick = { destination = Destination.CONTROL },
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text(Destination.CONTROL.label, fontSize = 9.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            when (destination) {
                Destination.MATRIX -> MatrixGraphScreen(viewModel, Modifier.fillMaxSize())
                Destination.CHAT -> ProviderChatScreen(viewModel, Modifier.fillMaxSize())
                Destination.AGENTS -> AgentsScreen(viewModel, Modifier.fillMaxSize())
                Destination.DATA -> DataCenterScreen(viewModel, Modifier.fillMaxSize())
                Destination.CONTROL -> ControlHubScreen(viewModel, themeMode, onThemeModeChange, Modifier.fillMaxSize())
            }
        }
    }
}

