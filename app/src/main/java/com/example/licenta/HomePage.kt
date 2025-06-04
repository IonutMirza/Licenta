package com.example.licenta

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.licenta.model.Car
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

@SuppressLint("MissingPermission", "ContextCastToActivity")
@Composable
fun HomePage(
    onLogout: () -> Unit,
    onNavigateToMapPage: () -> Unit,
    onNavigateToSwitchPage: () -> Unit,
    onNavigateToWalletPage: () -> Unit,
    selectedCar: Car?,
    onCarSelected: (Car) -> Unit
) {
    val context = LocalContext.current
    val activity = (LocalContext.current as? Activity)
    val user = FirebaseAuth.getInstance().currentUser
    val username = user?.email?.substringBefore("@") ?: "User"
    val firestore = FirebaseFirestore.getInstance()
    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    // — Firestore “cars” list states —
    var cars by remember { mutableStateOf(emptyList<Car>()) }
    var showAddCarDialog by remember { mutableStateOf(false) }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var licensePlate by remember { mutableStateOf("") }

    // — OBD/Bluetooth states —
    var bluetoothStatus by remember { mutableStateOf("Disconnected") }
    var connectedDeviceName by remember { mutableStateOf<String?>(null) }
    var isObdConnected by remember { mutableStateOf(false) }

    // Hold the actual BluetoothSocket / streams once connected:
    var obdSocket by remember { mutableStateOf<BluetoothSocket?>(null) }
    var obdOutStream by remember { mutableStateOf<OutputStream?>(null) }
    var obdInStream by remember { mutableStateOf<InputStream?>(null) }

    // — Dialog-control states for real OBD data —
    var showObdSpecsDialog by remember { mutableStateOf(false) }
    var obdSpecsRaw by remember { mutableStateOf<String?>(null) }

    var showObdLiveDataDialog by remember { mutableStateOf(false) }
    // We'll fill this with a map of “Parameter name” → “value”
    var liveDataMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // — Permission launcher for BLUETOOTH_CONNECT, BLUETOOTH_SCAN, ACCESS_FINE_LOCATION —
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // If all requested perms granted, attempt actual OBD connection
            attemptObdConnection(
                context = context,
                bluetoothAdapter = bluetoothAdapter,
                onConnectionResult = { name, success, socket, outSt, inSt ->
                    connectedDeviceName = name
                    isObdConnected = success
                    obdSocket = socket
                    obdOutStream = outSt
                    obdInStream = inSt
                    bluetoothStatus = if (success) "Connected" else "Failed"
                }
            )
        } else {
            Toast.makeText(context, "Bluetooth permissions denied", Toast.LENGTH_SHORT).show()
        }
    }

    // — Firestore: load “cars” once user.uid changes —
    LaunchedEffect(user?.uid) {
        user?.uid?.let { uid ->
            firestore.collection("cars1")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener { result ->
                    cars = result.map { it.toObject(Car::class.java) }
                    if (selectedCar == null && cars.isNotEmpty()) {
                        onCarSelected(cars.first())
                    }
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // — Title & Welcome text —
        Text("Drive like a Pro", fontSize = 32.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("Welcome, $username!", fontSize = 24.sp)
        Spacer(Modifier.height(16.dp))

        // — Bluetooth status row —
        Text(
            text = "Bluetooth Status: $bluetoothStatus" + (connectedDeviceName?.let { " - $it" } ?: ""),
            color = if (isObdConnected) Color.Green else Color.Red,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(16.dp))

        // — Selected Car display —
        selectedCar?.let {
            Text(
                text = "Selected Car: ${it.brand} ${it.model}",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(16.dp))
        }

        // — List of cars (LazyColumn) —
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(cars) { car ->
                Text(
                    text = "${car.brand} ${car.model}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCarSelected(car) }
                        .background(if (car == selectedCar) Color.LightGray else Color.Transparent)
                        .padding(8.dp),
                    fontSize = 16.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // — “Add New Car” button —
        Button(
            onClick = { showAddCarDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add New Car")
        }
        Spacer(Modifier.height(8.dp))

        // — “Connect to OBD” actual button —
        Button(
            onClick = {
                // Request the three permissions in one shot:
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Connect to OBD")
        }

        if (isObdConnected) {
            Spacer(Modifier.height(8.dp))
            // — Real “View Data Specifications” button —
            Button(
                onClick = {
                    liveDataMap = emptyMap() // clear any previous
                    showObdSpecsDialog = true
                    // Actual code to read PID 0100 will run in the dialog’s LaunchedEffect
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text("View Data Specifications", color = Color.White)
            }

            Spacer(Modifier.height(8.dp))
            // — Real “Read Live Data” button —
            Button(
                onClick = {
                    liveDataMap = emptyMap()
                    showObdLiveDataDialog = true
                    // Actual code to read live PIDs will run in the dialog’s LaunchedEffect
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
            ) {
                Text("Read Live Data", color = Color.White)
            }
        }

        Spacer(Modifier.height(8.dp))
        // — Logout button —
        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Log Out", color = Color.White)
        }

        Spacer(Modifier.height(16.dp))
        // — Bottom navigation bar (unchanged) —
        BottomNavigationBar(
            onMapClick = onNavigateToMapPage,
            onSwitchClick = onNavigateToSwitchPage,
            onWalletClick = onNavigateToWalletPage
        )
    }

    // — “Add Car” dialog (unchanged) —
    AddCarDialog(
        show = showAddCarDialog,
        brand = brand,
        model = model,
        year = year,
        licensePlate = licensePlate,
        onDismiss = { showAddCarDialog = false },
        onConfirm = { b, m, y, lp ->
            val uid = user?.uid ?: return@AddCarDialog
            val newCar = Car(b, m, y, lp, uid)
            firestore.collection("cars1").add(newCar).addOnSuccessListener {
                showAddCarDialog = false
                brand = ""; model = ""; year = ""; licensePlate = ""
                // reload list
                firestore.collection("cars1")
                    .whereEqualTo("userId", uid)
                    .get()
                    .addOnSuccessListener { result ->
                        cars = result.map { it.toObject(Car::class.java) }
                        if (selectedCar == null && cars.isNotEmpty()) {
                            onCarSelected(cars.first())
                        }
                    }
            }
        }
    )

    // — Dialog: “Data Specifications” (PID 0100) —
    if (showObdSpecsDialog) {
        ObdSpecsDialog(
            obdInStream = obdInStream,
            obdOutStream = obdOutStream,
            onDismiss = { showObdSpecsDialog = false },
            rawResult = obdSpecsRaw
        ) { raw ->
            obdSpecsRaw = raw
        }
    }

    // — Dialog: “Live Data” (read RPM, Speed, Temp, Throttle) —
    if (showObdLiveDataDialog) {
        ObdLiveDataDialog(
            obdInStream = obdInStream,
            obdOutStream = obdOutStream,
            onDismiss = { showObdLiveDataDialog = false },
            liveData = liveDataMap
        ) { dataMap ->
            liveDataMap = dataMap
        }
    }
}


// ————————————————————————————————————————————————
// Helper function to attempt real OBD connection once perms granted:

@SuppressLint("MissingPermission")
private fun attemptObdConnection(
    context: android.content.Context,
    bluetoothAdapter: BluetoothAdapter?,
    onConnectionResult: (
        deviceName: String?,
        success: Boolean,
        socket: BluetoothSocket?,
        outSt: OutputStream?,
        inSt: InputStream?
    ) -> Unit
) {
    if (bluetoothAdapter == null) {
        Toast.makeText(context, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
        onConnectionResult(null, false, null, null, null)
        return
    }
    if (!bluetoothAdapter.isEnabled) {
        Toast.makeText(context, "Bluetooth not enabled", Toast.LENGTH_SHORT).show()
        onConnectionResult(null, false, null, null, null)
        return
    }

    // Find the first paired device whose name contains “OBD”
    val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
    val obdDevice = pairedDevices.firstOrNull { it.name.contains("OBD", ignoreCase = true) }

    if (obdDevice == null) {
        Toast.makeText(context, "No OBD device found among paired devices", Toast.LENGTH_SHORT).show()
        onConnectionResult(null, false, null, null, null)
        return
    }

    // Everything looks good → attempt socket connection on a background thread
    Thread {
        try {
            // Standard SPP UUID
            val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            val socket = obdDevice.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothAdapter.cancelDiscovery()
            socket.connect()

            val outSt = socket.outputStream
            val inSt = socket.inputStream

            // Notify success on UI thread
            (context as? Activity)?.runOnUiThread {
                onConnectionResult(obdDevice.name, true, socket, outSt, inSt)
                Toast.makeText(context, "Connected to ${obdDevice.name}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            (context as? Activity)?.runOnUiThread {
                Toast.makeText(context, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                onConnectionResult(obdDevice.name, false, null, null, null)
            }
        }
    }.start()
}


// ————————————————————————————————————————————————
// Dialog for PID 0100 → read supported PIDs (raw hex) and display:

@Composable
private fun ObdSpecsDialog(
    obdInStream: InputStream?,
    obdOutStream: OutputStream?,
    onDismiss: () -> Unit,
    rawResult: String?,
    onRawResultReady: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var localRaw by remember { mutableStateOf(rawResult) }

    // When dialog appears, if rawResult is null, we launch a coroutine to send “0100” and read:
    LaunchedEffect(Unit) {
        if (localRaw == null && obdInStream != null && obdOutStream != null) {
            withContext(Dispatchers.IO) {
                try {
                    // Send “0100\r”
                    obdOutStream.write("0100\r".toByteArray())
                    delay(200) // short delay to allow response
                    val buffer = ByteArray(128)
                    val bytesRead = obdInStream.read(buffer)
                    val response = String(buffer, 0, bytesRead).trim()
                    // We expect something like “4100BE1FA813” (for example)
                    onRawResultReady(response)
                    localRaw = response
                } catch (e: IOException) {
                    onRawResultReady("Error: ${e.message}")
                    localRaw = "Error: ${e.message}"
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("OBD Supported PIDs (0100)") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (localRaw == null) {
                    // still loading
                    Text("Loading…", fontSize = 16.sp)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                } else {
                    Text("Raw response: $localRaw", fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Interpreting bits → Each hex‐pair’s bits = supported PIDs.\n" +
                                "(See OBD-II spec for full bit‐mapping.)",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}


// ————————————————————————————————————————————————
// Dialog for “Live Data”: read 010D (speed), 010C (RPM), 0105 (temp), 0111 (throttle)

@Composable
private fun ObdLiveDataDialog(
    obdInStream: InputStream?,
    obdOutStream: OutputStream?,
    onDismiss: () -> Unit,
    liveData: Map<String, String>,
    onLiveDataReady: (Map<String, String>) -> Unit
) {
    val scope = rememberCoroutineScope()
    var localData by remember { mutableStateOf(liveData) }

    // When dialog appears, if localData is empty and streams exist, read all four PIDs:
    LaunchedEffect(Unit) {
        if (localData.isEmpty() && obdInStream != null && obdOutStream != null) {
            withContext(Dispatchers.IO) {
                val results = mutableMapOf<String, String>()

                // Helper to send a PID and read response
                fun readObdPid(pid: String): String {
                    return try {
                        obdOutStream.write("$pid\r".toByteArray())
                        Thread.sleep(200) // wait for response
                        val buffer = ByteArray(128)
                        val bytesRead = obdInStream.read(buffer)
                        String(buffer, 0, bytesRead).trim()
                    } catch (e: IOException) {
                        "Err"
                    }
                }

                // 1) Vehicle speed (010D)
                val raw010d = readObdPid("010D")
                val speed =
                    raw010d.takeIf { it.startsWith("41 0D".replace(" ", "")) }?.let {
                        // remove any whitespace, then parse the 3rd byte as hex
                        val hex = it.removeSpaces().substring(4, 6)
                        hex.toIntOrNull(16)?.toString() + " km/h"
                    } ?: "N/A"
                results["Speed"] = speed

                // 2) RPM (010C)
                val raw010c = readObdPid("010C")
                val rpm = raw010c.takeIf { it.startsWith("410C", ignoreCase = true) }?.let {
                    // “410C A B” → ((A*256)+B)/4
                    val noSpaces = it.removeSpaces()
                    // “410C1AF8” → A = “1A”, B = “F8”
                    if (noSpaces.length >= 8) {
                        val a = noSpaces.substring(4, 6).toIntOrNull(16) ?: 0
                        val b = noSpaces.substring(6, 8).toIntOrNull(16) ?: 0
                        ((a * 256 + b) / 4).toString() + " rpm"
                    } else "N/A"
                } ?: "N/A"
                results["RPM"] = rpm

                // 3) Engine coolant temp (0105): raw “4105 XX” → temp = XX – 40
                val raw0105 = readObdPid("0105")
                val temp = raw0105.takeIf { it.startsWith("4105", ignoreCase = true) }?.let {
                    val noSpaces = it.removeSpaces()
                    if (noSpaces.length >= 6) {
                        val x = noSpaces.substring(4, 6).toIntOrNull(16) ?: 0
                        (x - 40).toString() + " °C"
                    } else "N/A"
                } ?: "N/A"
                results["Engine Temp"] = temp

                // 4) Throttle position (0111): raw “4111 XX” → (XX*100)/255 %
                val raw0111 = readObdPid("0111")
                val throttle = raw0111.takeIf { it.startsWith("4111", ignoreCase = true) }?.let {
                    val noSpaces = it.removeSpaces()
                    if (noSpaces.length >= 6) {
                        val x = noSpaces.substring(4, 6).toIntOrNull(16) ?: 0
                        ((x * 100) / 255).toString() + " %"
                    } else "N/A"
                } ?: "N/A"
                results["Throttle"] = throttle

                onLiveDataReady(results)
                localData = results
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Live OBD Data") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (localData.isEmpty()) {
                    Text("Reading live data…", fontSize = 16.sp)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                } else {
                    localData.forEach { (key, value) ->
                        Text("$key: $value", fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}


/** Extension to strip all whitespace from a hex string. */
private fun String.removeSpaces(): String = this.replace("\\s".toRegex(), "")


// ————————————————————————————————————————————————
// “AddCarDialog” and “BottomNavigationBar” are unchanged from before.

@Composable
fun AddCarDialog(
    show: Boolean,
    brand: String,
    model: String,
    year: String,
    licensePlate: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var b by remember { mutableStateOf(brand) }
    var m by remember { mutableStateOf(model) }
    var y by remember { mutableStateOf(year) }
    var lp by remember { mutableStateOf(licensePlate) }

    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add New Car") },
            text = {
                Column {
                    OutlinedTextField(
                        value = b,
                        onValueChange = { b = it },
                        label = { Text("Brand") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = m,
                        onValueChange = { m = it },
                        label = { Text("Model") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = y,
                        onValueChange = { y = it },
                        label = { Text("Year") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = lp,
                        onValueChange = { lp = it },
                        label = { Text("License Plate") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(b, m, y, lp) }) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun BottomNavigationBar(
    onMapClick: () -> Unit,
    onSwitchClick: () -> Unit,
    onWalletClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = { /* Home does nothing—already here */ }, modifier = Modifier.size(50.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.home),
                    contentDescription = "Home",
                    tint = Color.Black,
                    modifier = Modifier.fillMaxSize()
                )
            }
            IconButton(onClick = onMapClick, modifier = Modifier.size(50.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.map),
                    contentDescription = "Map",
                    tint = Color.LightGray
                )
            }
            IconButton(onClick = onSwitchClick, modifier = Modifier.size(50.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.garage),
                    contentDescription = "Garage",
                    tint = Color.LightGray
                )
            }
            IconButton(onClick = onWalletClick, modifier = Modifier.size(50.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.settings),
                    contentDescription = "Settings",
                    tint = Color.LightGray
                )
            }
        }
    }
}
