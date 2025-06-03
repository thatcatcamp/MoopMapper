package com.capricallctx.moopmapper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.capricallctx.moopmapper.ui.theme.MoopMapperTheme
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.*

class CameraPreviewActivity : ComponentActivity() {
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastLocation: Location? = null
    private var mappingStartTime: Long = 0
    private var sequenceNumber: Int = 0
    private var uploadKey: String = ""
    private var isMappingActive = false
    private var sessionPhotoCount = 0

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
                var currentLocation by remember { mutableStateOf<Location?>(null) }
                var photoCount by remember { mutableStateOf(0) }
                var isActive by remember { mutableStateOf(false) }
                var sequence by remember { mutableStateOf(0) }
                
                LaunchedEffect(Unit) {
                    startLocationUpdates { location ->
                        currentLocation = location
                        if (isActive) {
                            checkAndCapturePhoto(location) { count ->
                                photoCount = count
                                sequence = sequenceNumber
                            }
                        }
                    }
                }
                
                CameraPreviewScreen(
                    onStartMapping = {
                        isActive = true
                        isMappingActive = true
                        sessionPhotoCount = 0
                        lastLocation = null
                        sequenceNumber = 0
                    },
                    onStopMapping = {
                        isActive = false
                        isMappingActive = false
                    },
                    currentLocation = currentLocation,
                    photoCount = photoCount,
                    sequenceNumber = sequence,
                    uploadKey = uploadKey,
                    isActive = isActive
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

            val preview = Preview.Builder().build().also {
                // Preview will be set up in Compose
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e("CameraPreview", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(onLocationUpdate: (Location) -> Unit) {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000 // 1 second interval
        ).setMinUpdateIntervalMillis(500).build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    onLocationUpdate(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }

    private fun checkAndCapturePhoto(location: Location, onPhotoTaken: (Int) -> Unit) {
        if (!isMappingActive) return

        if (lastLocation == null) {
            // First location - take initial photo
            capturePhoto(location, onPhotoTaken)
            lastLocation = location
        } else {
            // Calculate distance from last photo location
            val distance = lastLocation!!.distanceTo(location)
            
            // Take photo every 50cm (0.5 meters)
            if (distance >= 0.5f) {
                capturePhoto(location, onPhotoTaken)
                lastLocation = location
            }
        }
    }

    private fun capturePhoto(location: Location, onPhotoTaken: (Int) -> Unit) {
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
                    Log.e("CameraPreview", "Photo capture failed: ${exception.message}", exception)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    addExifData(photoFile, location)
                    onPhotoTaken(sessionPhotoCount)
                }
            }
        )
    }

    private fun addExifData(photoFile: File, location: Location) {
        try {
            val exif = ExifInterface(photoFile.absolutePath)
            
            val latRef = if (location.latitude >= 0) "N" else "S"
            val lonRef = if (location.longitude >= 0) "E" else "W"
            
            val latitude = convertDecimalToDMS(abs(location.latitude))
            val longitude = convertDecimalToDMS(abs(location.longitude))
            
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, latitude)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latRef)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, longitude)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lonRef)
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, location.altitude.toString())
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, "0")
            
            exif.setAttribute(ExifInterface.TAG_DATETIME, 
                SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date()))
            
            if (location.hasAccuracy()) {
                exif.setAttribute(ExifInterface.TAG_GPS_DOP, location.accuracy.toString())
            }
            
            exif.saveAttributes()
        } catch (e: Exception) {
            Log.e("CameraPreview", "Failed to add EXIF data", e)
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
fun CameraPreviewScreen(
    onStartMapping: () -> Unit,
    onStopMapping: () -> Unit,
    currentLocation: Location?,
    photoCount: Int,
    sequenceNumber: Int,
    uploadKey: String,
    isActive: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { preview ->
                    previewView = preview
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val previewUseCase = Preview.Builder().build().also {
                            it.setSurfaceProvider(preview.surfaceProvider)
                        }
                        
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                previewUseCase
                            )
                        } catch (exc: Exception) {
                            Log.e("CameraPreview", "Use case binding failed", exc)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Information
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top overlay - Date and GPS info
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
                        text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    currentLocation?.let { location ->
                        Text(
                            text = "GPS: ${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Accuracy: ${String.format("%.1f", location.accuracy)}m",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    } ?: Text(
                        text = "GPS: Acquiring location...",
                        color = Color.Yellow,
                        fontSize = 12.sp
                    )
                    
                    Text(
                        text = "Upload Key: $uploadKey",
                        color = Color.White,
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
                        containerColor = Color.Red.copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "● MAPPING",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Bottom overlay - Counter and controls
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
                            text = "Sequence: ${String.format("%04d", sequenceNumber)}",
                            color = Color.White,
                            fontSize = 14.sp
                        )
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
                            Text("Start Mapping from One Corner")
                        }
                    } else {
                        Button(
                            onClick = onStopMapping,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF44336)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Stop Mapping")
                        }
                    }
                }
            }
        }
    }
}