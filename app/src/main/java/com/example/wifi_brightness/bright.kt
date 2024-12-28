package com.example.wifi_brightness



import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if the app has permission to adjust brightness
        if (!Settings.System.canWrite(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }

        setContent {
            MainApp()
        }
    }
}

@Composable
fun MainApp() {
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Battery Status
                BatteryStatusComposable(context)

                // Brightness Control
                BrightnessControlComposable(context)
            }
        }
    )
}

@Composable
fun BatteryStatusComposable(context: Context) {
    var batteryPercentage by remember { mutableStateOf("Loading...") }
    var chargingStatus by remember { mutableStateOf("Unknown") }

    LaunchedEffect(Unit) {
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val batteryPct = (level / scale.toFloat()) * 100
                    batteryPercentage = "Battery Percentage: ${batteryPct.toInt()}%"

                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    chargingStatus = if (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                    ) {
                        "Charging: Yes"
                    } else {
                        "Charging: No"
                    }
                }
            }
        }
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, intentFilter)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = batteryPercentage, style = MaterialTheme.typography.bodyLarge)
        Text(text = chargingStatus, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun BrightnessControlComposable(context: Context) {
    var sliderValue by remember { mutableStateOf(0.5f) }
    val scope = rememberCoroutineScope()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Adjust Brightness", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = sliderValue,
            onValueChange = { newValue -> sliderValue = newValue },
            onValueChangeFinished = {
                scope.launch(Dispatchers.IO) {
                    val brightnessValue = (sliderValue * 255).toInt()
                    try {
                        Settings.System.putInt(
                            context.contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS,
                            brightnessValue
                        )
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Permission required to adjust brightness.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainAppPreview() {
    MainApp()
}
