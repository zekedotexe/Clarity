package com.example.nathacks26

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.nathacks26.ui.theme.NatHacks26Theme
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// Curated dictionary of common English words for OCR cross-referencing & fuzzy correction
val COMMON_ENGLISH_DICTIONARY: Set<String> by lazy {
    setOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for", "not", "on", "with",
        "he", "as", "you", "do", "at", "this", "but", "his", "by", "from", "they", "we", "say", "her",
        "she", "or", "an", "will", "my", "one", "all", "would", "there", "their", "what", "so", "up",
        "out", "if", "about", "who", "get", "which", "go", "me", "when", "make", "can", "like", "time",
        "no", "just", "him", "know", "take", "people", "into", "year", "your", "good", "some", "could",
        "them", "see", "other", "than", "then", "now", "look", "only", "come", "its", "over", "think",
        "also", "back", "after", "use", "two", "how", "our", "work", "first", "well", "way", "even",
        "new", "want", "because", "any", "these", "give", "day", "most", "us", "water", "food", "read",
        "book", "page", "text", "word", "paper", "write", "screen", "light", "phone", "vision", "eye",
        "view", "camera", "hand", "finger", "point", "green", "blue", "red", "yellow", "white", "black",
        "help", "home", "life", "world", "school", "state", "family", "student", "group", "country",
        "problem", "hand", "part", "place", "case", "week", "company", "system", "program", "question",
        "number", "night", "point", "home", "water", "room", "mother", "area", "money", "story", "fact",
        "month", "lot", "right", "study", "book", "eye", "job", "word", "business", "issue", "side",
        "kind", "head", "far", "black", "long", "both", "little", "house", "yes", "after", "since",
        "around", "where", "down", "off", "always", "next", "open", "here", "why", "thing", "small"
    )
}

fun levenshteinDistance(s1: String, s2: String): Int {
    val dp = IntArray(s2.length + 1) { it }
    for (i in 1..s1.length) {
        var prev = dp[0]
        dp[0] = i
        for (j in 1..s2.length) {
            val temp = dp[j]
            val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
            dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + cost)
            prev = temp
        }
    }
    return dp[s2.length]
}

fun autocorrectWord(input: String, enableAutocorrect: Boolean): String {
    val cleanInput = input.lowercase(Locale.US).filter { it.isLetter() || it == '\'' || it == '-' }
    if (cleanInput.length < 2) return input

    if (!enableAutocorrect) return cleanInput

    if (COMMON_ENGLISH_DICTIONARY.contains(cleanInput)) {
        return cleanInput
    }

    // Common OCR misread character mapping (e.g., "vv" -> "w", "0" -> "o", "1" -> "l")
    val ocrCorrected = cleanInput
        .replace("vv", "w")
        .replace("0", "o")
        .replace("1", "l")
        .replace("rn", "m")
        .replace("cl", "d")

    if (COMMON_ENGLISH_DICTIONARY.contains(ocrCorrected)) {
        return ocrCorrected
    }

    // Levenshtein fuzzy distance matching against dictionary
    val maxDistance = if (cleanInput.length <= 4) 1 else 2
    var bestMatch: String? = null
    var minDistance = Int.MAX_VALUE

    for (dictWord in COMMON_ENGLISH_DICTIONARY) {
        if (kotlin.math.abs(dictWord.length - cleanInput.length) > maxDistance) continue
        val dist = levenshteinDistance(cleanInput, dictWord)
        if (dist <= maxDistance && dist < minDistance) {
            minDistance = dist
            bestMatch = dictWord
        }
    }

    return bestMatch ?: ocrCorrected
}

fun identifyPointingFinger(landmarks: List<NormalizedLandmark>): Int? {
    if (landmarks.size < 21) return null

    fun dist(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

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

    // Middle finger pointing
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
                    CardboardVrScreen()
                }
            }
        }
    }
}

@Composable
fun CardboardVrScreen() {
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
        CardboardDualEyeVrLayout()
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text(text = "Grant Camera Permission for Cardboard VR Passthrough")
            }
        }
    }
}

