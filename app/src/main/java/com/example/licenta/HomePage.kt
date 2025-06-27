package com.example.licenta

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.licenta.model.Car
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
    onNavigateToGaragePage: () -> Unit,
    onNavigateToSettingsPage: () -> Unit,
    selectedCar: Car?,
    onCarSelected: (Car) -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var darkModeEnabled by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }

    LaunchedEffect(Unit) {
        snapshotFlow { prefs.getBoolean("dark_mode", false) }
            .collect { darkModeEnabled = it }
    }

    val backgroundColor = if (darkModeEnabled) Color(0xFF121212) else Color.White
    val textColor       = if (darkModeEnabled) Color.White       else Color.Black

    val user = FirebaseAuth.getInstance().currentUser
    val username = user?.email?.substringBefore("@") ?: "User"
    val firestore = FirebaseFirestore.getInstance()
    var cars by remember { mutableStateOf(emptyList<Car>()) }

    var showAddCarDialog by remember { mutableStateOf(false) }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var licensePlate by remember { mutableStateOf("") }

    var bluetoothStatus by remember { mutableStateOf("Disconnected") }
    var connectedDeviceName by remember { mutableStateOf<String?>(null) }
    var isObdConnected by remember { mutableStateOf(false) }
    var obdSocket by remember { mutableStateOf<BluetoothSocket?>(null) }
    var obdOutStream by remember { mutableStateOf<OutputStream?>(null) }
    var obdInStream by remember { mutableStateOf<InputStream?>(null) }
    var showObdSpecsDialog by remember { mutableStateOf(false) }
    var obdSpecsRaw by remember { mutableStateOf<String?>(null) }
    var showObdLiveDataDialog by remember { mutableStateOf(false) }
    var liveDataMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    var showObdErrorDialog by remember { mutableStateOf(false) }
    var errorCodes by remember { mutableStateOf<List<String>>(emptyList()) }

    var isEcuReady by remember { mutableStateOf(false) }
    var ecuStatus by remember { mutableStateOf("ECU: Not initialized") }
    var showObdClearConfirmDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
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

                    if (success && inSt != null && outSt != null) {
                        initElm(inSt, outSt) { ecuReady ->
                            isEcuReady = ecuReady
                            ecuStatus = if (ecuReady) "ECU: Ready" else "ECU: Init Fail"
                        }
                    }
                }
            )
        } else {
            Toast.makeText(context, "Permisiunile Bluetooth au fost refuzate", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(user?.uid) {
        val listenerRegistration = user?.uid?.let { uid ->
            firestore.collection("cars1")
                .whereEqualTo("userId", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Toast.makeText(context, "Eroare la încărcarea mașinilor: ${error.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val loaded = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(Car::class.java)
                        }
                        cars = loaded
                        if (selectedCar == null && cars.isNotEmpty()) {
                            onCarSelected(cars.first())
                        }
                    }
                }
        }
        onDispose { listenerRegistration?.remove() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Drive like a Pro", fontSize = 32.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("Bine ai venit, $username!", fontSize = 24.sp, color = textColor)
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Bluetooth Status: $bluetoothStatus" + (connectedDeviceName?.let { " - $it" } ?: ""),
            color = if (isObdConnected) Color.Green else Color.Red,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(16.dp))

        Text(
            text = ecuStatus,
            color = when {
                isEcuReady -> Color.Green
                isObdConnected -> Color(0xFFFFA000)
                else -> Color.Red
            },
            fontSize = 16.sp
        )
        Spacer(Modifier.height(16.dp))

        selectedCar?.let {
            Text(
                text = "Mașina selectată: ${it.brand} ${it.model}",
                fontSize = 18.sp,
                color = textColor
            )
            Spacer(Modifier.height(16.dp))
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(cars) { car ->
                Text(
                    text = "${car.brand} ${car.model}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCarSelected(car) }
                        .background(if (car == selectedCar) Color.LightGray else Color.Transparent)
                        .padding(8.dp),
                    fontSize = 16.sp,
                    color = textColor
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { showAddCarDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Adaugă o nouă mașină", color = textColor)
        }
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
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
            Text("Conecteză-te la OBD", color = textColor)
        }

        if (isObdConnected && isEcuReady) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    liveDataMap = emptyMap()
                    showObdSpecsDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text("Vezi specificațiile", color = textColor)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    liveDataMap = emptyMap()
                    showObdLiveDataDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
            ) {
                Text("Citește date live", color = textColor)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    errorCodes = emptyList()
                    showObdErrorDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E24AA))
            ) {
                Text("Citește Codurile de Eroare", color = textColor)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    showObdClearConfirmDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
            ) {
                Text("Șterge Codurile de Eroare", color = textColor)
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Deconectare", color = textColor)
        }

        Spacer(Modifier.height(16.dp))
        Spacer(Modifier.height(16.dp))
        BottomNavigationBar(
            onMapClick = onNavigateToMapPage,
            onGarageClick = onNavigateToGaragePage,
            onSettingsClick = onNavigateToSettingsPage,
            textColor = textColor
        )
    }

    AddCarDialog(
        show = showAddCarDialog,
        brand = brand,
        model = model,
        year = year,
        licensePlate = licensePlate,
        onDismiss = { showAddCarDialog = false },
        onConfirm = { b, m, y, lp ->
            val uid = user?.uid ?: return@AddCarDialog
            val yearInt = y.toIntOrNull()
            if (b.isBlank() || m.isBlank() || yearInt == null || lp.isBlank()) {
                Toast.makeText(context, "Completează corect toate câmpurile", Toast.LENGTH_SHORT).show()
                return@AddCarDialog
            }
            firestore.collection("cars1")
                .whereEqualTo("userId", uid)
                .whereEqualTo("licensePlate", lp.trim())
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (querySnapshot.documents.isNotEmpty()) {
                        Toast.makeText(context, "Această mașină există deja", Toast.LENGTH_SHORT).show()
                    } else {
                        val docRef = firestore.collection("cars1").document()
                        val car = Car(
                            id = docRef.id,
                            brand = b.trim(),
                            model = m.trim(),
                            year = yearInt,
                            licensePlate = lp.trim(),
                            userId = uid
                        )
                        docRef.set(car)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Mașina s-a salvat cu succces", Toast.LENGTH_SHORT).show()
                                showAddCarDialog = false
                                brand = ""; model = ""; year = ""; licensePlate = ""
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Eroare la salvare: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Eroare la verificarea duplicatului: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        },

    )

    if (showObdClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showObdClearConfirmDialog = false },
            title = { Text("Coduri șterse cu succes!") },
            text = { Text("Ești sigur că vrei să ștergi toate codurile?") },
            confirmButton = {
                TextButton(onClick = {
                    showObdClearConfirmDialog = false
                    scope.launch {
                        val resp = sendElmCommand("04", obdInStream, obdOutStream)
                        Toast.makeText(
                            context,
                            if (resp.contains("44")) "Coduri șterse cu succes" else "Eroare la ștergerea codurilor",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }) {
                    Text("Da")
                }
            },
            dismissButton = {
                TextButton(onClick = { showObdClearConfirmDialog = false }) {
                    Text("Nu")
                }
            }
        )
    }

    if (showObdSpecsDialog) {
        ObdSpecsDialog(
            obdInStream = obdInStream,
            obdOutStream = obdOutStream,
            onDismiss = { showObdSpecsDialog = false },
            rawResult = obdSpecsRaw
        ) { raw -> obdSpecsRaw = raw }
    }

    if (showObdLiveDataDialog) {
        ObdLiveDataDialog(
            obdInStream = obdInStream,
            obdOutStream = obdOutStream,
            onDismiss = { showObdLiveDataDialog = false },
            liveData = liveDataMap,
        ) { dataMap -> liveDataMap = dataMap }
    }

    if (showObdErrorDialog) {
        ObdErrorCodesDialog(
            obdInStream = obdInStream,
            obdOutStream = obdOutStream,
            onDismiss = { showObdErrorDialog = false },
            errorCodes = errorCodes
        ) { codes -> errorCodes = codes }
    }
}

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
        Toast.makeText(context, "Bluetooth-ul nu este suportat", Toast.LENGTH_SHORT).show()
        onConnectionResult(null, false, null, null, null)
        return
    }
    if (!bluetoothAdapter.isEnabled) {
        Toast.makeText(context, "Bluetooth-ul nu este disponibil", Toast.LENGTH_SHORT).show()
        onConnectionResult(null, false, null, null, null)
        return
    }

    val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
    val obdDevice = pairedDevices.firstOrNull { it.name?.contains("OBD", ignoreCase = true) == true }

    if (obdDevice == null) {
        Toast.makeText(context, "Niciun dispozitiv OBD găsit", Toast.LENGTH_SHORT).show()
        onConnectionResult(null, false, null, null, null)
        return
    }

    Thread {
        try {
            val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            val socket = obdDevice.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothAdapter.cancelDiscovery()
            socket.connect()

            val outSt = socket.outputStream
            val inSt = socket.inputStream
            (context as? Activity)?.runOnUiThread {
                onConnectionResult(obdDevice.name, true, socket, outSt, inSt)
                Toast.makeText(context, "Conectat la ${obdDevice.name}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            (context as? Activity)?.runOnUiThread {
                Toast.makeText(context, "Eroare de conexiune: ${e.message}", Toast.LENGTH_LONG).show()
                onConnectionResult(obdDevice.name, false, null, null, null)
            }
        }
    }.start()
}


