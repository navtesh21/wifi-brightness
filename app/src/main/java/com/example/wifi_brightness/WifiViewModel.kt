import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.BassBoost
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Data class to represent Wi-Fi state
data class WifiState(
    val ssid: String,
    val level: Int,
    val isConnected: Boolean = false
)

class WifiViewModel(private val context: Context) : ViewModel() {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _wifiNetworks = MutableStateFlow<List<WifiState>>(emptyList())
    val wifiNetworks: StateFlow<List<WifiState>> = _wifiNetworks

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val activity = context as? ComponentActivity

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.CHANGE_NETWORK_STATE
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.CHANGE_NETWORK_STATE
            )
        }
    }

    sealed class ConnectionState {
        object Idle : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val ssid: String) : ConnectionState()
        data class Failed(val error: String) : ConnectionState()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun requestPermissions() {
        activity?.let {
            ActivityCompat.requestPermissions(
                it,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun startScan() {
        if (checkPermissions()) {
            performWifiScan()
        } else {
            requestPermissions()
        }
    }

    private fun performWifiScan() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val scanResults = wifiManager.scanResults
                val currentSsid = getCurrentSsid()

                val wifiList = scanResults.map { result ->
                    WifiState(
                        ssid = result.SSID,
                        level = WifiManager.calculateSignalLevel(result.level, 5),
                        isConnected = result.SSID == currentSsid
                    )
                }
                _wifiNetworks.value = wifiList
            } catch (e: SecurityException) {
                Log.e("WifiScan", "Permission not granted for Wi-Fi scan", e)
                _connectionState.value = ConnectionState.Failed("Permission denied for Wi-Fi scan")
            }
        }
    }

    private fun getCurrentSsid(): String? {
        return try {
            val connectionInfo = wifiManager.connectionInfo
            connectionInfo?.ssid?.replace("\"", "")
        } catch (e: SecurityException) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun connectToNetwork(ssid: String, password: String) {
        if (!checkPermissions()) {
            _connectionState.value = ConnectionState.Failed(
                "Required permissions not granted. Please grant Wi-Fi and location permissions."
            )
            requestPermissions()
            return
        }

        _connectionState.value = ConnectionState.Connecting

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectAndroid10Plus(ssid, password)
        } else {
            connectLegacy(ssid, password)
        }
    }

    private fun connectAndroid10Plus(ssid: String, password: String) {
        try {
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .setNetworkSpecifier(specifier)
                .build()

            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                @RequiresApi(Build.VERSION_CODES.TIRAMISU)
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    connectivityManager.bindProcessToNetwork(network)
                    _connectionState.value = ConnectionState.Connected(ssid)
                    startScan()
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    _connectionState.value = ConnectionState.Failed("Failed to connect to $ssid. Please check the password and try again.")
                }
            }

            connectivityManager.requestNetwork(request, networkCallback)
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Failed("Error connecting to network: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun connectLegacy(ssid: String, password: String) {
        try {
            @Suppress("DEPRECATION")
            val wifiConfig = android.net.wifi.WifiConfiguration().apply {
                SSID = "\"$ssid\""
                preSharedKey = "\"$password\""
            }

            val netId = wifiManager.addNetwork(wifiConfig)
            wifiManager.disconnect()
            val success = wifiManager.enableNetwork(netId, true) && wifiManager.reconnect()

            if (success) {
                _connectionState.value = ConnectionState.Connected(ssid)
                startScan()
            } else {
                _connectionState.value = ConnectionState.Failed("Failed to connect to $ssid")
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Failed("Error connecting to network: ${e.message}")
        }
    }
    fun isWifiEnabled(): Boolean {
        return wifiManager.isWifiEnabled
    }

    fun enableWifi(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
            context.startActivity(panelIntent)
        } else {
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = true
        }
    }
}
