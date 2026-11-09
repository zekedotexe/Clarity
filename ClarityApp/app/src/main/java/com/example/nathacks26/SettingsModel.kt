package com.example.nathacks26

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Data model for Application Settings covering Accessibility, Video, Identified Word Styling, and Target Words.
 */
data class ApplicationSettingsState(
    // Accessibility
    val textScale: Float = 1.0f,          // Enlarging text (0.8f .. 2.5f)

    // Video
    val ipdOffsetDp: Float = 24.0f,       // Eye-width / IPD offset (0.0f .. 60.0f)
    val fingerSensitivity: Float = 1.0f,  // Sensitivity of finger-point selection (0.5f .. 3.0f)

    // Identified Word Styling
    val wordFontFamily: String = "Default", // "Default", "Monospace", "Serif", "Cursive"
    val wordColorHex: String = "#00FFFF",     // "#00FFFF" (Cyan), "#FFFFFF" (White), "#00FF00" (Green), "#FFFF00" (Yellow)
    val wordLetterSpacingSp: Float = 1.0f,    // Letter spacing (0.0f .. 8.0f sp)

    // Target Words: Map of word -> isHeard (true if spoken/heard, false if read)
    val targetWordsMap: Map<String, Boolean> = emptyMap()
)

/**
 * Manager class to handle state persistence.
 */
class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_universal_settings", Context.MODE_PRIVATE)

    var state by mutableStateOf(loadSettings())
        private set

    fun updateTextScale(scale: Float) {
        val clamped = scale.coerceIn(0.8f, 2.5f)
        state = state.copy(textScale = clamped)
        prefs.edit().putFloat(KEY_TEXT_SCALE, clamped).apply()
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

    fun updateWordFontFamily(font: String) {
        state = state.copy(wordFontFamily = font)
        prefs.edit().putString(KEY_WORD_FONT, font).apply()
    }

    fun updateWordColorHex(colorHex: String) {
        state = state.copy(wordColorHex = colorHex)
        prefs.edit().putString(KEY_WORD_COLOR, colorHex).apply()
    }

    fun updateWordLetterSpacing(spacing: Float) {
        val clamped = spacing.coerceIn(0.0f, 8.0f)
        state = state.copy(wordLetterSpacingSp = clamped)
        prefs.edit().putFloat(KEY_WORD_SPACING, clamped).apply()
    }

    fun addSavedWord(word: String, isHeard: Boolean = false) {
        val trimmed = word.trim()
        if (trimmed.isNotEmpty()) {
            val currentMap = state.targetWordsMap.toMutableMap()
            val existingHeard = currentMap[trimmed] ?: false
            currentMap[trimmed] = existingHeard || isHeard
            state = state.copy(targetWordsMap = currentMap)
            saveTargetWordsToPrefs(currentMap)
        }
    }

    fun removeSavedWord(word: String) {
        val currentMap = state.targetWordsMap.toMutableMap()
        currentMap.remove(word)
        state = state.copy(targetWordsMap = currentMap)
        saveTargetWordsToPrefs(currentMap)
    }

    private fun saveTargetWordsToPrefs(map: Map<String, Boolean>) {
        val serialized = map.entries.joinToString(";") { "${it.key}:${it.value}" }
        prefs.edit().putString(KEY_TARGET_WORDS_MAP, serialized).apply()
    }

    fun resetDefaults() {
        state = ApplicationSettingsState()
        prefs.edit().clear().apply()
    }

    private fun loadSettings(): ApplicationSettingsState {
        val mapStr = prefs.getString(KEY_TARGET_WORDS_MAP, null)
        val targetMap = if (mapStr != null && mapStr.isNotEmpty()) {
            mapStr.split(";").mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) parts[0] to parts[1].toBoolean() else null
            }.toMap()
        } else {
            val oldSet = prefs.getStringSet("saved_words", emptySet()) ?: emptySet()
            oldSet.associateWith { false }
        }

        return ApplicationSettingsState(
            textScale = prefs.getFloat(KEY_TEXT_SCALE, 1.0f),
            ipdOffsetDp = prefs.getFloat(KEY_IPD, 24.0f),
            fingerSensitivity = prefs.getFloat(KEY_SENSITIVITY, 1.0f),
            wordFontFamily = prefs.getString(KEY_WORD_FONT, "Default") ?: "Default",
            wordColorHex = prefs.getString(KEY_WORD_COLOR, "#00FFFF") ?: "#00FFFF",
            wordLetterSpacingSp = prefs.getFloat(KEY_WORD_SPACING, 1.0f),
            targetWordsMap = targetMap
        )
    }

    companion object {
        private const val KEY_TEXT_SCALE = "text_scale"
        private const val KEY_IPD = "ipd_offset"
        private const val KEY_SENSITIVITY = "finger_sensitivity"
        private const val KEY_WORD_FONT = "word_font"
        private const val KEY_WORD_COLOR = "word_color"
        private const val KEY_WORD_SPACING = "word_spacing"
        private const val KEY_TARGET_WORDS_MAP = "target_words_map"
    }
}
