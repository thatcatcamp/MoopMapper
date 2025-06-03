package com.capricallctx.moopmapper

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.capricallctx.moopmapper.ui.theme.MoopMapperTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class PhotoInfo(
    val file: File,
    val uploadKey: String,
    val timestamp: String,
    val sequence: String,
    val formattedDate: String
)

class ImageBrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MoopMapperTheme {
                ImageBrowserScreen(
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageBrowserScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf<List<PhotoInfo>>(emptyList()) }
    var selectedPhoto by remember { mutableStateOf<PhotoInfo?>(null) }
    var groupedPhotos by remember { mutableStateOf<Map<String, List<PhotoInfo>>>(emptyMap()) }

    LaunchedEffect(Unit) {
        photos = loadPhotos(context)
        groupedPhotos = photos.groupBy { it.uploadKey }
    }

    if (selectedPhoto != null) {
        // Full screen photo viewer
        PhotoDetailScreen(
            photo = selectedPhoto!!,
            onBackPressed = { selectedPhoto = null }
        )
    } else {
        // Photo grid browser
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Browse Mapping Photos",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF2196F3),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                if (photos.isEmpty()) {
                    // Empty state
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = "No images",
                            modifier = Modifier.size(80.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No mapping photos found",
                            fontSize = 18.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Take some photos with the mapping feature first!",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Summary stats
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Photo Library",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Total Photos: ${photos.size}",
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Upload Keys: ${groupedPhotos.keys.size}",
                                fontSize = 14.sp
                            )
                            if (groupedPhotos.isNotEmpty()) {
                                Text(
                                    text = "Latest Session: ${groupedPhotos.maxByOrNull { it.value.size }?.key ?: "N/A"}",
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Photo grid grouped by upload key
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groupedPhotos.forEach { (uploadKey, photoList) ->
                            item {
                                // Upload key header spanning full width
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF2196F3).copy(alpha = 0.1f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = uploadKey,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2196F3)
                                        )
                                        Text(
                                            text = "${photoList.size} photos",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }

                            items(photoList) { photo ->
                                PhotoThumbnail(
                                    photo = photo,
                                    onClick = { selectedPhoto = photo }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoThumbnail(
    photo: PhotoInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box {
            SimpleImageLoader(
                file = photo.file,
                contentDescription = "Photo ${photo.sequence}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Overlay with sequence info
            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "#${photo.sequence}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    photo: PhotoInfo,
    onBackPressed: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Photo #${photo.sequence}",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Full size image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                SimpleImageLoader(
                    file = photo.file,
                    contentDescription = "Photo ${photo.sequence}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            // Photo details
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Photo Details",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    DetailRow("Upload Key", photo.uploadKey)
                    DetailRow("Sequence", "#${photo.sequence}")
                    DetailRow("Timestamp", photo.timestamp)
                    DetailRow("Date", photo.formattedDate)
                    DetailRow("File", photo.file.name)
                    DetailRow("Size", "${photo.file.length() / 1024} KB")
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

private fun loadPhotos(context: android.content.Context): List<PhotoInfo> {
    val photosDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    if (photosDir == null || !photosDir.exists()) {
        return emptyList()
    }

    return photosDir.listFiles { file ->
        file.isFile && file.extension.lowercase() == "jpg"
    }?.mapNotNull { file ->
        parsePhotoInfo(file)
    }?.sortedWith(compareByDescending<PhotoInfo> { it.timestamp }.thenByDescending { it.sequence }) ?: emptyList()
}

private fun parsePhotoInfo(file: File): PhotoInfo? {
    // Parse filename: UploadKey_Timestamp_Sequence.jpg
    val nameWithoutExt = file.nameWithoutExtension
    val parts = nameWithoutExt.split("_")

    if (parts.size >= 3) {
        val uploadKey = parts.dropLast(2).joinToString("-") // Handle multi-word keys
        val timestamp = parts[parts.size - 2]
        val sequence = parts.last()

        // Format timestamp for display
        val formattedDate = try {
            val date = Date(timestamp.toLong() * 1000)
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(date)
        } catch (e: Exception) {
            "Unknown date"
        }

        return PhotoInfo(
            file = file,
            uploadKey = uploadKey,
            timestamp = timestamp,
            sequence = sequence,
            formattedDate = formattedDate
        )
    }

    return null
}

@Composable
fun SimpleImageLoader(
    file: File,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(file) {
        try {
            // Load bitmap in background thread
            val loadedBitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            bitmap = loadedBitmap
            isLoading = false
        } catch (e: Exception) {
            isLoading = false
        }
    }
    
    Box(modifier = modifier) {
        if (isLoading) {
            // Loading placeholder
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color(0xFF2196F3)
                )
            }
        } else if (bitmap != null) {
            // Display loaded image
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            // Error placeholder
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = "Error loading image",
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )
                    Text(
                        text = "Error loading image",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
