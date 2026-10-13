package app.gamenative.launch

import androidx.compose.runtime.staticCompositionLocalOf

// Keep XServerScreen's parameter count stable: its generated dex method is near
// the verifier's register limit. The route owns this value for the whole session.
internal val LocalExecutableShortcutPath = staticCompositionLocalOf<String?> { null }
