package app.gamenative.data

import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable

/** Metadata only: the installation, Steam identity and Wine prefix remain owned by the parent. */
@Serializable
data class ExecutableShortcut(
    val id: String = UUID.randomUUID().toString(),
    val parentAppId: String,
    val containerId: String,
    val executablePath: String,
    val name: String,
    val artworkPath: String = "",
) {
    fun toLibraryItem(parent: LibraryItem): LibraryItem = parent.copy(
        appId = "EXECUTABLE_$id",
        name = name,
        executableShortcutId = id,
        shortcutParentAppId = parentAppId,
        iconHash = artworkPath.ifEmpty { parent.clientIconUrl },
        capsuleImageUrl = artworkPath.ifEmpty { parent.capsuleImageUrl },
        headerImageUrl = artworkPath.ifEmpty { parent.headerImageUrl },
        heroImageUrl = artworkPath.ifEmpty { parent.heroImageUrl },
        sizeBytes = 0L,
        isInstalled = true,
    )

    init {
        require(UUID.fromString(id).toString() == id)
        require(parentAppId.startsWith("STEAM_") && parentAppId.removePrefix("STEAM_").toInt() > 0)
        require(containerId == parentAppId)
        require(name.isNotBlank())
        require(isSafeExecutablePath(executablePath))
    }

    companion object {
        fun isSafeExecutablePath(path: String): Boolean {
            val normalized = path.replace('\\', '/')
            return normalized.isNotBlank() && normalized.endsWith(".exe", ignoreCase = true) &&
                !normalized.startsWith('/') && ':' !in normalized &&
                normalized.none { it.isISOControl() || it == '"' } &&
                normalized.split('/').none { it.isEmpty() || it == "." || it == ".." }
        }

        /** Reject traversal and symlinks escaping the existing installation. Never writes to it. */
        fun resolveExecutable(installation: File, path: String): File {
            require(isSafeExecutablePath(path)) { "Invalid executable path" }
            val root = installation.canonicalFile
            val executable = File(root, path.replace('\\', '/')).canonicalFile
            require(executable.toPath().startsWith(root.toPath()) && executable.isFile) {
                "Executable missing or outside the installation"
            }
            return executable
        }
    }
}
