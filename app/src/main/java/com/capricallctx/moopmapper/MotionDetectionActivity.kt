package com.capricallctx.moopmapper

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.*
import android.media.ExifInterface
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.capricallctx.moopmapper.ui.theme.MoopMapperTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.*

class MotionDetectionActivity : ComponentActivity() {
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mappingStartTime: Long = 0
    private var sequenceNumber: Int = 0
    private var uploadKey: String = ""
    private var isMappingActive = false
    private var sessionPhotoCount = 0
    
    // Motion detection variables
    private var previousFrame: ByteArray? = null
    private var motionThreshold = 0.15f // 15% of frame must change
    private var lastCaptureTime = 0L
    private var minCaptureInterval = 1000L // Minimum 1 second between captures
    
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        
        if (cameraGranted && locationGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera and location permissions required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        uploadKey = intent.getStringExtra("uploadKey") ?: "Unknown-Device-Key"
        mappingStartTime = System.currentTimeMillis() / 1000
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        setContent {
            MoopMapperTheme {
                var photoCount by remember { mutableStateOf(0) }
                var isActive by remember { mutableStateOf(false) }
                var sequence by remember { mutableStateOf(0) }
                var motionLevel by remember { mutableStateOf(0f) }
                var isTracking by remember { mutableStateOf(false) }
                
                MotionDetectionScreen(
                    onStartMapping = {
                        isActive = true
                        isMappingActive = true
                        sessionPhotoCount = 0
                        sequenceNumber = 0
                        previousFrame = null
                        isTracking = true
                    },
                    onStopMapping = {
                        isActive = false
                        isMappingActive = false
                        isTracking = false
                        previousFrame = null
                    },
                    photoCount = photoCount,
                    sequenceNumber = sequence,
                    uploadKey = uploadKey,
                    isActive = isActive,
                    motionLevel = motionLevel,
                    isTracking = isTracking,
                    onPhotoTaken = { count ->
                        photoCount = count
                        sequence = sequenceNumber
                    },
                    onMotionDetected = { level ->
                        motionLevel = level
                    }
                )
            }
        }
    }

