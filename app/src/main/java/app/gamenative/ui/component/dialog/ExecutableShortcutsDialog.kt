package app.gamenative.ui.component.dialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.data.ExecutableShortcut
import app.gamenative.data.GameSource
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.utils.ExecutableShortcutStore
import app.gamenative.utils.createPinnedShortcut
import app.gamenative.ui.util.ListItemImage
import com.winlator.container.ContainerData
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExecutableShortcutsDialog(parentAppId: String, containerData: ContainerData, onDismiss: () -> Unit, selectedEntryId: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember(parentAppId) {
        mutableStateOf(ExecutableShortcutStore.all(context).filter { it.parentAppId == parentAppId }.sortedBy { it.id != selectedEntryId })
    }
    var editing by remember { mutableStateOf<ExecutableShortcut?>(null) }
    var name by remember { mutableStateOf("") }
    var executable by remember { mutableStateOf("") }
    var artwork by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { artwork = it }
    fun reload() {
        entries = ExecutableShortcutStore.all(context).filter { it.parentAppId == parentAppId }
    }
    fun reset() {
        editing = null
        name = ""
        executable = ""
        artwork = null
        error = null
    }
    fun perform(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: context.getString(R.string.executable_shortcut_launch_failed)
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.create_executable_shortcut)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.executable_shortcuts_description))
                entries.forEach { entry ->
                    HorizontalDivider()
                    Row {
                        ListItemImage(image = { entry.artworkPath }, modifier = Modifier.size(64.dp), imageModifier = Modifier)
                        Column {
                            Text(entry.name)
                            Text(entry.executablePath)
                        }
                    }
                    Row {
                        TextButton(enabled = !busy, onClick = {
                            perform {
                                withContext(Dispatchers.IO) { ExecutableShortcutStore.validate(context, entry) }
                                context.startActivity(ExecutableShortcutStore.launchIntent(context, entry))
                                onDismiss()
                            }
                        }) { Text(stringResource(R.string.executable_shortcut_play)) }
                        TextButton(enabled = !busy, onClick = {
                            perform {
                                createPinnedShortcut(context, entry.parentAppId.removePrefix("STEAM_").toInt(), entry.name,
                                    GameSource.STEAM, entry.artworkPath.takeIf { it.isNotEmpty() }, entry.id)
                                SnackbarManager.show(context.getString(R.string.executable_shortcut_pin_requested))
                            }
                        }) { Text(stringResource(R.string.create_shortcut)) }
                    }
                    Row {
                        TextButton(enabled = !busy, onClick = {
                            editing = entry
                            name = entry.name
                            executable = entry.executablePath
                            artwork = null
                        }) { Text(stringResource(R.string.executable_shortcut_edit)) }
                        TextButton(enabled = !busy, onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(entry.name, ExecutableShortcutStore.launchUri(entry).toString()))
                        }) { Text(stringResource(R.string.executable_shortcut_copy_link)) }
                        TextButton(enabled = !busy, onClick = {
                            perform {
                                withContext(Dispatchers.IO) { ExecutableShortcutStore.remove(context, entry) }
                                if (editing?.id == entry.id) reset()
                                reload()
                            }
                        }) { Text(stringResource(R.string.executable_shortcut_delete)) }
                    }
                }
                HorizontalDivider()
                OutlinedTextField(value = name, onValueChange = { name = it }, enabled = !busy,
                    label = { Text(stringResource(R.string.executable_shortcut_name)) }, singleLine = true)
                ExecutablePathDropdown(value = executable, onValueChange = { executable = it }, containerData = containerData,
                    modifier = Modifier.fillMaxWidth())
                ListItemImage(image = { artwork?.toString() ?: editing?.artworkPath.orEmpty() }, modifier = Modifier.size(80.dp), imageModifier = Modifier)
                TextButton(enabled = !busy, onClick = { picker.launch("image/*") }) {
                    Text(stringResource(R.string.executable_shortcut_artwork))
                }
                if (editing != null) TextButton(enabled = !busy, onClick = { reset() }) {
                    Text(stringResource(R.string.executable_shortcut_new))
                }
                error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank() && ExecutableShortcut.isSafeExecutablePath(executable), onClick = {
                val oldEntry = editing
                val entryId = oldEntry?.id ?: UUID.randomUUID().toString()
                val selectedArtwork = artwork
                val entryName = name.trim()
                val entryExecutable = executable.replace('\\', '/')
                perform {
                    val savedEntry = withContext(Dispatchers.IO) {
                        var entry = ExecutableShortcut(entryId, parentAppId, parentAppId, entryExecutable, entryName,
                            oldEntry?.artworkPath.orEmpty())
                        ExecutableShortcutStore.validate(context, entry)
                        if (selectedArtwork != null) {
                            val destination = File(context.filesDir, "executable_shortcut_art/$entryId-${UUID.randomUUID()}")
                            destination.parentFile?.mkdirs()
                            val temporary = File(destination.parentFile, "$entryId.tmp")
                            try {
                                context.contentResolver.openInputStream(selectedArtwork).use { input ->
                                    requireNotNull(input) { "Cannot read artwork" }
                                    temporary.outputStream().use { output -> input.copyTo(output) }
                                }
                                check(temporary.renameTo(destination)) { "Cannot save artwork" }
                            } finally {
                                temporary.delete()
                            }
                            entry = entry.copy(artworkPath = destination.absolutePath)
                        }
                        ExecutableShortcutStore.save(context, entry)
                        if (oldEntry != null && oldEntry.artworkPath != entry.artworkPath) {
                            ExecutableShortcutStore.removeArtwork(context, oldEntry)
                        }
                        entry
                    }
                    if (oldEntry != null) {
                        createPinnedShortcut(context, savedEntry.parentAppId.removePrefix("STEAM_").toInt(), savedEntry.name,
                            GameSource.STEAM, savedEntry.artworkPath.takeIf { it.isNotEmpty() }, savedEntry.id, requestPin = false)
                    }
                    reload()
                    reset()
                }
            }) { Text(stringResource(R.string.executable_shortcut_save)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
