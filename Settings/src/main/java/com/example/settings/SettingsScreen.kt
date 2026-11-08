package com.example.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Floating corner Settings Icon Button to open the settings overlay from any screen corner.
 */
@Composable
fn SettingsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape),
        color = Color.Black.copy(alpha = 0.75f),
        shape = CircleShape,
        shadowElevation = 6.dp
    ) {
        IconButton(onClick = onClick) {
            Text(
                text = "⚙️",
                fontSize = 22.sp
            )
        }
    }
}

/**
 * Comprehensive Settings Dialog / Tab containing Accessibility, Volume, and Video adjustments.
 */
@Composable
fun SettingsDialog(
    settingsManager: SettingsManager,
    activity: Activity?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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
                            text = "⚙️ Application Settings",
                            color = Color.White,
                            fontSize = (20 * state.textScale).sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Accessibility, Volume & Video Configurations",
                            color = Color.Gray,
                            fontSize = (12 * state.textScale).sp
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Done ➔", color = Color.White, fontSize = (14 * state.textScale).sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.DarkGray)
                Spacer(modifier = Modifier.height(16.dp))

                // ==================== ACCESSIBILITY ====================
                SettingsSectionHeader(title = "♿ ACCESSIBILITY", textScale = state.textScale)

                // 1. Enlarging Text
                SettingCard(
                    title = "🔤 Enlarging Text (Scale: ${String.format("%.1fx", state.textScale)})",
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

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Screen Brightness
                SettingCard(
                    title = "☀️ Screen Brightness (${(state.screenBrightness * 100).toInt()}%)",
                    textScale = state.textScale
                ) {
                    Slider(
                        value = state.screenBrightness,
                        onValueChange = { settingsManager.updateScreenBrightness(it, activity) },
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFFC107),
                            activeTrackColor = Color(0xFFFFD54F)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ==================== VOLUME ====================
                SettingsSectionHeader(title = "🔊 VOLUME", textScale = state.textScale)

                // 3. Volume of the audio
                SettingCard(
                    title = "🎵 Volume of the Audio (${(state.audioVolume * 100).toInt()}%)",
                    textScale = state.textScale
                ) {
                    Slider(
                        value = state.audioVolume,
                        onValueChange = { settingsManager.updateAudioVolume(it, context) },
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF2196F3),
                            activeTrackColor = Color(0xFF64B5F6)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ==================== VIDEO ====================
                SettingsSectionHeader(title = "🥽 VIDEO", textScale = state.textScale)

                // 4. Eye-Width
                SettingCard(
                    title = "👁️ Eye-Width (${state.ipdOffsetDp.toInt()} dp)",
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

                // 5. Sensitivity of finger-point selection
                SettingCard(
                    title = "🎯 Sensitivity of Finger-Point Selection (${String.format("%.1fx", state.fingerSensitivity)})",
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
                    onClick = { settingsManager.resetDefaults(activity, context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A65))
                ) {
                    Text("🔄 Reset All Settings to Default", fontSize = (13 * state.textScale).sp)
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