    private fun allPermissionsGranted() = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION
    ).all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            // Image analysis for motion detection
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, MotionAnalyzer())
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("MotionDetection", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private inner class MotionAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(image: ImageProxy) {
            if (!isMappingActive) {
                image.close()
                return
            }

            val currentTime = System.currentTimeMillis()
            
            // Convert image to grayscale byte array
            val currentFrame = imageToGrayscaleBytes(image)
            
            if (previousFrame != null && currentFrame != null) {
                val motionLevel = calculateMotionLevel(previousFrame!!, currentFrame)
                
                // Update UI with motion level
                runOnUiThread {
                    // Motion level callback would go here
                }
                
                // Trigger capture if enough motion detected and enough time has passed
                if (motionLevel > motionThreshold && 
                    currentTime - lastCaptureTime > minCaptureInterval) {
                    
                    capturePhoto { count ->
                        sessionPhotoCount = count
                        runOnUiThread {
                            // Photo count callback would go here
                        }
                    }
                    lastCaptureTime = currentTime
                }
            }
            
            previousFrame = currentFrame
            image.close()
        }
    }

    private fun imageToGrayscaleBytes(image: ImageProxy): ByteArray? {
        try {
            val yBuffer = image.planes[0].buffer
            val ySize = yBuffer.remaining()
            val nv21 = ByteArray(ySize)
            yBuffer.get(nv21, 0, ySize)
            return nv21
        } catch (e: Exception) {
            Log.e("MotionDetection", "Failed to convert image to grayscale", e)
            return null
        }
    }

    private fun calculateMotionLevel(previousFrame: ByteArray, currentFrame: ByteArray): Float {
        if (previousFrame.size != currentFrame.size) return 0f
        
        var differences = 0
        val threshold = 30 // Pixel difference threshold
        val stepSize = 8 // Sample every 8th pixel for better performance
        
        for (i in previousFrame.indices step stepSize) {
            if (i < currentFrame.size) {
                val diff = abs(previousFrame[i].toInt() and 0xFF - currentFrame[i].toInt() and 0xFF)
                if (diff > threshold) {
                    differences++
                }
            }
        }
        
        val totalSamples = previousFrame.size / stepSize
        return if (totalSamples > 0) differences.toFloat() / totalSamples.toFloat() else 0f
    }

    private fun capturePhoto(onPhotoTaken: (Int) -> Unit) {
        val imageCapture = imageCapture ?: return

        sequenceNumber++
        sessionPhotoCount++
        
        val fileName = "${uploadKey}_${mappingStartTime}_${String.format("%04d", sequenceNumber)}.jpg"
        val photoFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName)

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Log.e("MotionDetection", "Photo capture failed: ${exception.message}", exception)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // Add minimal EXIF data (GPS would come from separate location tracking)
                    addBasicExifData(photoFile)
                    onPhotoTaken(sessionPhotoCount)
                    
                    runOnUiThread {
                        Toast.makeText(
                            this@MotionDetectionActivity,
                            "Auto-captured photo $sequenceNumber",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    private fun addBasicExifData(photoFile: File) {
        try {
            val exif = ExifInterface(photoFile.absolutePath)
            exif.setAttribute(ExifInterface.TAG_DATETIME, 
                SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date()))
            exif.saveAttributes()
        } catch (e: Exception) {
            Log.e("MotionDetection", "Failed to add EXIF data", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

@Composable
fun MotionDetectionScreen(
    onStartMapping: () -> Unit,
    onStopMapping: () -> Unit,
    photoCount: Int,
    sequenceNumber: Int,
    uploadKey: String,
    isActive: Boolean,
    motionLevel: Float,
    isTracking: Boolean,
    onPhotoTaken: (Int) -> Unit,
    onMotionDetected: (Float) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(surfaceProvider)
                        }
                        
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview
                            )
                        } catch (exc: Exception) {
                            Log.e("MotionDetection", "Use case binding failed", exc)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Motion tracking overlay
        if (isTracking) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val centerX = size.width / 2
                val centerY = size.height / 2
                val gridSize = 50f
                
                // Draw motion detection grid
                for (x in 0..size.width.toInt() step gridSize.toInt()) {
                    drawLine(
                        color = androidx.compose.ui.graphics.Color.Green.copy(alpha = 0.3f),
                        start = androidx.compose.ui.geometry.Offset(x.toFloat(), 0f),
                        end = androidx.compose.ui.geometry.Offset(x.toFloat(), size.height),
                        strokeWidth = 1f
                    )
                }
                
                for (y in 0..size.height.toInt() step gridSize.toInt()) {
                    drawLine(
                        color = androidx.compose.ui.graphics.Color.Green.copy(alpha = 0.3f),
                        start = androidx.compose.ui.geometry.Offset(0f, y.toFloat()),
                        end = androidx.compose.ui.geometry.Offset(size.width, y.toFloat()),
                        strokeWidth = 1f
                    )
                }
                
                // Center crosshair
                drawLine(
                    color = androidx.compose.ui.graphics.Color.Red,
                    start = androidx.compose.ui.geometry.Offset(centerX - 20, centerY),
                    end = androidx.compose.ui.geometry.Offset(centerX + 20, centerY),
                    strokeWidth = 3f
                )
                drawLine(
                    color = androidx.compose.ui.graphics.Color.Red,
                    start = androidx.compose.ui.geometry.Offset(centerX, centerY - 20),
                    end = androidx.compose.ui.geometry.Offset(centerX, centerY + 20),
                    strokeWidth = 3f
                )
            }
        }

        // Overlay Information
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top overlay - Motion detection info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Motion Detection Mapping",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 12.sp
                    )
                    
                    if (isTracking) {
                        Text(
                            text = "Motion Level: ${String.format("%.1f", motionLevel * 100)}%",
                            color = if (motionLevel > 0.15f) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Yellow,
                            fontSize = 12.sp
                        )
                        
                        Text(
                            text = "Threshold: 15% (Auto-capture when exceeded)",
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 10.sp
                        )
                    }
                    
                    Text(
                        text = "Upload Key: $uploadKey",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Center status indicator
            if (isActive) {
                Card(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "● AUTO MAPPING",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Bottom overlay - Instructions and controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Session Photos: $photoCount",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (sequenceNumber > 0) {
                        Text(
                            text = "Last Sequence: ${String.format("%04d", sequenceNumber)}",
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 14.sp
                        )
                    }
                    
                    if (isActive) {
                        Text(
                            text = "Hold phone steady, walk slowly.\nPhotos captured automatically when ground moves.",
                            color = androidx.compose.ui.graphics.Color.Yellow,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (!isActive) {
                        Button(
                            onClick = onStartMapping,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Auto-Mapping")
                        }
                    } else {
                        Button(
                            onClick = onStopMapping,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = androidx.compose.ui.graphics.Color(0xFFF44336)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Stop Auto-Mapping")
                        }
                    }
                }
            }
        }
    }
}