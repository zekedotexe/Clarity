package com.example.nathacks26

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Data model for VR Rig and Accessibility settings.
 */
data class VrSettingsState(
    // Accessibility
    val textScale: Float = 1.0f,          // Range: 0.8f .. 2.5f
    val screenBrightness: Float = 0.8f,   // Range: 0.1f .. 1.0f

    // Volume
    val audioVolume: Float = 0.8f,        // Range: 0.0f .. 1.0f

    // Video & Rig
    val ipdOffsetDp: Float = 24.0f,       // Range: 0.0f .. 60.0f
    val fingerSensitivity: Float = 1.0f,  // Range: 0.5f .. 3.0f

    // Gesture & Trigger Options
    val gestureTriggerEnabled: Boolean = true,
    val gestureHoldTimeMs: Long = 1500L   // Time in ms to trigger 3D settings in VR
)

/**
 * Repository to persist and restore VR settings via SharedPreferences.
 */
class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("vr_accessibility_settings", Context.MODE_PRIVATE)

    var state by mutableStateOf(loadSettings())
        private set

    fun updateTextScale(scale: Float) {
        val clamped = scale.coerceIn(0.8f, 2.5f)
        state = state.copy(textScale = clamped)
        prefs.edit().putFloat(KEY_TEXT_SCALE, clamped).apply()
    }

    fun updateScreenBrightness(brightness: Float, activity: Activity? = null) {
        val clamped = brightness.coerceIn(0.1f, 1.0f)
        state = state.copy(screenBrightness = clamped)
        prefs.edit().putFloat(KEY_BRIGHTNESS, clamped).apply()
        activity?.let { applyBrightness(it, clamped) }
    }

    fun updateAudioVolume(volume: Float, context: Context) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        state = state.copy(audioVolume = clamped)
        prefs.edit().putFloat(KEY_VOLUME, clamped).apply()
        applyAudioVolume(context, clamped)
    }

    fun updateIpdOffset(offsetDp: Float) {
        val clamped = offsetDp.coerceIn(0.0f, 60.0f)
        state = state.copy(ipdOffsetDp = clamped)
        prefs.edit().putFloat(KEY_IPD, clamped).apply()
    }

    fun updateFingerSensitivity(sensitivity: Float) {
        val clamped = sensitivity.coerceIn(0.5f, 3.0f)
        state = state.copy(fingerSensitivity = clamped)
        prefs.edit().putFloat(KEY_SENSITIVITY, clamped).apply()
    }

    fun updateGestureTriggerEnabled(enabled: Boolean) {
        state = state.copy(gestureTriggerEnabled = enabled)
        prefs.edit().putBoolean(KEY_GESTURE_ENABLED, enabled).apply()
    }

    fun applyPresets(textScale: Float, ipdDp: Float, sensitivity: Float) {
        updateTextScale(textScale)
        updateIpdOffset(ipdDp)
        updateFingerSensitivity(sensitivity)
    }

    fun resetDefaults(activity: Activity? = null, context: Context) {
        state = VrSettingsState()
        prefs.edit().clear().apply()
        activity?.let { applyBrightness(it, state.screenBrightness) }
        applyAudioVolume(context, state.audioVolume)
    }

    private fun loadSettings(): VrSettingsState {
        return VrSettingsState(
            textScale = prefs.getFloat(KEY_TEXT_SCALE, 1.0f),
            screenBrightness = prefs.getFloat(KEY_BRIGHTNESS, 0.8f),
            audioVolume = prefs.getFloat(KEY_VOLUME, 0.8f),
            ipdOffsetDp = prefs.getFloat(KEY_IPD, 24.0f),
            fingerSensitivity = prefs.getFloat(KEY_SENSITIVITY, 1.0f),
            gestureTriggerEnabled = prefs.getBoolean(KEY_GESTURE_ENABLED, true)
        )
    }

    companion object {
        private const val KEY_TEXT_SCALE = "text_scale"
        private const val KEY_BRIGHTNESS = "screen_brightness"
        private const val KEY_VOLUME = "audio_volume"
        private const val KEY_IPD = "ipd_offset"
        private const val KEY_SENSITIVITY = "finger_sensitivity"
        private const val KEY_GESTURE_ENABLED = "gesture_enabled"

        fun applyBrightness(activity: Activity, brightnessFraction: Float) {
            val layoutParams = activity.window.attributes
            layoutParams.screenBrightness = brightnessFraction.coerceIn(0.01f, 1.0f)
            activity.window.attributes = layoutParams
        }

        fun applyAudioVolume(context: Context, volumeFraction: Float) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (audioManager != null) {
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val targetVol = (maxVol * volumeFraction).toInt()
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
