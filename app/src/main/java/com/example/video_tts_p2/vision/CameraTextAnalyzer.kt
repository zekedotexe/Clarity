package com.example.video_tts_p2.vision

import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.video_tts_p2.audio.SyllableSplitter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import kotlin.math.hypot

/**
 * CameraX ImageAnalysis analyzer utilizing Google ML Kit Text Recognition
 * to detect full sentences/lines as well as candidate words with syllable breakdowns,
 * coordinate mapping, and low-latency frame handling.
 */
class CameraTextAnalyzer(
    private val onResult: (TextDetectionResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val wordRegex = Regex("^[a-zA-Z]{2,30}$")
    private val punctuationRegex = Regex("^[^a-zA-Z0-9]+|[^a-zA-Z0-9]+$")

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        val isRotated = (rotationDegrees == 90 || rotationDegrees == 270)
        val effectiveWidth = if (isRotated) imageProxy.height else imageProxy.width
        val effectiveHeight = if (isRotated) imageProxy.width else imageProxy.height

        val centerX = effectiveWidth / 2f
        val centerY = effectiveHeight / 2f

        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val (sentences, words) = extractSentencesAndWords(
                    visionText = visionText,
                    centerX = centerX,
                    centerY = centerY,
                    frameWidth = effectiveWidth,
                    frameHeight = effectiveHeight,
                    rotationDegrees = rotationDegrees
                )

                val targetSentence = sentences.minByOrNull { it.distanceToCenter }
                val targetWord = words.minByOrNull { it.distanceToCenter }

                val candidateWords = words
                    .sortedBy { it.distanceToCenter }
                    .distinctBy { it.cleanWord }
                    .take(8)

                onResult(
                    TextDetectionResult(
                        targetSentence = targetSentence,
                        candidateSentences = sentences.take(5),
                        targetWord = targetWord,
                        candidateWords = candidateWords,
                        imageWidth = effectiveWidth,
                        imageHeight = effectiveHeight,
                        rotationDegrees = rotationDegrees
                    )
                )
            }
            .addOnFailureListener {
                onResult(
                    TextDetectionResult(
                        targetSentence = null,
                        candidateSentences = emptyList(),
                        targetWord = null,
                        candidateWords = emptyList(),
                        imageWidth = effectiveWidth,
                        imageHeight = effectiveHeight,
                        rotationDegrees = rotationDegrees
                    )
                )
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun extractSentencesAndWords(
        visionText: Text,
        centerX: Float,
        centerY: Float,
        frameWidth: Int,
        frameHeight: Int,
        rotationDegrees: Int
    ): Pair<List<DetectedSentence>, List<DetectedWord>> {
        val sentenceList = mutableListOf<DetectedSentence>()
        val allWords = mutableListOf<DetectedWord>()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text.trim()
                if (lineText.length < 3 || !lineText.any { it.isLetter() }) {
                    continue
                }

                val wordsInLine = mutableListOf<DetectedWord>()
                val syllableWords = mutableListOf<WordSyllableItem>()

                for (element in line.elements) {
                    val raw = element.text
                    val cleaned = cleanWordCandidate(raw)

                    if (cleaned != null && wordRegex.matches(cleaned)) {
                        val bbox = element.boundingBox ?: Rect()
                        val corners = element.cornerPoints?.toList() ?: emptyList()

                        val wordCenterX = if (bbox.isEmpty) {
                            corners.map { it.x }.average().toFloat()
                        } else {
                            bbox.exactCenterX()
                        }
                        val wordCenterY = if (bbox.isEmpty) {
                            corners.map { it.y }.average().toFloat()
                        } else {
                            bbox.exactCenterY()
                        }

                        val distance = hypot(wordCenterX - centerX, wordCenterY - centerY)
                        val cleanLower = cleaned.lowercase(Locale.ROOT)

                        val detectedWord = DetectedWord(
                            rawText = raw,
                            cleanWord = cleanLower,
                            boundingBox = bbox,
                            cornerPoints = corners,
                            distanceToCenter = distance,
                            frameWidth = frameWidth,
                            frameHeight = frameHeight,
                            rotationDegrees = rotationDegrees
                        )

                        wordsInLine.add(detectedWord)
                        allWords.add(detectedWord)

                        val phoneticSyllables = SyllableSplitter.getPhoneticSyllables(cleaned)

                        syllableWords.add(
                            WordSyllableItem(
                                rawWord = raw,
                                cleanWord = cleanLower,
                                phoneticSyllables = phoneticSyllables
                            )
                        )
                    }
                }

                if (wordsInLine.isNotEmpty()) {
                    val lineBbox = line.boundingBox ?: Rect()
                    val lineCorners = line.cornerPoints?.toList() ?: emptyList()

                    val lineCenterX = if (lineBbox.isEmpty) {
                        lineCorners.map { it.x }.average().toFloat()
                    } else {
                        lineBbox.exactCenterX()
                    }
                    val lineCenterY = if (lineBbox.isEmpty) {
                        lineCorners.map { it.y }.average().toFloat()
                    } else {
                        lineBbox.exactCenterY()
                    }

                    val lineDistance = hypot(lineCenterX - centerX, lineCenterY - centerY)

                    sentenceList.add(
                        DetectedSentence(
                            rawText = lineText,
                            words = wordsInLine,
                            syllableWords = syllableWords,
                            boundingBox = lineBbox,
                            cornerPoints = lineCorners,
                            distanceToCenter = lineDistance
                        )
                    )
                }
            }
        }

        return Pair(sentenceList, allWords)
    }

    private fun cleanWordCandidate(raw: String): String? {
        val trimmed = raw.trim()
            .replace(punctuationRegex, "")
            .replace("’", "")
            .replace("'", "")
        return if (trimmed.length in 2..30 && trimmed.all { it.isLetter() }) trimmed else null
    }
}
