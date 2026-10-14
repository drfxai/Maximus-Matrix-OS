package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.studio.connections.DeveloperCredentialVault
import ai.drfx.maximus.matrixai.studio.connections.DeveloperServiceClient
import ai.drfx.maximus.matrixai.studio.connections.DevelopmentService
import ai.drfx.maximus.matrixai.studio.connections.GithubSourceFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * An explicit local user-owned connection editor. Credentials are encrypted
 * separately and never shared with autonomous tasks or AI prompts.
 */
@Composable
fun DeveloperConnectionsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vault = remember(context) { DeveloperCredentialVault(context) }
    val client = remember(context) { DeveloperServiceClient(vault) }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(DevelopmentService.GITHUB) }
    var rawToken by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }
    var newBranch by remember { mutableStateOf("feature/mobile-review") }
    var filePath by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf<GithubSourceFile?>(null) }
    var proposedText by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf("") }
    var workerNames by remember { mutableStateOf(emptyList<String>()) }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var credentialSaved by remember { mutableStateOf(vault.configured(selected)) }

    fun perform(action: suspend () -> String) {
        if (isBusy) return
        isBusy = true
        scope.launch {
            try {
                feedback = action().take(350)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                feedback = e.message?.take(250) ?: "Service operation failed."
            } finally {
                isBusy = false
            }
        }
    }

    ElevatedCard(modifier.fillMaxWidth().testTag("developer_connection_panel"),
        shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.dev_connections),
                    style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.dev_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected == DevelopmentService.GITHUB, onClick = {
                    selected = DevelopmentService.GITHUB
                    credentialSaved = vault.configured(selected)
                    rawToken = ""
                    feedback = ""
                }, label = { Text("GitHub") })
                FilterChip(selected == DevelopmentService.CLOUDFLARE, onClick = {
                    selected = DevelopmentService.CLOUDFLARE
                    credentialSaved = vault.configured(selected)
                    rawToken = ""
                    feedback = ""
                }, label = { Text("Cloudflare") })
            }
            Text(if (credentialSaved) stringResource(R.string.dev_saved)
                else stringResource(R.string.dev_not_connected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = rawToken, onValueChange = { rawToken = it.take(512) },
                visualTransformation = PasswordVisualTransformation(),
                label = { Text(stringResource(R.string.dev_token)) },
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("developer_token_input")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(enabled = rawToken.isNotBlank() && !isBusy, onClick = {
                    try {
                        vault.save(selected, rawToken)
                        rawToken = ""
                        credentialSaved = true
                        feedback = context.getString(R.string.dev_saved)
                    } catch (e: Exception) { feedback = e.message ?: "Could not encrypt token." }
                }) { Text(stringResource(R.string.dev_save_token)) }
                OutlinedButton(enabled = credentialSaved && !isBusy, onClick = {
                    perform {
                        if (selected == DevelopmentService.GITHUB)
                            client.verifyGithub()
                        else client.verifyCloudflare()
                    }
                }) { Text(stringResource(R.string.dev_verify)) }
                TextButton(enabled = credentialSaved && !isBusy, onClick = {
                    vault.clear(selected)
                    credentialSaved = false
                    loaded = null
                    proposedText = ""
                    workerNames = emptyList()
                    feedback = ""
                }) { Text(stringResource(R.string.dev_revoke)) }
            }
            if (selected == DevelopmentService.GITHUB) {
                Text(stringResource(R.string.dev_git_editor),
                    style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(owner, { owner = it.take(110) },
                        label = { Text(stringResource(R.string.dev_owner)) },
                        singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(repo, { repo = it.take(110) },
                        label = { Text(stringResource(R.string.dev_repository)) },
                        singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(branch, { branch = it.take(180); loaded = null },
                    label = { Text(stringResource(R.string.dev_branch)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(filePath, { filePath = it.take(450); loaded = null },
                    label = { Text(stringResource(R.string.dev_path)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    enabled = credentialSaved && !isBusy &&
                        owner.isNotBlank() && repo.isNotBlank() && filePath.isNotBlank(),
                    onClick = {
                        perform {
                            val result = client.readGithubFile(owner, repo, branch, filePath)
                            loaded = result
                            proposedText = result.text
                            context.getString(R.string.dev_file_loaded)
                        }
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.dev_load_source)) }

                if (loaded != null) {
                    Text(stringResource(R.string.dev_optimistic),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        proposedText, { proposedText = it.take(256 * 1024) },
                        label = { Text(stringResource(R.string.dev_edit_source)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 320.dp)
                            .testTag("developer_github_editor"),
                        maxLines = 14
                    )
                    Button(
                        enabled = !isBusy && proposedText != loaded?.text &&
                            loaded?.branch !in setOf("main", "master", "production", "release"),
                        onClick = { confirmAction = "commit" },
                        modifier = Modifier.fillMaxWidth().testTag("developer_github_commit")
                    ) { Text(stringResource(R.string.dev_commit_review)) }
                }
                HorizontalDivider()
                Text(stringResource(R.string.dev_create_feature),
                    style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    newBranch, { newBranch = it.take(180) },
                    label = { Text(stringResource(R.string.dev_new_branch)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(enabled = credentialSaved && !isBusy &&
                    owner.isNotBlank() && repo.isNotBlank() && branch.isNotBlank() &&
                    newBranch.isNotBlank(), onClick = {
                    confirmAction = "branch"
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.dev_create_branch))
                }
            } else {
                Text(stringResource(R.string.dev_cloudflare_readonly),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(accountId, { accountId = it.take(32) },
                    label = { Text(stringResource(R.string.dev_account)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    enabled = credentialSaved && !isBusy && accountId.isNotBlank(),
                    onClick = {
                        perform {
                            workerNames = client.listCloudflareWorkers(accountId)
                            context.getString(R.string.dev_workers_found, workerNames.size)
                        }
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.dev_list_workers)) }
                workerNames.take(12).forEach { name ->
                    Text("• $name", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (feedback.isNotBlank()) {
                Text(feedback, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.dev_safety),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    val requested = confirmAction
    if (requested != null) {
        val change = loaded
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = {
                Text(if (requested == "commit") stringResource(R.string.dev_confirm_commit)
                else stringResource(R.string.dev_confirm_branch))
            },
            text = {
                Text(if (requested == "commit" && change != null)
                    "${change.owner}/${change.repo} • ${change.branch} • ${change.path}"
                else "$owner/$repo • $branch → $newBranch")
            },
            confirmButton = {
                Button(onClick = {
                    confirmAction = null
                    if (requested == "commit" && change != null) {
                        perform {
                            val commit = client.commitGithubFile(change, proposedText, confirmed = true)
                            loaded = null
                            context.getString(R.string.dev_commit_done) + " " + commit.take(12)
                        }
                    } else if (requested == "branch") {
                        perform {
                            client.createGithubFeatureBranch(owner, repo, branch, newBranch,
                                approved = true)
                            branch = newBranch
                            loaded = null
                            context.getString(R.string.dev_branch_done)
                        }
                    }
                }) { Text(stringResource(R.string.graph_approve)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) {
                    Text(stringResource(R.string.graph_reject))
                }
            }
        )
    }
}
