package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.plugins.*
import ai.drfx.maximus.matrixai.studio.connections.DeveloperCredentialVault
import ai.drfx.maximus.matrixai.studio.connections.DevelopmentService

/** Curated native integrations, with exact manifest review; no remote code is loaded. */
@Composable
internal fun PluginStorePanel(onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val registry = remember(context) { NativePlugins.registry(context) }
    val vault = remember(context) { DeveloperCredentialVault(context) }
    var generation by remember { mutableIntStateOf(0) }
    var review by remember { mutableStateOf<PluginCatalog.Plugin?>(null) }
    var removal by remember { mutableStateOf<PluginCatalog.Plugin?>(null) }
    var failed by remember { mutableStateOf(false) }
    fun name(id: String): Int = when (id) {
        "github" -> R.string.plugin_github
        "cloudflare" -> R.string.plugin_cloudflare
        "phone-apk" -> R.string.plugin_apk
        else -> R.string.plugin_linux
    }
    ElevatedCard(Modifier.fillMaxWidth().testTag("plugin_store_panel")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.plugin_store_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.plugin_store_description), style = MaterialTheme.typography.bodySmall)
            PluginCatalog.ALL.forEach { plugin ->
                val installed = remember(plugin.id, generation) { registry.installed(plugin.id) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(name(plugin.id)))
                        Text(stringResource(if (installed) R.string.plugin_installed else R.string.plugin_available),
                            style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { if (installed) removal = plugin else review = plugin },
                        modifier = Modifier.testTag("plugin_" + plugin.id)) {
                        Text(stringResource(if (installed) R.string.plugin_remove else R.string.plugin_install))
                    }
                }
            }
            if (failed) Text(stringResource(R.string.plugin_failed), color = MaterialTheme.colorScheme.error)
        }
    }
    review?.let { plugin ->
        AlertDialog(onDismissRequest = { review = null },
            title = { Text(stringResource(R.string.plugin_permissions)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(name(plugin.id)))
                Text(stringResource(when (plugin.id) {
                    "github" -> R.string.plugin_github_permissions
                    "cloudflare" -> R.string.plugin_cloudflare_permissions
                    "phone-apk" -> R.string.plugin_apk_permissions
                    else -> R.string.plugin_linux_permissions
                }))
                Text("SHA-256: " + plugin.fingerprint, style = MaterialTheme.typography.labelSmall)
            } },
            confirmButton = { TextButton(onClick = {
                failed = runCatching { registry.install(plugin.id, plugin.fingerprint, approved = true) }.isFailure
                generation++; review = null; onChanged()
            }, modifier = Modifier.testTag("plugin_confirm_install")) { Text(stringResource(R.string.plugin_install)) } },
            dismissButton = { TextButton(onClick = { review = null }, modifier = Modifier.testTag("plugin_cancel_review")) { Text(stringResource(R.string.linux_cancel)) } })
    }
    removal?.let { plugin ->
        AlertDialog(onDismissRequest = { removal = null }, title = { Text(stringResource(R.string.plugin_remove)) },
            text = { Text(stringResource(R.string.plugin_remove_description)) },
            confirmButton = { TextButton(onClick = {
                failed = runCatching {
                    registry.remove(plugin.id)
                    when (plugin.id) {
                        "github" -> vault.clear(DevelopmentService.GITHUB)
                        "cloudflare" -> vault.clear(DevelopmentService.CLOUDFLARE)
                        else -> Unit
                    }
                }.isFailure
                generation++; removal = null; onChanged()
            }, modifier = Modifier.testTag("plugin_confirm_remove")) { Text(stringResource(R.string.plugin_remove)) } },
            dismissButton = { TextButton(onClick = { removal = null }) { Text(stringResource(R.string.linux_cancel)) } })
    }
}
