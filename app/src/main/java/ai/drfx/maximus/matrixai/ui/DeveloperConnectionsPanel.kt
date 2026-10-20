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
import ai.drfx.maximus.matrixai.studio.connections.CloudflareDeploymentClient
import ai.drfx.maximus.matrixai.studio.connections.CloudflareDeploymentReview
import ai.drfx.maximus.matrixai.studio.connections.CloudflareDeploymentReceipt
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
import ai.drfx.maximus.matrixai.studio.connections.DeveloperConnectionState
import ai.drfx.maximus.matrixai.studio.connections.GithubPullRequestReview
import ai.drfx.maximus.matrixai.studio.connections.GithubRepositoryEvidence
import ai.drfx.maximus.matrixai.studio.connections.DeveloperOperationsPolicy
import kotlinx.coroutines.delay
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    var connection by remember { mutableStateOf(DeveloperConnectionState(
        credentialSaved = vault.configured(DevelopmentService.GITHUB))) }
    val verified = connection.verified
    val credentialSaved = connection.credentialSaved
    val accountId = connection.accountId
    val workerNames = connection.workerNames
    var prTitle by remember { mutableStateOf("") }
    var prBody by remember { mutableStateOf("") }
    var prReview by remember { mutableStateOf<GithubPullRequestReview?>(null) }
    var prRevision by remember { mutableStateOf(-1L) }
    var prUrl by remember { mutableStateOf("") }
    var prSnapshotMatches by remember { mutableStateOf(true) }
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
    var confirmAction by remember { mutableStateOf<String?>(null) }

    var repositoryEvidence by remember(owner, repo, branch, connection.revision, selected) {
        mutableStateOf<GithubRepositoryEvidence?>(null)
    }
    var repositoryInspectionFailed by remember(owner, repo, branch, connection.revision, selected) {
        mutableStateOf(false)
    }
    var evidenceNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            evidenceNow = System.currentTimeMillis()
        }
    }

    fun perform(onFailure: (() -> Unit)? = null, action: suspend () -> String) {
        if (isBusy) return
        isBusy = true
        scope.launch {
            try {
                feedback = action().take(350)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                onFailure?.invoke()
                feedback = context.getString(R.string.dev_operation_failed)
            } finally {
                evidenceNow = System.currentTimeMillis()
                isBusy = false
            }
        }
    }

    ElevatedCard(modifier.fillMaxWidth().testTag("developer_connection_panel"),
        shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DeveloperOperationsEvidence(selected, connection, repositoryEvidence,
                repositoryInspectionFailed, evidenceNow)
            PluginStorePanel(onChanged = {
                connection = connection.reset(vault.configured(selected))
                prReview = null; prUrl = ""; loaded = null; accounts = emptyList()
                deployReview = null; deploymentDone = false
            })
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
                    connection = connection.reset(vault.configured(selected))
                    prReview = null; prUrl = ""
                    rawToken = ""
                    feedback = ""
                    accounts = emptyList()
                    accessKey = ""
                    deploymentDone = false; feedback = ""
                }, enabled = !isBusy, label = { Text("GitHub") })
                FilterChip(selected == DevelopmentService.CLOUDFLARE, onClick = {
                    selected = DevelopmentService.CLOUDFLARE
                    connection = connection.reset(vault.configured(selected))
                    prReview = null; prUrl = ""
                    rawToken = ""
                    feedback = ""
                    accounts = emptyList()
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
                value = rawToken, onValueChange = { rawToken = it.take(512); connection = connection.reset(credentialSaved); prReview = null; prUrl = "" },
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
                        connection = connection.reset(true)
                        prReview = null; prUrl = ""
                        accounts = emptyList()
                        deploymentDone = false; feedback = ""
                        feedback = context.getString(R.string.dev_saved)
                    } catch (e: Exception) { feedback = context.getString(R.string.dev_save_failed) }
                }) { Text(stringResource(R.string.dev_save_token)) }
                OutlinedButton(enabled = credentialSaved && rawToken.isBlank() && !isBusy, onClick = {
                    val serviceToVerify = selected
                    val revisionToVerify = connection.revision
                    perform {
                        connection = connection.copy(verified = false)
                        val identity = if (serviceToVerify == DevelopmentService.GITHUB) client.verifyGithub()
                        else client.verifyCloudflare()
                        connection = connection.finishVerification(revisionToVerify,
                            System.currentTimeMillis(), identity)
                        context.getString(R.string.dev_verified)
                    }
                }) { Text(stringResource(R.string.dev_verify)) }
                TextButton(enabled = credentialSaved && !isBusy, onClick = {
                    try {
                        vault.clear(selected)
                        connection = connection.reset(false)
                        prReview = null; prUrl = ""
                        accessKey = ""
                        accounts = emptyList()
                        deploymentDone = false; feedback = ""
                        loaded = null
                        proposedText = ""
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
                    loaded = null; prReview = null; prUrl = ""
                }, label = { Text(stringResource(R.string.dev_repository_link)) },
                    enabled = !isBusy, singleLine = true,
                    isError = repositoryUrl.isNotBlank() && (owner.isBlank() || repo.isBlank()),
                    supportingText = { if (repositoryUrl.isNotBlank() && (owner.isBlank() || repo.isBlank()))
                        Text(stringResource(R.string.dev_repository_invalid)) },
                    modifier = Modifier.fillMaxWidth().testTag("developer_repository_url"))
                if (owner.isNotBlank() && repo.isNotBlank()) Text(
                    stringResource(R.string.dev_repository_selected, owner, repo),
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = credentialSaved && rawToken.isBlank() && !isBusy &&
                    owner.isNotBlank() && repo.isNotBlank() && branch.isNotBlank(), onClick = {
                    val o = owner; val r = repo; val b = branch
                    val revision = connection.revision
                    perform(onFailure = { repositoryInspectionFailed = true }) {
                        val observation = client.inspectGithubRepository(o, r, b)
                        if (selected == DevelopmentService.GITHUB && connection.revision == revision &&
                            owner == o && repo == r && branch == b) {
                            repositoryEvidence = observation
                            repositoryInspectionFailed = false
                            evidenceNow = System.currentTimeMillis()
                        }
                        "Branch inspection complete. No source changes were sent."
                    }
                }, modifier = Modifier.fillMaxWidth().testTag("developer_inspect_repository")) {
                    Text("Inspect branch & latest workflow")
                }
                Text("Read only · selected branch: $branch · Actions read permission is optional.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { advanced = !advanced },
                    modifier = Modifier.fillMaxWidth().testTag("developer_advanced_editor_toggle")) {
                    Text(stringResource(if (advanced) R.string.dev_hide_advanced else R.string.dev_show_advanced))
                }
                AnimatedVisibility(advanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.dev_git_editor),
                            style = MaterialTheme.typography.titleSmall)
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedTextField(owner, { owner = it.take(110); loaded = null; prReview = null; prUrl = "" },
                                label = { Text(stringResource(R.string.dev_owner)) },
                                singleLine = true, enabled = !isBusy, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(repo, { repo = it.take(110); loaded = null; prReview = null; prUrl = "" },
                                label = { Text(stringResource(R.string.dev_repository)) },
                                singleLine = true, enabled = !isBusy, modifier = Modifier.fillMaxWidth())
                        }
                        OutlinedTextField(branch, { branch = it.take(180); loaded = null; prReview = null; prUrl = "" },
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
                        HorizontalDivider()
                        Text(stringResource(R.string.dev_pr_heading), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.dev_pr_hint), style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(prTitle, { prTitle = it.take(120); prReview = null; prUrl = "" },
                            enabled = !isBusy, singleLine = true,
                            label = { Text(stringResource(R.string.dev_pr_title)) }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(prBody, { prBody = it.take(20_000); prReview = null; prUrl = "" },
                            enabled = !isBusy, maxLines = 6,
                            label = { Text(stringResource(R.string.dev_pr_body)) }, modifier = Modifier.fillMaxWidth())
                        OutlinedButton(enabled = credentialSaved && verified && rawToken.isBlank() && !isBusy &&
                            owner.isNotBlank() && repo.isNotBlank() && branch.isNotBlank() && prTitle.isNotBlank(),
                            onClick = {
                                val o = owner; val r = repo; val h = branch
                                val title = prTitle; val body = prBody
                                val revision = connection.revision
                                perform {
                                    val review = client.prepareGithubPullRequest(o, r, h, title, body)
                                    if (connection.revision == revision) {
                                        prReview = review
                                        prRevision = revision
                                    }
                                    context.getString(R.string.dev_pr_preview_ready)
                                }
                            }, modifier = Modifier.fillMaxWidth().testTag("developer_pr_prepare")) {
                            Text(stringResource(R.string.dev_pr_prepare))
                        }
                        if (prUrl.isNotBlank()) {
                            Text(stringResource(if (prSnapshotMatches) R.string.dev_pr_created
                                else R.string.dev_pr_created_changed), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(prUrl))) }
                                    .onFailure { feedback = context.getString(R.string.dev_open_failed) }
                            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dev_pr_open)) }
                        }
                    }
                }
            } else {
                Text(stringResource(R.string.dev_step_cloud), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.dev_cloud_account_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = credentialSaved && !isBusy, onClick = {
                    perform {
                        accounts = client.listCloudflareAccounts()
                        if (accounts.size == 1) {
                            connection = connection.selectAccount(accounts.first().id)
                            deploymentDone = false
                        }
                        context.getString(R.string.dev_accounts_found, accounts.size)
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dev_choose_account)) }
                accounts.forEach { account ->
                    FilterChip(selected = accountId == account.id, onClick = {
                        connection = connection.selectAccount(account.id); deploymentDone = false; feedback = ""
                    }, label = { Text(account.name + " · " + account.id.take(8)) },
                        enabled = !isBusy, modifier = Modifier.fillMaxWidth())
                }
                Text(stringResource(R.string.dev_worker_discovery_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(accountId, { connection = connection.selectAccount(it.take(32)); deploymentDone = false; feedback = "" },
                    enabled = !isBusy,
                    label = { Text(stringResource(R.string.dev_account)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    enabled = credentialSaved && !isBusy && Regex("[a-fA-F0-9]{32}").matches(accountId),
                    onClick = {
                        val accountToRead = accountId
                        val revisionToRead = connection.revision
                        perform {
                            val names = client.listCloudflareWorkers(accountToRead)
                            connection = connection.receiveWorkers(revisionToRead, accountToRead, names)
                            context.getString(R.string.dev_workers_found, connection.workerNames.size)
                        }
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.dev_list_workers)) }
                workerNames.take(12).forEach { name ->
                    Text(name, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
                key(accountId, connection.revision, purpose) {
                    CloudflareStagedDeploymentPanel(client.cloudflareDeployments(), accountId, purpose,
                        enabled = credentialSaved && verified && rawToken.isBlank() && !isBusy,
                        onBusyChanged = { isBusy = it })
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

    prReview?.let { review ->
        AlertDialog(onDismissRequest = { prReview = null },
            title = { Text(stringResource(R.string.dev_pr_confirm)) },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${review.owner}/${review.repository}")
                Text(stringResource(R.string.dev_pr_target, review.head, review.base))
                Text(stringResource(R.string.dev_pr_changes, review.aheadBy, review.files.size))
                Text(review.title, style = MaterialTheme.typography.titleSmall)
                if (review.body.isNotBlank()) Text(review.body)
                review.files.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(stringResource(R.string.dev_pr_warning), style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { Button(enabled = !isBusy && connection.verified &&
                connection.revision == prRevision && rawToken.isBlank(), onClick = {
                val revision = prRevision
                prReview = null
                perform {
                    check(connection.revision == revision && connection.verified)
                    val created = client.createGithubPullRequest(review, approved = true)
                    prUrl = created.url
                    prSnapshotMatches = created.reviewedSnapshotMatches
                    context.getString(if (prSnapshotMatches) R.string.dev_pr_created
                        else R.string.dev_pr_created_changed)
                }
            }) { Text(stringResource(R.string.dev_pr_create)) } },
            dismissButton = { TextButton(onClick = { prReview = null }) {
                Text(stringResource(R.string.graph_reject))
            } })
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
                    val created = client.deployCloudflareAgent(review.accountId, review.workerName, review.purpose, key, approved = true)
                    deploymentDone = created.verified
                    if (created.verified) context.getString(R.string.dev_deployed_no_route)
                    else context.getString(R.string.cf_stage_partial_creation, created.workerId, created.version ?: "—")
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
                            loaded = null; prReview = null; prUrl = ""
                            context.getString(R.string.dev_commit_done) + " " + commit.take(12)
                        }
                    } else if (requested == "branch") {
                        perform {
                            client.createGithubFeatureBranch(owner, repo, branch, newBranch,
                                approved = true)
                            branch = newBranch
                            loaded = null; prReview = null; prUrl = ""
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


/** Existing Workers are never routed through the create-and-deploy shortcut. */
@Composable
private fun CloudflareStagedDeploymentPanel(
    client: CloudflareDeploymentClient, accountId: String, purpose: CloudAgentPurpose, enabled: Boolean,
    onBusyChanged: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var review by remember { mutableStateOf<CloudflareDeploymentReview?>(null) }
    var receipt by remember { mutableStateOf<CloudflareDeploymentReceipt?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var sourceVisible by remember { mutableStateOf(false) }
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true; failed = false; onBusyChanged(true)
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false; onBusyChanged(false) }
        }
    }
    ElevatedCard(Modifier.fillMaxWidth().testTag("cloudflare_staged_panel"), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.cf_stage_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.cf_stage_scope), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { name = it.take(63); review = null; receipt = null; failed = false },
                enabled = enabled && !busy, singleLine = true,
                label = { Text(stringResource(R.string.dev_worker_name)) }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(enabled = enabled && !busy && Regex("[A-Za-z0-9_-]{1,63}").matches(name) &&
                Regex("[a-fA-F0-9]{32}").matches(accountId), onClick = { perform {
                    review = null; receipt = null
                    review = client.inspect(accountId, name, DeveloperSetupPolicy.workerSource(purpose))
                } }, modifier = Modifier.fillMaxWidth().testTag("cloudflare_inspect")) {
                Text(stringResource(R.string.cf_stage_inspect))
            }
            review?.let { snapshot ->
                Text("${snapshot.accountId} / ${snapshot.workerName}", style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.cf_stage_active, snapshot.activeVersion), style = MaterialTheme.typography.bodySmall)
                Text("SHA-256 · ${snapshot.sourceSha256}", style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.cf_stage_bindings, snapshot.inheritedBindings.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { sourceVisible = !sourceVisible }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.dev_source_preview))
                }
                AnimatedVisibility(sourceVisible) {
                    OutlinedTextField(snapshot.source, {}, readOnly = true,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp), maxLines = 10)
                }
                Button(enabled = enabled && !busy && receipt == null, onClick = { confirmation = "stage" },
                    modifier = Modifier.fillMaxWidth().testTag("cloudflare_review_stage")) {
                    Text(stringResource(R.string.cf_stage_upload))
                }
            }
            receipt?.let { result ->
                Text(stringResource(R.string.cf_stage_receipt, result.stagedVersion), style = MaterialTheme.typography.bodySmall)
                result.activatedDeployment?.let { Text(stringResource(R.string.cf_stage_deployment, it), style = MaterialTheme.typography.bodySmall) }
                result.rolledBackDeployment?.let { Text(stringResource(R.string.cf_stage_rollback_receipt, it), style = MaterialTheme.typography.bodySmall) }
                if (!result.verified) Text(stringResource(R.string.cf_stage_unverified), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                if (result.activatedDeployment == null) {
                    Button(enabled = enabled && !busy && result.verified, onClick = { confirmation = "activate" },
                        modifier = Modifier.fillMaxWidth().testTag("cloudflare_review_activate")) {
                        Text(stringResource(R.string.cf_stage_activate))
                    }
                } else if (result.rolledBackDeployment == null) {
                    OutlinedButton(enabled = enabled && !busy && result.verified, onClick = { confirmation = "rollback" },
                        modifier = Modifier.fillMaxWidth().testTag("cloudflare_review_rollback")) {
                        Text(stringResource(R.string.cf_stage_rollback))
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) Text(stringResource(R.string.cf_stage_failed), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
    }
    val action = confirmation
    val snapshot = review
    if (action != null && snapshot != null) AlertDialog(onDismissRequest = { confirmation = null },
        title = { Text(stringResource(when (action) {
            "stage" -> R.string.cf_stage_upload
            "activate" -> R.string.cf_stage_activate
            else -> R.string.cf_stage_rollback
        })) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${snapshot.accountId} / ${snapshot.workerName}")
            Text(if (action == "stage") stringResource(R.string.cf_stage_upload_warning)
                else stringResource(R.string.cf_stage_traffic_warning))
            Text(if (action == "rollback") snapshot.activeVersion else receipt?.stagedVersion ?: snapshot.sourceSha256,
                style = MaterialTheme.typography.labelSmall)
        } },
        confirmButton = { Button(enabled = enabled && !busy, onClick = {
            val confirmedReceipt = receipt
            confirmation = null
            perform {
                receipt = if (action == "stage") client.stage(snapshot, approved = true)
                else client.activate(checkNotNull(confirmedReceipt), approved = true, rollback = action == "rollback")
            }
        }) { Text(stringResource(R.string.graph_approve)) } },
        dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.graph_reject)) } })
}

/** A dated read-only projection. Rendering never probes services or executes commands. */
@Composable
private fun DeveloperOperationsEvidence(
    service: DevelopmentService,
    state: DeveloperConnectionState,
    repository: GithubRepositoryEvidence?,
    inspectionFailed: Boolean,
    now: Long
) {
    fun date(at: Long): String = if (at > 0L) java.time.Instant.ofEpochMilli(at).toString() else "Unavailable"
    fun freshness(at: Long): String = DeveloperOperationsPolicy.freshness(at, now).name
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("developer_operations_evidence"),
        color = androidx.compose.ui.graphics.Color(0xFF061A2D),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF17446D))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Developer operations", style = MaterialTheme.typography.titleSmall,
                color = androidx.compose.ui.graphics.Color(0xFFFFCC62))
            Text("${if (service == DevelopmentService.GITHUB) "GitHub" else "Cloudflare"} · " +
                if (state.credentialSaved) "Credential stored on device" else "No credential",
                style = MaterialTheme.typography.bodySmall)
            Text("Token evidence: ${if (!state.verified && state.verifiedAtEpochMs > 0L) "ERROR · previous observation only" else freshness(state.verifiedAtEpochMs)}",
                style = MaterialTheme.typography.labelSmall)
            if (state.verifiedAtEpochMs > 0L) {
                Text("Observed: ${date(state.verifiedAtEpochMs)} UTC",
                    style = MaterialTheme.typography.bodySmall)
                if (state.verifiedIdentity.isNotBlank()) Text(
                    "API identity: ${state.verifiedIdentity}", style = MaterialTheme.typography.bodySmall)
            }
            if (service == DevelopmentService.GITHUB) {
                if (inspectionFailed) Text("Repository inspection: ERROR · previous evidence is historical",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (repository == null) {
                    Text("Repository evidence: UNKNOWN · inspect a selected branch",
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("${repository.repository} · ${repository.branch}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("${if (inspectionFailed) "Historical branch" else "Branch"} evidence: ${freshness(repository.observedAtEpochMs)} · ${date(repository.observedAtEpochMs)} UTC",
                        style = MaterialTheme.typography.labelSmall)
                    Text("Commit: ${repository.commitSha}", style = MaterialTheme.typography.bodySmall)
                    Text("Protected: ${repository.protected?.toString() ?: "UNKNOWN"} · " +
                        "Archived: ${repository.archived?.toString() ?: "UNKNOWN"}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Default branch: ${repository.defaultBranch}",
                        style = MaterialTheme.typography.bodySmall)
                    val run = repository.latestWorkflow
                    Text(when {
                        !repository.workflowAvailable -> "Latest workflow: UNAVAILABLE · check Actions read permission"
                        run == null -> "Latest workflow: UNKNOWN · no run returned for this commit"
                        else -> "Latest workflow #${run.id}: ${run.status} / ${run.conclusion ?: "not completed"}"
                    }, style = MaterialTheme.typography.bodySmall)
                    if (run?.updatedAtEpochMs != null) Text(
                        "Workflow updated: ${date(run.updatedAtEpochMs)} UTC",
                        style = MaterialTheme.typography.labelSmall)
                    Text("One latest workflow is not evidence that every required check passed.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text("Worker discovery: ${freshness(state.workersObservedAtEpochMs)}",
                    style = MaterialTheme.typography.bodySmall)
                if (state.workersObservedAtEpochMs > 0L) {
                    Text("Observed: ${date(state.workersObservedAtEpochMs)} UTC · " +
                        "${state.workerNames.size} names returned (max 50)",
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("Token verification and Worker discovery do not prove invocation health.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Reads are explicit. Source writes, staging and traffic changes keep their approval gates.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
