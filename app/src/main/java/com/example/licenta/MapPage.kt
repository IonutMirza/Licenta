package com.example.licenta

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest

@SuppressLint("MissingPermission")
@Composable
fun MapPage(
    onNavigateBack: () -> Unit,
    onNavigateToSwitchPage: () -> Unit,
    onNavigateToWalletPage: () -> Unit
) {
    val context = LocalContext.current

    // Inițializează Places dacă nu e deja
    LaunchedEffect(Unit) {
        if (!Places.isInitialized()) {
            Places.initialize(context.applicationContext, context.getString(R.string.google_api_key))
        }
    }

    val placesClient = remember { Places.createClient(context) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val cameraPositionState = rememberCameraPositionState()
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }

    var query by remember { mutableStateOf("") }
    var predictions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }

    // Caută sugestii când textul se schimbă
    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            val request = FindAutocompletePredictionsRequest.builder()
                .setQuery(query)
                .build()

            placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener { response ->
                    predictions = response.autocompletePredictions
                    Log.d("PlacesAPI", "Found ${predictions.size} predictions")
                }
                .addOnFailureListener {
                    predictions = emptyList()
                    Log.e("PlacesAPI", "Error fetching predictions: ${it.message}")
                }
        } else {
            predictions = emptyList()
        }
    }

    RequestLocationPermission {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val latLng = LatLng(it.latitude, it.longitude)
                currentLocation = latLng
                cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 🔍 SearchBar
        Column {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Caută locație") },
                modifier = Modifier.fillMaxWidth()
            )
            predictions.forEach { prediction ->
                TextButton(onClick = {
                    query = prediction.getFullText(null).toString()
                    predictions = emptyList()

                    val placeId = prediction.placeId
                    val placeFields = listOf(Place.Field.LAT_LNG)

                    val request = FetchPlaceRequest.builder(placeId, placeFields).build()
                    placesClient.fetchPlace(request)
                        .addOnSuccessListener { response ->
                            response.place.latLng?.let { latLng ->
                                cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Eroare la căutare", Toast.LENGTH_SHORT).show()
                        }
                }) {
                    Text(text = prediction.getFullText(null).toString())
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = true)
            ) {
                currentLocation?.let {
                    Marker(
                        state = MarkerState(position = it),
                        title = "Locația ta"
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = onNavigateBack, modifier = Modifier.size(50.dp)) {
                Icon(painter = painterResource(R.drawable.home), contentDescription = "Home", tint = Color.LightGray)
            }
            IconButton(onClick = {}, modifier = Modifier.size(50.dp)) {
                Icon(painter = painterResource(R.drawable.map), contentDescription = "Map", tint = Color.Black)
            }
            IconButton(onClick = onNavigateToSwitchPage, modifier = Modifier.size(50.dp)) {
                Icon(painter = painterResource(R.drawable.sageti), contentDescription = "Switch", tint = Color.LightGray)
            }
            IconButton(onClick = onNavigateToWalletPage, modifier = Modifier.size(50.dp)) {
                Icon(painter = painterResource(R.drawable.wallet), contentDescription = "Wallet", tint = Color.LightGray)
            }
        }
    }
}


@Composable
fun RequestLocationPermission(onPermissionGranted: () -> Unit) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGranted()
        } else {
            Toast.makeText(
                context,
                "Ai nevoie de permisiunea de locație pentru a vedea harta.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            onPermissionGranted()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}

