package app.gamenative.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.gamenative.data.ExecutableShortcut
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class)
class ExecutableShortcutIntentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val first = ExecutableShortcut(parentAppId = "STEAM_2552430", containerId = "STEAM_2552430", executablePath = "KH1.exe", name = "KH1")
    private val second = ExecutableShortcut(parentAppId = first.parentAppId, containerId = first.containerId, executablePath = "KH2.exe", name = "KH2")

    @Before fun seed() {
        context.getSharedPreferences("executable_shortcuts", Context.MODE_PRIVATE).edit().clear()
            .putString(first.id, Json.encodeToString(first)).putString(second.id, Json.encodeToString(second)).commit()
    }

    @Test fun deepLinksResolveDistinctEntriesToSameParent() {
        val a = IntentLaunchManager.parseLaunchIntent(ExecutableShortcutStore.launchIntent(context, first), context)!!
        val b = IntentLaunchManager.parseLaunchIntent(ExecutableShortcutStore.launchIntent(context, second), context)!!
        assertEquals(a.appId, b.appId)
        assertEquals(first.id, a.shortcutId)
        assertEquals(second.id, b.shortcutId)
        assertNull(a.containerConfig)
    }

    @Test fun androidShortcutResolvesStoredIdentityInsteadOfCallerSuppliedParent() {
        val intent = Intent("app.gamenative.LAUNCH_GAME").putExtra("app_id", 123)
            .putExtra("executable_shortcut_id", second.id)
        assertEquals(second.parentAppId, IntentLaunchManager.parseLaunchIntent(intent, context)!!.appId)
    }

    @Test fun missingShortcutDoesNotFallBackToParent() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("gamenative://run?shortcut=missing&appid=2552430&gamesource=STEAM"))
        assertNull(IntentLaunchManager.parseLaunchIntent(intent, context))
        assertNull(IntentLaunchManager.parseLaunchIntent(Intent("app.gamenative.LAUNCH_GAME")
            .putExtra("app_id", 2552430).putExtra("executable_shortcut_id", "missing"), context))
    }

    @Test fun existingGameLaunchContractsRemainUnchanged() {
        val extra = IntentLaunchManager.parseLaunchIntent(Intent("app.gamenative.LAUNCH_GAME").putExtra("app_id", 2552430))!!
        val link = IntentLaunchManager.parseLaunchIntent(Intent(Intent.ACTION_VIEW, Uri.parse("gamenative://run?appid=2552430&gamesource=STEAM")))!!
        assertEquals(extra, link)
        assertNull(extra.shortcutId)
        assertEquals(first.parentAppId, extra.appId)
    }

    @Test fun unsupportedActionsAndHostsCannotResolveStoredShortcuts() {
        assertNull(IntentLaunchManager.parseLaunchIntent(Intent(Intent.ACTION_MAIN).putExtra("executable_shortcut_id", first.id), context))
        assertNull(IntentLaunchManager.parseLaunchIntent(Intent(Intent.ACTION_VIEW, Uri.parse("gamenative://other?shortcut=${first.id}")), context))
    }
}
