package com.winlator.container

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class)
class ContainerSessionExecutableTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun sessionTargetSurvivesRuntimeReadsButIsNeverSaved() {
        val container = Container("STEAM_2552430")
        container.setRootDir(temporary.newFolder("container"))
        container.executablePath = "launcher.exe"
        container.setSessionExecutablePath("KH2.exe")
        assertEquals("KH2.exe", container.executablePath)
        assertEquals("launcher.exe", container.configuredExecutablePath)
        // Launch helpers assigning their resolved path must not overwrite the parent either.
        container.executablePath = container.executablePath
        assertTrue(container.saveDataChecked())
        val saved = JSONObject(container.configFile.readText())
        assertEquals("launcher.exe", saved.getString("executablePath"))
        assertFalse(saved.has("sessionExecutablePath"))
        container.setSessionExecutablePath(null)
        assertEquals("launcher.exe", container.executablePath)
    }
}
