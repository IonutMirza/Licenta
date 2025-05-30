package com.example.licenta

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.licenta.model.Car
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun SwitchPage(
    onNavigateToMapPage: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToWalletPage: () -> Unit,
    onCarSelected: (Car) -> Unit
) {
    val firestore = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser
    val userId = user?.uid ?: ""

    var selectedCar by remember { mutableStateOf<Car?>(null) }
    var cars by remember { mutableStateOf<List<Car>>(emptyList()) }

    var showEditDialog by remember { mutableStateOf(false) }
    var carToEdit by remember { mutableStateOf<Car?>(null) }

    // Încarcă mașinile din Firestore
    LaunchedEffect(userId) {
        firestore.collection("cars1")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { result ->
                cars = result.map { document -> document.toObject(Car::class.java) }
                if (selectedCar == null && cars.isNotEmpty()) {
                    selectedCar = cars[0]
                    onCarSelected(selectedCar!!)
                }
            }
            .addOnFailureListener {
                // Tratează eroarea
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
            text = "My Garage",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        selectedCar?.let {
            Text("Selected Car: ${it.brand} ${it.model}", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (cars.isEmpty()) {
            Text("No cars added yet.", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        } else {
            cars.sortedBy { it.brand }.forEach { car ->
                val isSelected = selectedCar?.licensePlate == car.licensePlate
                val backgroundColor = if (isSelected) Color.Green else Color.Gray

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .background(backgroundColor)
                        .clickable {
                            selectedCar = car
                            onCarSelected(car)
                        }
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("${car.brand} ${car.model}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Year: ${car.year}\nPlate: ${car.licensePlate}", fontSize = 16.sp)
                    }

                    // Buton editare
                    IconButton(onClick = {
                        carToEdit = car
                        showEditDialog = true
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.edit),
                            contentDescription = "Edit",
                            tint = Color.Blue
                        )
                    }

                    // Buton ștergere
                    IconButton(onClick = {
                        firestore.collection("cars1")
                            .document(car.licensePlate)
                            .delete()
                            .addOnSuccessListener {
                                cars = cars.filter { it.licensePlate != car.licensePlate }
                                if (selectedCar?.licensePlate == car.licensePlate) {
                                    selectedCar = cars.firstOrNull()
                                    selectedCar?.let { onCarSelected(it) }
                                }
                            }
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.delete),
                            contentDescription = "Delete",
                            tint = Color.Red
                        )
                    }
                }
            }
        }
    }

    // Navigație jos
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = { onNavigateBack() }, modifier = Modifier.size(50.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.home),
                    contentDescription = "Home",
                    tint = Color.LightGray,
                    modifier = Modifier.fillMaxSize()
                )
            }

            IconButton(onClick = { onNavigateToMapPage() }, modifier = Modifier.size(50.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.map),
                    contentDescription = "Map",
                    tint = Color.LightGray,
                    modifier = Modifier.fillMaxSize()
                )
            }

            IconButton(onClick = { }, modifier = Modifier.size(50.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.garage),
                    contentDescription = "Garage",
                    tint = Color.Black,
                    modifier = Modifier.fillMaxSize()
                )
            }

            IconButton(onClick = { onNavigateToWalletPage() }, modifier = Modifier.size(50.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.settings),
                    contentDescription = "Wallet",
                    tint = Color.LightGray,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Dialog de editare
    if (showEditDialog && carToEdit != null) {
        var editedBrand by remember { mutableStateOf(carToEdit!!.brand) }
        var editedModel by remember { mutableStateOf(carToEdit!!.model) }
        var editedYear by remember { mutableStateOf(carToEdit!!.year.toString()) }

        AlertDialog(
            onDismissRequest = {
                showEditDialog = false
                carToEdit = null
            },
            confirmButton = {
                Button(onClick = {
                    val updatedCar = carToEdit!!.copy(
                        brand = editedBrand,
                        model = editedModel,
                        //year = editedYear.toIntOrNull() ?: carToEdit!!.year
                    )
                    firestore.collection("cars1")
                        .document(carToEdit!!.licensePlate)
                        .set(updatedCar)
                        .addOnSuccessListener {
                            cars = cars.map {
                                if (it.licensePlate == updatedCar.licensePlate) updatedCar else it
                            }
                            if (selectedCar?.licensePlate == updatedCar.licensePlate) {
                                selectedCar = updatedCar
                                onCarSelected(updatedCar)
                            }
                        }
                    showEditDialog = false
                    carToEdit = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                Button(onClick = {
                    showEditDialog = false
                    carToEdit = null
                }) {
                    Text("Cancel")
                }
            },
            title = { Text("Edit Car") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editedBrand,
                        onValueChange = { editedBrand = it },
                        label = { Text("Brand") }
                    )
                    OutlinedTextField(
                        value = editedModel,
                        onValueChange = { editedModel = it },
                        label = { Text("Model") }
                    )
                    OutlinedTextField(
                        value = editedYear,
                        onValueChange = { editedYear = it },
                        label = { Text("Year") },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                    )
                }
            }
        )
    }
}
