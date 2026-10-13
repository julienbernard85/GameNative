package app.gamenative.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.gamenative.data.ExecutableShortcut
import app.gamenative.service.SteamService
import com.winlator.container.Container
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class)
class ExecutableShortcutStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var root: File
    private val parentId = "STEAM_2552430"

    @Before fun setup() {
        context.getSharedPreferences("executable_shortcuts", Context.MODE_PRIVATE).edit().clear().commit()
        root = temporary.newFolder("installation")
        File(root, "KH1.exe").writeText("original one")
        File(root, "KH2.exe").writeText("original two")
        val container = Container(parentId).apply { executablePath = "launcher.exe" }
        mockkObject(ContainerUtils, SteamService.Companion)
        every { ContainerUtils.hasContainer(context, parentId) } returns true
        every { ContainerUtils.getContainer(context, parentId) } returns container
        every { SteamService.isAppInstalled(2552430) } returns true
        every { SteamService.getAppDirPath(2552430) } returns root.absolutePath
    }

    @After fun cleanup() {
        unmockkObject(ContainerUtils, SteamService.Companion)
    }

    @Test fun saveEditAndRemoveOneSiblingPreservesParentAndOtherEntry() {
        val first = ExecutableShortcut(parentAppId = parentId, containerId = parentId, executablePath = "KH1.exe", name = "KH1")
        val second = ExecutableShortcut(parentAppId = parentId, containerId = parentId, executablePath = "KH2.exe", name = "KH2")
        ExecutableShortcutStore.save(context, first)
        ExecutableShortcutStore.save(context, second)
        assertEquals(2, ExecutableShortcutStore.all(context).size)
        ExecutableShortcutStore.save(context, first.copy(name = "Final Mix"))
        assertEquals("Final Mix", ExecutableShortcutStore.find(context, first.id)!!.name)
        ExecutableShortcutStore.remove(context, first)
        assertNull(ExecutableShortcutStore.find(context, first.id))
        assertEquals(second, ExecutableShortcutStore.find(context, second.id))
        assertEquals("launcher.exe", ContainerUtils.getContainer(context, parentId).executablePath)
        assertEquals("original one", File(root, "KH1.exe").readText())
        assertEquals("original two", File(root, "KH2.exe").readText())
    }

    @Test fun removedParentAndMissingExeFailValidationWithoutChangingMetadata() {
        val entry = ExecutableShortcut(parentAppId = parentId, containerId = parentId, executablePath = "KH1.exe", name = "KH1")
        ExecutableShortcutStore.save(context, entry)
        every { ContainerUtils.hasContainer(context, parentId) } returns false
        assertTrue(runCatching { ExecutableShortcutStore.validate(context, entry) }.isFailure)
        every { ContainerUtils.hasContainer(context, parentId) } returns true
        File(root, "KH1.exe").delete()
        assertTrue(runCatching { ExecutableShortcutStore.validate(context, entry) }.isFailure)
        assertEquals(entry, ExecutableShortcutStore.find(context, entry.id))
    }

    @Test fun artworkRemovalCannotDeleteFilesOutsideShortcutStorage() {
        val protectedFile = File(root, "cover.png").apply { writeText("keep") }
        val entry = ExecutableShortcut(parentAppId = parentId, containerId = parentId, executablePath = "KH1.exe", name = "KH1",
            artworkPath = protectedFile.absolutePath)
        ExecutableShortcutStore.removeArtwork(context, entry)
        assertEquals("keep", protectedFile.readText())
    }
}
