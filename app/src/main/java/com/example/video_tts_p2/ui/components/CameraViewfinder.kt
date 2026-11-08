package com.example.video_tts_p2.ui.components

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.video_tts_p2.vision.CameraTextAnalyzer
import com.example.video_tts_p2.vision.TextDetectionResult
import com.example.video_tts_p2.ui.theme.*
import java.util.concurrent.Executors

/**
 * Camera viewfinder with integrated CameraX PreviewView and real-time Canvas overlay
 * rendering bounding boxes for detected and angled text, plus center reticle crosshair.
 */
@Composable
fun CameraViewfinder(
    detectionResult: TextDetectionResult?,
    onDetectionResult: (TextDetectionResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(
                        cameraExecutor,
                        CameraTextAnalyzer { result ->
                            onDetectionResult(result)
                        }
                    )
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
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            } catch (ignored: Exception) {}
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Real-time canvas overlay for text bounding boxes and center aiming reticle
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 1. Draw Center Aiming Reticle (wide frame for sentence targeting)
            val centerReticleX = canvasWidth / 2f
            val centerReticleY = canvasHeight * 0.35f
            val reticleWidth = canvasWidth * 0.82f
            val reticleHeight = 80f

            // Sentence reticle frame
            drawRoundRect(
                color = GoogleBlue.copy(alpha = 0.6f),
                topLeft = Offset(centerReticleX - reticleWidth / 2f, centerReticleY - reticleHeight / 2f),
                size = Size(reticleWidth, reticleHeight),
                style = Stroke(width = 3f)
            )

            // Crosshair ticks
            drawLine(
                color = GoogleBlue,
                start = Offset(centerReticleX - 25f, centerReticleY),
                end = Offset(centerReticleX + 25f, centerReticleY),
                strokeWidth = 2.5f
            )
            drawLine(
                color = GoogleBlue,
                start = Offset(centerReticleX, centerReticleY - 18f),
                end = Offset(centerReticleX, centerReticleY + 18f),
                strokeWidth = 2.5f
            )

            // 2. Draw detected words bounding boxes and angled corner polygons
            detectionResult?.let { res ->
                val imgW = res.imageWidth.toFloat()
                val imgH = res.imageHeight.toFloat()

                if (imgW > 0 && imgH > 0) {
                    val scaleX = canvasWidth / imgW
                    val scaleY = canvasHeight / imgH

                    // Draw target sentence outline if present
                    res.targetSentence?.let { sentence ->
                        if (!sentence.boundingBox.isEmpty) {
                            val sLeft = sentence.boundingBox.left * scaleX
                            val sTop = sentence.boundingBox.top * scaleY
                            val sWidth = sentence.boundingBox.width() * scaleX
                            val sHeight = sentence.boundingBox.height() * scaleY

                            drawRoundRect(
                                color = GoogleBlue.copy(alpha = 0.85f),
                                topLeft = Offset(sLeft, sTop),
                                size = Size(sWidth, sHeight),
                                style = Stroke(width = 3.5f)
                            )
                        }
                    }

                    for (word in res.candidateWords) {
                        val isTarget = (word == res.targetWord)
                        val strokeColor = if (isTarget) GoogleBlue else Color(0x60FFFFFF)
                        val strokeWidth = if (isTarget) 3.5f else 1.5f

                        if (word.cornerPoints.size == 4) {
                            // Angled / skewed quadrilateral
                            val path = Path().apply {
                                val first = word.cornerPoints[0]
                                moveTo(first.x * scaleX, first.y * scaleY)
                                for (i in 1..3) {
                                    val pt = word.cornerPoints[i]
                                    lineTo(pt.x * scaleX, pt.y * scaleY)
                                }
                                close()
                            }
                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = Stroke(width = strokeWidth)
                            )
                        } else if (!word.boundingBox.isEmpty) {
                            // Rect fallback
                            val left = word.boundingBox.left * scaleX
                            val top = word.boundingBox.top * scaleY
                            val width = word.boundingBox.width() * scaleX
                            val height = word.boundingBox.height() * scaleY

                            drawRect(
                                color = strokeColor,
                                topLeft = Offset(left, top),
                                size = Size(width, height),
                                style = Stroke(width = strokeWidth)
                            )
                        }
                    }
                }
            }
        }
    }
}
