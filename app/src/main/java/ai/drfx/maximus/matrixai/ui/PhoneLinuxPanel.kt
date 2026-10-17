package ai.drfx.maximus.matrixai.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.studio.phonehost.*
import java.util.UUID

/** Functional controls in the existing Harness layout; no application redesign. */
@Composable
internal fun PhoneLinuxPanel() {
    val context = LocalContext.current
    val session = remember(context) { PhoneLinuxSessions.get(context) }
    val state by session.state.collectAsState()
    var command by remember { mutableStateOf("pwd; uname -m; cat /etc/os-release") }
    var review by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth().testTag("phone_linux_panel")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.linux_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.linux_description), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(command, { if (it.length <= PhoneLinuxCommand.MAX_CHARS) command = it },
                enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("phone_linux_command"),
                label = { Text(stringResource(R.string.linux_command)) }, minLines = 2, maxLines = 5)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { review = command }, enabled = !state.busy && command.isNotBlank(),
                    modifier = Modifier.testTag("phone_linux_run")) { Text(stringResource(R.string.linux_run)) }
                OutlinedButton(onClick = { session.cancel(state.id) }, enabled = state.busy) {
                    Text(stringResource(R.string.linux_stop))
                }
            }
            Text(stringResource(when (state.phase) {
                "running" -> R.string.linux_running
                "finished" -> R.string.linux_finished
                "cancelled" -> R.string.linux_cancelled
                "failed", "timeout" -> R.string.linux_failed
                else -> R.string.linux_idle
            }))
            state.exitCode?.let { Text(stringResource(R.string.linux_exit, it)) }
            if (failed) Text(stringResource(R.string.linux_failed), color = MaterialTheme.colorScheme.error)
            SelectionContainer { Text(state.output, fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("phone_linux_output")) }
        }
    }
    review?.let { captured ->
        AlertDialog(onDismissRequest = { review = null }, title = { Text(stringResource(R.string.linux_review)) },
            text = { Column {
                Text(stringResource(R.string.linux_consent))
                Text(captured, fontFamily = FontFamily.Monospace)
            } }, confirmButton = { TextButton(onClick = {
                review = null; failed = false
                try {
                    PhoneLinuxCommand.validate(captured)
                    context.startForegroundService(Intent(context, PhoneLinuxService::class.java).apply {
                        action = PhoneLinuxService.RUN
                        putExtra(PhoneLinuxService.ID, UUID.randomUUID().toString())
                        putExtra(PhoneLinuxService.COMMAND, captured)
                    })
                } catch (_: Exception) { failed = true }
            }) { Text(stringResource(R.string.linux_run)) } },
            dismissButton = { TextButton(onClick = { review = null }) { Text(stringResource(R.string.linux_cancel)) } })
    }
}
