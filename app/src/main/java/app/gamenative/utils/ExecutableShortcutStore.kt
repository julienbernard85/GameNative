package app.gamenative.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.gamenative.MainActivity
import app.gamenative.data.ExecutableShortcut
import app.gamenative.service.SteamService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Stored in this app's private preferences; no game files or parent config are modified. */
object ExecutableShortcutStore {
    private val _revision = kotlinx.coroutines.flow.MutableStateFlow(0)
    val revision: kotlinx.coroutines.flow.StateFlow<Int> = _revision
    private val json = Json { ignoreUnknownKeys = true }
    private fun prefs(context: Context) = context.getSharedPreferences("executable_shortcuts", Context.MODE_PRIVATE)

    @Synchronized
    fun all(context: Context): List<ExecutableShortcut> = prefs(context).all.values.mapNotNull { value ->
        runCatching { json.decodeFromString<ExecutableShortcut>(value as String) }.getOrNull()
    }.sortedBy { it.name.lowercase() }

    fun find(context: Context, id: String): ExecutableShortcut? =
        prefs(context).getString(id, null)?.let { runCatching { json.decodeFromString<ExecutableShortcut>(it) }.getOrNull() }

    @Synchronized
    fun save(context: Context, entry: ExecutableShortcut) {
        validate(context, entry)
        check(prefs(context).edit().putString(entry.id, json.encodeToString(entry)).commit()) { "Could not save shortcut" }
        _revision.value += 1
    }

    @Synchronized
    fun remove(context: Context, entry: ExecutableShortcut) {
        check(prefs(context).edit().remove(entry.id).commit()) { "Could not remove shortcut" }
        _revision.value += 1
        val manager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
        manager?.removeDynamicShortcuts(listOf("executable_${entry.id}"))
        manager?.disableShortcuts(listOf("executable_${entry.id}"), "Executable shortcut removed")
        // Artwork belongs exclusively to the shortcut and lives in app-private storage.
        removeArtwork(context, entry)
    }

    fun removeArtwork(context: Context, entry: ExecutableShortcut) {
        if (entry.artworkPath.isEmpty()) return
        val directory = java.io.File(context.filesDir, "executable_shortcut_art").canonicalFile
        val artwork = java.io.File(entry.artworkPath).canonicalFile
        if (artwork.parentFile == directory && (artwork.name == entry.id || artwork.name.startsWith("${entry.id}-"))) {
            artwork.delete()
        }
    }

    fun validate(context: Context, entry: ExecutableShortcut) {
        require(ContainerUtils.hasContainer(context, entry.containerId)) { "Parent container missing" }
        val container = ContainerUtils.getContainer(context, entry.containerId)
        require(!container.isLaunchRealSteam || container.isLaunchHeadlessSteam) {
            context.getString(app.gamenative.R.string.executable_shortcut_gui_steam_unsupported)
        }
        val gameId = entry.parentAppId.removePrefix("STEAM_").toInt()
        require(SteamService.isAppInstalled(gameId)) { "Parent game is not installed" }
        ExecutableShortcut.resolveExecutable(java.io.File(SteamService.getAppDirPath(gameId)), entry.executablePath)
    }

    fun launchUri(entry: ExecutableShortcut): Uri = Uri.Builder().scheme("gamenative").authority("run")
        .appendQueryParameter("shortcut", entry.id).build()

    fun launchIntent(context: Context, entry: ExecutableShortcut): Intent =
        Intent(Intent.ACTION_VIEW, launchUri(entry), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
