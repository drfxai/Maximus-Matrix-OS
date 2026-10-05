package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DataCenterScreen(viewModel: MatrixViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.dataCenter.collectAsState()
    var baseUrl by remember(state.baseUrl) { mutableStateOf(state.baseUrl) }
    var token by remember { mutableStateOf("") }
    var query by remember { mutableStateOf(state.query) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Knowledge Data Center", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Gateway for the company database, Pine library, documents, projects and reusable primitives.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Company Data API", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Connect the real company backend. The app does not fabricate Pine-library or database counts.",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Data API base URL") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Access token (optional if local/public)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.connectDataCenter(baseUrl, token) },
                            enabled = !state.busy && baseUrl.isNotBlank()
                        ) {
                            Text(if (state.busy) "Connecting..." else "Connect")
                        }
                        if (state.status.connected) {
                            OutlinedButton(onClick = viewModel::disconnectDataCenter) {
                                Text("Disconnect")
                            }
                        }
                    }
                    Text(
                        state.status.message,
                        color = if (state.status.connected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (state.status.connected) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(state.status.name, fontWeight = FontWeight.SemiBold)
                        DataMetric("Pine sources", state.status.pineSources)
                        DataMetric("Documents", state.status.documents)
                        DataMetric("Projects", state.status.projects)
                        DataMetric("Reusable primitives", state.status.primitives)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Search company knowledge") },
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.searchDataCenter(query) },
                        enabled = !state.busy && query.isNotBlank(),
                        modifier = Modifier.size(54.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Search, "Search")
                    }
                }
            }
            items(state.searchResults) { item ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(item.type.uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 9.sp)
                        }
                        if (item.summary.isNotBlank()) {
                            Spacer(Modifier.height(5.dp))
                            Text(
                                item.summary,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DataMetric(label: String, value: Int?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Text(value?.toString() ?: "Not reported", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
