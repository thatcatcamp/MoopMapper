package com.capricallctx.moopmapper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Camera
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.ExifInterface
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.capricallctx.moopmapper.ui.theme.MoopMapperTheme
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.*

class MappingActivity : ComponentActivity(), LocationListener {
    private var camera: Camera? = null
    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null
    private var mappingStartTime: Long = 0
    private var sequenceNumber: Int = 0
    public var uploadKey: String = ""
    private var isMappingActive = false

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val storageGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true

        if (cameraGranted && locationGranted && storageGranted) {
            initializeMapping()
        } else {
            Toast.makeText(this, "Camera, location, and storage permissions required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        uploadKey = intent.getStringExtra("uploadKey") ?: "Unknown-Device-Key"

        if (hasRequiredPermissions()) {
            initializeMapping()
        } else {
            requestPermissions()
        }

        setContent {
            MoopMapperTheme {
                MappingScreen(
                    onStartMapping = { startMapping() },
                    onStopMapping = { stopMapping() },
                    isMappingActive = isMappingActive,
                    sequenceNumber = sequenceNumber
                )
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun initializeMapping() {
        try {
            camera = Camera.open()
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000, // 1 second
                0f,   // 0 meters (we handle distance ourselves)
                this
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to initialize camera or GPS: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startMapping() {
        if (!isMappingActive) {
            mappingStartTime = System.currentTimeMillis() / 1000 // Unix timestamp
            sequenceNumber = 0
            isMappingActive = true
            lastLocation = null
            Toast.makeText(this, "Mapping started. Walk slowly and cover the area systematically.", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopMapping() {
        isMappingActive = false
        Toast.makeText(this, "Mapping stopped. $sequenceNumber photos captured.", Toast.LENGTH_SHORT).show()
    }

    override fun onLocationChanged(location: Location) {
        if (!isMappingActive) return

        if (lastLocation == null) {
            // First location - take initial photo
            capturePhoto(location)
            lastLocation = location
        } else {
            // Calculate distance from last photo location
            val distance = lastLocation!!.distanceTo(location)

            // Take photo every 50cm (0.5 meters)
            if (distance >= 0.5f) {
                capturePhoto(location)
                lastLocation = location
            }
        }
    }

    private fun capturePhoto(location: Location) {
        try {
            sequenceNumber++

            val fileName = "${uploadKey}_${mappingStartTime}_${String.format("%04d", sequenceNumber)}.jpg"
            val photoFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName)

            camera?.takePicture(null, null) { data, _ ->
                try {
                    val fos = FileOutputStream(photoFile)
                    fos.write(data)
                    fos.close()

                    // Add EXIF data with GPS coordinates
                    addExifData(photoFile, location)

                    Toast.makeText(this, "Photo $sequenceNumber captured", Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    Toast.makeText(this, "Failed to save photo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to capture photo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addExifData(photoFile: File, location: Location) {
        try {
            val exif = ExifInterface(photoFile.absolutePath)

            // Convert decimal degrees to degrees/minutes/seconds format for EXIF
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

            // Add timestamp
            exif.setAttribute(ExifInterface.TAG_DATETIME,
                java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date()))

            // Add accuracy if available
            if (location.hasAccuracy()) {
                exif.setAttribute(ExifInterface.TAG_GPS_DOP, location.accuracy.toString())
            }

            exif.saveAttributes()
        } catch (e: IOException) {
            Toast.makeText(this, "Failed to add GPS data to photo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun convertDecimalToDMS(decimal: Double): String {
        val degrees = decimal.toInt()
        val minutesFloat = (decimal - degrees) * 60
        val minutes = minutesFloat.toInt()
        val seconds = (minutesFloat - minutes) * 60

        return "$degrees/1,$minutes/1,${(seconds * 1000).toInt()}/1000"
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onDestroy() {
        super.onDestroy()
        camera?.release()
        locationManager?.removeUpdates(this)
    }
}

@Composable
fun MappingScreen(
    onStartMapping: () -> Unit,
    onStopMapping: () -> Unit,
    isMappingActive: Boolean,
    sequenceNumber: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Lot Mapping",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        if (isMappingActive) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "MAPPING ACTIVE",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Photos captured: $sequenceNumber",
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            }
        }

        Text(
            text = "Instructions:\n" +
                   "• Hold phone parallel to ground\n" +
                   "• Walk slowly in systematic pattern\n" +
                   "• Photos taken automatically every 50cm\n" +
                   "• GPS coordinates saved with each image",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        if (!isMappingActive) {
            val context = LocalContext.current
            Button(
                onClick = {
                    val intent = android.content.Intent(context, CameraPreviewActivity::class.java)
                    intent.putExtra("uploadKey", (context as MappingActivity).uploadKey)
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text(
                    text = "Start Mapping from One Corner",
                    fontSize = 18.sp
                )
            }
        } else {
            Button(
                onClick = onStopMapping,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
            ) {
                Text(
                    text = "Stop Mapping",
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
