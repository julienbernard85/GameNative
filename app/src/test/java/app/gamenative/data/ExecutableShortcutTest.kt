package app.gamenative.data

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExecutableShortcutTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun siblingsHaveIndependentIdsAndMetadata() {
        val first = ExecutableShortcut(parentAppId = "STEAM_2552430", containerId = "STEAM_2552430", executablePath = "KH1.exe", name = "Kingdom Hearts")
        val second = ExecutableShortcut(parentAppId = first.parentAppId, containerId = first.containerId, executablePath = "KH2.exe", name = "Kingdom Hearts II")
        assertNotEquals(first.id, second.id)
        assertEquals(first.parentAppId, second.parentAppId)
        assertEquals(first, Json.decodeFromString<ExecutableShortcut>(Json.encodeToString(first)))
    }

    @Test fun onlyRelativeExePathsAreAccepted() {
        listOf("KH1.exe", "KINGDOM HEARTS II FINAL MIX.exe", "KINGDOM HEARTS FINAL MIX.exe",
            "KINGDOM HEARTS Birth by Sleep FINAL MIX.exe", "KINGDOM HEARTS Re_Chain of Memories.exe",
            "KINGDOM HEARTS Theater.exe", "bin/game.EXE", "bin\\game.exe").forEach {
            assertTrue(it, ExecutableShortcut.isSafeExecutablePath(it))
        }
        listOf("", "../game.exe", "bin/../../game.exe", "C:\\game.exe", "/game.exe", "bin//game.exe", "game.bat", "game.exe\n", "a\".exe").forEach {
            assertFalse(it, ExecutableShortcut.isSafeExecutablePath(it))
        }
    }

    @Test fun libraryEntriesKeepUniqueKeysAndParentSteamIdentity() {
        val parent = LibraryItem(appId = "STEAM_2552430", name = "ReMIX", iconHash = "parent-icon", capsuleImageUrl = "parent-cover")
        val entry = ExecutableShortcut(parentAppId = parent.appId, containerId = parent.appId,
            executablePath = "KINGDOM HEARTS FINAL MIX.exe", name = "KH Final Mix", artworkPath = "/private/cover")
        val item = entry.toLibraryItem(parent)
        assertNotEquals(parent.appId, item.appId)
        assertEquals(entry.id, item.executableShortcutId)
        assertEquals(parent.gameId, item.gameId)
        assertEquals(entry.artworkPath, item.clientIconUrl)
        assertEquals(entry.artworkPath, item.capsuleImageUrl)
        assertEquals("parent-cover", parent.capsuleImageUrl)
        assertEquals(0L, item.sizeBytes)
    }

    @Test fun resolutionDoesNotChangeExecutableAndRejectsMissingTargets() {
        val root = temporary.newFolder("game")
        val file = File(root, "game.exe").apply { writeText("unchanged executable") }
        assertEquals(file.canonicalFile, ExecutableShortcut.resolveExecutable(root, "game.exe"))
        assertEquals("unchanged executable", file.readText())
        assertTrue(runCatching { ExecutableShortcut.resolveExecutable(root, "missing.exe") }.isFailure)
        assertTrue(runCatching { ExecutableShortcut.resolveExecutable(root, "../game.exe") }.isFailure)
    }
}
