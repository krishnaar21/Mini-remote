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

    private val askNotifications = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}

    private fun handle(intent: android.content.Intent?) {
        when (intent?.action) {
            Outside.ACTION_LAUNCH -> { intent.getStringExtra(Outside.EXTRA_SHORTCUT)?.let { vm.launchWhenReady(it) }; intent.action = null }
            Outside.ACTION_WAKE -> { vm.wake(); intent.action = null }
        }
        // CI screenshot runs: `adb shell am start -e shot <screen>` opens one screen on pretend TVs.
        // Only on a debuggable build with USB debugging on; nothing is saved or sent (see RemoteVM.shot).
        intent?.getStringExtra(com.krishna.miniremote.ui.Shot.EXTRA)?.let { s ->
            intent.removeExtra(com.krishna.miniremote.ui.Shot.EXTRA)
            val debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val adb = android.provider.Settings.Global.getInt(contentResolver, android.provider.Settings.Global.ADB_ENABLED, 0) == 1
            if (debuggable && adb && s.length <= 24) {
                vm.screenshotDemo(trackpad = s == "trackpad", live = s == "live")
                com.krishna.miniremote.ui.Shot.name = s
            }
        }
        if (intent?.getBooleanExtra(Updater.EXTRA_INSTALL, false) == true) {
            intent.removeExtra(Updater.EXTRA_INSTALL)
            vm.installFromNotification()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        Outside.hide(this)
        vm.maybeCheckForUpdate()
        vm.autoConnect()
        vm.btResume()
    }

    override fun onStop() {
        super.onStop()
        // In the background with a TV connected: volume and play keys in a notification.
        val d = vm.active
        if (!isChangingConfigurations && vm.settings.notifRemote && d != null && vm.link == Link.READY) Outside.show(this, d.name, vm.playing)
    }

    override fun onDestroy() {
        if (isFinishing) Outside.hide(this)
        super.onDestroy()
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
        AppIcon.reconcile(this)
        Outside.vm = java.lang.ref.WeakReference(vm)
        handle(intent)
        // Android 13+: ask once so a ready update can show a notification.
        if ((vm.settings.autoUpdate || vm.settings.notifRemote) && android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            val accent = com.krishna.miniremote.ui.accentColor(vm.settings.accentHex)
            // The theme: every colour in the app reads from it.
            val pal = com.krishna.miniremote.ui.Palettes.resolve(vm.settings.theme, androidx.compose.foundation.isSystemInDarkTheme())
            if (com.krishna.miniremote.ui.Look.p != pal) com.krishna.miniremote.ui.Look.p = pal
            if (com.krishna.miniremote.ui.Look.depth != vm.settings.depth) com.krishna.miniremote.ui.Look.depth = vm.settings.depth
            androidx.compose.runtime.LaunchedEffect(pal) {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = pal.light; isAppearanceLightNavigationBars = pal.light
                }
            }
            val onAccent = if (accent.luminance() > 0.5f) Color(0xFF1B1B1B) else Color.White
            MaterialTheme(
                // Material 3 dark, neutral surfaces, with the chosen accent as the primary colour.
                colorScheme = (if (pal.light) androidx.compose.material3.lightColorScheme(
                    primary = accent, onPrimary = onAccent,
                    background = pal.bg, surface = pal.bg, surfaceContainerLow = pal.sheet, surfaceContainer = pal.container,
                    surfaceContainerHigh = pal.high, surfaceContainerHighest = pal.highest, onSurface = pal.ink, onSurfaceVariant = pal.dim,
                    outline = pal.sub, outlineVariant = pal.line,
                ) else darkColorScheme(
                    primary = accent, onPrimary = onAccent,
                    primaryContainer = accent.copy(alpha = 0.3f).compositeOver(pal.bg), onPrimaryContainer = Color.White,
                    secondaryContainer = accent.copy(alpha = 0.22f).compositeOver(pal.bg), onSecondaryContainer = Color.White,
                    background = pal.bg, surface = pal.bg,
                    surfaceContainerLowest = pal.screen, surfaceContainerLow = pal.sheet,
                    surfaceContainer = pal.container, surfaceContainerHigh = pal.high, surfaceContainerHighest = pal.highest,
                    onSurface = pal.ink, onSurfaceVariant = pal.dim,
                    outline = pal.sub, outlineVariant = pal.line,
                )),
                typography = Typography().withFont(Inter),
            ) {
                AppShell(vm)
            }
            // Long-press the app icon: Quick access apps and Turn on TV.
            androidx.compose.runtime.LaunchedEffect(vm.shortcuts) { Outside.publishShortcuts(this@MainActivity, vm.shortcuts) }
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
