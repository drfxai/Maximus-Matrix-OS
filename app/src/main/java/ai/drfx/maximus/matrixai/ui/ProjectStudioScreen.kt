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
import ai.drfx.maximus.matrixai.studio.ProjectWorkspaceStore
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
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf(store.projects()) }
    var activeId by remember { mutableStateOf(projects.firstOrNull()?.id) }
    val active = projects.firstOrNull { it.id == activeId }
    var files by remember { mutableStateOf(emptyList<StudioFile>()) }
    var brief by remember(activeId) { mutableStateOf(active?.brief.orEmpty()) }
    var projectName by remember { mutableStateOf("") }
    var githubUrl by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var showGithub by remember { mutableStateOf(false) }
    var showSelector by remember { mutableStateOf(false) }
    var editingFile by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Workspace operations are local until you explicitly request AI planning or export.") }

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
                message = "Project " + project.name + " is ready. No cloud build has been run."
            } catch (t: Exception) {
                message = "Operation failed: " + (t.message ?: t.javaClass.simpleName)
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
                    message = "Project ZIP exported. Review its files and secrets before sharing."
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
                Text("Project Studio  ·  استودیوی پروژه", style = MaterialTheme.typography.titleMedium)
                Text("Build your workspace on Android; use connected AI for planning.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            StudioPanel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.FolderOpen, null, tint = studioCyan)
                    Column(Modifier.weight(1f)) {
                        Text("پروژه فعال", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Text(active?.name ?: "No project", fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (active == null) "Create or import a project" else
                            active.fileCount.toString() + " files · App-private storage", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        TextButton(onClick = { showSelector = true }, enabled = projects.isNotEmpty()) {
                            Text("Select"); Icon(Icons.Default.ArrowDropDown, null)
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
                StudioAction("New", Icons.Default.Add, studioCyan, enabled = !busy) { projectName = ""; showCreate = true }
                StudioAction("GitHub", Icons.Default.CloudDownload, studioPurple, enabled = !busy) {
                    githubUrl = ""; showGithub = true
                }
                StudioAction("ZIP", Icons.Default.FolderZip, studioGreen, enabled = !busy) {
                    zipPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                }
                StudioAction("Export", Icons.Default.UploadFile, studioCyan, enabled = !busy && active != null) {
                    zipWriter.launch((active?.name ?: "project") + ".zip")
                }
            }
        }
        item {
            StudioPanel {
                Text("AI Project Brief", fontWeight = FontWeight.SemiBold, color = studioPurple)
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(
                    value = brief,
                    onValueChange = { brief = it.take(12000) },
                    label = { Text("Describe the application or next task") },
                    placeholder = { Text("Build a Kotlin / Jetpack Compose Android app...") },
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
                                message = "Project brief saved locally."
                            } catch (e: Exception) { message = e.message ?: "Save failed." }
                        }
                    }, enabled = active != null && !busy) {
                        Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp)); Text("Save")
                    }
                    Button(onClick = {
                        val id = activeId ?: return@Button
                        if (busy) return@Button
                        scope.launch {
                            try {
                                val plan = withContext(Dispatchers.IO) {
                                    store.saveBrief(id, brief)
                                    store.aiPlanPrompt(id)
                                }
                                refresh(id)
                                val sent = onPlanWithAi(plan)
                                message = if (sent) "AI planning request sent to the selected model." else
                                    "Connect a supported API/model in Chat first. No request was sent."
                            } catch (e: Exception) { message = e.message ?: "Could not request AI planning." }
                        }
                    }, enabled = active != null && !busy) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp)); Text("Plan with AI")
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text("Only project name, description and file paths are sent when you tap Plan with AI. " +
                    "Project source code is not automatically uploaded.", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            StudioPanel {
                Text("Project pipeline", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("Plan", "Files", "Tests", "Build", "Review").forEachIndexed { index, label ->
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
                Text("Workspace created/imported does not mean Android compilation or tests passed. " +
                    "Cloud Worker and signed release integration are not configured.", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            StudioPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, tint = studioCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Files / فایل‌های پروژه", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(files.size.toString(), color = studioGreen)
                }
                Spacer(Modifier.height(5.dp))
                if (files.isEmpty()) Text("No files to display", fontSize = 12.sp)
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
                if (files.size > 80) Text("Showing first 80 files. Export ZIP to inspect all.", fontSize = 11.sp)
            }
        }
        item {
            StudioPanel {
                Text("Cloud Execution", fontWeight = FontWeight.SemiBold, color = studioCyan)
                Text("The Maximus Android APK is built and signed in GitHub Actions. Imported user projects " +
                    "are never sent to that privileged signing workflow. A separate authenticated, isolated " +
                    "build worker is required for safe user-project builds.", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("Status: NOT CONNECTED", fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        item {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showCreate) {
        AlertDialog(onDismissRequest = { showCreate = false },
            title = { Text("Create Android project") },
            text = { OutlinedTextField(value = projectName, onValueChange = { projectName = it.take(49) },
                label = { Text("Project name (English letters)") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { showCreate = false; operate { store.newProject(projectName) } }) {
                    Text("Create")
                }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } })
    }

    if (showGithub) {
        AlertDialog(onDismissRequest = { showGithub = false }, title = { Text("Import public GitHub repository") },
            text = { Column {
                OutlinedTextField(value = githubUrl, onValueChange = { githubUrl = it.take(250) },
                    label = { Text("https://github.com/owner/repo") }, singleLine = true)
                Text("Public repositories only. Size limit 20 MB compressed, 32 MB extracted. " +
                    "Imported code will not execute automatically.", fontSize = 11.sp)
            } },
            confirmButton = { TextButton(onClick = {
                showGithub = false; operate { store.importPublicGitHub(githubUrl) }
            }) { Text("Import") } },
            dismissButton = { TextButton(onClick = { showGithub = false }) { Text("Cancel") } })
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
                        message = "Saved: " + path
                    } catch (e: Exception) { message = e.message ?: "Edit failed." }
                }
            }) { Text("Save file") } },
            dismissButton = { TextButton(onClick = { editingFile = null }) { Text("Cancel") } })
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
