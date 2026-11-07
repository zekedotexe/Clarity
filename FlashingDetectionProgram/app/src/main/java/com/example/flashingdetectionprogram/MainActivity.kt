package com.example.flashingdetectionprogram

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.framework.image.BitmapImageBuilder
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Camera permission is required for hand tracking", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FingerTrackerScreen()
                }
            }
        }
    }
}

@Composable
fun FingerTrackerScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Rendered dot position & target position for butter-smooth VSYNC interpolation
    var dotPosition by remember { mutableStateOf(Offset(300f, 300f)) }
    var targetPosition by remember { mutableStateOf(Offset(300f, 300f)) }

    // Continuous 60Hz/120Hz VSYNC animation loop to eliminate camera frame stutter/teleporting
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { _ ->
                val smoothingFactor = 0.25f
                dotPosition = Offset(
                    dotPosition.x + smoothingFactor * (targetPosition.x - dotPosition.x),
                    dotPosition.y + smoothingFactor * (targetPosition.y - dotPosition.y)
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
            null
        }
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            handLandmarker?.close()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    targetPosition += dragAmount
                    dotPosition = targetPosition
                }
            }
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            val bitmap = imageProxy.toBitmap()
                            if (bitmap != null && handLandmarker != null) {
                                try {
                                    val mpImage = BitmapImageBuilder(bitmap).build()
                                    val result = handLandmarker.detect(mpImage)
                                    if (result.landmarks().isNotEmpty()) {
                                        val indexTip = result.landmarks()[0][8] // Index finger tip landmark
                                        val rawX = (1.0f - indexTip.y()) * previewView.width
                                        val rawY = indexTip.x() * previewView.height
                                        targetPosition = Offset(rawX, rawY)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            imageProxy.close()
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera permission not granted.")
                Button(onClick = {
                    hasCameraPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                }) {
                    Text("Check Permission")
                }
            }
        }

        // Overlay canvas drawing the tracking dot
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.Cyan.copy(alpha = 0.4f),
                radius = 48f,
                center = dotPosition
            )
            drawCircle(
                color = Color.Blue,
                radius = 24f,
                center = dotPosition
            )
            drawCircle(
                color = Color.White,
                radius = 8f,
                center = dotPosition
            )
        }
    }
}