private fun initElm(inStream: InputStream, outStream: OutputStream, onResult: (Boolean) -> Unit) {
    Thread {
        val initCommands = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")
        try {
            for (cmd in initCommands) {
                sendElm(cmd, outStream, inStream)
                Thread.sleep(150)
            }
            val supported = sendElm("0100", outStream, inStream)
            onResult(supported.contains("41 00") || supported.contains("4100"))
        } catch (e: Exception) {
            onResult(false)
        }
    }.start()
}

private fun sendElm(cmd: String, out: OutputStream, `in`: InputStream): String {
    out.write((cmd.trim() + "\r").toByteArray())
    out.flush()
    val response = StringBuilder()
    val buffer = ByteArray(1024)
    var timeout = 0L
    while (true) {
        if (`in`.available() > 0) {
            val len = `in`.read(buffer)
            response.append(String(buffer, 0, len))
            if (response.contains(">")) break
        } else {
            Thread.sleep(50)
            timeout += 50
            if (timeout > 3000) break
        }
    }
    return response.toString().replace("\r", "").replace("\n", "").trim()
}

suspend fun sendElmCommand(
    command: String,
    inStream: InputStream?,
    outStream: OutputStream?,
    timeoutMs: Long = 1200L
): String = withContext(Dispatchers.IO) {
    if (inStream == null || outStream == null) return@withContext "STREAMS_NOT_READY"

    try {
        outStream.write((command.trim() + "\r").toByteArray())
        outStream.flush()

        val start = System.currentTimeMillis()
        val buffer = ByteArray(256)
        val sb = StringBuilder()

        while (System.currentTimeMillis() - start < timeoutMs) {
            if (inStream.available() > 0) {
                val len = inStream.read(buffer)
                sb.append(String(buffer, 0, len))
                if (sb.contains(">")) break
            }
            delay(30)
        }

        return@withContext sb.toString()
            .replace("\r", "")
            .replace("\n", "")
            .replace(">", "")
            .trim()
    } catch (e: IOException) {
        return@withContext "ERR:${e.message}"
    }
}

