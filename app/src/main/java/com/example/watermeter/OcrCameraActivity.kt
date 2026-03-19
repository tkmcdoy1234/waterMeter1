package com.example.watermeter

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.*
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.camera.core.Camera as CameraXCamera

class OcrCameraActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var camera: CameraXCamera? = null
    private var isFlashOn = false

    private lateinit var viewFinder: PreviewView
    private lateinit var overlay: View
    private lateinit var txtStatus: TextView
    private lateinit var btnFlash: ImageButton
    private lateinit var progressBar: ProgressBar

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var frameCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ocr_camera)

        viewFinder = findViewById(R.id.viewFinder)
        overlay = findViewById(R.id.viewFinderOverlay)
        txtStatus = findViewById(R.id.txtOcrStatus)
        btnFlash = findViewById(R.id.btnFlash)
        progressBar = findViewById(R.id.ocrProgress)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 10)
        }

        btnFlash.setOnClickListener { toggleFlash() }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (frameCounter++ % 5 == 0) {
                            processImageProxy(imageProxy)
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
                camera?.cameraControl?.enableTorch(true)
                isFlashOn = true
            } catch (e: Exception) {
                Log.e("OcrCamera", "Use case binding failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return
        
        // 1. Convert to Bitmap for processing
        val bitmap = imageProxyToBitmap(imageProxy)
        
        // 2. Apply Pre-processing
        val processedBitmap = applyOcrPreProcessing(bitmap)
        
        // 3. Create InputImage from processed bitmap
        // Note: rotation is handled during bitmap conversion
        val image = InputImage.fromBitmap(processedBitmap, 0)
        
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                analyzeText(visionText)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun applyOcrPreProcessing(original: Bitmap): Bitmap {
        // A. Crop to overlay area to reduce noise and perspective distortion
        val cropped = cropToOverlay(original)
        
        // B. Grayscale & Binarization
        val processed = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(processed)
        val paint = Paint()
        
        // Color matrix for Grayscale
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        
        // High contrast / Binarization simulation via ColorMatrix
        val contrast = 2.0f 
        val brightness = -100f
        val matrix = floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        )
        cm.postConcat(ColorMatrix(matrix))
        
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(cropped, 0f, 0f, paint)
        
        return processed
    }

    private fun cropToOverlay(bitmap: Bitmap): Bitmap {
        // Calculate coordinates relative to the bitmap
        val viewFinderWidth = viewFinder.width
        val viewFinderHeight = viewFinder.height
        
        val overlayLeft = overlay.left
        val overlayTop = overlay.top
        val overlayWidth = overlay.width
        val overlayHeight = overlay.height
        
        val scaleX = bitmap.width.toFloat() / viewFinderWidth
        val scaleY = bitmap.height.toFloat() / viewFinderHeight
        
        val left = (overlayLeft * scaleX).toInt().coerceAtLeast(0)
        val top = (overlayTop * scaleY).toInt().coerceAtLeast(0)
        val width = (overlayWidth * scaleX).toInt().coerceAtMost(bitmap.width - left)
        val height = (overlayHeight * scaleY).toInt().coerceAtMost(bitmap.height - top)
        
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    private fun analyzeText(visionText: Text) {
        val regex = Regex("\\d{4,6}")
        
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val filteredText = element.text.replace(Regex("[^0-9]"), "")
                    if (filteredText.matches(regex)) {
                        runOnUiThread {
                            txtStatus.text = "Locked: $filteredText"
                            txtStatus.setTextColor(Color.GREEN)
                            triggerHapticFeedback()
                            captureEvidenceAndFinish(filteredText)
                        }
                        return
                    }
                }
            }
        }
    }

    private fun captureEvidenceAndFinish(digits: String) {
        val imageCapture = imageCapture ?: return
        imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageProxyToBitmap(image)
                val base64 = encodeImage(bitmap)
                image.close()

                val resultIntent = Intent()
                resultIntent.putExtra("digits", digits)
                resultIntent.putExtra("photoBase64", base64)
                setResult(RESULT_OK, resultIntent)
                finish()
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("OcrCamera", "Evidence capture failed", exception)
                val resultIntent = Intent()
                resultIntent.putExtra("digits", digits)
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        })
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val rotationDegrees = image.imageInfo.rotationDegrees
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        
        // Rotate bitmap to match display orientation
        if (rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        return bitmap
    }

    private fun encodeImage(bitmap: Bitmap): String {
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, bos)
        return Base64.encodeToString(bos.toByteArray(), Base64.DEFAULT)
    }

    private fun toggleFlash() {
        isFlashOn = !isFlashOn
        camera?.cameraControl?.enableTorch(isFlashOn)
    }

    private fun triggerHapticFeedback() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(150)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
