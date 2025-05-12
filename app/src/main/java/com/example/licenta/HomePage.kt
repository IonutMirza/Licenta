package com.example.licenta

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.licenta.model.Car
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@SuppressLint("MissingPermission")
@Composable
fun HomePage(
    onLogout: () -> Unit,
    onNavigateToMapPage: () -> Unit,
    onNavigateToSwitchPage: () -> Unit,
    onNavigateToWalletPage: () -> Unit,
    onNavigateToObdDataPage: () -> Unit,
    selectedCar: Car?,  // 🔹 adăugat
    onCarSelected: (Car) -> Unit
) {
    val context = LocalContext.current
    val user = FirebaseAuth.getInstance().currentUser
    val username = user?.email?.substringBefore("@") ?: "User"
    val firestore = FirebaseFirestore.getInstance()
    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    var cars by remember { mutableStateOf(emptyList<Car>()) }
    var selectedCar by remember { mutableStateOf<Car?>(null) }
    var showAddCarDialog by remember { mutableStateOf(false) }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var licensePlate by remember { mutableStateOf("") }
    var bluetoothStatus by remember { mutableStateOf("Disconnected") }
    var connectedDeviceName by remember { mutableStateOf<String?>(null) }
    var isObdConnected by remember { mutableStateOf(false) }
    var showObdDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            connectToOBD(context, bluetoothAdapter) { name, success ->
                connectedDeviceName = name
                isObdConnected = success
                bluetoothStatus = if (success) "Connected" else "Failed"
            }
        } else {
            Toast.makeText(context, "Bluetooth permissions denied", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestBluetoothPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        )
    }

    LaunchedEffect(user?.uid) {
        user?.uid?.let { uid ->
            firestore.collection("cars")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener { result ->
                    cars = result.map { it.toObject(Car::class.java) }
                    if (selectedCar == null && cars.isNotEmpty()) {
                        selectedCar = cars[0]
                        onCarSelected(cars[0])
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
        Text("Drive like a Pro", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Welcome, $username!", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Bluetooth Status: $bluetoothStatus" +
                    (connectedDeviceName?.let { " - $it" } ?: ""),
            fontSize = 16.sp,
            color = if (isObdConnected) Color.Green else Color.Red
        )

        Spacer(modifier = Modifier.height(16.dp))

        selectedCar?.let {
            Text("Selected Car: ${it.brand} ${it.model}", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(cars) { car ->
                Text(
                    text = "${car.brand} ${car.model}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedCar = car
                            onCarSelected(car)
                        }
                        .background(if (selectedCar == car) Color.LightGray else Color.Transparent)
                        .padding(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { showAddCarDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Add New Car")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { requestBluetoothPermissions() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Connect to OBD")
        }

        if (isObdConnected) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { showObdDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
            ) {
                Text("View Data Specifications", color = Color.White)
            }
        }


        Spacer(modifier = Modifier.height(24.dp))

        BottomNavigationBar(
            onMapClick = onNavigateToMapPage,
            onSwitchClick = onNavigateToSwitchPage,
            onWalletClick = onNavigateToWalletPage,
            onLogout = onLogout
        )
    }

    if (showObdDialog) {
        AlertDialog(
            onDismissRequest = { showObdDialog = false },
            title = { Text("OBD Data") },
            text = {
                val speed = (30..160).random()
                val rpm = (800..5000).random()
                val fuelConsumption = (5..15).random() + (0..9).random() * 0.1
                Column {
                    Text("Speed: $speed km/h")
                    Text("RPM: $rpm")
                    Text("Fuel Consumption: ${"%.1f".format(fuelConsumption)} L/100km")
                }
            },
            confirmButton = {
                TextButton(onClick = { showObdDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showAddCarDialog) {
        AlertDialog(
            onDismissRequest = { showAddCarDialog = false },
            title = { Text("Add New Car") },
            text = {
                Column {
                    OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("Brand") })
                    OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") })
                    OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Year") })
                    OutlinedTextField(value = licensePlate, onValueChange = { licensePlate = it }, label = { Text("License Plate") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newCar = Car(brand, model, year, licensePlate, user?.uid ?: "")
                    firestore.collection("cars").add(newCar).addOnSuccessListener {
                        showAddCarDialog = false
                    }
                }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun BottomNavigationBar(
    onMapClick: () -> Unit,
    onSwitchClick: () -> Unit,
    onWalletClick: () -> Unit,
    onLogout: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = {}, modifier = Modifier.size(50.dp)) {
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

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Log Out", color = Color.White)
        }
    }
}

@SuppressLint("MissingPermission")
fun connectToOBD(
    context: android.content.Context,
    bluetoothAdapter: BluetoothAdapter?,
    onConnectionResult: (deviceName: String?, success: Boolean) -> Unit
) {
    try {
        // Verificarea permisiunii Bluetooth
        val bluetoothPermissionGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

        // Verificarea permisiunii de locație
        val locationPermissionGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // Dacă nu avem permisiunile necesare, cerem permisiunile
        if (!bluetoothPermissionGranted || !locationPermissionGranted) {
            ActivityCompat.requestPermissions(
                context as Activity, // context trebuie să fie o activitate pentru a cere permisiuni
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                1
            )
            return
        }

        if (bluetoothAdapter == null) {
            Toast.makeText(context, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
            onConnectionResult(null, false)
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(context, "Bluetooth not enabled", Toast.LENGTH_SHORT).show()
            onConnectionResult(null, false)
            return
        }

        // Accesăm dispozitivele asociate
        val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
        val obdDevice = pairedDevices.firstOrNull { it.name.contains("OBD", ignoreCase = true) }

        if (obdDevice != null) {
            Toast.makeText(context, "Connecting to ${obdDevice.name}", Toast.LENGTH_SHORT).show()
            onConnectionResult(obdDevice.name, true) // Simulare conectare
        } else {
            Toast.makeText(context, "No OBD device found", Toast.LENGTH_SHORT).show()
            onConnectionResult(null, false)
        }
    } catch (e: SecurityException) {
        Toast.makeText(context, "Permission denied: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        onConnectionResult(null, false)
    } catch (e: Exception) {
        Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        onConnectionResult(null, false)
    }
}
