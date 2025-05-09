package com.example.licenta

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.provider.Settings
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
    onCarSelected: (Car) -> Unit
) {
    val user = FirebaseAuth.getInstance().currentUser
    val displayName = user?.email ?: "User"
    val username = displayName.substringBefore("@")
    val firestore = FirebaseFirestore.getInstance()

    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var licensePlate by remember { mutableStateOf("") }
    var selectedCar by remember { mutableStateOf<Car?>(null) }
    var cars by remember { mutableStateOf<List<Car>>(emptyList()) }
    var showAddCarDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    val permissions = arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val allGranted = permissionsMap.values.all { it }
        if (allGranted) {
            connectToOBD(context, bluetoothAdapter)
        } else {
            Toast.makeText(context, "Bluetooth permissions denied", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestBluetoothPermissions() {
        permissionLauncher.launch(permissions)
    }

    LaunchedEffect(user?.uid) {
        firestore.collection("cars")
            .whereEqualTo("userId", user?.uid)
            .get()
            .addOnSuccessListener { result ->
                cars = result.map { document ->
                    document.toObject(Car::class.java)
                }
                if (selectedCar == null && cars.isNotEmpty()) {
                    selectedCar = cars[0]
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Drive like a Pro",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Welcome, $username!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))

        selectedCar?.let {
            Text(
                "Selected Car: ${it.brand} ${it.model}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(cars) { car ->
                Text(
                    text = "${car.brand} ${car.model}",
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
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

        Button(
            onClick = { showAddCarDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
        ) {
            Text("Add New Car", color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { requestBluetoothPermissions() },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
        ) {
            Text("Connect to OBD", color = Color.White)
        }
    }

    if (showAddCarDialog) {
        AlertDialog(
            onDismissRequest = { showAddCarDialog = false },
            title = { Text("Add New Car") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("Brand") })
                    OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") })
                    OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Year") })
                    OutlinedTextField(value = licensePlate, onValueChange = { licensePlate = it }, label = { Text("License Plate") })
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val car = Car(
                            brand = brand,
                            model = model,
                            year = year,
                            licensePlate = licensePlate,
                            userId = user?.uid ?: ""
                        )
                        firestore.collection("cars")
                            .add(car)
                            .addOnSuccessListener {
                                showAddCarDialog = false
                            }
                    }
                ) {
                    Text("Done")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = { onLogout() },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Log Out", color = Color.White)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
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
            IconButton(onClick = onNavigateToMapPage, modifier = Modifier.size(50.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.map),
                    contentDescription = "Map",
                    tint = Color.LightGray,
                    modifier = Modifier.fillMaxSize()
                )
            }
            IconButton(onClick = onNavigateToSwitchPage, modifier = Modifier.size(50.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.garage),
                    contentDescription = "Garage",
                    tint = Color.LightGray,
                    modifier = Modifier.fillMaxSize()
                )
            }
            IconButton(onClick = onNavigateToWalletPage, modifier = Modifier.size(50.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.settings),
                    contentDescription = "Settings",
                    tint = Color.LightGray,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

fun connectToOBD(context: android.content.Context, bluetoothAdapter: BluetoothAdapter?) {
    try {
        if (bluetoothAdapter == null) {
            Toast.makeText(context, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(context, "Bluetooth not enabled", Toast.LENGTH_SHORT).show()
            return
        }

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        if (pairedDevices.isNullOrEmpty()) {
            Toast.makeText(context, "No paired devices found", Toast.LENGTH_SHORT).show()
            return
        }

        val obdDevice = pairedDevices.firstOrNull { it.name.contains("OBD", ignoreCase = true) }
        if (obdDevice == null) {
            Toast.makeText(context, "OBD device not found", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(context, "Connecting to ${obdDevice.name}", Toast.LENGTH_SHORT).show()
        // TODO: Add actual BluetoothSocket connection code here

    } catch (e: Exception) {
        Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        e.printStackTrace()
    }
}
