package ai.drfx.maximus.matrixai.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.drfx.maximus.matrixai.R
import androidx.compose.ui.res.stringResource
import ai.drfx.maximus.matrixai.studio.ProjectWorkspaceStore
import ai.drfx.maximus.matrixai.studio.ProjectTaskMode
import ai.drfx.maximus.matrixai.studio.ProjectTaskModeStore
import ai.drfx.maximus.matrixai.studio.ProjectTaskModePolicy
import ai.drfx.maximus.matrixai.studio.ProjectContinuityStore
import ai.drfx.maximus.matrixai.memory.SharedProjectMemory
import ai.drfx.maximus.matrixai.studio.StudioProject
import ai.drfx.maximus.matrixai.studio.StudioFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val studioCyan = Color(0xFFF4C35A)
private val studioPurple = Color(0xFFFF8D38)
private val studioGreen = Color(0xFFFFE079)

/**
 * Real on-device workspace operations, rendered mobile-first.
 * The external AI request contains project name, brief and filenames only.
 * No build or remote execution is claimed without a configured worker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectStudioScreen(
    onPlanWithAi: (String) -> Boolean,
    initialSection: Int = 0,
    onChat: () -> Unit = {},
    onPine: () -> Unit = {},
    projectTeamRun: ai.drfx.maximus.matrixai.agent.teams.ProjectTeamRun? = null,
    projectTeamMessage: String = "",
    projectTeamInvalid: Boolean = false,
    onDiscardInvalidTeam: () -> Unit = {},
    onPrepareTeam: (String) -> Unit = {},
    onApproveTeam: (String) -> Unit = {},
    onCancelTeam: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var section by remember { mutableStateOf(initialSection.coerceIn(0, 6)) }
    var showAdvancedNavigation by remember { mutableStateOf(false) }
    val workspaceListState = rememberLazyListState()
    val workspaceScope = rememberCoroutineScope()
    var previousSection by remember { mutableIntStateOf(section) }
    // Keep Work navigation visible when switching sections. Previously a section change
    // scrolled to the top of the tall Pine hero, hiding the navigation controls.
    LaunchedEffect(section) {
        if (previousSection != section) {
            previousSection = section
            workspaceListState.scrollToItem(if (section == 0) 4 else 3)
        }
    }
    val context = LocalContext.current
    val connectionVault = remember(context) { ai.drfx.maximus.matrixai.studio.connections.DeveloperCredentialVault(context) }
    val store = remember(context) { ProjectWorkspaceStore(context.applicationContext) }
    val continuity = remember(context) { ProjectContinuityStore(context.applicationContext, store) }
    val sharedMemory = remember(context) { SharedProjectMemory(context.applicationContext) }
    val taskModes = remember(context) { ProjectTaskModeStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf(store.projects()) }
    var activeId by remember { mutableStateOf(projects.firstOrNull()?.id) }
    val active = projects.firstOrNull { it.id == activeId }
    var files by remember { mutableStateOf(emptyList<StudioFile>()) }
    var brief by remember(activeId) { mutableStateOf(active?.brief.orEmpty()) }
    var projectTaskMode by remember(activeId) { mutableStateOf(taskModes.read(activeId)) }
    var nextTask by remember(activeId) { mutableStateOf("Review outstanding tasks, verify source files, and continue safely.") }
    var projectName by remember { mutableStateOf("") }
    var githubUrl by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var showGithub by remember { mutableStateOf(false) }
    var showSelector by remember { mutableStateOf(false) }
    var editingFile by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf("") }
    var originalEditor by remember { mutableStateOf("") }
    var pendingEdit by remember(activeId) { mutableStateOf<Triple<String, String, String>?>(null) }
    var previewPath by remember(activeId) { mutableStateOf("") }
    var previewText by remember(activeId) { mutableStateOf("") }
    var previewExpanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(context.getString(R.string.studio_initial_status)) }

    fun refresh(selected: String? = activeId) {
        projects = store.projects()
        activeId = selected?.takeIf { id -> projects.any { it.id == id } } ?: projects.firstOrNull()?.id
    }

    fun operate(action: suspend () -> StudioProject) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val project = withContext(Dispatchers.IO) { action() }
                refresh(project.id)
                message = context.getString(R.string.studio_project_ready, project.name)
            } catch (t: Exception) {
                message = context.getString(R.string.studio_action_failed, t.message ?: t.javaClass.simpleName)
            } finally { busy = false }
        }
    }

    LaunchedEffect(activeId) {
        files = withContext(Dispatchers.IO) {
            if (activeId == null) emptyList() else runCatching { store.listFiles(activeId!!) }.getOrDefault(emptyList())
        }
        previewPath = files.firstOrNull { it.path.endsWith(".kt") }?.path ?: files.firstOrNull()?.path.orEmpty()
        previewText = withContext(Dispatchers.IO) {
            val id = activeId
            if (id != null && previewPath.isNotBlank()) runCatching { store.readFile(id, previewPath) }.getOrDefault("") else ""
        }
        brief = projects.firstOrNull { it.id == activeId }?.brief.orEmpty()
    }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) operate {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                ?.replace(Regex("[^A-Za-z0-9 _-]"), "").orEmpty().take(45)
                .takeIf { it.length >= 2 && it[0].isLetter() } ?: "Imported Project"
            context.contentResolver.openInputStream(uri)?.use { store.importZip(name, it) }
                ?: error("Unable to open the selected file.")
        }
    }

    val zipWriter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        val id = activeId
        if (id != null && uri != null && !busy) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { store.exportZip(id, it) }
                            ?: error("Cannot write selected document.")
                    }
                    message = context.getString(R.string.studio_exported)
                } catch (t: Exception) {
                    message = "Export failed: " + (t.message ?: "Unknown error")
                } finally { busy = false }
            }
        }
    }

    Column(modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth().testTag("project_studio_screen"),
        state = workspaceListState,
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WorkspaceBrand()
                MaximusSectionHeader("Work", "Build, test, and deploy with AI")
            }
        }


        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Surface(
                    onClick = {
                        if (projects.isEmpty()) { projectName = ""; showCreate = true }
                        else showSelector = true
                    },
                    modifier = Modifier.weight(1f).testTag("work_project_selector"),
                    shape = RoundedCornerShape(10.dp), color = MaximusReference.navy,
                    border = BorderStroke(1.dp, MaximusReference.stroke)
                ) {
                    Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(19.dp), tint = MaximusReference.muted)
                        Spacer(Modifier.width(7.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(active?.name ?: "Select project", color = MaximusReference.white,
                                fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (active == null) "Create or import" else "${active.fileCount} files · Local",
                                color = MaximusReference.muted, fontSize = 10.sp)
                        }
                        Icon(Icons.Default.ArrowDropDown, null, Modifier.size(17.dp), tint = MaximusReference.muted)
                    }
                }
                Surface(
                    onClick = { section = 4 },
                    modifier = Modifier.weight(1f).testTag("work_branch_selector"),
                    shape = RoundedCornerShape(10.dp), color = MaximusReference.navy,
                    border = BorderStroke(1.dp, MaximusReference.stroke)
                ) {
                    Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountTree, null, Modifier.size(19.dp), tint = MaximusReference.muted)
                        Spacer(Modifier.width(7.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("Git branch", color = MaximusReference.white, fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold)
                            Text("Not linked", color = MaximusReference.muted, fontSize = 10.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = MaximusReference.muted)
                    }
                }
            }
            if (showSelector) WorkspaceChoiceSheet(
                title = stringResource(R.string.studio_select),
                choices = projects.map { WorkspaceChoice(it.id, it.name, "") },
                selected = activeId.orEmpty(),
                onDismiss = { showSelector = false },
                onSelect = { activeId = it; showSelector = false })
        }
        if (section == 0) item {
            PremiumPineWorkCard(
                onOpen = onPine,
                onJumpToWorkspace = {
                    workspaceScope.launch { workspaceListState.animateScrollToItem(4) }
                }
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(ai.drfx.maximus.matrixai.studio.connections.DevelopmentService.GITHUB,
                    ai.drfx.maximus.matrixai.studio.connections.DevelopmentService.CLOUDFLARE).forEach { service ->
                    Surface(
                        onClick = { section = 4 },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp), color = MaximusReference.navy,
                        border = BorderStroke(1.dp, MaximusReference.stroke)
                    ) {
                        Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (service == ai.drfx.maximus.matrixai.studio.connections.DevelopmentService.GITHUB)
                                    Icons.Default.Code else Icons.Default.Cloud,
                                null, Modifier.size(21.dp), tint = MaximusReference.gold)
                            Spacer(Modifier.width(7.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (service == ai.drfx.maximus.matrixai.studio.connections.DevelopmentService.GITHUB)
                                    "GitHub" else "Cloudflare",
                                    color = MaximusReference.white, fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold)
                                Text(stringResource(if (connectionVault.configured(service))
                                    R.string.dev_saved else R.string.dev_not_connected),
                                    color = MaximusReference.muted, fontSize = 10.sp,
                                    maxLines = 1)
                            }
                            Icon(Icons.Default.ChevronRight, null,
                                Modifier.size(16.dp), tint = MaximusReference.muted)
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MaximusTabs(
                    listOf("Assistant", stringResource(R.string.studio_step_files),
                        if (pendingEdit == null) "Changes" else "Changes · 1"),
                    when (section) { 1 -> 1; 6 -> 2; else -> 0 },
                    { index -> section = when (index) { 1 -> 1; 2 -> 6; else -> 0 } }
                )
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TextButton(onClick = { showAdvancedNavigation = !showAdvancedNavigation }) {
                        Icon(Icons.Default.Tune, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (showAdvancedNavigation) "Hide tools" else "Runtime · Connections · Advanced",
                            fontSize = 11.sp)
                    }
                }
                if (showAdvancedNavigation || section in 2..5) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("Build", stringResource(R.string.design_runtime),
                            stringResource(R.string.design_connections),
                            stringResource(R.string.ui_advanced)).forEachIndexed { i, label ->
                                val target = i + 2
                                FilterChip(selected = section == target, onClick = { section = target },
                                    label = { Text(label, fontSize = 11.sp) },
                                    modifier = Modifier.testTag("studio_section_${target}"))
                            }
                    }
                }
            }
        }
        if ((section == 0 || section == 6) && pendingEdit != null) {
            item {
                val change = pendingEdit!!
                StudioPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, null, tint = studioCyan)
                        Spacer(Modifier.width(8.dp))
                        Text(change.first, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 2)
                        WorkspaceCompanion(modifier = Modifier.size(48.dp))
                    }
                    Text(stringResource(R.string.ui_review), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.ui_local_edit), style = MaterialTheme.typography.bodySmall)
                    androidx.compose.animation.AnimatedVisibility(previewExpanded) {
                        WorkspaceDiffCard(change.second, change.third)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { previewExpanded = !previewExpanded }) {
                            Text(stringResource(R.string.ui_changes))
                        }
                        Button(enabled = !busy, onClick = {
                            val id = activeId ?: return@Button
                            busy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        check(store.readFile(id, change.first) == change.second) { context.getString(R.string.ui_edit_conflict) }
                                        store.saveFile(id, change.first, change.third)
                                    }
                                    pendingEdit = null; refresh(id)
                                    message = context.getString(R.string.studio_file_saved, change.first)
                                } catch (error: Exception) { message = error.message.orEmpty() }
                                finally { busy = false }
                            }
                        }) { Text(stringResource(R.string.studio_save_file)) }
                    }
                    TextButton(onClick = { pendingEdit = null }) { Text(stringResource(R.string.studio_cancel)) }
                }
            }
        }
        if (section == 6 && pendingEdit == null) {
            item {
                MaximusFramedCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FactCheck, null, tint = MaximusReference.gold)
                        Spacer(Modifier.width(8.dp))
                        Text("Changes · 0", color = MaximusReference.white,
                            fontWeight = FontWeight.Bold)
                    }
                    Text("No staged file changes in this local workspace.",
                        color = MaximusReference.muted, fontSize = 12.sp)
                    Text("Edit a project file to create a reviewable diff. Nothing will be written remotely.",
                        color = MaximusReference.muted, fontSize = 11.sp)
                    OutlinedButton(onClick = { section = 1 }, enabled = active != null) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Open project files")
                    }
                    if (active == null) Text("Choose or import a project first.",
                        color = MaximusReference.muted, fontSize = 11.sp)
                }
            }
        }
        if (section == 0) {
            if (active == null) item {
                MaximusFramedCard {
                    Text("Your workspace is ready", fontWeight = FontWeight.Bold,
                        color = MaximusReference.white)
                    Text("Create a local project, import a ZIP, or connect a service to start reviewing real files.",
                        fontSize = 12.sp, color = MaximusReference.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { projectName = ""; showCreate = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaximusReference.gold,
                                contentColor = Color.Black)) { Text("New project") }
                        OutlinedButton(onClick = { githubUrl = ""; showGithub = true }) {
                            Text("Import GitHub")
                        }
                    }
                }
            }
            if (active != null) item {
                StudioProgressTracker(
                    projectReady = true,
                    filesReady = files.isNotEmpty(),
                    editReady = pendingEdit != null
                )
            }
            if (previewText.isNotBlank() && pendingEdit == null) item {
                WorkspaceCodeCard(MessagePart(previewText, previewPath.substringAfterLast('.', "text")), previewPath.substringAfterLast('/'))
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioShortcut(stringResource(R.string.ui_source_preview), Icons.Default.PlayCircleOutline, Modifier.weight(1f)) { section = 1 }
                    StudioShortcut(stringResource(R.string.plugin_apk), Icons.Default.Android, Modifier.weight(1f)) { section = 2 }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    StudioShortcut("Run on · Select runtime", Icons.Default.PlayArrow,
                        Modifier.weight(1f)) { section = 3 }
                    StudioShortcut("Activity", Icons.Default.History,
                        Modifier.weight(1f)) { section = 5 }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    WorkspaceCompanion(working = busy)
                }
            }
        }
        if (section == 5) {
        item {
            ProjectTeamRunPanel(activeId, projectTeamRun, projectTeamMessage,
                onPrepareTeam, onApproveTeam, onCancelTeam, projectTeamInvalid, onDiscardInvalidTeam)
        }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioAction(stringResource(R.string.studio_new), Icons.Default.Add, studioCyan, enabled = !busy) { projectName = ""; showCreate = true }
                StudioAction(stringResource(R.string.studio_github), Icons.Default.CloudDownload, studioPurple, enabled = !busy) {
                    githubUrl = ""; showGithub = true
                }
                StudioAction(stringResource(R.string.studio_zip), Icons.Default.FolderZip, studioGreen, enabled = !busy) {
                    zipPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                }
                StudioAction(stringResource(R.string.studio_export), Icons.Default.UploadFile, studioCyan, enabled = !busy && active != null) {
                    zipWriter.launch((active?.name ?: "project") + ".zip")
                }
            }
        }
        if (section == 5) {
        item {
            StudioPanel {
                Text(stringResource(R.string.studio_ai_brief), fontWeight = FontWeight.SemiBold, color = studioPurple)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.project_mode), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ProjectTaskMode.entries.forEach { mode ->
                        FilterChip(
                            selected = projectTaskMode == mode,
                            enabled = activeId != null && !busy,
                            onClick = {
                                val id = activeId ?: return@FilterChip
                                try {
                                    taskModes.save(id, mode)
                                    projectTaskMode = mode
                                } catch (error: Exception) {
                                    message = error.message ?: "Unable to save workspace mode."
                                }
                            },
                            label = { Text(mode.displayName) }
                        )
                    }
                }
                Text(stringResource(R.string.project_mode_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(
                    value = brief,
                    onValueChange = { brief = it.take(12000) },
                    label = { Text(stringResource(R.string.studio_describe)) },
                    placeholder = { Text(stringResource(R.string.studio_example)) },
                    minLines = 3, maxLines = 7,
                    enabled = active != null && !busy,
                    modifier = Modifier.fillMaxWidth().testTag("studio_project_brief")
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val id = activeId ?: return@OutlinedButton
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { store.saveBrief(id, brief) }
                                refresh(id)
                                message = context.getString(R.string.studio_brief_saved)
                            } catch (e: Exception) { message = e.message ?: "Save failed." }
                        }
                    }, enabled = active != null && !busy) {
                        Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp)); Text(stringResource(R.string.studio_save))
                    }
                    Button(onClick = {
                        val id = activeId ?: return@Button
                        if (busy) return@Button
                        scope.launch {
                            try {
                                val plan = withContext(Dispatchers.IO) {
                                    store.saveBrief(id, brief)
                                    ProjectTaskModePolicy.enrichPlan(store.aiPlanPrompt(id), projectTaskMode)
                                }
                                refresh(id)
                                val sent = onPlanWithAi(plan)
                                message = context.getString(if (sent) R.string.studio_plan_sent else R.string.studio_connect_model)
                            } catch (e: Exception) { message = e.message ?: "Could not request AI planning." }
                        }
                    }, enabled = active != null && !busy) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp)); Text(stringResource(R.string.studio_plan_with_ai))
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(stringResource(R.string.studio_ai_consent), fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }
        if (section == 5) {
        item {
            StudioPanel {
                HindsightMemoryPanel(activeId)
            }
        }
        }
        if (section == 5) {
        item {
            StudioPanel {
                Text(stringResource(R.string.project_continuity), fontWeight = FontWeight.Bold, color = studioCyan)
                Text("Save hashes and task state privately; no project files or keys are uploaded. Handoff requires verified files and explicit user approval.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = nextTask, onValueChange = { nextTask = it.take(2000) },
                    label = { Text(stringResource(R.string.project_next)) },
                    minLines = 2, maxLines = 4, enabled = active != null && !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val id = activeId ?: return@OutlinedButton
                        if (busy) return@OutlinedButton
                        busy = true
                        scope.launch {
                            try {
                                val snap = withContext(Dispatchers.IO) {
                                    store.saveBrief(id, brief)
                                    continuity.save(id, nextTask)
                                }
                                message = "Checkpoint verified: " + snap.checkpointId.take(8) + "…"
                            } catch (e: Exception) {
                                message = e.message ?: "Checkpoint failed."
                            } finally { busy = false }
                        }
                    }, enabled = active != null && !busy && nextTask.isNotBlank()) {
                        Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp)); Text(stringResource(R.string.project_checkpoint))
                    }
                    Button(onClick = {
                        val id = activeId ?: return@Button
                        if (busy) return@Button
                        val memoryAccess = runCatching { sharedMemory.captureAccess() }.getOrNull()
                            ?.takeIf { it.scope.project == id }
                        busy = true
                        scope.launch {
                            try {
                                val prompt = withContext(Dispatchers.IO) { continuity.resumePrompt(id) }
                                val relevantMemory = sharedMemory.cachedContext(memoryAccess)
                                val handoff = if (relevantMemory.isNotBlank()) prompt + "\n\n" + relevantMemory else prompt
                                val sent = onPlanWithAi(handoff)
                                message = if (sent) "Verified checkpoint sent for planning (source contents not attached)."
                                else "Connect a suitable model to continue."
                            } catch (e: Exception) {
                                message = e.message ?: "Resume unavailable."
                            } finally { busy = false }
                        }
                    }, enabled = active != null && !busy) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp)); Text(stringResource(R.string.project_resume))
                    }
                }
            }
        }
        }
        if (section == 1) {
        item {
            StudioPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, tint = studioCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.studio_files_title), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(files.size.toString(), color = studioGreen)
                }
                Spacer(Modifier.height(5.dp))
                if (files.isEmpty()) Text(stringResource(R.string.studio_no_files), fontSize = 12.sp)
                else files.take(80).forEach { file ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {
                            val id = activeId ?: return@clickable
                            scope.launch {
                                try {
                                    editor = withContext(Dispatchers.IO) { store.readFile(id, file.path) }
                                    originalEditor = editor
                                    editingFile = file.path
                                } catch (e: Exception) { message = e.message ?: "Cannot preview file." }
                            }
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Description, null, tint = studioPurple, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(file.path, modifier = Modifier.weight(1f), fontSize = 12.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text((file.length / 1024L).toString() + " KB", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (files.size > 80) Text(stringResource(R.string.studio_show_files), fontSize = 11.sp)
            }
        }
        }
        if (section == 2) {
        item { PhoneHarnessPanel(projectId = activeId, showWorkspaceTools = false) }
        }
        if (section == 3) {
        item { PhoneLinuxPanel() }
        item { RemoteWorkerPanel(projectId = activeId) }
        }
        if (section == 4) {
            item { DeveloperConnectionsPanel() }
            item { PluginStorePanel() }
        }
        item {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (section == 0) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp).testTag("work_pinned_composer")) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaximusReference.navy,
                    border = BorderStroke(1.dp, studioCyan.copy(alpha = .5f))) {
                    Row(Modifier.fillMaxWidth().padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { projectName = ""; showCreate = true },
                            modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Add, "New project", tint = MaximusReference.muted)
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = brief,
                            onValueChange = { brief = it.take(12000) },
                            modifier = Modifier.weight(1f).heightIn(min = 35.dp, max = 74.dp)
                                .padding(horizontal = 5.dp, vertical = 8.dp)
                                .testTag("work_ai_composer"),
                            enabled = active != null && !busy,
                            maxLines = 3,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = MaximusReference.white, fontSize = 12.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaximusReference.gold),
                            decorationBox = { inner ->
                                Box {
                                    if (brief.isBlank()) Text(
                                        if (active == null) "Select a project to begin..."
                                        else "Ask MAXIMUS to edit...",
                                        color = MaximusReference.muted, fontSize = 12.sp,
                                        maxLines = 1)
                                    inner()
                                }
                            }
                        )
                        IconButton(enabled = active != null && !busy && brief.isNotBlank(), onClick = {
                            val id = activeId ?: return@IconButton
                            scope.launch {
                                try {
                                    val plan = withContext(Dispatchers.IO) {
                                        store.saveBrief(id, brief)
                                        ProjectTaskModePolicy.enrichPlan(store.aiPlanPrompt(id), projectTaskMode)
                                    }
                                    val sent = onPlanWithAi(plan)
                                    message = context.getString(if (sent) R.string.studio_plan_sent else R.string.studio_connect_model)
                                } catch (error: Exception) { message = error.message.orEmpty() }
                            }
                        }) { Icon(Icons.Default.Send, stringResource(R.string.studio_plan_with_ai), tint = studioCyan) }
                    }
                }
        }
    }
    }

    if (showCreate) {
        AlertDialog(onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.studio_create_title)) },
            text = { OutlinedTextField(value = projectName, onValueChange = { projectName = it.take(49) },
                label = { Text(stringResource(R.string.studio_project_name)) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { showCreate = false; operate { store.newProject(projectName) } }) {
                    Text(stringResource(R.string.studio_create))
                }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.studio_cancel)) } })
    }

    if (showGithub) {
        AlertDialog(onDismissRequest = { showGithub = false }, title = { Text(stringResource(R.string.studio_import_title)) },
            text = { Column {
                OutlinedTextField(value = githubUrl, onValueChange = { githubUrl = it.take(250) },
                    label = { Text("https://github.com/owner/repo") }, singleLine = true)
                Text(stringResource(R.string.studio_import_hint), fontSize = 11.sp)
            } },
            confirmButton = { TextButton(onClick = {
                showGithub = false; operate { store.importPublicGitHub(githubUrl) }
            }) { Text(stringResource(R.string.studio_import)) } },
            dismissButton = { TextButton(onClick = { showGithub = false }) { Text(stringResource(R.string.studio_cancel)) } })
    }

    if (editingFile != null) {
        val path = editingFile!!
        AlertDialog(onDismissRequest = { editingFile = null },
            title = { Text(path, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 440.dp),
                    value = editor, onValueChange = { editor = it.take(512 * 1024) },
                    textStyle = MaterialTheme.typography.bodySmall,
                    minLines = 10
                )
            },
            confirmButton = { TextButton(onClick = {
                pendingEdit = Triple(path, originalEditor, editor)
                previewExpanded = true
                editingFile = null
                section = 0
            }) { Text(stringResource(R.string.ui_review)) } },
            dismissButton = { TextButton(onClick = { editingFile = null }) { Text(stringResource(R.string.studio_cancel)) } })
    }
}

/** Progress reflects only local evidence, never fabricated remote commits or deployments. */
@Composable
private fun StudioProgressTracker(projectReady: Boolean, filesReady: Boolean, editReady: Boolean) {
    val labels = listOf(
        stringResource(R.string.studio_step_plan),
        stringResource(R.string.studio_step_files),
        stringResource(R.string.ui_code),
        stringResource(R.string.studio_step_review)
    )
    val complete = listOf(projectReady, filesReady, editReady, false)
    val reviewing = editReady
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        HorizontalDivider(Modifier.fillMaxWidth().padding(top = 15.dp),
            color = if (projectReady) studioCyan.copy(alpha = .9f)
                    else MaterialTheme.colorScheme.outlineVariant,
            thickness = 2.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEachIndexed { index, title ->
                val done = complete[index]
                val active = index == 3 && reviewing
                val tint = when {
                    done -> studioCyan
                    active -> studioPurple
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(shape = RoundedCornerShape(50),
                        color = if (active) MaterialTheme.colorScheme.surface else tint,
                        border = androidx.compose.foundation.BorderStroke(
                            if (active) 2.dp else 1.dp,
                            if (active) studioPurple else tint)) {
                        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                            if (done) Icon(Icons.Default.Check, null, tint = Color.Black,
                                modifier = Modifier.size(18.dp))
                            else Text((index + 1).toString(), fontSize = 12.sp,
                                color = if (active) studioPurple else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = if (active) studioPurple else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1)
                    Text(stringResource(when {
                        done -> R.string.design_step_complete
                        active -> R.string.design_step_review
                        else -> R.string.design_step_pending
                    }), fontSize = 9.sp, maxLines = 1,
                        color = if (active) studioPurple else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StudioPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaximusReference.navy,
        border = BorderStroke(1.dp, MaximusReference.stroke),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp), content = content)
    }
}

@Composable
private fun StudioAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                         accent: Color, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.65f)),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 11.dp)) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
    }
}

@Composable
private fun StudioShortcut(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) .96f else 1f,
        label = "studio-shortcut-press")
    Surface(onClick = onClick, interactionSource = interaction, modifier = modifier.scale(scale),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(12.dp).heightIn(min = 46.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2,
                modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
