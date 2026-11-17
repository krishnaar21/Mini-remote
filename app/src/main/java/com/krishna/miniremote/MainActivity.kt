package com.krishna.miniremote

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.krishna.miniremote.ui.Inter
import com.krishna.miniremote.ui.AppShell
import androidx.compose.material3.Typography
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.text.font.FontFamily

class MainActivity : ComponentActivity() {
    private val vm: RemoteVM by viewModels()

    /** Phone volume buttons drive the TV while one is connected (and the setting is on). */
    private fun tvVolumeKey(keyCode: Int): RemoteKey? {
        if (!vm.settings.volumeKeys || vm.active == null || vm.link != Link.READY) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> RemoteKey.VOL_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> RemoteKey.VOL_DOWN
            else -> null
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val k = tvVolumeKey(keyCode) ?: return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) vm.press(k) else vm.scrubTick(k)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        tvVolumeKey(keyCode) != null || super.onKeyUp(keyCode, event)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val accent = com.krishna.miniremote.ui.accentColor(vm.settings.accentHex)
            val onAccent = if (accent.luminance() > 0.5f) Color(0xFF1B1B1B) else Color.White
            MaterialTheme(
                // Material 3 dark, neutral surfaces, with the chosen accent as the primary colour.
                colorScheme = darkColorScheme(
                    primary = accent, onPrimary = onAccent,
                    primaryContainer = accent.copy(alpha = 0.3f).compositeOver(Color(0xFF1C1C1E)), onPrimaryContainer = Color.White,
                    secondaryContainer = accent.copy(alpha = 0.22f).compositeOver(Color(0xFF1C1C1E)), onSecondaryContainer = Color.White,
                    background = Color(0xFF1C1C1E), surface = Color(0xFF1C1C1E),
                    surfaceContainerLowest = Color(0xFF161618), surfaceContainerLow = Color(0xFF262629),
                    surfaceContainer = Color(0xFF2E2E31), surfaceContainerHigh = Color(0xFF3D3D41), surfaceContainerHighest = Color(0xFF4C4C51),
                    onSurface = Color(0xFFF7F7F8), onSurfaceVariant = Color(0xFFD6D9DC),
                    outline = Color(0xFF8E918F), outlineVariant = Color(0xFF444746),
                ),
                typography = Typography().withFont(Inter),
            ) {
                AppShell(vm)
            }
            // Window flags are a side effect, so apply them after composition rather than during it.
            val keepAwake = vm.settings.keepAwake
            SideEffect {
                if (keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

/** Every Material text style in [f], so buttons, fields and dialogs use the design's font too. */
private fun Typography.withFont(f: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f), displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f), headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f), titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f), bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f), labelSmall = labelSmall.copy(fontFamily = f),
)
