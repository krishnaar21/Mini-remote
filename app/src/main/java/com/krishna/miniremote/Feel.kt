package com.krishna.miniremote

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class Feel { PRESS, TICK, DETENT, HEAVY, ERROR, SUCCESS }

/** Haptics + synthesized sounds (no audio files, nothing to download). */
class FeelEngine(ctx: Context) {
    var haptics = true
    var sound = true

    private val vib: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31)
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private val main = Handler(Looper.getMainLooper())
    private val rate = 22050

    private val pcm: Map<Feel, ShortArray> by lazy {
        mapOf(
            Feel.PRESS to tone(1500.0, 22, 0.35),
            Feel.TICK to tone(2600.0, 9, 0.22),
            Feel.DETENT to tone(1900.0, 14, 0.3),
            Feel.HEAVY to tone(170.0, 55, 0.6),
            Feel.ERROR to tone(240.0, 110, 0.5),
            Feel.SUCCESS to tone(2100.0, 70, 0.3),
        )
    }

    private fun tone(freq: Double, ms: Int, gain: Double): ShortArray {
        val n = rate * ms / 1000
        return ShortArray(n) { i ->
            val t = i.toDouble() / rate
            val env = exp(-6.0 * i / n) * minOf(1.0, i / 40.0)
            (sin(2 * PI * freq * t) * env * gain * Short.MAX_VALUE).toInt().toShort()
        }
    }

    fun fire(k: Feel) {
        if (haptics) buzz(k)
        if (sound) play(k)
    }

    private fun buzz(k: Feel) {
        val v = vib ?: return
        if (!v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val id = when (k) {
                    Feel.TICK -> VibrationEffect.EFFECT_TICK
                    Feel.HEAVY, Feel.ERROR -> VibrationEffect.EFFECT_HEAVY_CLICK
                    else -> VibrationEffect.EFFECT_CLICK
                }
                v.vibrate(VibrationEffect.createPredefined(id))
            } else {
                val ms = when (k) { Feel.TICK -> 8L; Feel.HEAVY, Feel.ERROR -> 40L; else -> 18L }
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    private fun play(k: Feel) {
        val data = pcm[k] ?: return
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                )
                .setBufferSizeInBytes(data.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(data, 0, data.size)
            track.play()
            main.postDelayed({ runCatching { track.release() } }, (data.size * 1000L / rate) + 120)
        }
    }
}
