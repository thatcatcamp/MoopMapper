package com.capricallctx.moopmapper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextDecoration
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
    var showSnapshotDialog by remember { mutableStateOf(false) }

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
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val url = "https://moopmapper.com/v1/view/$deviceId"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
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
                    onClick = { showSnapshotDialog = true },
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
    
    if (showSnapshotDialog) {
        AlertDialog(
            onDismissRequest = { showSnapshotDialog = false },
            title = {
                Text(
                    text = "Snapshot Instructions",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "You will now be able to photograph your lot.\n\n" +
                           "• Hold the phone parallel to the ground\n" +
                           "• Walk slowly from end to end\n" +
                           "• Cover the entire area systematically\n" +
                           "• Photos will be taken automatically\n\n" +
                           "Photos will be stored locally and uploaded when you tap the Upload button later.",
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        showSnapshotDialog = false
                        // TODO: Start camera/mapping activity
                    }
                ) {
                    Text("Start Mapping")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSnapshotDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
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
    if (BuildConfig.DEBUG) {
        return "Blue-Fox-Crystal"
    }
    
    val adjectives = listOf(
        "Red", "Blue", "Green", "Yellow", "Purple", "Orange", "Pink", "Silver", "Gold", "Black",
        "White", "Brown", "Gray", "Violet", "Cyan", "Magenta", "Lime", "Teal", "Navy", "Maroon",
        "Crimson", "Azure", "Jade", "Amber", "Ruby", "Emerald", "Sapphire", "Pearl", "Coral", "Mint",
        "Scarlet", "Turquoise", "Indigo", "Bronze", "Copper", "Platinum", "Rose", "Ivory", "Cream", "Beige",
        "Tan", "Khaki", "Olive", "Forest", "Sage", "Lavender", "Plum", "Burgundy", "Wine", "Cherry",
        "Peach", "Apricot", "Honey", "Butter", "Vanilla", "Chocolate", "Coffee", "Caramel", "Cinnamon", "Ginger",
        "Paprika", "Saffron", "Mustard", "Lemon", "Citrus", "Lime", "Apple", "Grass", "Jade", "Emerald",
        "Malachite", "Aqua", "Sky", "Steel", "Slate", "Charcoal", "Ash", "Smoke", "Storm", "Thunder",
        "Lightning", "Fire", "Flame", "Ember", "Sunset", "Dawn", "Twilight", "Midnight", "Starlight", "Moonbeam",
        "Solar", "Lunar", "Cosmic", "Galactic", "Stellar", "Nebula", "Aurora", "Prism", "Spectrum", "Rainbow",
        "Mystic", "Magic", "Enchanted", "Divine", "Sacred", "Ancient", "Eternal", "Infinite", "Radiant", "Brilliant"
    )
    
    val animals = listOf(
        "Tiger", "Lion", "Eagle", "Wolf", "Bear", "Fox", "Hawk", "Owl", "Shark", "Whale",
        "Dolphin", "Elephant", "Giraffe", "Zebra", "Panda", "Koala", "Kangaroo", "Penguin", "Falcon", "Raven",
        "Leopard", "Cheetah", "Jaguar", "Panther", "Lynx", "Cougar", "Bobcat", "Coyote", "Jackal", "Hyena",
        "Rhino", "Hippo", "Bison", "Buffalo", "Moose", "Elk", "Deer", "Antelope", "Gazelle", "Impala",
        "Oryx", "Ibex", "Yak", "Llama", "Alpaca", "Camel", "Dromedary", "Okapi", "Tapir", "Capybara",
        "Beaver", "Otter", "Seal", "Walrus", "Manatee", "Dugong", "Narwhal", "Beluga", "Orca", "Porpoise",
        "Marlin", "Tuna", "Salmon", "Trout", "Pike", "Bass", "Cod", "Halibut", "Flounder", "Sole",
        "Octopus", "Squid", "Jellyfish", "Starfish", "Seahorse", "Lobster", "Crab", "Shrimp", "Turtle", "Tortoise",
        "Iguana", "Gecko", "Chameleon", "Monitor", "Python", "Cobra", "Viper", "Anaconda", "Boa", "Mamba",
        "Condor", "Vulture", "Kite", "Harrier", "Buzzard", "Kestrel", "Merlin", "Goshawk", "Sparrowhawk", "Peregrine",
        "Albatross", "Pelican", "Cormorant", "Heron", "Stork", "Ibis", "Flamingo", "Swan", "Goose", "Duck",
        "Crane", "Rail", "Plover", "Sandpiper", "Gull", "Tern", "Puffin", "Auk", "Grouse", "Pheasant"
    )
    
    val objects = listOf(
        "Rock", "Star", "Moon", "Sun", "Fire", "Wave", "Wind", "Storm", "Thunder", "Lightning",
        "Crystal", "Diamond", "Sword", "Shield", "Arrow", "Spear", "Hammer", "Axe", "Crown", "Ring",
        "Tower", "Castle", "Bridge", "Mountain", "River", "Ocean", "Desert", "Forest", "Valley", "Peak",
        "Canyon", "Cliff", "Cave", "Grotto", "Cavern", "Plateau", "Mesa", "Butte", "Hill", "Ridge",
        "Glacier", "Iceberg", "Avalanche", "Blizzard", "Tornado", "Hurricane", "Typhoon", "Monsoon", "Cyclone", "Tempest",
        "Volcano", "Crater", "Geyser", "Spring", "Waterfall", "Rapids", "Delta", "Estuary", "Lagoon", "Bay",
        "Peninsula", "Island", "Archipelago", "Atoll", "Reef", "Coral", "Kelp", "Seaweed", "Driftwood", "Shell",
        "Pebble", "Boulder", "Cobble", "Sand", "Dune", "Oasis", "Mirage", "Aurora", "Comet", "Meteor",
        "Asteroid", "Nebula", "Galaxy", "Constellation", "Quasar", "Pulsar", "Supernova", "Blackhole", "Wormhole", "Vortex",
        "Prism", "Lens", "Mirror", "Kaleidoscope", "Compass", "Sundial", "Hourglass", "Pendulum", "Gyroscope", "Telescope",
        "Microscope", "Periscope", "Binoculars", "Magnifier", "Lantern", "Beacon", "Lighthouse", "Torch", "Candle", "Ember",
        "Spark", "Flint", "Steel", "Bronze", "Copper", "Brass", "Pewter", "Zinc", "Titanium", "Platinum"
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
