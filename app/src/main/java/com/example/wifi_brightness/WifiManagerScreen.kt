import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
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

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun MainApp( modifier: Modifier = Modifier) {
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

                // WiFi Manager
                WifiManagerScreen(modifier = Modifier.weight(1f))
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

    // Initialize slider value with the current brightness setting
    LaunchedEffect(Unit) {
        try {
            val currentBrightness = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )
            sliderValue = currentBrightness / 255f
        } catch (e: Exception) {
            // Handle exception if brightness setting can't be read
            Toast.makeText(
                context,
                "Unable to read current brightness setting.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Adjust Brightness", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = sliderValue,
            onValueChange = { newValue -> sliderValue = newValue },
            onValueChangeFinished = {
                scope.launch(Dispatchers.IO) {
                    val brightnessValue = (sliderValue * 255).toInt()
                    try {
                        // Check if permission is granted
                        if (Settings.System.canWrite(context)) {
                            Settings.System.putInt(
                                context.contentResolver,
                                Settings.System.SCREEN_BRIGHTNESS,
                                brightnessValue
                            )
                        } else {
                            // Prompt user to grant permission if not already granted
                            Toast.makeText(
                                context,
                                "Permission required to adjust brightness. Please grant it in settings.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Exception) {
                        // Handle exception while setting brightness
                        Toast.makeText(
                            context,
                            "Failed to adjust brightness: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun WifiManagerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val wifiViewModel = remember { WifiViewModel(context) }
    val isWifiEnabled = wifiViewModel.isWifiEnabled()
    val wifiNetworks by wifiViewModel.wifiNetworks.collectAsState()
    val connectionState by wifiViewModel.connectionState.collectAsState()

    var selectedNetwork by remember { mutableStateOf<WifiState?>(null) }
    var password by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Handle connection errors
    LaunchedEffect(connectionState) {
        if (connectionState is WifiViewModel.ConnectionState.Failed) {
            val errorMessage = (connectionState as WifiViewModel.ConnectionState.Failed).error
            snackbarHostState.showSnackbar(errorMessage, actionLabel = "Dismiss")
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Scan WiFi Networks Button
            Button(
                onClick = {
                    if (!wifiViewModel.isWifiEnabled()) {
                        wifiViewModel.enableWifi(context)
                    }
                    wifiViewModel.startScan()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!wifiViewModel.isWifiEnabled()) {
                    Text("Enable Wifi")

                } else {
                    Text("Scan WiFi Networks")
                }
            }


            Spacer(modifier = Modifier.height(16.dp))

            // Display connection state
            connectionState.let { state ->
                when (state) {
                    is WifiViewModel.ConnectionState.Connected -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Text("Connected to ${state.ssid}")
                            }
                        }
                    }

                    is WifiViewModel.ConnectionState.Connecting -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    else -> {}
                }
            }

            if (isWifiEnabled) {
                // List of available WiFi networks

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(wifiNetworks) { network ->


                        WifiNetworkItem(
                            network = network,
                            isConnected = connectionState is WifiViewModel.ConnectionState.Connected &&
                                    (connectionState as WifiViewModel.ConnectionState.Connected).ssid == network.ssid,
                            onClick = {
                                selectedNetwork = network
                                showPasswordDialog = true
                            }
                        )
                    }
                }
            }


        }

        }

        // Password Dialog
        if (showPasswordDialog && selectedNetwork != null) {
            AlertDialog(
                onDismissRequest = {
                    showPasswordDialog = false
                    password = ""
                },
                title = { Text("Connect to ${selectedNetwork?.ssid}") },
                text = {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            selectedNetwork?.let {
                                if (password.length >= 5) {
                                    wifiViewModel.connectToNetwork(it.ssid, password)
                                    showPasswordDialog = false
                                    password = ""
                                }
                            }
                        },
                        enabled = password.length >= 5
                    ) {
                        Text("Connect")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showPasswordDialog = false
                            password = ""
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }



@Composable
fun WifiNetworkItem(
    network: WifiState,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isConnected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column {
                    Text(
                        text = network.ssid,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Signal Strength: ${network.level} bars",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isConnected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Connected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
