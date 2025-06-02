package com.capricallctx.moopmapper

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capricallctx.moopmapper.ui.theme.MoopMapperTheme
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoopMapperTheme {
                LaunchScreen()
            }
        }
    }
}

@Composable
fun LaunchScreen() {
    val context = LocalContext.current
    var deviceId by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        deviceId = getOrCreateDeviceId(context)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.bg0),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = "MoopMapper",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )

                Text(
                    text = "Burning Man Lot Mapping",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Upload Key:",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = deviceId,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Capture and map Burning Man camp lots before and after the event. Help create a comprehensive visual record of the playa's transformation by taking snapshots of your assigned area and uploading them to contribute to the collective mapping effort.",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = { /* TODO: Implement snapshot */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = "Snapshot",
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { /* TODO: Implement upload */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = "Upload",
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "© 2025 CAT Camp • Licensed under MIT License - Steal this Code! :)",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun getOrCreateDeviceId(context: Context): String {
    val prefs = context.getSharedPreferences("moopmapper_prefs", Context.MODE_PRIVATE)
    val existingId = prefs.getString("device_id", null)

    return if (existingId != null && existingId.contains("-") && !existingId.contains(" ")) {
        existingId
    } else {
        val newId = generateThreeWordId()
        prefs.edit().putString("device_id", newId).apply()
        newId
    }
}

private fun generateThreeWordId(): String {
    val adjectives = listOf(
        "Red", "Blue", "Green", "Yellow", "Purple", "Orange", "Pink", "Silver", "Gold", "Black",
        "White", "Brown", "Gray", "Violet", "Cyan", "Magenta", "Lime", "Teal", "Navy", "Maroon",
        "Crimson", "Azure", "Jade", "Amber", "Ruby", "Emerald", "Sapphire", "Pearl", "Coral", "Mint"
    )

    val animals = listOf(
        "Tiger", "Lion", "Eagle", "Wolf", "Bear", "Fox", "Hawk", "Owl", "Shark", "Whale",
        "Dolphin", "Elephant", "Giraffe", "Zebra", "Panda", "Koala", "Kangaroo", "Penguin", "Falcon", "Raven",
        "Leopard", "Cheetah", "Jaguar", "Panther", "Lynx", "Cougar", "Bobcat", "Coyote", "Jackal", "Hyena"
    )

    val objects = listOf(
        "Rock", "Star", "Moon", "Sun", "Fire", "Wave", "Wind", "Storm", "Thunder", "Lightning",
        "Crystal", "Diamond", "Sword", "Shield", "Arrow", "Spear", "Hammer", "Axe", "Crown", "Ring",
        "Tower", "Castle", "Bridge", "Mountain", "River", "Ocean", "Desert", "Forest", "Valley", "Peak"
    )

    val adjective = adjectives[Random.nextInt(adjectives.size)]
    val animal = animals[Random.nextInt(animals.size)]
    val obj = objects[Random.nextInt(objects.size)]

    return "$adjective-$animal-$obj"
}

@Preview(showBackground = true)
@Composable
fun LaunchScreenPreview() {
    MoopMapperTheme {
        LaunchScreen()
    }
}
