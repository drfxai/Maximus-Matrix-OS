package ai.drfx.maximus.matrixai.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

private val studioCyan = Color(0xFF48CEE8)
private val studioPurple = Color(0xFFAA86F7)
private val studioGreen = Color(0xFF54D9B0)

/**
 * Real on-device workspace operations, rendered mobile-first.
 * The external AI request contains project name, brief and filenames only.
 * No build or remote execution is claimed without a configured worker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectStudioScreen(
    onPlanWithAi: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("project_studio_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("MAXIMUS MATRIX OS", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = studioCyan)
                Text(stringResource(R.string.studio_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.studio_subtitle),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            StudioPanel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.FolderOpen, null, tint = studioCyan)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.studio_active_project), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Text(active?.name ?: stringResource(R.string.studio_no_project), fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (active == null) stringResource(R.string.studio_create_import) else
                            stringResource(R.string.studio_files_count, active.fileCount), fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        TextButton(onClick = { showSelector = true }, enabled = projects.isNotEmpty()) {
                            Text(stringResource(R.string.studio_select)); Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = showSelector, onDismissRequest = { showSelector = false }) {
                            projects.forEach { project ->
                                DropdownMenuItem(text = { Text(project.name) }, onClick = {
                                    activeId = project.id; showSelector = false
                                })
                            }
                        }
                    }
                }
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
        item {
            StudioPanel {
                HindsightMemoryPanel(activeId)
            }
        }
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
        item {
            StudioPanel {
                Text(stringResource(R.string.studio_pipeline), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(stringResource(R.string.studio_step_plan), stringResource(R.string.studio_step_files),
                        stringResource(R.string.studio_step_tests), stringResource(R.string.studio_step_build),
                        stringResource(R.string.studio_step_review)).forEachIndexed { index, label ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(shape = RoundedCornerShape(50),
                                color = if (index == 0 && active != null) studioGreen else MaterialTheme.colorScheme.surfaceVariant) {
                                Text(if (index == 0 && active != null) "✓" else (index + 1).toString(),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    color = if (index == 0 && active != null) Color.Black else MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(label, fontSize = 11.sp)
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
                Text(stringResource(R.string.studio_pipeline_notice), fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        item { PhoneHarnessPanel(projectId = activeId) }
        item { RemoteWorkerPanel(projectId = activeId) }
        item {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                val id = activeId
                editingFile = null
                if (id != null) scope.launch {
                    try {
                        withContext(Dispatchers.IO) { store.saveFile(id, path, editor) }
                        refresh(id)
                        message = context.getString(R.string.studio_file_saved, path)
                    } catch (e: Exception) { message = e.message ?: "Edit failed." }
                }
            }) { Text(stringResource(R.string.studio_save_file)) } },
            dismissButton = { TextButton(onClick = { editingFile = null }) { Text(stringResource(R.string.studio_cancel)) } })
    }
}

@Composable
private fun StudioPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, studioCyan.copy(alpha = 0.32f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), content = content)
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
