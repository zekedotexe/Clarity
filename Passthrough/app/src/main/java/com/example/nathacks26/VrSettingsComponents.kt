package com.example.nathacks26

import android.app.Activity
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 2D Touchscreen Settings Setup UI Sheet
 * Used before slipping mobile phone into VR headset rig.
 */
@Composable
fun TouchscreenSettingsDialog(
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
                            text = "⚙️ VR Rig & Accessibility Settings",
                            color = Color.White,
                            fontSize = (20 * state.textScale).sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Configure values before placing phone in VR Headset Rig",
                            color = Color.Gray,
                            fontSize = (12 * state.textScale).sp
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Ready for VR ➔", color = Color.White, fontSize = (14 * state.textScale).sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.DarkGray)
                Spacer(modifier = Modifier.height(16.dp))

                // Section 1: Accessibility (Text Size & Screen Brightness)
                SettingsCategoryHeader(title = "♿ ACCESSIBILITY ADJUSTMENTS", textScale = state.textScale)

                // Enlarging Text Slider
                SettingItemCard(
                    title = "🔤 Enlarging Text (Text Scale: ${String.format("%.1fx", state.textScale)})",
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
                        // Live Text Preview Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF262C38), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sample Rehab Text: Reading Words Made Easy",
                                color = Color.White,
                                fontSize = (14 * state.textScale).sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Screen Brightness Slider
                SettingItemCard(
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

                // Section 2: Volume
                SettingsCategoryHeader(title = "🔊 AUDIO VOLUME", textScale = state.textScale)

                SettingItemCard(
                    title = "Audio Volume Level (${(state.audioVolume * 100).toInt()}%)",
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

                // Section 3: Video & Rig Controls
                SettingsCategoryHeader(title = "🥽 VIDEO & RIG STEREO SETUP", textScale = state.textScale)

                // Eye-Width (IPD Offset) Slider
                SettingItemCard(
                    title = "👁️ Eye-Width / IPD Offset (${state.ipdOffsetDp.toInt()} dp)",
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
                            text = "Adjusts left/right lens separation for comfortable 3D VR stereo alignment.",
                            color = Color.Gray,
                            fontSize = (11 * state.textScale).sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sensitivity of Finger-Point Selection
                SettingItemCard(
                    title = "🎯 Finger-Point Tracking Sensitivity (${String.format("%.1fx", state.fingerSensitivity)})",
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
                            text = "Higher sensitivity increases pointer response; lower sensitivity provides maximum smoothing.",
                            color = Color.Gray,
                            fontSize = (11 * state.textScale).sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Quick Presets & Reset Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { settingsManager.applyPresets(1.4f, 24f, 1.0f) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("📖 Dyslexia Preset", fontSize = (12 * state.textScale).sp, color = Color(0xFF81C784))
                    }
                    OutlinedButton(
                        onClick = { settingsManager.applyPresets(1.8f, 28f, 0.8f) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("♿ Rehab Preset", fontSize = (12 * state.textScale).sp, color = Color(0xFF64B5F6))
                    }
                    OutlinedButton(
                        onClick = { settingsManager.resetDefaults(activity, context) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🔄 Reset", fontSize = (12 * state.textScale).sp, color = Color(0xFFFF8A65))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsCategoryHeader(title: String, textScale: Float) {
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
fun SettingItemCard(
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
