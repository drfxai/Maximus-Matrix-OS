package ai.drfx.maximus.matrixai.ui

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.localmodels.LocalModelEntry
import ai.drfx.maximus.matrixai.localmodels.LocalModelFormat
import ai.drfx.maximus.matrixai.localmodels.LocalModelIntakePolicy
import ai.drfx.maximus.matrixai.localmodels.LocalModelLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * File inventory, not inference. A model remains unrunnable until a separate
 * native engine passes ABI, RAM, format and live capability verification.
 */
@Composable
fun LocalModelHubPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val library = remember(context) { LocalModelLibrary(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(library.list()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var pendingForget by remember { mutableStateOf<LocalModelEntry?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && !busy) {
            busy = true
            scope.launch {
                try {
                    val newEntry = withContext(Dispatchers.IO) {
                        val resolver = context.contentResolver
                        var displayName: String? = null
                        var size = -1L
                        resolver.query(
                            uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                            null, null, null
                        )?.use { cursor: Cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                                    displayName = cursor.getString(nameIndex)
                                }
                                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                                    size = cursor.getLong(sizeIndex)
                                }
                            }
                        }
                        val name = requireNotNull(displayName) { "File name is unavailable." }
                        val prefix = ByteArray(8)
                        val count = resolver.openInputStream(uri)?.use { input ->
                            var read = 0
                            while (read < prefix.size) {
                                val n = input.read(prefix, read, prefix.size - read)
                                if (n < 0) break
                                if (n == 0) break
                                read += n
                            }
                            read
                        } ?: error("Cannot open the selected document.")
                        val probe = LocalModelIntakePolicy.inspect(
                            name, size, prefix.copyOf(count)
                        )
                        require(probe.format != LocalModelFormat.UNSUPPORTED) { probe.note }
                        // The Android document picker grants the initial user permission.
                        // Persist only a read grant, not broad storage access.
                        resolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        library.register(uri, name, size, probe)
                    }
                    entries = library.list()
                    status = newEntry.displayName + " indexed. " + newEntry.note
                } catch (error: Exception) {
                    status = error.message ?: "Model import failed."
                } finally {
                    busy = false
                }
            }
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp).testTag("local_model_hub"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Local Model Hub", style = MaterialTheme.typography.titleLarge)
        Text(
            "Register your own model files from Android storage without uploading " +
                "them. This is a secure model inventory: native model execution " +
                "and Hugging Face downloads are NOT enabled in this build.",
            style = MaterialTheme.typography.bodySmall
        )
        Button(
            onClick = { picker.launch(arrayOf("application/octet-stream", "*/*")) },
            enabled = !busy, modifier = Modifier.testTag("local_model_import")
        ) { Text("Import GGUF / LiteRT-LM file") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
        Text("Registered files · " + entries.size, style = MaterialTheme.typography.titleMedium)
        if (entries.isEmpty()) {
            Text("No local models registered yet.", style = MaterialTheme.typography.bodySmall)
        }
        entries.forEach { entry ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(entry.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        entry.format.displayName + " · " +
                            String.format(Locale.ROOT, "%.2f GiB",
                                entry.sizeBytes.toDouble() / (1024.0 * 1024 * 1024)),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(entry.note, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Not yet runnable · no runtime/vision/audio capability confirmed",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    TextButton(onClick = { pendingForget = entry }) {
                        Text("Forget file reference")
                    }
                }
            }
        }
        Text(
            "A document picker may return a cloud-backed file. Being indexed " +
                "does not prove that a model is available offline. Forgetting a " +
                "reference does not delete the original model file.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    pendingForget?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingForget = null },
            title = { Text("Forget model reference?") },
            text = { Text("This removes the saved reference to " + entry.displayName +
                " without deleting the original file.") },
            confirmButton = {
                Button(onClick = {
                    try {
                        val removed = library.forget(entry.id)
                        if (removed != null) {
                            context.contentResolver.releasePersistableUriPermission(
                                Uri.parse(removed.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                        entries = library.list()
                        status = "File reference forgotten. Original file is unchanged."
                    } catch (error: Exception) {
                        status = error.message ?: "Could not forget file reference."
                    } finally {
                        pendingForget = null
                    }
                }) { Text("Forget reference") }
            },
            dismissButton = {
                TextButton(onClick = { pendingForget = null }) { Text("Cancel") }
            }
        )
    }
}
