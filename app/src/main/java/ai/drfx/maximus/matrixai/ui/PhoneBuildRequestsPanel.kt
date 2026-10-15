package ai.drfx.maximus.matrixai.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneHostSession
import ai.drfx.maximus.matrixai.studio.phonehost.PhoneLocalBuildService
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** Only this visible on-phone confirmation can arm a requested source snapshot. */
@Composable
internal fun PhoneBuildRequestsPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf<ai.drfx.maximus.matrixai.studio.phonehost.PhoneHarnessHttpServer?>(null) }
    var views by remember { mutableStateOf<List<ai.drfx.maximus.matrixai.studio.phonehost.PhoneBuildJobs.View>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ai.drfx.maximus.matrixai.studio.phonehost.PhoneBuildJobs.View?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            val active = PhoneHostSession.active
            val current = withContext(Dispatchers.IO) { active?.jobs?.views().orEmpty() }
            host = active
            views = current
            delay(1000)
        }
    }
    val server = host
    val pending = views.firstOrNull { it.phase == "awaiting_approval" }
    if (pending != null) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.phone_jobs_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.phone_jobs_summary, pending.projectId.take(8), pending.files.size, pending.bytes))
                Text(pending.sha256, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { selected = pending }) { Text(stringResource(R.string.phone_jobs_review)) }
                    OutlinedButton(onClick = { scope.launch { withContext(Dispatchers.IO) { server?.jobs?.cancel(pending.id) } } }) {
                        Text(stringResource(R.string.phone_jobs_reject))
                    }
                }
            }
        }
    }
    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    val snapshot = selected
    if (snapshot != null) AlertDialog(
        onDismissRequest = { selected = null },
        title = { Text(stringResource(R.string.phone_jobs_approve_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.phone_jobs_warning))
                Text(snapshot.sha256, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                Text(snapshot.files.take(12).joinToString("\n") + if (snapshot.files.size > 12) "\n…" else "",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                selected = null
                scope.launch { runCatching {
                    check(server != null && PhoneHostSession.active === server)
                    withContext(Dispatchers.IO) { server.jobs.approve(snapshot.id, snapshot.sha256) }
                    check(PhoneHostSession.active === server)
                    try {
                        context.startForegroundService(Intent(context, PhoneLocalBuildService::class.java).apply {
                            action = PhoneLocalBuildService.BUILD
                            putExtra(PhoneLocalBuildService.JOB_ID, snapshot.id)
                        })
                    } catch (e: Exception) {
                        withContext(Dispatchers.IO) { server.jobs.cancel(snapshot.id) }
                        throw e
                    }
                    error = ""
                }.onFailure { error = context.getString(R.string.phone_jobs_expired) } }
            }) { Text(stringResource(R.string.phone_jobs_approve)) }
        },
        dismissButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.phone_jobs_back)) } }
    )
}
