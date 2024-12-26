import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.delay

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
    var showErrorSnackbar by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(connectionState) {
        when (connectionState) {
            is WifiViewModel.ConnectionState.Failed -> {
                errorMessage = (connectionState as WifiViewModel.ConnectionState.Failed).error
                snackbarHostState.showSnackbar(errorMessage, actionLabel = "Dismiss")
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(16.dp)
            ) { snackbarData ->
                Snackbar(
                    action = {
                        snackbarData.visuals.actionLabel?.let { actionLabel ->
                            TextButton(onClick = { snackbarData.dismiss() }) {
                                Text(actionLabel)
                            }
                        }
                    },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(snackbarData.visuals.message)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Button(
                onClick = {
                    if (!wifiViewModel.isWifiEnabled()) {
                        wifiViewModel.enableWifi()
                    }
                    wifiViewModel.startScan()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan WiFi Networks")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Connection Status
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
                    password = ""
                },
                title = { Text("Connect to ${selectedNetwork?.ssid}") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (password.length < 5) {
                            Text(
                                "Password must be at least 5 characters",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
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
            } else {
                Button(onClick = onClick) {
                    Text("Connect")
                }
            }
        }
    }
}
