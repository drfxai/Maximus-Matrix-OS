package ai.drfx.maximus.matrixai.ui

import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.security.SecureRandom
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import ai.drfx.maximus.matrixai.studio.connections.CloudAgentPurpose
import ai.drfx.maximus.matrixai.studio.connections.CloudflareAccount
import ai.drfx.maximus.matrixai.studio.connections.DeveloperSetupPolicy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
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

private data class DeveloperDeployReview(
    val accountId: String,
    val workerName: String,
    val purpose: CloudAgentPurpose,
    val accessKey: String
)

/**
 * An explicit local user-owned connection editor. Credentials are encrypted
 * separately and never shared with autonomous tasks or AI prompts.
 */
@Composable
fun DeveloperConnectionsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val vault = remember(context) { DeveloperCredentialVault(context) }
    val client = remember(context) { DeveloperServiceClient(vault) }
    val scope = rememberCoroutineScope()
    var verified by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var repositoryUrl by remember { mutableStateOf("") }
    var accounts by remember { mutableStateOf(emptyList<CloudflareAccount>()) }
    var purpose by remember { mutableStateOf(CloudAgentPurpose.ASSISTANT) }
    var workerName by remember { mutableStateOf(DeveloperSetupPolicy.suggestedWorkerName(CloudAgentPurpose.ASSISTANT)) }
    var accessKey by remember { mutableStateOf("") }
    var sourceExpanded by remember { mutableStateOf(false) }
    var deployReview by remember { mutableStateOf<DeveloperDeployReview?>(null) }
    var deploymentDone by remember { mutableStateOf(false) }
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
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                feedback = context.getString(R.string.dev_operation_failed)
            } finally {
                isBusy = false
            }
        }
    }

    ElevatedCard(modifier.fillMaxWidth().testTag("developer_connection_panel"),
        shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    verified = false
                    accounts = emptyList()
                    accountId = ""
                    accessKey = ""
                    deploymentDone = false; feedback = ""
                }, enabled = !isBusy, label = { Text("GitHub") })
                FilterChip(selected == DevelopmentService.CLOUDFLARE, onClick = {
                    selected = DevelopmentService.CLOUDFLARE
                    credentialSaved = vault.configured(selected)
                    rawToken = ""
                    feedback = ""
                    verified = false
                    accounts = emptyList()
                    accountId = ""
                    accessKey = ""
                    deploymentDone = false; feedback = ""
                }, enabled = !isBusy, label = { Text("Cloudflare") })
            }
            Text(stringResource(R.string.dev_step_connect), style = MaterialTheme.typography.titleSmall)
            AssistChip(onClick = {}, label = {
                Text(stringResource(if (verified) R.string.dev_verified else if (credentialSaved)
                    R.string.dev_stored_unverified else R.string.dev_not_connected))
            })
            OutlinedButton(onClick = {
                runCatching {
                    val url = if (selected == DevelopmentService.GITHUB)
                        "https://github.com/settings/personal-access-tokens/new"
                    else DeveloperSetupPolicy.cloudflareTokenSetupUrl(accountId)
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                    .onFailure {
                        feedback = context.getString(if (selected == DevelopmentService.CLOUDFLARE &&
                            accountId.isNotBlank() && !Regex("[a-fA-F0-9]{32}").matches(accountId))
                            R.string.dev_invalid_account else R.string.dev_open_failed)
                    }
            }, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.dev_open_token_form))
            }
            Text(stringResource(if (selected == DevelopmentService.GITHUB)
                R.string.dev_github_scope_hint else R.string.dev_cloudflare_scope_hint),
                style = MaterialTheme.typography.bodySmall)
            Text(if (credentialSaved) stringResource(R.string.dev_saved)
                else stringResource(R.string.dev_not_connected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = rawToken, onValueChange = { rawToken = it.take(512); verified = false },
                enabled = !isBusy,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text(stringResource(R.string.dev_token)) },
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("developer_token_input")
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(enabled = rawToken.isNotBlank() && !isBusy, onClick = {
                    try {
                        vault.save(selected, rawToken)
                        rawToken = ""
                        credentialSaved = true
                        verified = false
                        accounts = emptyList()
                        workerNames = emptyList()
                        deploymentDone = false; feedback = ""
                        feedback = context.getString(R.string.dev_saved)
                    } catch (e: Exception) { feedback = context.getString(R.string.dev_save_failed) }
                }) { Text(stringResource(R.string.dev_save_token)) }
                OutlinedButton(enabled = credentialSaved && rawToken.isBlank() && !isBusy, onClick = {
                    val serviceToVerify = selected
                    perform {
                        verified = false
                        if (serviceToVerify == DevelopmentService.GITHUB) client.verifyGithub()
                        else client.verifyCloudflare()
                        verified = true
                        context.getString(R.string.dev_verified)
                    }
                }) { Text(stringResource(R.string.dev_verify)) }
                TextButton(enabled = credentialSaved && !isBusy, onClick = {
                    try {
                        vault.clear(selected)
                        credentialSaved = false
                        verified = false
                        accessKey = ""
                        accounts = emptyList()
                        accountId = ""
                        deploymentDone = false; feedback = ""
                        loaded = null
                        proposedText = ""
                        workerNames = emptyList()
                        feedback = ""
                    } catch (e: Exception) {
                        feedback = context.getString(R.string.dev_clear_failed)
                    }
                }) { Text(stringResource(R.string.dev_revoke)) }
            }
            if (selected == DevelopmentService.GITHUB) {
                Text(stringResource(R.string.dev_step_project), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(repositoryUrl, { value ->
                    repositoryUrl = value.take(600)
                    val parsed = runCatching { DeveloperSetupPolicy.parseGithubRepository(value) }.getOrNull()
                    owner = parsed?.first.orEmpty()
                    repo = parsed?.second.orEmpty()
                    loaded = null
                }, label = { Text(stringResource(R.string.dev_repository_link)) },
                    enabled = !isBusy, singleLine = true,
                    isError = repositoryUrl.isNotBlank() && (owner.isBlank() || repo.isBlank()),
                    supportingText = { if (repositoryUrl.isNotBlank() && (owner.isBlank() || repo.isBlank()))
                        Text(stringResource(R.string.dev_repository_invalid)) },
                    modifier = Modifier.fillMaxWidth().testTag("developer_repository_url"))
                if (owner.isNotBlank() && repo.isNotBlank()) Text(
                    stringResource(R.string.dev_repository_selected, owner, repo),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { advanced = !advanced },
                    modifier = Modifier.fillMaxWidth().testTag("developer_advanced_editor_toggle")) {
                    Text(stringResource(if (advanced) R.string.dev_hide_advanced else R.string.dev_show_advanced))
                }
                AnimatedVisibility(advanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.dev_git_editor),
                            style = MaterialTheme.typography.titleSmall)
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedTextField(owner, { owner = it.take(110); loaded = null },
                                label = { Text(stringResource(R.string.dev_owner)) },
                                singleLine = true, enabled = !isBusy, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(repo, { repo = it.take(110); loaded = null },
                                label = { Text(stringResource(R.string.dev_repository)) },
                                singleLine = true, enabled = !isBusy, modifier = Modifier.fillMaxWidth())
                        }
                        OutlinedTextField(branch, { branch = it.take(180); loaded = null },
                            enabled = !isBusy, label = { Text(stringResource(R.string.dev_branch)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(filePath, { filePath = it.take(450); loaded = null },
                            enabled = !isBusy, label = { Text(stringResource(R.string.dev_path)) },
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
                                enabled = !isBusy, label = { Text(stringResource(R.string.dev_edit_source)) },
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
                            enabled = !isBusy, label = { Text(stringResource(R.string.dev_new_branch)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(enabled = credentialSaved && !isBusy &&
                            owner.isNotBlank() && repo.isNotBlank() && branch.isNotBlank() &&
                            newBranch.isNotBlank(), onClick = {
                            confirmAction = "branch"
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.dev_create_branch))
                        }
                    }
                }
            } else {
                Text(stringResource(R.string.dev_step_cloud), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.dev_cloud_account_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = credentialSaved && !isBusy, onClick = {
                    perform {
                        accounts = client.listCloudflareAccounts()
                        if (accounts.size == 1) accountId = accounts.first().id
                        context.getString(R.string.dev_accounts_found, accounts.size)
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dev_choose_account)) }
                accounts.forEach { account ->
                    FilterChip(selected = accountId == account.id, onClick = {
                        accountId = account.id; deploymentDone = false; feedback = ""
                    }, label = { Text(account.name + " · " + account.id.take(8)) },
                        enabled = !isBusy, modifier = Modifier.fillMaxWidth())
                }
                Text(stringResource(R.string.dev_worker_discovery_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(accountId, { accountId = it.take(32); workerNames = emptyList(); deploymentDone = false; feedback = "" },
                    enabled = !isBusy,
                    label = { Text(stringResource(R.string.dev_account)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    enabled = credentialSaved && !isBusy && Regex("[a-fA-F0-9]{32}").matches(accountId),
                    onClick = {
                        val accountToRead = accountId
                        perform {
                            workerNames = client.listCloudflareWorkers(accountToRead)
                            context.getString(R.string.dev_workers_found, workerNames.size)
                        }
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.dev_list_workers)) }
                workerNames.take(12).forEach { name ->
                    Text(name, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
                Text(stringResource(R.string.dev_step_deploy), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.dev_cloud_limits), style = MaterialTheme.typography.bodySmall)
                Text(DeveloperSetupPolicy.WORKER_MODEL, style = MaterialTheme.typography.labelSmall)
                CloudAgentPurpose.entries.forEach { role ->
                    FilterChip(selected = purpose == role, onClick = {
                        purpose = role
                        workerName = DeveloperSetupPolicy.suggestedWorkerName(role)
                        deploymentDone = false; feedback = ""
                    }, enabled = !isBusy, modifier = Modifier.fillMaxWidth(), label = {
                        Text(stringResource(when (role) {
                            CloudAgentPurpose.ASSISTANT -> R.string.dev_role_assistant
                            CloudAgentPurpose.RESEARCH -> R.string.dev_role_research
                            CloudAgentPurpose.PROJECT -> R.string.dev_role_project
                        }))
                    })
                }
                OutlinedTextField(workerName, { workerName = it.take(63); deploymentDone = false; feedback = "" },
                    enabled = !isBusy,
                    label = { Text(stringResource(R.string.dev_worker_name)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedButton(enabled = !isBusy, onClick = {
                    accessKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
                        .joinToString("") { "%02x".format(it.toInt() and 255) }
                    deploymentDone = false; feedback = ""
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dev_generate_access_key)) }
                if (accessKey.isNotBlank()) {
                    OutlinedTextField(accessKey, {}, readOnly = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text(stringResource(R.string.dev_access_key)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = {
                        val clip = ClipData.newPlainText(context.getString(R.string.dev_access_key), accessKey)
                        clip.description.extras = PersistableBundle().apply {
                            putBoolean("android.content.extra.IS_SENSITIVE", true)
                        }
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(clip)
                        feedback = context.getString(R.string.dev_access_key_copied)
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.chat_copy)) }
                }
                TextButton(onClick = { sourceExpanded = !sourceExpanded }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.dev_source_preview))
                }
                AnimatedVisibility(sourceExpanded) {
                    OutlinedTextField(DeveloperSetupPolicy.workerSource(purpose), {}, readOnly = true,
                        label = { Text(stringResource(R.string.dev_source_preview)) },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp), maxLines = 10)
                }
                Button(enabled = credentialSaved && verified && rawToken.isBlank() && !isBusy &&
                    Regex("[a-fA-F0-9]{32}").matches(accountId) &&
                    Regex("[a-z0-9][a-z0-9-]{0,62}").matches(workerName) && accessKey.length == 64,
                    onClick = { deployReview = DeveloperDeployReview(accountId, workerName, purpose, accessKey) },
                    modifier = Modifier.fillMaxWidth().testTag("developer_cloud_review_deploy")) {
                    Text(stringResource(R.string.dev_review_deploy))
                }
                if (deploymentDone) Text(stringResource(R.string.dev_deployed_no_route),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (isBusy) {
                Text(stringResource(R.string.dev_working), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (feedback.isNotBlank()) {
                Text(feedback, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.dev_safety),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    deployReview?.let { review ->
        AlertDialog(onDismissRequest = { deployReview = null },
            title = { Text(stringResource(R.string.dev_confirm_deploy)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(context.getString(R.string.dev_deploy_target, review.accountId, review.workerName))
                Text(stringResource(R.string.dev_deploy_cost_warning))
            } },
            confirmButton = { Button(enabled = !isBusy, onClick = {
                val key = review.accessKey
                deployReview = null
                perform {
                    client.deployCloudflareAgent(review.accountId, review.workerName, review.purpose, key, approved = true)
                    deploymentDone = true
                    context.getString(R.string.dev_deployed_no_route)
                }
            }) { Text(stringResource(R.string.graph_approve)) } },
            dismissButton = { TextButton(onClick = { deployReview = null }) {
                Text(stringResource(R.string.graph_reject))
            } })
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
