package com.capricallctx.moopmapper

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

class SimpleMotionDetectionActivity : ComponentActivity() {
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mappingStartTime: Long = 0
    private var sequenceNumber: Int = 0
    private var uploadKey: String = ""
    private var isMappingActive = false
    private var sessionPhotoCount = 0
    private var currentLocation: Location? = null
    
    // Simplified motion detection variables
    private var previousLuminance: Float = 0f
    private var motionThreshold = 0.02f // 2% luminance change (more sensitive)
    private var lastCaptureTime = 0L
    private var minCaptureInterval = 1000L // Minimum 1 second between captures
    private var frameCount = 0
    
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        
        if (cameraGranted && locationGranted) {
            startCamera()
            startLocationUpdates()
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
            startLocationUpdates()
        } else {
            requestPermissions()
        }

        setContent {
            MoopMapperTheme {
                var photoCount by remember { mutableStateOf(0) }
                var isActive by remember { mutableStateOf(false) }
                var sequence by remember { mutableStateOf(0) }
                var motionLevel by remember { mutableStateOf(0f) }
                var location by remember { mutableStateOf<Location?>(null) }
                
                LaunchedEffect(currentLocation) {
                    location = currentLocation
                }
                
                SimpleMotionScreen(
                    onStartMapping = {
                        isActive = true
                        isMappingActive = true
                        sessionPhotoCount = 0
                        sequenceNumber = 0
                        previousLuminance = 0f
                        lastCaptureTime = 0L
                        frameCount = 0
                        Log.d("MotionDetection", "Started mapping session")
                    },
                    onStopMapping = {
                        isActive = false
                        isMappingActive = false
                    },
                    photoCount = photoCount,
                    sequenceNumber = sequence,
                    uploadKey = uploadKey,
                    isActive = isActive,
                    motionLevel = motionLevel,
                    currentLocation = location,
                    motionThreshold = motionThreshold,
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

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000 // 2 second interval
        ).setMinUpdateIntervalMillis(1000).build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    currentLocation = location
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
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

            // Simplified image analysis for motion detection
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, SimpleLuminanceAnalyzer())
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("SimpleMotion", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private inner class SimpleLuminanceAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(image: ImageProxy) {
            if (!isMappingActive) {
                image.close()
                return
            }

            frameCount++
            val currentTime = System.currentTimeMillis()
            
            // Calculate average luminance
            val currentLuminance = calculateAverageLuminance(image)
            
            if (previousLuminance > 0f) {
                val luminanceChange = abs(currentLuminance - previousLuminance) / previousLuminance
                
                Log.d("MotionDetection", "Frame $frameCount: Luminance change: ${String.format("%.4f", luminanceChange)}, Threshold: $motionThreshold")
                
                // Trigger capture if enough luminance change and enough time has passed
                if (luminanceChange > motionThreshold && 
                    currentTime - lastCaptureTime > minCaptureInterval) {
                    
                    Log.d("MotionDetection", "Triggering capture! Change: ${String.format("%.4f", luminanceChange)}")
                    capturePhoto { count ->
                        sessionPhotoCount = count
                    }
                    lastCaptureTime = currentTime
                } else if (frameCount % 30 == 0) { // Log every 30 frames for debugging
                    Log.d("MotionDetection", "No capture - Change: ${String.format("%.4f", luminanceChange)}, Time since last: ${currentTime - lastCaptureTime}ms")
                }
                
                // Update UI with motion level
                runOnUiThread {
                    // Could add motion level callback here
                }
            } else {
                Log.d("MotionDetection", "Setting initial luminance: $currentLuminance")
            }
            
            previousLuminance = currentLuminance
            image.close()
        }
    }

    private fun calculateAverageLuminance(image: ImageProxy): Float {
        try {
            val yBuffer = image.planes[0].buffer
            val ySize = yBuffer.remaining()
            
            // Reset buffer position
            yBuffer.rewind()
            
            var sum = 0L
            val stepSize = 50 // Sample every 50th pixel for better sensitivity
            var sampleCount = 0
            
            val bytes = ByteArray(ySize)
            yBuffer.get(bytes)
            
            for (i in bytes.indices step stepSize) {
                sum += (bytes[i].toInt() and 0xFF)
                sampleCount++
            }
            
            val result = if (sampleCount > 0) sum.toFloat() / sampleCount else 0f
            Log.v("MotionDetection", "Calculated luminance: $result from $sampleCount samples")
            return result
            
        } catch (e: Exception) {
            Log.e("SimpleMotion", "Failed to calculate luminance", e)
            return 0f
        }
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
                    Log.e("SimpleMotion", "Photo capture failed: ${exception.message}", exception)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    addExifData(photoFile, currentLocation)
                    onPhotoTaken(sessionPhotoCount)
                    
                    runOnUiThread {
                        Toast.makeText(
                            this@SimpleMotionDetectionActivity,
                            "Auto-captured photo $sequenceNumber",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    private fun addExifData(photoFile: File, location: Location?) {
        try {
            val exif = ExifInterface(photoFile.absolutePath)
            
            exif.setAttribute(ExifInterface.TAG_DATETIME, 
                SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date()))
            
            location?.let { loc ->
                val latRef = if (loc.latitude >= 0) "N" else "S"
                val lonRef = if (loc.longitude >= 0) "E" else "W"
                
                val latitude = convertDecimalToDMS(abs(loc.latitude))
                val longitude = convertDecimalToDMS(abs(loc.longitude))
                
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, latitude)
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latRef)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, longitude)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lonRef)
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, loc.altitude.toString())
                
                if (loc.hasAccuracy()) {
                    exif.setAttribute(ExifInterface.TAG_GPS_DOP, loc.accuracy.toString())
                }
            }
            