@Composable
private fun ObdSpecsDialog(
    obdInStream: InputStream?,
    obdOutStream: OutputStream?,
    onDismiss: () -> Unit,
    rawResult: String?,
    onRawResultReady: (String) -> Unit
) {
    var localRaw by remember { mutableStateOf(rawResult) }

    LaunchedEffect(Unit) {
        if (localRaw == null) {
            val resp = sendElmCommand("0100", obdInStream, obdOutStream)
            onRawResultReady(resp)
            localRaw = resp
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("OBD suportat PIDs (0100)") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (localRaw == null) {
                    Text("Se încarcă…", fontSize = 16.sp)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                } else {
                    Text("Raw răspuns: $localRaw", fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Interpretarea biților → Fiecare pereche hexadecimală reprezintă biți = PIDs suportate.\\n(Vezi specificația OBD-II pentru maparea completă a biților.))",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Închide")
            }
        }
    )
}

@Composable
private fun ObdLiveDataDialog(
    obdInStream: InputStream?,
    obdOutStream: OutputStream?,
    onDismiss: () -> Unit,
    liveData: Map<String, String>,
    onLiveDataReady: (Map<String, String>) -> Unit
) {
    var localData by remember { mutableStateOf(liveData) }

    LaunchedEffect(Unit) {
        if (localData.isEmpty() && obdInStream != null && obdOutStream != null) {
            withContext(Dispatchers.IO) {
                val results = mutableMapOf<String, String>()

                suspend fun pid(p: String) = sendElmCommand(p, obdInStream, obdOutStream)

                val speedRaw = pid("010D")
                results["Viteză"] = speedRaw.takeIf { it.startsWith("410D", true) }?.let {
                    it.replace(" ", "").substring(4, 6).toInt(16).toString() + " km/h"
                } ?: "N/A"

                val rpmRaw = pid("010C")
                results["RPM"] = rpmRaw.takeIf { it.startsWith("410C", true) }?.let {
                    val n = it.replace(" ", "")
                    val a = n.substring(4, 6).toInt(16)
                    val b = n.substring(6, 8).toInt(16)
                    ((a * 256 + b) / 4).toString() + " rpm"
                } ?: "N/A"

                val tempRaw = pid("0105")
                results["Twmperatură motor"] = tempRaw.takeIf { it.startsWith("4105", true) }?.let {
                    (it.replace(" ", "").substring(4, 6).toInt(16) - 40).toString() + " °C"
                } ?: "N/A"

                val throttleRaw = pid("0111")
                results["Clapetă accelerație"] = throttleRaw.takeIf { it.startsWith("4111", true) }?.let {
                    (it.replace(" ", "").substring(4, 6).toInt(16) * 100 / 255).toString() + " %"
                } ?: "N/A"

                onLiveDataReady(results)
                localData = results
            }

        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Citește date live de la OBD") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (localData.isEmpty()) {
                    Text("Se încarcă…", fontSize = 16.sp)
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
                Text("Închide")
            }
        }
    )
}

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
            title = { Text("Adaugă o mașină nouă") },
            text = {
                Column {
                    OutlinedTextField(
                        value = b,
                        onValueChange = { b = it },
                        label = { Text("Marcă") },
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
                        label = { Text("Anul fabricației") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = lp,
                        onValueChange = { lp = it },
                        label = { Text("Numărul de înmatriculare") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(b, m, y, lp) }) {
                    Text("Gata")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Închide")
                }
            }
        )
    }
}

