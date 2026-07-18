package com.winlator.cmod.delta

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.preference.PreferenceManager
import kotlin.math.roundToInt

/**
 * Chassis haptics — perceptible but refined. Uses the system vibrator as the
 * primary path (Compose/view haptics alone are often muted on OEM skins).
 */
class ChassisHaptics(context: Context, private val view: View?) {
    private val appContext = context.applicationContext
    private val vibrator: Vibrator? = resolveVibrator(appContext)
    var strength: Float = 1f

    private var lastPulseAt = 0L
    private var lastStickRing = -1
    private var lastStickAngleBucket = -1
    private var lastPadTickAt = 0L

    init {
        view?.isHapticFeedbackEnabled = true
        // Keep sidebar toggle in sync (was often saved false, silencing all feedback).
        PreferenceManager.getDefaultSharedPreferences(appContext)
            .edit()
            .putBoolean("touchscreen_haptics_enabled", true)
            .apply()
    }

    /** Chassis feedback is always active — refined intensity, never silent. */
    private fun enabled(): Boolean = true

    fun buttonDown() {
        if (!enabled()) return
        tickView(HapticFeedbackConstants.KEYBOARD_TAP)
        pulse(16, 110)
    }

    fun buttonUp() {
        if (!enabled()) return
        if (SystemClock.uptimeMillis() - lastPulseAt > 30) {
            pulse(8, 45)
        }
    }

    fun shoulderDown() {
        if (!enabled()) return
        tickView(HapticFeedbackConstants.VIRTUAL_KEY)
        pulse(20, 140)
    }

    fun shoulderUp() {
        if (!enabled()) return
        if (SystemClock.uptimeMillis() - lastPulseAt > 30) {
            pulse(10, 55)
        }
    }

    fun guideDown() {
        if (!enabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tickView(HapticFeedbackConstants.CONFIRM)
        } else {
            tickView(HapticFeedbackConstants.LONG_PRESS)
        }
        pulse(28, 170)
    }

    fun stickEngage() {
        if (!enabled()) return
        lastStickRing = -1
        lastStickAngleBucket = -1
        tickView(HapticFeedbackConstants.CLOCK_TICK)
        pulse(14, 90)
    }

    fun stickTravel(nx: Float, ny: Float) {
        if (!enabled()) return
        val mag = kotlin.math.hypot(nx.toDouble(), ny.toDouble()).toFloat().coerceIn(0f, 1f)
        val ring = when {
            mag >= 0.98f -> 4
            mag >= 0.72f -> 3
            mag >= 0.48f -> 2
            mag >= 0.24f -> 1
            else -> 0
        }
        if (ring != lastStickRing) {
            lastStickRing = ring
            when (ring) {
                1 -> pulse(10, 70)
                2 -> pulse(12, 95)
                3 -> pulse(14, 120)
                4 -> {
                    tickView(HapticFeedbackConstants.CLOCK_TICK)
                    pulse(18, 155)
                }
                0 -> pulse(8, 50)
            }
        }
        if (mag > 0.18f) {
            val angle = Math.toDegrees(kotlin.math.atan2(ny.toDouble(), nx.toDouble())).toFloat()
            val bucket = ((angle + 180f) / 45f).roundToInt() % 8
            if (bucket != lastStickAngleBucket) {
                lastStickAngleBucket = bucket
                if (SystemClock.uptimeMillis() - lastPulseAt > 45) {
                    pulse(7, 40)
                }
            }
        } else {
            lastStickAngleBucket = -1
        }
    }

    fun stickRelease() {
        if (!enabled()) return
        lastStickRing = -1
        lastStickAngleBucket = -1
        pulse(12, 70)
    }

    fun padTick(speed: Float) {
        if (!enabled()) return
        val now = SystemClock.uptimeMillis()
        val interval = when {
            speed > 0.75f -> 30L
            speed > 0.40f -> 45L
            speed > 0.18f -> 65L
            else -> 95L
        }
        if (now - lastPadTickAt < interval) return
        lastPadTickAt = now
        val amp = (40 + speed * 90).roundToInt().coerceIn(40, 140)
        pulse(8, amp)
    }

    fun padDown() {
        if (!enabled()) return
        tickView(HapticFeedbackConstants.CLOCK_TICK)
        pulse(12, 85)
    }

    fun padUp() {
        if (!enabled()) return
        pulse(8, 45)
    }

    fun faceTransfer() {
        if (!enabled()) return
        pulse(12, 95)
    }

    private fun tickView(constant: Int) {
        val v = view ?: return
        try {
            v.isHapticFeedbackEnabled = true
            @Suppress("DEPRECATION")
            val flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            v.performHapticFeedback(constant, flags)
        } catch (_: Throwable) {
        }
    }

    private fun pulse(durationMs: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPulseAt < 10) return
        lastPulseAt = now
        val amp = (amplitude * strength).roundToInt().coerceIn(1, 255)
        try {
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect.createOneShot(durationMs, amp)
            } else {
                null
            }
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && effect != null -> {
                    val attrs = VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_TOUCH)
                        .build()
                    v.vibrate(effect, attrs)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && effect != null -> {
                    v.vibrate(effect)
                }
                else -> {
                    @Suppress("DEPRECATION")
                    v.vibrate(durationMs)
                }
            }
        } catch (_: Throwable) {
            try {
                @Suppress("DEPRECATION")
                v.vibrate(durationMs)
            } catch (_: Throwable) {
            }
        }
    }

    companion object {
        private fun resolveVibrator(context: Context): Vibrator? {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            } catch (_: Throwable) {
                null
            }
        }
    }
}

@Composable
fun rememberChassisHaptics(): ChassisHaptics {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(context, view) { ChassisHaptics(context, view) }
}
