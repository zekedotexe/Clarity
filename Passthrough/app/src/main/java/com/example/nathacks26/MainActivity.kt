package com.example.nathacks26

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.nathacks26.ui.theme.NatHacks26Theme
import com.example.settings.SettingsManager
import com.example.settings.SettingsDialog
import com.example.settings.SettingsIconButton
import com.example.settings.SettingsDialog
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot

fun identifyPointingFinger(landmarks: List<NormalizedLandmark>): Int? {
    if (landmarks.size < 21) return null

    fun dist(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    // Measure extension relative to local MCP (knuckle) joints to be invariant to hand angle, rotation, and camera distance
    // Index: MCP 5, PIP 6, TIP 8
    val indexExt = dist(landmarks[5], landmarks[8]) > dist(landmarks[5], landmarks[6]) * 1.35f
    // Middle: MCP 9, PIP 10, TIP 12
    val middleCurl = dist(landmarks[9], landmarks[12]) < dist(landmarks[9], landmarks[10]) * 1.25f
    // Ring: MCP 13, PIP 14, TIP 16
    val ringCurl = dist(landmarks[13], landmarks[16]) < dist(landmarks[13], landmarks[14]) * 1.25f
    // Pinky: MCP 17, PIP 18, TIP 20
    val pinkyCurl = dist(landmarks[17], landmarks[20]) < dist(landmarks[17], landmarks[18]) * 1.25f

    if (indexExt && middleCurl && ringCurl && pinkyCurl) {
        return 8 // Index finger tip
    }

    // Also support middle finger pointing
    val middleExt = dist(landmarks[9], landmarks[12]) > dist(landmarks[9], landmarks[10]) * 1.35f
    val indexCurl = dist(landmarks[5], landmarks[8]) < dist(landmarks[5], landmarks[6]) * 1.25f

    if (middleExt && indexCurl && ringCurl && pinkyCurl) {
        return 12 // Middle finger tip
    }

    return null
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enableEdgeToEdge()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            NatHacks26Theme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    CardboardVrScreen(activity = this)
                }
            }
        }
    }
}

@Composable
fun CardboardVrScreen(activity: Activity?) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { hasCameraPermission = it }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CardboardDualEyeVrLayout(activity = activity)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text(text = "Grant Camera Permission for Cardboard VR Passthrough")
            }
        }
    }
}

@Composable
fun CardboardDualEyeVrLayout(activity: Activity?) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize Settings Manager
    val settingsManager = remember { SettingsManager(context) }
    val settingsState = settingsManager.state

    // Apply brightness on startup
    LaunchedEffect(settingsState.screenBrightness) {
        activity?.let { SettingsManager.applyBrightness(it, settingsState.screenBrightness) }
    }

    var cameraBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var is2DSettingsOpen by remember { mutableStateOf(false) }

    // Rendered normalized position, target position, detection state & timestamp for ultra-smooth gliding
    var fingerPos by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var targetFingerPos by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var isFingerDetected by remember { mutableStateOf(false) }
    var lastDetectionTime by remember { mutableStateOf(0L) }

    // Continuous VSYNC animation loop with EMA smoothing and detection grace period
    LaunchedEffect(settingsState.fingerSensitivity) {
        while (true) {
            withFrameNanos { _ ->
                val now = System.currentTimeMillis()
                isFingerDetected = (now - lastDetectionTime) < 350L

                val baseSmoothing = 0.15f
                val smoothingFactor = (baseSmoothing * settingsState.fingerSensitivity).coerceIn(0.05f, 0.35f)
                fingerPos = Offset(
                    fingerPos.x + smoothingFactor * (targetFingerPos.x - fingerPos.x),
                    fingerPos.y + smoothingFactor * (targetFingerPos.y - fingerPos.y)
                )
            }
        }
    }

<<<<<<< HEAD
    // Initialize MediaPipe HandLandmarker safely with RunningMode.VIDEO for high performance
=======
    // Initialize MediaPipe HandLandmarker safely with RunningMode.VIDEO for temporal stability & high precision
