package com.example.licenta

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class ObdConnectActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "OBD"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var mmSocket: BluetoothSocket? = null
    private var mmOutStream: OutputStream? = null
    private var mmInStream: InputStream? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Inițializează BluetoothAdapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            // Dacă nu există Bluetooth pe dispozitiv, putem să închidem activitatea
            finish()
            return
        }

        // 2. UI Compose
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ObdConnectScreen(
                        bluetoothAdapter = bluetoothAdapter!!,
                        onConnect = { device, onStatusUpdate ->
                            connectToDevice(device, onStatusUpdate)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Închide stream-urile și socket-ul
        try {
            mmInStream?.close()
            mmOutStream?.close()
            mmSocket?.close()
        } catch (e: IOException) {
            // ignorăm
        }
    }

    /**
     * Funcție care face conexiunea Bluetooth + OBD pe un thread de fundal.
     * Parametrul onStatusUpdate este o lambda care actualizează text-ul de status din UI Compose.
     */
    @SuppressLint("MissingPermission")
    private fun connectToDevice(
        device: BluetoothDevice,
        onStatusUpdate: (String) -> Unit
    ) {
        onStatusUpdate("Conectare la ${device.name}...")
        // Folosim CoroutineScope (Main) pentru a lansa în IO
        val scope = lifecycleScope
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    mmSocket?.close()
                    mmSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    bluetoothAdapter?.cancelDiscovery()
                    mmSocket?.connect()

                    mmOutStream = mmSocket?.outputStream
                    mmInStream = mmSocket?.inputStream

                    // Trimitem comanda OBD 010C pentru RPM
                    val rpmCommand = "010C\r"
                    mmOutStream?.write(rpmCommand.toByteArray())

                    // Citim răspunsul
                    val buffer = ByteArray(1024)
                    val bytesRead = mmInStream?.read(buffer) ?: 0
                    val rawResponse = String(buffer, 0, bytesRead).trim()

                    handler.post {
                        onStatusUpdate("Conectat la ${device.name}\nRăspuns OBD: $rawResponse")
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Eroare la conectare OBD: ${e.message}")
                    handler.post {
                        onStatusUpdate("Eroare la conectare: ${e.message}")
                    }
                }
            }
        }
    }
}

/**
 * Composable-ul principal pentru ecranul de conectare OBD.
 * - Afișează un buton pentru a lista dispozitivele împerecheate
 * - Afișează lista într-un LazyColumn
 * - Când apeși pe un item, apelează onConnect(device, onStatusUpdate)
 * - Afișează statusul curent într-un Text
 */
@Composable
fun ObdConnectScreen(
    bluetoothAdapter: BluetoothAdapter,
    onConnect: (BluetoothDevice, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current

    // Ținem starea listei de dispozitive și a statusului
    var deviceList by remember { mutableStateOf(listOf<BluetoothDevice>()) }
    var statusText by remember { mutableStateOf("Apasă butonul de mai sus") }

    // Pentru permisiunea LOCATION necesară la scanarea Bluetooth
    val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION
    val permissionGranted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, locationPermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            permissionGranted.value = granted
            if (granted) {
                // După ce e permisia acordată, populăm lista de device-uri împerecheate
                val paired = bluetoothAdapter.bondedDevices.toList()
                deviceList = paired
                if (paired.isEmpty()) {
                    statusText = "Nu există dispozitive Bluetooth împerecheate."
                } else {
                    statusText = "Selectează un dispozitiv din listă."
                }
            } else {
                statusText = "Permisiune LOCATION refuzată."
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Buton care cere permisiunea și listează dispozitivele împerecheate
        Button(
            onClick = {
                if (!permissionGranted.value) {
                    launcher.launch(locationPermission)
                } else {
                    val paired = bluetoothAdapter.bondedDevices.toList()
                    deviceList = paired
                    statusText = if (paired.isEmpty()) {
                        "Nu există dispozitive Bluetooth împerecheate."
                    } else {
                        "Selectează un dispozitiv din listă."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Afișează dispozitive împerecheate")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // LazyColumn pentru lista de device-uri Bluetooth
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            itemsIndexed(deviceList) { index, device ->
                DeviceItem(
                    name = device.name ?: "Nume necunoscut",
                    address = device.address,
                    onClick = {
                        onConnect(device) { newStatus ->
                            statusText = newStatus
                        }
                    }
                )
                Divider(color = Color.Gray, thickness = 0.5.dp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Textarea pentru statusul conexiunii / răspunsul OBD
        Text(
            text = statusText,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp)
                .background(Color(0xFFEFEFEF))
                .padding(8.dp),
            textAlign = TextAlign.Start
        )
    }
}

@Composable
fun DeviceItem(name: String, address: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = name, style = MaterialTheme.typography.bodyLarge)
            Text(text = address, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
