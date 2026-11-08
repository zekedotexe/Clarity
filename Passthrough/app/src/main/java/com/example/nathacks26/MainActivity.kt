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
import androidx.compose.ui.platform.LocalDensity
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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

@Composable
fun CardboardDualEyeVrLayout() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var ipdOffsetDp by remember { mutableFloatStateOf(24f) }

    // Rendered normalized position & target normalized position for butter-smooth VSYNC interpolation
    var fingerPos by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var targetFingerPos by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var isFingerDetected by remember { mutableStateOf(false) }

    // Continuous 60Hz/120Hz VSYNC animation loop to eliminate camera frame stutter/teleporting
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { _ ->
                val smoothingFactor = 0.25f
                fingerPos = Offset(
                    fingerPos.x + smoothingFactor * (targetFingerPos.x - fingerPos.x),
                    fingerPos.y + smoothingFactor * (targetFingerPos.y - fingerPos.y)
                )
            }
        }
    }

    // Initialize MediaPipe HandLandmarker safely
    val handLandmarker = remember {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumHands(1)
                .build()
            HandLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val isAnalyzing = remember { AtomicBoolean(false) }

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

                if (handLandmarker != null && isAnalyzing.compareAndSet(false, true)) {
                    analysisExecutor.execute {
                        try {
                            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
                            val result = handLandmarker.detect(mpImage)
                            if (result.landmarks().isNotEmpty()) {
                                val indexTip = result.landmarks()[0][8] // Index finger tip landmark
                                targetFingerPos = Offset(indexTip.x(), indexTip.y())
                                isFingerDetected = true
                            } else {
                                isFingerDetected = false
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
                    fingerPos = fingerPos,
                    isFingerDetected = isFingerDetected
                )
            }

            Box(modifier = Modifier.fillMaxHeight(0.85f).width(2.dp).background(Color.DarkGray))

            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 4.dp), contentAlignment = Alignment.Center) {
                VrLensViewport(
                    eyeLabel = "RIGHT EYE",
                    bitmap = cameraBitmap,
                    horizontalOffsetDp = (-ipdOffsetDp).dp,
                    fingerPos = fingerPos,
                    isFingerDetected = isFingerDetected
                )
            }
        }

        Row(
            modifier = Modifier
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

        Text(
            text = "Google Cardboard VR Passthrough • Index Finger Tracking Active",
            color = Color.Gray,
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
        )
    }
}

@Composable
fun VrLensViewport(
    eyeLabel: String,
    bitmap: Bitmap?,
    horizontalOffsetDp: Dp,
    fingerPos: Offset,
    isFingerDetected: Boolean
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

                        val dx = (fingerPos.x - 0.5f) * bmpW * totalScale + offsetPx
                        val dy = (fingerPos.y - 0.5f) * bmpH * totalScale

                        val dotCenterX = boxWidthPx / 2f + dx
                        val dotCenterY = boxHeightPx / 2f + dy

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val dotCenter = Offset(dotCenterX, dotCenterY)
                            // Outer circle overlay
                            drawCircle(
                                color = Color.Cyan.copy(alpha = 0.4f),
                                radius = 48f,
                                center = dotCenter
                            )
                            // Middle circle
                            drawCircle(
                                color = Color.Blue,
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
    }
}