@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CardboardDualEyeVrLayout() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var ipdOffsetDp by remember { mutableFloatStateOf(24f) }

    // Settings States
    var showSettingsDialog by remember { mutableStateOf(false) }
    var textSizeMultiplier by remember { mutableFloatStateOf(1.0f) }
    var screenBrightness by remember { mutableFloatStateOf(1.0f) }
    var audioVolume by remember {
        mutableFloatStateOf(
            run {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (audioManager != null) {
                    val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                    if (max > 0f) current / max else 0.8f
                } else 0.8f
            }
        )
    }
    var sensitivityLevel by remember { mutableFloatStateOf(2.0f) }
    var pointerLengthMultiplier by remember { mutableFloatStateOf(1.2f) }

    // New Assistive Settings
    var isTtsEnabled by remember { mutableStateOf(true) }
    var enableAutocorrect by remember { mutableStateOf(true) }
    var wordDisplayDurationSeconds by remember { mutableFloatStateOf(3.0f) }

    // Text-To-Speech Initialization
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Set default language
            }
        }
        tts.language = Locale.US
        ttsEngine = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    // Expanded Floating Word State
    var expandedWord by remember { mutableStateOf<String?>(null) }

    // Auto-clear expanded word after duration
    LaunchedEffect(expandedWord, wordDisplayDurationSeconds) {
        if (expandedWord != null) {
            delay((wordDisplayDurationSeconds * 1000).toLong())
            expandedWord = null
        }
    }

    // Derived sensitivity parameters
    val requiredStreak = remember(sensitivityLevel) {
        when {
            sensitivityLevel <= 1.2f -> 1
            sensitivityLevel <= 2.2f -> 2
            else -> 3
        }
    }
    val maxJumpThreshold = remember(sensitivityLevel) {
        when {
            sensitivityLevel <= 1.2f -> 0.45f
            sensitivityLevel <= 2.2f -> 0.35f
            else -> 0.25f
        }
    }

    // Smooth VSYNC Interpolated States for both finger tip origin & laser tip end target
    var fingerTipPos by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var targetFingerTipPos by remember { mutableStateOf(Offset(0.5f, 0.5f)) }

    var laserTipPos by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var targetLaserTipPos by remember { mutableStateOf(Offset(0.5f, 0.5f)) }

    var isFingerDetected by remember { mutableStateOf(false) }

    // Continuous 60Hz/120Hz VSYNC animation loop for BOTH endpoints of the laser line
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { _ ->
                val smoothingFactor = 0.25f
                fingerTipPos = Offset(
                    fingerTipPos.x + smoothingFactor * (targetFingerTipPos.x - fingerTipPos.x),
                    fingerTipPos.y + smoothingFactor * (targetFingerTipPos.y - fingerTipPos.y)
                )
                laserTipPos = Offset(
                    laserTipPos.x + smoothingFactor * (targetLaserTipPos.x - laserTipPos.x),
                    laserTipPos.y + smoothingFactor * (targetLaserTipPos.y - laserTipPos.y)
                )
            }
        }
    }

    // Initialize MediaPipe HandLandmarker safely with RunningMode.VIDEO
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
    var lastSpokenWord by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val matrix = Matrix()

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val imageAnalysisBuilder = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)

            val camera2Extender = androidx.camera.camera2.interop.Camera2Interop.Extender(imageAnalysisBuilder)
            camera2Extender.setCaptureRequestOption(
                android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE,
                android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE_OFF
            )
            camera2Extender.setCaptureRequestOption(
                android.hardware.camera2.CaptureRequest.LENS_FOCUS_DISTANCE,
                2.5f
            )

            val imageAnalysis = imageAnalysisBuilder.build()

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
                
                cameraBitmap = rotatedBitmap

                if (handLandmarker != null && isAnalyzing.compareAndSet(false, true)) {
                    analysisExecutor.execute {
                        try {
                            val timestamp = System.currentTimeMillis()
                            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
                            val result = handLandmarker.detectForVideo(mpImage, timestamp)
                            if (result.landmarks().isNotEmpty()) {
                                val landmarks = result.landmarks()[0]
                                val pointingFingerIndex = identifyPointingFinger(landmarks)

                                if (pointingFingerIndex != null) {
                                    missingFramesCount = 0
                                    val tip = landmarks[pointingFingerIndex]
                                    val pip = landmarks[if (pointingFingerIndex == 8) 6 else 10]

                                    val rawTipX = tip.x()
                                    val rawTipY = tip.y()

                                    val dirX = rawTipX - pip.x()
                                    val dirY = rawTipY - pip.y()
                                    val len = kotlin.math.sqrt((dirX * dirX + dirY * dirY).toDouble()).toFloat()

                                    val extFactor = 1.25f * pointerLengthMultiplier
                                    val extX = if (len > 0.001f) (dirX / len) * (len * extFactor) else 0f
                                    val extY = if (len > 0.001f) (dirY / len) * (len * extFactor) else 0f

                                    val rawLaserX = rawTipX + extX
                                    val rawLaserY = rawTipY + extY

                                    val dx = rawTipX - targetFingerTipPos.x
                                    val dy = rawTipY - targetFingerTipPos.y
                                    val distance = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                                    if (!isFingerDetected || distance < maxJumpThreshold) {
                                        val newTipTarget = Offset(rawTipX, rawTipY)
                                        val newLaserTarget = Offset(rawLaserX, rawLaserY)
                                        executor.execute {
                                            targetFingerTipPos = newTipTarget
                                            targetLaserTipPos = newLaserTarget
                                            isFingerDetected = true
                                        }
                                    }

                                    // High-specificity OCR word identification at the TIP OF THE LASER BEAM
                                    if (timestamp - lastOcrTime.longValue > 250) {
                                        lastOcrTime.longValue = timestamp
                                        val px = (targetLaserTipPos.x * rotatedBitmap.width).toInt()
                                        val py = (targetLaserTipPos.y * rotatedBitmap.height).toInt()

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
                                                                    val isUnderneath = px in (globalRect.left - 10)..(globalRect.right + 10) &&
                                                                            py in globalRect.bottom..(globalRect.bottom + 50)

                                                                    val verticalHit = py in (globalRect.top - 10)..(globalRect.bottom + 10) && px in (globalRect.left - 15)..(globalRect.right + 15)
                                                                    val centerX = globalRect.exactCenterX()
                                                                    val centerY = globalRect.exactCenterY()
                                                                    val verticalDist = kotlin.math.abs(py - centerY)
                                                                    val horizontalDist = kotlin.math.abs(px - centerX)

                                                                    if (contains || isUnderneath || verticalHit) {
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

                                                    val rawDetected = bestWord?.trim()
                                                    val correctedWord = if (rawDetected != null) {
                                                        autocorrectWord(rawDetected, enableAutocorrect)
                                                    } else null

                                                    executor.execute {
                                                        if (correctedWord != null && correctedWord.length >= 2) {
                                                            if (correctedWord == candidateWord) {
                                                                candidateStreak++
                                                                if (candidateStreak >= requiredStreak) {
                                                                    identifiedWord = correctedWord
                                                                    expandedWord = correctedWord

                                                                    // Audio TTS Pronunciation
                                                                    if (isTtsEnabled && ttsEngine != null && correctedWord != lastSpokenWord) {
                                                                        lastSpokenWord = correctedWord
                                                                        ttsEngine?.speak(
                                                                            correctedWord,
                                                                            TextToSpeech.QUEUE_FLUSH,
                                                                            null,
                                                                            "WORD_TTS_${System.currentTimeMillis()}"
                                                                        )
                                                                    }
                                                                }
                                                            } else {
                                                                candidateWord = correctedWord
                                                                candidateStreak = 1
                                                                if (requiredStreak == 1) {
                                                                    identifiedWord = correctedWord
                                                                    expandedWord = correctedWord

                                                                    if (isTtsEnabled && ttsEngine != null && correctedWord != lastSpokenWord) {
                                                                        lastSpokenWord = correctedWord
                                                                        ttsEngine?.speak(
                                                                            correctedWord,
                                                                            TextToSpeech.QUEUE_FLUSH,
                                                                            null,
                                                                            "WORD_TTS_${System.currentTimeMillis()}"
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            candidateWord = null
                                                            candidateStreak = 0
                                                            identifiedWord = null
                                                        }
                                                    }
                                                }
                                                .addOnFailureListener {
                                                    // OCR failure handling
                                                }
                                        }
                                    }
                                } else {
                                    executor.execute {
                                        missingFramesCount++
                                        if (missingFramesCount > 8) {
                                            isFingerDetected = false
                                            identifiedWord = null
                                            candidateWord = null
                                            candidateStreak = 0
                                            lastSpokenWord = null
                                        }
                                    }
                                }
                            } else {
                                executor.execute {
                                    missingFramesCount++
                                    if (missingFramesCount > 8) {
                                        isFingerDetected = false
                                        identifiedWord = null
                                        candidateWord = null
                                        candidateStreak = 0
                                        lastSpokenWord = null
                                    }
                                }
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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 4.dp), contentAlignment = Alignment.Center) {
                VrLensViewport(
                    eyeLabel = "LEFT EYE",
                    bitmap = cameraBitmap,
                    horizontalOffsetDp = ipdOffsetDp.dp,
                    fingerTipPos = fingerTipPos,
                    laserTipPos = laserTipPos,
                    isFingerDetected = isFingerDetected,
                    identifiedWord = identifiedWord,
                    expandedWord = expandedWord,
                    textSizeMultiplier = textSizeMultiplier
                )
            }

            Box(modifier = Modifier.fillMaxHeight(0.85f).width(2.dp).background(Color.DarkGray))

            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 4.dp), contentAlignment = Alignment.Center) {
                VrLensViewport(
                    eyeLabel = "RIGHT EYE",
                    bitmap = cameraBitmap,
                    horizontalOffsetDp = (-ipdOffsetDp).dp,
                    fingerTipPos = fingerTipPos,
                    laserTipPos = laserTipPos,
                    isFingerDetected = isFingerDetected,
                    identifiedWord = identifiedWord,
                    expandedWord = expandedWord,
                    textSizeMultiplier = textSizeMultiplier
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp),
            color = if (identifiedWord != null) Color.Cyan.copy(alpha = 0.95f) else Color.DarkGray.copy(alpha = 0.85f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = identifiedWord?.let { "🎯 Identified Word: $it" }
                    ?: if (isFingerDetected) "👆 Pointing at text (searching word)..." else "🖐️ Point laser pointer at any word",
                color = if (identifiedWord != null) Color.Black else Color.White,
                fontSize = (12 * textSizeMultiplier).sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        // Circular Settings Gear Button at Top Right (Strictly icon only, no text)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 16.dp)
                .size(44.dp)
                .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                .border(1.5.dp, Color.Cyan.copy(alpha = 0.6f), CircleShape)
                .clip(CircleShape)
                .clickable { showSettingsDialog = true },
            contentAlignment = Alignment.Center
        ) {
            GearIcon(
                modifier = Modifier.size(24.dp),
                color = Color.White
            )
        }

        Text(
            text = "Google Cardboard VR Passthrough • Word OCR & Audio TTS Active",
            color = Color.Gray,
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
        )

        if (showSettingsDialog) {
            SettingsDialog(
                onDismissRequest = { showSettingsDialog = false },
                textSizeMultiplier = textSizeMultiplier,
                onTextSizeMultiplierChange = { textSizeMultiplier = it },
                screenBrightness = screenBrightness,
                onScreenBrightnessChange = { newBrightness ->
                    screenBrightness = newBrightness
                    (context as? ComponentActivity)?.let { act ->
                        val lp = act.window.attributes
                        lp.screenBrightness = newBrightness
                        act.window.attributes = lp
                    }
                },
                audioVolume = audioVolume,
                onAudioVolumeChange = { newVolume ->
                    audioVolume = newVolume
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    if (audioManager != null) {
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            (maxVol * newVolume).toInt(),
                            0
                        )
                    }
                },
                ipdOffsetDp = ipdOffsetDp,
                onIpdOffsetDpChange = { ipdOffsetDp = it },
                sensitivityLevel = sensitivityLevel,
                onSensitivityLevelChange = { sensitivityLevel = it },
                pointerLengthMultiplier = pointerLengthMultiplier,
                onPointerLengthMultiplierChange = { pointerLengthMultiplier = it },
                isTtsEnabled = isTtsEnabled,
                onIsTtsEnabledChange = { isTtsEnabled = it },
                enableAutocorrect = enableAutocorrect,
                onEnableAutocorrectChange = { enableAutocorrect = it },
                wordDisplayDurationSeconds = wordDisplayDurationSeconds,
                onWordDisplayDurationSecondsChange = { wordDisplayDurationSeconds = it }
            )
        }
    }
}

@Composable
fun VrLensViewport(
    eyeLabel: String,
    bitmap: Bitmap?,
    horizontalOffsetDp: Dp,
    fingerTipPos: Offset,
    laserTipPos: Offset,
    isFingerDetected: Boolean,
    identifiedWord: String?,
    expandedWord: String?,
    textSizeMultiplier: Float
) {
    val vrLensShape = remember { RoundedCornerShape(36.dp) }

    Box(
        modifier = Modifier
            .fillMaxHeight(0.95f)
            .aspectRatio(1.1f)
            .clip(vrLensShape)
            .background(Color.Black)
            .border(3.dp, Color.Gray.copy(alpha = 0.6f), vrLensShape),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.25f)
                    .offset(x = horizontalOffsetDp)
            )

            if (isFingerDetected) {
                val density = LocalDensity.current
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val boxWidthPx = with(density) { maxWidth.toPx() }
                    val boxHeightPx = with(density) { maxHeight.toPx() }
                    val offsetPx = with(density) { horizontalOffsetDp.toPx() }

                    val bmpW = bitmap.width.toFloat()
                    val bmpH = bitmap.height.toFloat()

                    if (bmpW > 0f && bmpH > 0f && boxWidthPx > 0f && boxHeightPx > 0f) {
                        val scaleFactor = maxOf(boxWidthPx / bmpW, boxHeightPx / bmpH)
                        val totalScale = scaleFactor * 1.25f

                        // Transform finger tip origin (start of laser beam)
                        val dxStart = (fingerTipPos.x - 0.5f) * bmpW * totalScale + offsetPx
                        val dyStart = (fingerTipPos.y - 0.5f) * bmpH * totalScale
                        val startPx = Offset(boxWidthPx / 2f + dxStart, boxHeightPx / 2f + dyStart)

                        // Transform laser target tip (end of laser beam where text is read)
                        val dxEnd = (laserTipPos.x - 0.5f) * bmpW * totalScale + offsetPx
                        val dyEnd = (laserTipPos.y - 0.5f) * bmpH * totalScale
                        val endPx = Offset(boxWidthPx / 2f + dxEnd, boxHeightPx / 2f + dyEnd)

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Laser outer glow
                            drawLine(
                                color = Color.Cyan.copy(alpha = 0.45f),
                                start = startPx,
                                end = endPx,
                                strokeWidth = 4f
                            )
                            // Core crisp laser beam
                            drawLine(
                                color = Color.White.copy(alpha = 0.9f),
                                start = startPx,
                                end = endPx,
                                strokeWidth = 1.5f
                            )

                            // Small subtle origin indicator at finger tip
                            drawCircle(
                                color = Color.Cyan.copy(alpha = 0.6f),
                                radius = 6f,
                                center = startPx
                            )

                            // Crisp target reticle at the TIP of the laser pointer
                            drawCircle(
                                color = Color.Cyan.copy(alpha = 0.35f),
                                radius = 18f,
                                center = endPx
                            )
                            drawCircle(
                                color = Color.Cyan,
                                radius = 8f,
                                center = endPx
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3f,
                                center = endPx
                            )
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Text(text = "Initializing Camera...", color = Color.Gray, fontSize = 12.sp)
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            color = Color.Black.copy(alpha = 0.7f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = eyeLabel,
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        // Floating Stereoscopically Fused Magnified Word for Dyslexia Assistance
        if (expandedWord != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = horizontalOffsetDp)
                    .padding(12.dp),
                color = Color(0xFF00E5FF).copy(alpha = 0.95f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(2.5.dp, Color.White)
            ) {
                Text(
                    text = expandedWord,
                    color = Color.Black,
                    fontSize = (28 * textSizeMultiplier).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        } else if (identifiedWord != null) {
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
                    fontSize = (12 * textSizeMultiplier).sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun GearIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = radius * 0.9f
        val innerRadius = radius * 0.65f
        val holeRadius = radius * 0.3f
        val numTeeth = 6

        for (i in 0 until numTeeth) {
            val angle = (i * 360f / numTeeth) * (Math.PI / 180f).toFloat()
            val toothWidth = size.minDimension * 0.16f
            val startX = center.x + innerRadius * kotlin.math.cos(angle)
            val startY = center.y + innerRadius * kotlin.math.sin(angle)
            val endX = center.x + outerRadius * kotlin.math.cos(angle)
            val endY = center.y + outerRadius * kotlin.math.sin(angle)

            drawLine(
                color = color,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = toothWidth
            )
        }

        drawCircle(
            color = color,
            radius = innerRadius,
            center = center,
            style = Stroke(width = size.minDimension * 0.12f)
        )

        drawCircle(
            color = Color.Transparent,
            radius = holeRadius,
            center = center
        )
    }
}

@Composable
fun CloseIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.12f
        val inset = size.minDimension * 0.25f
        drawLine(
            color = color,
            start = Offset(inset, inset),
            end = Offset(size.width - inset, size.height - inset),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = color,
            start = Offset(size.width - inset, inset),
            end = Offset(inset, size.height - inset),
            strokeWidth = strokeWidth
        )
    }
}

@Composable
fun SettingsDialog(
    onDismissRequest: () -> Unit,
    textSizeMultiplier: Float,
    onTextSizeMultiplierChange: (Float) -> Unit,
    screenBrightness: Float,
    onScreenBrightnessChange: (Float) -> Unit,
    audioVolume: Float,
    onAudioVolumeChange: (Float) -> Unit,
    ipdOffsetDp: Float,
    onIpdOffsetDpChange: (Float) -> Unit,
    sensitivityLevel: Float,
    onSensitivityLevelChange: (Float) -> Unit,
    pointerLengthMultiplier: Float,
    onPointerLengthMultiplierChange: (Float) -> Unit,
    isTtsEnabled: Boolean,
    onIsTtsEnabledChange: (Boolean) -> Unit,
    enableAutocorrect: Boolean,
    onEnableAutocorrectChange: (Boolean) -> Unit,
    wordDisplayDurationSeconds: Float,
    onWordDisplayDurationSecondsChange: (Float) -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1B1C1E),
            border = BorderStroke(1.5.dp, Color.Cyan.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settings",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                            .clip(CircleShape)
                            .clickable { onDismissRequest() },
                        contentAlignment = Alignment.Center
                    ) {
                        CloseIcon(
                            modifier = Modifier.size(20.dp),
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ACCESSIBILITY & DYSLEXIA ASSIST
                Text(
                    text = "ACCESSIBILITY & DYSLEXIA ASSIST",
                    color = Color.Cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Audio Speech TTS Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Audio Speech (TTS)", color = Color.White, fontSize = 14.sp)
                    Switch(
                        checked = isTtsEnabled,
                        onCheckedChange = onIsTtsEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Cyan,
                            checkedTrackColor = Color.Cyan.copy(alpha = 0.5f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Dictionary Autocorrect Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dictionary Autocorrect", color = Color.White, fontSize = 14.sp)
                    Switch(
                        checked = enableAutocorrect,
                        onCheckedChange = onEnableAutocorrectChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Cyan,
                            checkedTrackColor = Color.Cyan.copy(alpha = 0.5f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Expanded Word Display Timer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Word Display Duration", color = Color.White, fontSize = 14.sp)
                    Text("${(kotlin.math.round(wordDisplayDurationSeconds * 10f) / 10f)} sec", color = Color.Gray, fontSize = 14.sp)
                }
                Slider(
                    value = wordDisplayDurationSeconds,
                    onValueChange = onWordDisplayDurationSecondsChange,
                    valueRange = 1.0f..6.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Cyan,
                        activeTrackColor = Color.Cyan,
                        inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Enlarging Text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enlarging Text Scale", color = Color.White, fontSize = 14.sp)
                    Text("${(textSizeMultiplier * 100).toInt()}%", color = Color.Gray, fontSize = 14.sp)
                }
                Slider(
                    value = textSizeMultiplier,
                    onValueChange = onTextSizeMultiplierChange,
                    valueRange = 1.0f..2.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Cyan,
                        activeTrackColor = Color.Cyan,
                        inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Screen Brightness
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Screen Brightness", color = Color.White, fontSize = 14.sp)
                    Text("${(screenBrightness * 100).toInt()}%", color = Color.Gray, fontSize = 14.sp)
                }
                Slider(
                    value = screenBrightness,
                    onValueChange = onScreenBrightnessChange,
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Cyan,
                        activeTrackColor = Color.Cyan,
                        inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // VOLUME
                Text(
                    text = "VOLUME",
                    color = Color.Cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Audio Volume
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Audio Volume", color = Color.White, fontSize = 14.sp)
                    Text("${(audioVolume * 100).toInt()}%", color = Color.Gray, fontSize = 14.sp)
                }
                Slider(
                    value = audioVolume,
                    onValueChange = onAudioVolumeChange,
                    valueRange = 0.0f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Cyan,
                        activeTrackColor = Color.Cyan,
                        inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // VIDEO
                Text(
                    text = "VIDEO",
                    color = Color.Cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Eye-Width (IPD)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Eye-Width (IPD)", color = Color.White, fontSize = 14.sp)
                    Text("${ipdOffsetDp.toInt()} dp", color = Color.Gray, fontSize = 14.sp)
                }
                Slider(
                    value = ipdOffsetDp,
                    onValueChange = onIpdOffsetDpChange,
                    valueRange = 0f..50f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Cyan,
                        activeTrackColor = Color.Cyan,
                        inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Laser Pointer Length Extension
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Laser Pointer Length", color = Color.White, fontSize = 14.sp)
                    Text("${(kotlin.math.round(pointerLengthMultiplier * 10f) / 10f)}x", color = Color.Gray, fontSize = 14.sp)
                }
                Slider(
                    value = pointerLengthMultiplier,
                    onValueChange = onPointerLengthMultiplierChange,
                    valueRange = 0.5f..2.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Cyan,
                        activeTrackColor = Color.Cyan,
                        inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Sensitivity of finger-point selection
                val sensitivityLabel = when {
                    sensitivityLevel <= 1.2f -> "High (Fast Response)"
                    sensitivityLevel <= 2.2f -> "Medium (Balanced)"
                    else -> "Low (High Stability)"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Finger-Point Sensitivity", color = Color.White, fontSize = 14.sp)
                    Text(sensitivityLabel, color = Color.Gray, fontSize = 14.sp)
                }
                Slider(
                    value = sensitivityLevel,
                    onValueChange = onSensitivityLevelChange,
                    valueRange = 1.0f..3.0f,
                    steps = 1,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Cyan,
                        activeTrackColor = Color.Cyan,
                        inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}
