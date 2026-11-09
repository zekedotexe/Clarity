package com.example.nathacks26

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Floating corner Settings Button to open the settings overlay from screen corner.
 */
@Composable
fun SettingsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(12.dp)),
        color = Color.Black.copy(alpha = 0.75f),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 6.dp
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Settings",
                fontSize = 13.sp,
                color = Color.White
            )
        }
    }
}

/**
 * Comprehensive Settings Dialog containing Target Words, Identified Word Styling, Accessibility, and Video adjustments.
 */
@Composable
fun SettingsDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val state = settingsManager.state

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, Color(0xFF4CAF50), RoundedCornerShape(24.dp)),
            color = Color(0xFF181C24)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Application Settings",
                            color = Color.White,
                            fontSize = (20 * state.textScale).sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Target Words, Styling, Accessibility & Video",
                            color = Color.Gray,
                            fontSize = (12 * state.textScale).sp
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Done", color = Color.White, fontSize = (14 * state.textScale).sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.DarkGray)
                Spacer(modifier = Modifier.height(16.dp))

                // ==================== TARGET WORDS ====================
                SettingsSectionHeader(title = "TARGET WORDS (Read & Heard)", textScale = state.textScale)

                if (state.targetWordsMap.isEmpty()) {
                    SettingCard(
                        title = "No target words yet",
                        textScale = state.textScale
                    ) {
                        Text(
                            text = "Point and linger on a word for 5s to mark as Read, or 10s to mark as Heard.",
                            color = Color.Gray,
                            fontSize = (12 * state.textScale).sp
                        )
                    }
                } else {
                    for ((word, isHeard) in state.targetWordsMap) {
                        SettingCard(
                            title = word,
                            textScale = state.textScale
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = if (isHeard) Color(0xFF2196F3) else Color(0xFF4CAF50),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (isHeard) "Heard" else "Read",
                                        color = Color.White,
                                        fontSize = (11 * state.textScale).sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                Button(
                                    onClick = { settingsManager.removeSavedWord(word) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Remove", fontSize = (11 * state.textScale).sp, color = Color.White)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ==================== IDENTIFIED WORD STYLING ====================
                SettingsSectionHeader(title = "IDENTIFIED WORD STYLING", textScale = state.textScale)

                // 1. Font Family Selector
                SettingCard(
                    title = "Word Font Family: ${state.wordFontFamily}",
                    textScale = state.textScale
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val fonts = listOf("Default", "Monospace", "Serif", "Cursive")
                        for (font in fonts) {
                            val isSelected = state.wordFontFamily == font
                            Button(
                                onClick = { settingsManager.updateWordFontFamily(font) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) Color(0xFF4CAF50) else Color(0xFF262C38)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(font, fontSize = (11 * state.textScale).sp, color = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Color Selector
                SettingCard(
                    title = "Word Color",
                    textScale = state.textScale
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val colors = mapOf(
                            "Cyan" to "#00FFFF",
                            "White" to "#FFFFFF",
                            "Green" to "#00FF00",
                            "Yellow" to "#FFFF00",
                            "Magenta" to "#FF00FF"
                        )
                        for ((name, hex) in colors) {
                            val isSelected = state.wordColorHex == hex
                            Button(
                                onClick = { settingsManager.updateWordColorHex(hex) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) Color(0xFF4CAF50) else Color(0xFF262C38)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(name, fontSize = (11 * state.textScale).sp, color = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Letter Spacing Slider
                SettingCard(
                    title = "Letter Spacing (${String.format("%.1f", state.wordLetterSpacingSp)} sp)",
                    textScale = state.textScale
                ) {
                    Slider(
                        value = state.wordLetterSpacingSp,
                        onValueChange = { settingsManager.updateWordLetterSpacing(it) },
                        valueRange = 0.0f..8.0f,
                        steps = 16,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00BCD4),
                            activeTrackColor = Color(0xFF4DD0E1)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ==================== ACCESSIBILITY ====================
                SettingsSectionHeader(title = "ACCESSIBILITY", textScale = state.textScale)

                // 4. Enlarging Text
                SettingCard(
                    title = "Enlarging Text (Scale: ${String.format("%.1fx", state.textScale)})",
                    textScale = state.textScale
                ) {
                    Column {
                        Slider(
                            value = state.textScale,
                            onValueChange = { settingsManager.updateTextScale(it) },
                            valueRange = 0.8f..2.5f,
                            steps = 16,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF4CAF50),
                                activeTrackColor = Color(0xFF81C784)
                            )
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF262C38), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sample Text Preview: Readable & Clear",
                                color = Color.White,
                                fontSize = (14 * state.textScale).sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ==================== VIDEO ====================
                SettingsSectionHeader(title = "VIDEO", textScale = state.textScale)

                // 5. Eye-Width
                SettingCard(
                    title = "Eye-Width (${state.ipdOffsetDp.toInt()} dp)",
                    textScale = state.textScale
                ) {
                    Column {
                        Slider(
                            value = state.ipdOffsetDp,
                            onValueChange = { settingsManager.updateIpdOffset(it) },
                            valueRange = 0.0f..60.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF9C27B0),
                                activeTrackColor = Color(0xFFBA68C8)
                            )
                        )
                        Text(
                            text = "Adjusts stereo lens separation for comfortable 3D viewing.",
                            color = Color.Gray,
                            fontSize = (11 * state.textScale).sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 6. Sensitivity of finger-point selection
                SettingCard(
                    title = "Sensitivity of Finger-Point Selection (${String.format("%.1fx", state.fingerSensitivity)})",
                    textScale = state.textScale
                ) {
                    Column {
                        Slider(
                            value = state.fingerSensitivity,
                            onValueChange = { settingsManager.updateFingerSensitivity(it) },
                            valueRange = 0.5f..3.0f,
                            steps = 25,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFE91E63),
                                activeTrackColor = Color(0xFFF06292)
                            )
                        )
                        Text(
                            text = "Controls pointer response and smoothing for hand gesture tracking.",
                            color = Color.Gray,
                            fontSize = (11 * state.textScale).sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Reset Defaults Button
                OutlinedButton(
                    onClick = { settingsManager.resetDefaults() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A65))
                ) {
                    Text("Reset All Settings to Default", fontSize = (13 * state.textScale).sp)
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String, textScale: Float) {
    Text(
        text = title,
        color = Color(0xFF81C784),
        fontSize = (13 * textScale).sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

@Composable
fun SettingCard(
    title: String,
    textScale: Float,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF222834))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = (14 * textScale).sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
