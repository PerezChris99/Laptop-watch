package com.example.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Utility class providing subtle, responsive tactile feedback for mobile interface interactions.
 * Ensures consistent, high-fidelity haptic clicks across devices and Android API levels.
 */
class AppHaptics(
    private val view: View?,
    private val context: Context
) {
    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Subtle, crisp click for primary buttons, switches, and action triggers.
     */
    fun click() {
        try {
            val handled = view?.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            ) ?: false

            if (!handled && vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(12L, 50))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(12L)
                }
            }
        } catch (_: Exception) {
            // Gracefully ignore on devices without vibration hardware
        }
    }

    /**
     * Distinctive tactile feedback for Remote Screen Lock / Unlock button.
     * When locking, provides a solid security confirmation pulse.
     * When unlocking, provides a lighter release tick.
     */
    fun lockToggle(isCurrentlyLocked: Boolean) {
        try {
            if (isCurrentlyLocked) {
                // Unlocking: crisp unlock tick
                val handled = view?.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                ) ?: false

                if (!handled && vibrator?.hasVibrator() == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(10L, 40))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(10L)
                    }
                }
            } else {
                // Locking laptop: solid, reassuring confirmation feedback
                val handled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    view?.performHapticFeedback(
                        HapticFeedbackConstants.CONFIRM,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                    ) ?: false
                } else {
                    view?.performHapticFeedback(
                        HapticFeedbackConstants.VIRTUAL_KEY,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                    ) ?: false
                }

                if (!handled && vibrator?.hasVibrator() == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(25L, 110))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(25L)
                    }
                }
            }
        } catch (_: Exception) {
            // Gracefully ignore
        }
    }

    /**
     * Tactile feedback for siren alarm trigger or urgent security actions.
     */
    fun alarmWarning() {
        try {
            val handled = view?.performHapticFeedback(
                HapticFeedbackConstants.LONG_PRESS,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            ) ?: false

            if (!handled && vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(35L, 120))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(35L)
                }
            }
        } catch (_: Exception) {
            // Gracefully ignore
        }
    }

    /**
     * Very subtle, light tick for navigation tabs, pills, camera shutter, and switches.
     */
    fun tick() {
        try {
            val handled = view?.performHapticFeedback(
                HapticFeedbackConstants.CLOCK_TICK,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            ) ?: false

            if (!handled && vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(8L, 35))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(8L)
                }
            }
        } catch (_: Exception) {
            // Gracefully ignore
        }
    }
}

/**
 * Composable helper remembering an AppHaptics instance tied to current View & Context.
 */
@Composable
fun rememberAppHaptics(): AppHaptics {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view, context) {
        AppHaptics(view, context)
    }
}
