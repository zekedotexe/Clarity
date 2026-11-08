package com.example.video_tts_p2.vision

import android.graphics.Point
import android.graphics.Rect
import com.example.video_tts_p2.audio.PhoneticSyllable

/**
 * Represents a single detected word from the camera frame with spatial coordinates.
 */
data class DetectedWord(
    val rawText: String,
    val cleanWord: String,
    val boundingBox: Rect,
    val cornerPoints: List<Point>,
    val distanceToCenter: Float,
    val frameWidth: Int,
    val frameHeight: Int,
    val rotationDegrees: Int
)

/**
 * A word decomposed into its constituent phonetic syllables for read-a-long highlighting and pronunciation.
 */
data class WordSyllableItem(
    val rawWord: String,
    val cleanWord: String,
    val phoneticSyllables: List<PhoneticSyllable>
) {
    // Phonetic pronunciation strings for each syllable (e.g., ["kuhn", "dahyn"])
    val syllables: List<String> get() = phoneticSyllables.map { it.phonetic }
}

/**
 * Represents a full sentence or clause detected from the camera frame.
 */
data class DetectedSentence(
    val rawText: String,
    val words: List<DetectedWord>,
    val syllableWords: List<WordSyllableItem>,
    val boundingBox: Rect,
    val cornerPoints: List<Point>,
    val distanceToCenter: Float
)

/**
 * Result payload emitted by CameraTextAnalyzer
 */
data class TextDetectionResult(
    val targetSentence: DetectedSentence?,
    val candidateSentences: List<DetectedSentence>,
    val targetWord: DetectedWord?,
    val candidateWords: List<DetectedWord>,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotationDegrees: Int,
    val timestamp: Long = System.currentTimeMillis()
)
