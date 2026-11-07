package com.example.nathacks26

import android.Manifest
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.nathacks26.ui.theme.NatHacks26Theme
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

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
                Text(text = "Grant Camera Permission for CardboardXR Hand Tracking")
            }
        }
    }
}

@Composable
fun CardboardDualEyeVrLayout() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var handLandmarkerResult by remember { mutableStateOf<HandLandmarkerResult?>(null) }
    var ipdOffsetDp by remember { mutableFloatStateOf(24f) }
    var handStatusText by remember { mutableStateOf("CardboardXR: Scanning for hands...") }

    // Initialize MediaPipe HandLandmarker using bundled hand_landmarker.task model asset
    val handLandmarker = remember {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumHands(2)
                .build()
            HandLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

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

                cameraBitmap = rotatedBitmap

                if (handLandmarker != null) {
                    try {
                        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
                        val result = handLandmarker.detect(mpImage)
                        handLandmarkerResult = result
                        val count = result.landmarks().size
                        handStatusText = if (count > 0) "CardboardXR: Hands Detected ($count)" else "CardboardXR: Scanning for hands..."
                    } catch (_: Exception) {
                        handStatusText = "CardboardXR: Hand Tracking Active"
                    }
                } else {
                    handStatusText = "CardboardXR: Model Asset Missing"
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
            runCatching {
                cameraProviderFuture.get().unbindAll()
                handLandmarker?.close()
            }
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
                    handResult = handLandmarkerResult,
                    horizontalOffsetDp = ipdOffsetDp.dp
                )
            }

            Box(modifier = Modifier.fillMaxHeight(0.85f).width(2.dp).background(Color.DarkGray))

            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 4.dp), contentAlignment = Alignment.Center) {
                VrLensViewport(
                    eyeLabel = "RIGHT EYE",
                    bitmap = cameraBitmap,
                    handResult = handLandmarkerResult,
                    horizontalOffsetDp = (-ipdOffsetDp).dp
                )
            }
        }

        // HIGH VISIBILITY HUD & IPD CONTROLS
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            color = Color.Black.copy(alpha = 0.85f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color.Cyan)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = { if (ipdOffsetDp > 0) ipdOffsetDp -= 4f }, shape = CircleShape) {
                    Text(text = "IPD -", fontSize = 12.sp)
                }
                Text(
                    text = "$handStatusText  |  IPD: ${ipdOffsetDp.toInt()}dp",
                    color = Color.Cyan,
                    fontSize = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Button(onClick = { ipdOffsetDp += 4f }, shape = CircleShape) {
                    Text(text = "IPD +", fontSize = 12.sp)
                }
            }
        }

        // Bottom Branding
        Text(
            text = "CardboardXR • AI Hand Tracking & Passthrough",
            color = Color.LightGray,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
        )
    }
}

@Composable
fun VrLensViewport(
    eyeLabel: String,
    bitmap: Bitmap?,
    handResult: HandLandmarkerResult?,
    horizontalOffsetDp: Dp
) {
    val vrLensShape = remember { RoundedCornerShape(36.dp) }

    // MediaPipe 21 Hand Landmark Connections (Wireframe Skeleton)
    val connections = remember {
        listOf(
            Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4),         // Thumb
            Pair(0, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8),         // Index
            Pair(5, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12),    // Middle
            Pair(9, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16),  // Ring
            Pair(13, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20), // Pinky
            Pair(0, 17)                                              // Palm base
        )
    }

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
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.25f)
                        .offset(x = horizontalOffsetDp)
                )

                // Real-time MediaPipe Hand Skeleton Overlay (Cyan bones + Green joints)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val hands = handResult?.landmarks()
                    if (!hands.isNullOrEmpty()) {
                        for (hand in hands) {
                            // Draw bone connection lines
                            for ((startIdx, endIdx) in connections) {
                                val p1 = hand[startIdx]
                                val p2 = hand[endIdx]
                                drawLine(
                                    color = Color.Cyan,
                                    start = Offset(p1.x() * size.width, p1.y() * size.height),
                                    end = Offset(p2.x() * size.width, p2.y() * size.height),
                                    strokeWidth = 3.5.dp.toPx()
                                )
                            }
                            // Draw joint landmark circles
                            for (landmark in hand) {
                                val x = landmark.x() * size.width
                                val y = landmark.y() * size.height
                                drawCircle(
                                    color = Color.Green,
                                    radius = 5.dp.toPx(),
                                    center = Offset(x, y)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Text(text = "Initializing Camera...", color = Color.Gray, fontSize = 12.sp)
            }
        }

        // Center VR Reticle
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(Color.Cyan.copy(alpha = 0.9f), shape = CircleShape)
                .border(1.dp, Color.White, CircleShape)
        )

        // Eye Label
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
    }
}