>>>>>>> 1c0854a6f40f900cc8dc99cb81be1dc8c15584e9
    val handLandmarker = remember {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.VIDEO)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.6f)
                .setMinHandPresenceConfidence(0.6f)
                .setMinTrackingConfidence(0.6f)
                .build()
            HandLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val isAnalyzing = remember { AtomicBoolean(false) }

    // ML Kit Text Recognizer for high-specificity word identification
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    var identifiedWord by remember { mutableStateOf<String?>(null) }
    var candidateWord by remember { mutableStateOf<String?>(null) }
    var candidateStreak by remember { mutableIntStateOf(0) }
    var missingFramesCount by remember { mutableIntStateOf(0) }
    val lastOcrTime = remember { mutableLongStateOf(0L) }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val matrix = Matrix()

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                val rotation = imageProxy.imageInfo.rotationDegrees
                val bitmap = imageProxy.toBitmap()

                val rotatedBitmap = if (rotation != 0) {
                    matrix.reset()
                    matrix.postRotate(rotation.toFloat())
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                } else {
                    bitmap
                }
                
                // Recycle previous cameraBitmap to prevent memory leaks / OOM
                if (cameraBitmap != null && cameraBitmap != rotatedBitmap && !cameraBitmap!!.isRecycled) {
                    cameraBitmap!!.recycle()
                }
                cameraBitmap = rotatedBitmap

                if (handLandmarker != null && isAnalyzing.compareAndSet(false, true)) {
                    val timestamp = System.currentTimeMillis()
                    analysisExecutor.execute {
                        try {
                            val timestamp = System.currentTimeMillis()
                            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
                            val result = handLandmarker.detectForVideo(mpImage, timestamp)
                            if (result.landmarks().isNotEmpty()) {
<<<<<<< HEAD
                                val indexTip = result.landmarks()[0][8] // Index finger tip
                                val rawX = indexTip.x()
                                val rawY = indexTip.y()
                                // Apply EMA low-pass filter on target position to eliminate jitter
                                targetFingerPos = Offset(
                                    targetFingerPos.x + 0.4f * (rawX - targetFingerPos.x),
                                    targetFingerPos.y + 0.4f * (rawY - targetFingerPos.y)
                                )
                                lastDetectionTime = System.currentTimeMillis()
=======
                                val landmarks = result.landmarks()[0]
                                val pointingFingerIndex = identifyPointingFinger(landmarks)

                                if (pointingFingerIndex != null) {
                                    missingFramesCount = 0
                                    val fingerTip = landmarks[pointingFingerIndex]
                                    val rawX = fingerTip.x()
                                    val rawY = fingerTip.y()

                                    // Outlier rejection: reject erratic single-frame jumps to background objects
                                    val dx = rawX - targetFingerPos.x
                                    val dy = rawY - targetFingerPos.y
                                    val distance = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                                    val maxJumpThreshold = 0.35f

                                    if (!isFingerDetected || distance < maxJumpThreshold) {
                                        targetFingerPos = Offset(rawX, rawY)
                                        isFingerDetected = true
                                    }

                                    // High-specificity OCR word identification using Region of Interest (ROI) cropping with vertical/horizontal precision weighting
                                    if (timestamp - lastOcrTime.longValue > 250) {
                                        lastOcrTime.longValue = timestamp
                                        val px = (targetFingerPos.x * rotatedBitmap.width).toInt()
                                        val py = (targetFingerPos.y * rotatedBitmap.height).toInt()

                                        val cropSize = 500
                                        val left = (px - cropSize / 2).coerceIn(0, (rotatedBitmap.width - cropSize).coerceAtLeast(0))
                                        val top = (py - cropSize / 2).coerceIn(0, (rotatedBitmap.height - cropSize).coerceAtLeast(0))
                                        val w = cropSize.coerceAtMost(rotatedBitmap.width - left)
                                        val h = cropSize.coerceAtMost(rotatedBitmap.height - top)

                                        if (w > 50 && h > 50) {
                                            val croppedBitmap = Bitmap.createBitmap(rotatedBitmap, left, top, w, h)
                                            val image = InputImage.fromBitmap(croppedBitmap, 0)
                                            textRecognizer.process(image)
                                                .addOnSuccessListener { textResult ->
                                                    var bestWord: String? = null
                                                    var minScore = Float.MAX_VALUE

                                                    for (block in textResult.textBlocks) {
                                                        for (line in block.lines) {
                                                            for (element in line.elements) {
                                                                val localRect = element.boundingBox
                                                                if (localRect != null) {
                                                                    val globalRect = android.graphics.Rect(
                                                                        localRect.left + left,
                                                                        localRect.top + top,
                                                                        localRect.right + left,
                                                                        localRect.bottom + top
                                                                    )

                                                                    val contains = globalRect.contains(px, py)
                                                                    // Underneath pointing: finger tip is slightly below the word bottom (underline style) so camera/eyes can read it clearly
                                                                    val isUnderneath = px in (globalRect.left - 10)..(globalRect.right + 10) &&
                                                                            py in globalRect.bottom..(globalRect.bottom + 50)

                                                                    val verticalHit = py in (globalRect.top - 10)..(globalRect.bottom + 10) && px in (globalRect.left - 15)..(globalRect.right + 15)
                                                                    val centerX = globalRect.exactCenterX()
                                                                    val centerY = globalRect.exactCenterY()
                                                                    val verticalDist = kotlin.math.abs(py - centerY)
                                                                    val horizontalDist = kotlin.math.abs(px - centerX)

                                                                    if (contains || isUnderneath || verticalHit) {
                                                                        // Prioritize underneath pointing (underline style) so pointing just below the word selects it
                                                                        val priorityBonus = if (isUnderneath) -50f else 0f
                                                                        val score = verticalDist * 1.5f + horizontalDist * 0.5f + priorityBonus
                                                                        if (score < minScore) {
                                                                            minScore = score
                                                                            bestWord = element.text
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    val detectedWord = bestWord?.trim()
                                                    if (detectedWord != null && detectedWord.length >= 2) {
                                                        if (detectedWord == candidateWord) {
                                                            candidateStreak++
                                                            if (candidateStreak >= 2) {
                                                                identifiedWord = detectedWord
                                                            }
                                                        } else {
                                                            candidateWord = detectedWord
                                                            candidateStreak = 1
                                                        }
                                                    } else {
                                                        candidateWord = null
                                                        candidateStreak = 0
                                                        identifiedWord = null
                                                    }

                                                    if (!croppedBitmap.isRecycled) {
                                                        croppedBitmap.recycle()
                                                    }
                                                }
                                                .addOnFailureListener {
                                                    if (!croppedBitmap.isRecycled) {
                                                        croppedBitmap.recycle()
                                                    }
                                                }
                                        }
                                    }
                                } else {
                                    missingFramesCount++
                                    if (missingFramesCount > 8) { // ~250ms grace period to eliminate flashing
                                        isFingerDetected = false
                                        identifiedWord = null
                                        candidateWord = null
                                        candidateStreak = 0
                                    }
                                }
                            } else {
                                missingFramesCount++
                                if (missingFramesCount > 8) {
                                    isFingerDetected = false
                                    identifiedWord = null
                                    candidateWord = null
                                    candidateStreak = 0
                                }
>>>>>>> 1c0854a6f40f900cc8dc99cb81be1dc8c15584e9
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            isAnalyzing.set(false)
                        }
                    }
                }

                imageProxy.close()
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, executor)

        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
            analysisExecutor.shutdown()
            handLandmarker?.close()
            textRecognizer.close()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Dual Eye Stereo Layout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT EYE VIEWPORT
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                VrLensViewport(
                    eyeLabel = "LEFT EYE",
                    bitmap = cameraBitmap,
                    horizontalOffsetDp = settingsState.ipdOffsetDp.dp,
                    fingerPos = fingerPos,
                    isFingerDetected = isFingerDetected,
<<<<<<< HEAD
                    textScale = settingsState.textScale
=======
                    identifiedWord = identifiedWord
>>>>>>> 1c0854a6f40f900cc8dc99cb81be1dc8c15584e9
                )
            }

            // Divider Line
            Box(modifier = Modifier.fillMaxHeight(0.85f).width(2.dp).background(Color.DarkGray))

            // RIGHT EYE VIEWPORT
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                VrLensViewport(
                    eyeLabel = "RIGHT EYE",
                    bitmap = cameraBitmap,
                    horizontalOffsetDp = (-settingsState.ipdOffsetDp).dp,
                    fingerPos = fingerPos,
                    isFingerDetected = isFingerDetected,
<<<<<<< HEAD
                    textScale = settingsState.textScale
=======
                    identifiedWord = identifiedWord
>>>>>>> 1c0854a6f40f900cc8dc99cb81be1dc8c15584e9
                )
            }
        }

        // Minimalist Gear Settings Icon in Top Corner
        SettingsIconButton(
            onClick = { is2DSettingsOpen = true },
            modifier = Modifier
<<<<<<< HEAD
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
=======
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { if (ipdOffsetDp > 0) ipdOffsetDp -= 4f }, shape = CircleShape) {
                Text(text = "IPD -", fontSize = 11.sp)
            }
            Text(text = "IPD Shift: ${ipdOffsetDp.toInt()}dp", color = Color.White, fontSize = 11.sp)
            Button(onClick = { ipdOffsetDp += 4f }, shape = CircleShape) {
                Text(text = "IPD +", fontSize = 11.sp)
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 52.dp),
            color = if (identifiedWord != null) Color.Cyan.copy(alpha = 0.95f) else Color.DarkGray.copy(alpha = 0.85f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = identifiedWord?.let { "🎯 Identified Word: $it" }
                    ?: if (isFingerDetected) "👆 Pointing at text (searching word)..." else "🖐️ Point your index finger at any word",
                color = if (identifiedWord != null) Color.Black else Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        Text(
            text = "Google Cardboard VR Passthrough • Word OCR & Finger Tracking Active",
            color = Color.Gray,
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
>>>>>>> 1c0854a6f40f900cc8dc99cb81be1dc8c15584e9
        )

        // 2D Touchscreen Settings Dialog Sheet
        if (is2DSettingsOpen) {
            SettingsDialog(
                settingsManager = settingsManager,
                activity = activity,
                onDismiss = { is2DSettingsOpen = false }
            )
        }
    }
}

@Composable
fun VrLensViewport(
    eyeLabel: String,
    bitmap: Bitmap?,
    horizontalOffsetDp: Dp,
    fingerPos: Offset,
    isFingerDetected: Boolean,
<<<<<<< HEAD
    textScale: Float
=======
    identifiedWord: String?
>>>>>>> 1c0854a6f40f900cc8dc99cb81be1dc8c15584e9
) {
    val vrLensShape = remember { RoundedCornerShape(36.dp) }

    Box(
        modifier = Modifier
            .fillMaxHeight(0.95f)
            .aspectRatio(1.1f)
            .clip(vrLensShape)
            .background(Color.Black)
            .border(3.dp, Color(0xFF81C784).copy(alpha = 0.6f), vrLensShape),
        contentAlignment = Alignment.Center
    ) {
        // Camera Passthrough Background Image
        if (bitmap != null) {
            val bmp = bitmap
            val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.25f)
                    .offset(x = horizontalOffsetDp)
            )

            // Index Finger Tracking Indicator Dot
            if (isFingerDetected) {
                val density = LocalDensity.current
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val boxWidthPx = with(density) { maxWidth.toPx() }
                    val boxHeightPx = with(density) { maxHeight.toPx() }
                    val offsetPx = with(density) { horizontalOffsetDp.toPx() }

                    val bmpW = bmp.width.toFloat()
                    val bmpH = bmp.height.toFloat()

                    if (bmpW > 0f && bmpH > 0f && boxWidthPx > 0f && boxHeightPx > 0f) {
                        val scaleFactor = maxOf(boxWidthPx / bmpW, boxHeightPx / bmpH)
                        val totalScale = scaleFactor * 1.25f

                        val dx = (fingerPos.x - 0.5f) * bmpW * totalScale + offsetPx
                        val dy = (fingerPos.y - 0.5f) * bmpH * totalScale

                        val dotCenterX = boxWidthPx / 2f + dx
                        val dotCenterY = boxHeightPx / 2f + dy
                        val dotCenter = Offset(dotCenterX, dotCenterY)

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Outer circle overlay
                            drawCircle(
                                color = Color.Cyan.copy(alpha = 0.45f),
                                radius = 48f,
                                center = dotCenter
                            )
                            // Middle circle
                            drawCircle(
                                color = Color(0xFF1E88E5),
                                radius = 24f,
                                center = dotCenter
                            )
                            // Inner core circle
                            drawCircle(
                                color = Color.White,
                                radius = 8f,
                                center = dotCenter
                            )
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Text(
                    text = "Initializing Camera Passthrough...",
                    color = Color.Gray,
                    fontSize = (12 * textScale).sp
                )
            }
        }

        // Eye Label Overlay
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            color = Color.Black.copy(alpha = 0.75f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = eyeLabel,
                color = Color.White,
                fontSize = (11 * textScale).sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }

        if (identifiedWord != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                color = Color.Cyan.copy(alpha = 0.9f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "🎯 $identifiedWord",
                    color = Color.Black,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
