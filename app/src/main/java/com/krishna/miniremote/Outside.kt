package com.krishna.miniremote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import java.lang.ref.WeakReference

/**
 * The remote outside the app: a notification with volume and play keys while a TV is
 * connected and the app is in the background, a Quick Settings tile, and launcher
 * shortcuts for the apps in Quick access.
 */
object Outside {
    private const val CHANNEL = "remote"
    private const val ID = 7
    const val ACTION_KEY = "com.krishna.miniremote.KEY"
    const val ACTION_LAUNCH = "com.krishna.miniremote.LAUNCH"
    const val ACTION_WAKE = "com.krishna.miniremote.WAKE"
    const val EXTRA_KEY = "key"
    const val EXTRA_SHORTCUT = "shortcut"

    /** The running remote, while the app process is alive (keys from the notification go here). */
    @Volatile var vm: WeakReference<RemoteVM>? = null

    private fun keyIntent(ctx: Context, k: RemoteKey): PendingIntent = PendingIntent.getBroadcast(
        ctx, k.ordinal, Intent(ctx, KeyReceiver::class.java).setAction(ACTION_KEY).putExtra(EXTRA_KEY, k.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Shows (or updates) the remote notification for [tvName]. */
    fun show(ctx: Context, tvName: String, playing: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "TV remote", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Volume and play keys while a TV is connected"; setShowBadge(false)
            })
        }
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_n_remote)
            .setContentTitle(tvName)
            .setContentText("Mini Remote")
            .setContentIntent(open)
            .setOngoing(false).setSilent(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_n_vol_down, "Vol −", keyIntent(ctx, RemoteKey.VOL_DOWN))
            .addAction(if (playing) R.drawable.ic_n_pause else R.drawable.ic_n_play, if (playing) "Pause" else "Play", keyIntent(ctx, RemoteKey.PLAY_PAUSE))
            .addAction(R.drawable.ic_n_vol_up, "Vol +", keyIntent(ctx, RemoteKey.VOL_UP))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID, n) }
    }

    fun hide(ctx: Context) { runCatching { NotificationManagerCompat.from(ctx).cancel(ID) } }

    /** Launcher shortcuts (long-press the app icon): up to four Quick access apps, plus Turn on TV. */
    fun publishShortcuts(ctx: Context, list: List<Shortcut>) {
        runCatching {
            val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(ctx).coerceAtMost(5)
            val apps = list.filter { it.pinned && it.kind == ShortcutKind.APP }.take((max - 1).coerceAtLeast(0))
            val out = apps.mapIndexed { i, s ->
                ShortcutInfoCompat.Builder(ctx, "app-${s.id}")
                    .setShortLabel(s.name.take(20)).setLongLabel("Open ${s.name} on the TV".take(40))
                    .setIcon(IconCompat.createWithResource(ctx, R.drawable.ic_n_play))
                    .setIntent(Intent(ctx, MainActivity::class.java).setAction(ACTION_LAUNCH).putExtra(EXTRA_SHORTCUT, s.id))
                    .setRank(i + 1).build()
            } + ShortcutInfoCompat.Builder(ctx, "wake")
                .setShortLabel("Turn on TV").setLongLabel("Turn on the TV")
                .setIcon(IconCompat.createWithResource(ctx, R.drawable.ic_n_power))
                .setIntent(Intent(ctx, MainActivity::class.java).setAction(ACTION_WAKE))
                .setRank(0).build()
            ShortcutManagerCompat.setDynamicShortcuts(ctx, out)
        }
    }
}

/** Keys from the notification. Works while the app is running; otherwise it opens the app. */
class KeyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val k = intent.getStringExtra(Outside.EXTRA_KEY)?.let { runCatching { RemoteKey.valueOf(it) }.getOrNull() } ?: return
        val vm = Outside.vm?.get()
        if (vm == null) {
            ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        vm.remoteKey(k)
        if (k == RemoteKey.PLAY_PAUSE) vm.active?.let { Outside.show(ctx, it.name, vm.playing) }
    }
}

/** Quick Settings tile: opens the remote. */
class RemoteTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply { state = android.service.quicksettings.Tile.STATE_INACTIVE; label = "TV remote"; updateTile() }
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(i)
        }
    }
}
