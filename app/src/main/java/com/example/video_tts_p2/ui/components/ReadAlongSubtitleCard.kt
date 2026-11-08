package com.example.video_tts_p2.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.video_tts_p2.audio.PhoneticSyllable
import com.example.video_tts_p2.vision.DetectedSentence
import com.example.video_tts_p2.vision.WordSyllableItem
import com.example.video_tts_p2.ui.theme.*

/**
 * Displays real-time sentence detection with interactive read-a-long subtitles.
 * Each syllable is dynamically highlighted in vibrant Google Blue (#1A73E8)
 * as the audio plays step-by-step.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadAlongSubtitleCard(
    detectedSentence: DetectedSentence?,
    activeWordIndex: Int,
    activeSyllableIndex: Int,
    isReading: Boolean,
    onReadSentenceToggle: () -> Unit,
    onWordClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xF2FFFFFF) // High-contrast translucent surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Read-a-long status & Play/Stop action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = "Read-a-long subtitles",
                        tint = GoogleBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sentence Read-a-Long",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoogleBlue,
                        letterSpacing = 0.5.sp
                    )
                }

                // Read/Stop button
                if (detectedSentence != null && detectedSentence.syllableWords.isNotEmpty()) {
                    Button(
                        onClick = onReadSentenceToggle,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isReading) Color(0xFFD93025) else GoogleBlue
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 10.dp,
                            vertical = 0.dp
                        )
                    ) {
                        Icon(
                            imageVector = if (isReading) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isReading) "Stop Reading" else "Read Sentence",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isReading) "Stop" else "Read",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle words and syllables breakdown
            if (detectedSentence != null && detectedSentence.syllableWords.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    detectedSentence.syllableWords.forEachIndexed { wordIdx, wordItem ->
                        val isWordActive = (wordIdx == activeWordIndex)

                        // Word container with written text & phonetic pronunciation syllables
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isWordActive) Color(0xFFE8F0FE) else Color(0x0C000000),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onWordClicked(wordItem.cleanWord) }
                                .then(
                                    if (isWordActive) {
                                        Modifier.border(1.dp, GoogleBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    } else Modifier
                                )
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Original written word
                                Text(
                                    text = wordItem.rawWord,
                                    fontSize = 11.sp,
                                    color = if (isWordActive) GoogleBlue else GoogleTextSecondary,
                                    fontWeight = if (isWordActive) FontWeight.Bold else FontWeight.Normal
                                )

                                Spacer(modifier = Modifier.height(1.dp))

                                // Phonetic pronunciation syllables
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    wordItem.phoneticSyllables.forEachIndexed { sylIdx, syllable ->
                                        val isSyllableActive = isWordActive && (sylIdx == activeSyllableIndex)

                                        val textColor by animateColorAsState(
                                            targetValue = if (isSyllableActive) GoogleBlue else GoogleDarkCharcoal,
                                            animationSpec = tween(durationMillis = 150),
                                            label = "syllableColor"
                                        )

                                        val backgroundColor by animateColorAsState(
                                            targetValue = if (isSyllableActive) GoogleBlue.copy(alpha = 0.2f) else Color.Transparent,
                                            animationSpec = tween(durationMillis = 150),
                                            label = "syllableBg"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(backgroundColor)
                                                .padding(horizontal = 2.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = syllable.phonetic, // Phonetic pronunciation!
                                                fontSize = 14.sp,
                                                fontWeight = if (isSyllableActive) FontWeight.ExtraBold else FontWeight.Medium,
                                                color = textColor,
                                                letterSpacing = 0.2.sp
                                            )
                                        }

                                        // Middle dot between syllables of the same word
                                        if (sylIdx < wordItem.phoneticSyllables.size - 1) {
                                            Text(
                                                text = "·",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isWordActive) GoogleBlue.copy(alpha = 0.6f) else Color.Gray,
                                                modifier = Modifier.padding(horizontal = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Point camera at any printed sentence to read out loud...",
                    fontSize = 13.sp,
                    color = GoogleTextSecondary
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ReadAlongSubtitleCardPreview() {
    ReadAlongSubtitleCard(
        detectedSentence = DetectedSentence(
            rawText = "The condign punishment was swiftly administered",
            words = emptyList(),
            syllableWords = listOf(
                WordSyllableItem("The", "the", listOf(PhoneticSyllable("The", "thuh"))),
                WordSyllableItem("condign", "condign", listOf(PhoneticSyllable("con", "kuhn"), PhoneticSyllable("dign", "dahyn"))),
                WordSyllableItem("punishment", "punishment", listOf(PhoneticSyllable("pun", "puhn"), PhoneticSyllable("ish", "ish"), PhoneticSyllable("ment", "muhnt"))),
                WordSyllableItem("was", "was", listOf(PhoneticSyllable("was", "wuhz"))),
                WordSyllableItem("swiftly", "swiftly", listOf(PhoneticSyllable("swift", "swift"), PhoneticSyllable("ly", "lee"))),
                WordSyllableItem("administered", "administered", listOf(PhoneticSyllable("ad", "ad"), PhoneticSyllable("min", "min"), PhoneticSyllable("is", "ih"), PhoneticSyllable("tered", "sterd")))
            ),
            boundingBox = android.graphics.Rect(),
            cornerPoints = emptyList(),
            distanceToCenter = 0f
        ),
        activeWordIndex = 1,
        activeSyllableIndex = 1, // "dahyn" is highlighted blue!
        isReading = true,
        onReadSentenceToggle = {},
        onWordClicked = {}
    )
}