@Composable
fun BottomNavigationBar(
    onMapClick: () -> Unit,
    onGarageClick: () -> Unit,
    onSettingsClick: () -> Unit,
    textColor: Color
)
{
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = { }, modifier = Modifier.size(50.dp)) {
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
            IconButton(onClick = onGarageClick, modifier = Modifier.size(50.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.garage),
                    contentDescription = "Garage",
                    tint = Color.LightGray
                )
            }
            IconButton(onClick = onSettingsClick, modifier = Modifier.size(50.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.settings),
                    contentDescription = "Settings",
                    tint = Color.LightGray
                )
            }
        }
    }
}

@Composable
fun ObdErrorCodesDialog(
    obdInStream: InputStream?,
    obdOutStream: OutputStream?,
    onDismiss: () -> Unit,
    errorCodes: List<String>,
    onCodesReady: (List<String>) -> Unit
) {
    var localCodes by remember { mutableStateOf(errorCodes) }
    LaunchedEffect(Unit) {
        if (localCodes.isEmpty()) {
            withContext(Dispatchers.IO) {
                val resp = sendElmCommand("03", obdInStream, obdOutStream, 1500)
                val codes = mutableListOf<String>()

                if (resp.startsWith("43")) {
                    val data = resp.drop(2)
                    for (i in data.indices step 4) {
                        if (i + 4 <= data.length) {
                            val raw = data.substring(i, i + 4)
                            if (raw != "0000") codes.add(parseDtc(raw))
                        }
                    }
                }
                if (codes.isEmpty()) codes.add("Nu au fost găsite coduri de eroare")
                onCodesReady(codes)
                localCodes = codes
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Coduri de eroare (DTCs)") },
        text = {
            Column {
                if (localCodes.isEmpty()) {
                    Text("Citire coduri de eroare…", fontSize = 16.sp)
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                } else {
                    localCodes.forEach { code ->
                        Text(code, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Închide") } }
    )
}

private fun parseDtc(hex: String): String {
    if (hex.length < 4) return "Cod incorect"
    val firstByte = hex.substring(0, 2).toInt(16)
    val secondByte = hex.substring(2, 4)
    val type = when (firstByte shr 6) {
        0 -> "P"
        1 -> "C"
        2 -> "B"
        3 -> "U"
        else -> "?"
    }
    val code = ((firstByte and 0x3F).toString(16).padStart(2, '0') + secondByte).uppercase()
    return "$type$code"
}

