package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.llm.ConnectionStatus
import ai.drfx.maximus.matrixai.llm.LlmProvider
import ai.drfx.maximus.matrixai.llm.LlmUiState
import ai.drfx.maximus.matrixai.llm.ModelCompatibilityFilter
import ai.drfx.maximus.matrixai.llm.ModelProbeOutcome
import ai.drfx.maximus.matrixai.llm.ProviderKeyDetector

private val okGreen = Color(0xFF2AD49F)
private val failRed = Color(0xFFF0585B)

/** Key field with show/hide and paste, plus a live hint about which service the key belongs to. */
@Composable
internal fun SmartApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    selected: LlmProvider,
    label: String,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Row {
                    IconButton(onClick = { visible = !visible }, modifier = Modifier.size(36.dp)) {
                        Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            if (visible) "Hide key" else "Show key", Modifier.size(18.dp))
                    }
                    IconButton(onClick = onPaste, modifier = Modifier.size(36.dp).testTag("api_key_paste")) {
                        Icon(Icons.Default.ContentPaste, "Paste key", Modifier.size(18.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("api_key_field")
        )
        KeyDetectionHint(value, selected)
    }
}

@Composable
internal fun KeyDetectionHint(key: String, selected: LlmProvider) {
    val detected = remember(key) { ProviderKeyDetector.detect(key) } ?: return
    val switching = remember(key, selected) { ProviderKeyDetector.mismatch(selected, key) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("api_key_detected")) {
        Icon(if (switching != null) Icons.Default.SwapHoriz else Icons.Default.Verified, null,
            Modifier.size(15.dp), tint = if (switching != null) MaximusReference.orange else okGreen)
        Spacer(Modifier.width(5.dp))
        Text(
            when {
                switching != null -> "This looks like a ${switching.service} key. Connecting will switch to ${switching.service}."
                detected.ambiguous -> "Looks like an OpenAI-style key. If it is from another service, enter that service's endpoint."
                else -> "Looks like a ${detected.service} key."
            },
            fontSize = 11.sp, lineHeight = 14.sp,
            color = if (switching != null) MaximusReference.orange else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Compatibility scan controls and results for the current authenticated catalog. */
@Composable
internal fun ModelCompatibilityPanel(
    state: LlmUiState,
    showAll: Boolean,
    onShowAllChange: (Boolean) -> Unit,
    onScan: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.models.isEmpty()) return
    val scan = state.modelScan
    val evidence = state.modelProbeEvidence
    val passed = state.models.count { evidence[it.id]?.outcome == ModelProbeOutcome.SUCCEEDED }
    val failed = state.models.count { evidence[it.id]?.outcome == ModelProbeOutcome.FAILED }
    val untested = state.models.count { evidence[it.id] == null }
    Surface(
        modifier = modifier.fillMaxWidth().testTag("model_compatibility_panel"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f))
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FactCheck, null, Modifier.size(18.dp), tint = MaximusReference.gold)
                Spacer(Modifier.width(6.dp))
                Text("Model compatibility", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("${state.models.size} listed", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CountPill("✓ $passed answered", okGreen)
                CountPill("✕ $failed failed", failRed)
                CountPill("$untested untested", MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (scan.running) {
                LinearProgressIndicator(
                    progress = { if (scan.total == 0) 0f else scan.tested / scan.total.toFloat() },
                    modifier = Modifier.fillMaxWidth(), color = MaximusReference.gold)
                Text("Testing ${scan.tested} / ${scan.total}…", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (scan.message.isNotBlank() && !scan.running) Text(scan.message, fontSize = 11.sp, lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("model_scan_message"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (scan.running) OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Stop test") }
                else Button(
                    onClick = onScan,
                    enabled = untested > 0 && !state.isGenerating && state.status !in setOf(ConnectionStatus.DETECTING, ConnectionStatus.VALIDATING),
                    modifier = Modifier.weight(1f).testTag("model_scan_start"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaximusReference.gold, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.PlaylistAddCheck, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (passed + failed == 0) "Find working models" else "Test remaining models", fontSize = 12.sp, maxLines = 1)
                }
            }
            if (ModelCompatibilityFilter.hasPassing(state.models, evidence)) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show untested and failed models", fontSize = 12.sp, modifier = Modifier.weight(1f))
                Switch(checked = showAll, onCheckedChange = onShowAllChange, modifier = Modifier.testTag("model_show_all"))
            }
            Text("Each test sends one tiny request (“Reply only OK.”, at most 16 output tokens) and may use credits or free-tier quota.",
                fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CountPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = .12f), border = BorderStroke(1.dp, color.copy(alpha = .4f))) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, color = color, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

/** Confirmation that names the request count before any compatibility test is sent. */
@Composable
internal fun ModelScanConfirmDialog(state: LlmUiState, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    val revision = remember { state.apiInputRevision }
    val count = remember(state.models, state.modelProbeEvidence) {
        state.models.count { state.modelProbeEvidence[it.id] == null }.coerceAtMost(40)
    }
    if (revision != state.apiInputRevision) { LaunchedEffect(Unit) { onDismiss() }; return }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.PlaylistAddCheck, null) },
        title = { Text("Find models that work with this key") },
        text = {
            Text("Send up to $count short test requests to ${state.provider.displayName} (3 at a time)? " +
                "Each asks for “OK” with at most 16 output tokens and can consume credits or free-tier quota. " +
                "The test stops if the key is rejected or a rate limit is reached. No chat content is sent.")
        },
        confirmButton = { TextButton(onClick = { onConfirm(revision) }, modifier = Modifier.testTag("model_scan_confirm")) { Text("Test $count models") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
