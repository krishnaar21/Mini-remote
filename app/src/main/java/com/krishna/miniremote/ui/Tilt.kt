package com.krishna.miniremote.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

/**
 * "Gyroscope lighting": phone tilt as x/y in -1..1 (0,0 = held upright at a normal reading angle).
 * Uses the gravity sensor (cheap, already fused by the OS) and only listens while the app is
 * resumed and the setting is on. Off when the system has animations turned off.
 */
val LocalTilt = staticCompositionLocalOf<State<Offset>> { mutableStateOf(Offset.Zero) }

@Composable
fun rememberTilt(enabled: Boolean): State<Offset> {
    val ctx = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    val owner = LocalLifecycleOwner.current
    val on = enabled && !animationsOff(ctx)

    DisposableEffect(on, owner) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val g = SensorManager.GRAVITY_EARTH
                // Gamma-like left/right lean, and forward tilt around a ~50 degree reading angle.
                val x = (-e.values[0] / g * 1.6f).coerceIn(-1f, 1f)
                val y = ((e.values[1] / g - 0.766f) / 0.5f).coerceIn(-1f, 1f)
                val p = tilt.value
                // Light smoothing so highlights glide instead of jitter.
                tilt.value = Offset(p.x + (x - p.x) * 0.2f, p.y + (y - p.y) * 0.2f)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        fun start() { if (on && sm != null && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI) }
        fun stop() { sm?.unregisterListener(listener) }
        val obs = LifecycleEventObserver { _, ev ->
            when (ev) { Lifecycle.Event.ON_RESUME -> start(); Lifecycle.Event.ON_PAUSE -> stop(); else -> {} }
        }
        if (on) owner.lifecycle.addObserver(obs) else tilt.value = Offset.Zero
        onDispose { owner.lifecycle.removeObserver(obs); stop() }
    }
    return tilt
}

private fun animationsOff(ctx: Context): Boolean =
    runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)

/** Soft top highlight that slides with the tilt, like light catching a rubber button. */
fun DrawScope.sheen(t: Offset, strength: Float = 0.16f) {
    val c = Offset(size.width * (0.5f + t.x * 0.34f), size.height * (0.08f + t.y * 0.12f))
    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = strength), Color.Transparent), c, size.maxDimension * 0.55f))
}
