import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun WifiManagerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val wifiViewModel = remember { WifiViewModel(context) }
    val wifiNetworks by wifiViewModel.wifiNetworks.collectAsState()
    val connectionState by wifiViewModel.connectionState.collectAsState()

    var selectedNetwork by remember { mutableStateOf<WifiState?>(null) }
    var password by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Brightness Control
            BrightnessControlComposable(context)

            Spacer(modifier = Modifier.height(16.dp))

            // Battery Status
            BatteryStatusComposable(context)

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (!wifiViewModel.isWifiEnabled()) wifiViewModel.enableWifi()
                    wifiViewModel.startScan()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan WiFi Networks")
            }

            Spacer(modifier = Modifier.height(16.dp))

            connectionState.let { state ->
                when (state) {
                    is WifiViewModel.ConnectionState.Connected -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
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

            LazyColumn(
                modifier = Modifier.weight(1f),
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

        if (showPasswordDialog && selectedNetwork != null) {
            AlertDialog(
                onDismissRequest = {
                    showPasswordDialog = false
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
                    Button(onClick = {
                        selectedNetwork?.let {
                            if (password.isNotEmpty()) {
                                wifiViewModel.connectToNetwork(it.ssid, password)
                                showPasswordDialog = false
                            }
                        }
                    }) { Text("Connect") }
                },
                dismissButton = {
                    TextButton(onClick = { showPasswordDialog = false }) { Text("Cancel") }
                }
            )
        }
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

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text = batteryPercentage, style = MaterialTheme.typography.bodyLarge)
        Text(text = chargingStatus, style = MaterialTheme.typography.bodyLarge)
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
            ,
        onClick = onClick
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
                    tint = if (isConnected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column {
                    Text(
                        text = network.ssid,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Signal Strength: ${network.level}",
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
            } else {
                Button(onClick = onClick) { Text("Connect") }
            }
        }
    }
}