            exif.saveAttributes()
        } catch (e: Exception) {
            Log.e("SimpleMotion", "Failed to add EXIF data", e)
        }
    }

    private fun convertDecimalToDMS(decimal: Double): String {
        val degrees = decimal.toInt()
        val minutesFloat = (decimal - degrees) * 60
        val minutes = minutesFloat.toInt()
        val seconds = (minutesFloat - minutes) * 60
        
        return "$degrees/1,$minutes/1,${(seconds * 1000).toInt()}/1000"
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

@Composable
fun SimpleMotionScreen(
    onStartMapping: () -> Unit,
    onStopMapping: () -> Unit,
    photoCount: Int,
    sequenceNumber: Int,
    uploadKey: String,
    isActive: Boolean,
    motionLevel: Float,
    currentLocation: Location?,
    onPhotoTaken: (Int) -> Unit,
    onMotionDetected: (Float) -> Unit,
    motionThreshold: Float = 0.02f
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
                            Log.e("SimpleMotion", "Use case binding failed", exc)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Simple center crosshair
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            
            // Center crosshair
            drawLine(
                color = Color.Red,
                start = androidx.compose.ui.geometry.Offset(centerX - 30, centerY),
                end = androidx.compose.ui.geometry.Offset(centerX + 30, centerY),
                strokeWidth = 4f
            )
            drawLine(
                color = Color.Red,
                start = androidx.compose.ui.geometry.Offset(centerX, centerY - 30),
                end = androidx.compose.ui.geometry.Offset(centerX, centerY + 30),
                strokeWidth = 4f
            )
        }

        // Overlay Information
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top overlay
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Smart Auto-Mapping",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    
                    currentLocation?.let { location ->
                        Text(
                            text = "GPS: ${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Accuracy: ${String.format("%.1f", location.accuracy)}m",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    } ?: Text(
                        text = "GPS: Acquiring location...",
                        color = Color.Yellow,
                        fontSize = 11.sp
                    )
                    
                    Text(
                        text = "Upload Key: $uploadKey",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Center status
            if (isActive) {
                Card(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Green.copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "● AUTO MAPPING",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Bottom controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Session Photos: $photoCount",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (sequenceNumber > 0) {
                        Text(
                            text = "Last Sequence: ${String.format("%04d", sequenceNumber)}",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                    
                    if (isActive) {
                        Text(
                            text = "Walk slowly and steadily.\nPhotos captured automatically based on scene changes.\nThreshold: ${String.format("%.1f", motionThreshold * 100)}%",
                            color = Color.Yellow,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        
                        // Manual capture button for testing
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { 
                                Log.d("MotionDetection", "Manual capture triggered")
                                onPhotoTaken(photoCount + 1)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF9800)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Manual Capture (Test)", fontSize = 14.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (!isActive) {
                        Button(
                            onClick = onStartMapping,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Smart Auto-Mapping")
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onStopMapping,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF44336)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Stop Auto-Mapping")
                            }
                            
                            Button(
                                onClick = { 
                                    onStopMapping()
                                    (context as? SimpleMotionDetectionActivity)?.finish()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2196F3)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Complete Session & Return")
                            }
                        }
                    }
                }
            }
        }
    }
}