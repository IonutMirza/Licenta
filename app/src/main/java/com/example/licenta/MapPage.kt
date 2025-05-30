package com.example.licenta

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest

/* ---------- UTILS -------------------------------------------------------------------------- */

fun bitmapDescriptorFromVector(context: android.content.Context, vectorResId: Int): BitmapDescriptor {
    val drawable: Drawable = ContextCompat.getDrawable(context, vectorResId)!!
    val bitmap = Bitmap.createBitmap(
        drawable.intrinsicWidth,
        drawable.intrinsicHeight,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/* ---------- DATA CLASSES ------------------------------------------------------------------- */

data class Trip(
    val title: String,
    val date: String,
    val distanceKm: Double,
    val durationMin: Int,
    val avgSpeedKmh: Double
) {
    override fun toString(): String =
        "$title – $date • ${distanceKm} km • $durationMin min • ${avgSpeedKmh} km/h"
}

/* ---------- COMPOSABLE PAGE ---------------------------------------------------------------- */

@SuppressLint("MissingPermission")
@Composable
fun MapPage(
    onNavigateBack: () -> Unit,
    onNavigateToSwitchPage: () -> Unit,
    onNavigateToWalletPage: () -> Unit,
) {
    val context = LocalContext.current

    /* --- Initialise Google Places --------------------------------------------------------- */
    LaunchedEffect(Unit) {
        if (!Places.isInitialized()) {
            Places.initialize(context, context.getString(R.string.google_api_key))
        }
    }

    val placesClient = remember { Places.createClient(context) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val cameraPositionState = rememberCameraPositionState()
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }

    /* --- Trip dialog state ---------------------------------------------------------------- */
    var showTripsDialog by remember { mutableStateOf(false) }

    /* Mock list: ultimele 10 drumuri cu mașina – hardcodate momentan */
    val mockTrips = remember {
        listOf(
            Trip("Home → Office",        "14 Mai 2025", 12.3,  22, 33.5),
            Trip("Office → Gym",         "13 Mai 2025",  4.8,  10, 28.8),
            Trip("Gym → Supermarket",    "13 Mai 2025",  5.2,   9, 34.5),
            Trip("Supermarket → Home",   "13 Mai 2025",  6.1,  12, 30.5),
            Trip("Home → Airport",       "12 Mai 2025", 22.0,  35, 37.7),
            Trip("Airport → City Center","12 Mai 2025", 10.4,  18, 34.6),
            Trip("City Center → Cinema", "11 Mai 2025",  3.9,   8, 29.3),
            Trip("Cinema → Home",        "11 Mai 2025", 11.0,  20, 33.0),
            Trip("Home → Service",       "10 Mai 2025",  9.7,  17, 34.2),
            Trip("Service → Home",       "10 Mai 2025",  9.6,  16, 36.0)
        )
    }

    /* --- Search bar state ----------------------------------------------------------------- */
    var query by remember { mutableStateOf("") }
    var predictions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }

    /* --- Autocomplete search -------------------------------------------------------------- */
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
        } else predictions = emptyList()
    }

    /* --- Request location and move camera ------------------------------------------------- */
    RequestLocationPermission {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                currentLocation = LatLng(it.latitude, it.longitude)
                cameraPositionState.position = CameraPosition.fromLatLngZoom(currentLocation!!, 15f)
            }
        }
    }

    /* ----------------------------------- UI ---------------------------------------------- */
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        /* 1️⃣  Search bar + suggestions */
        Column {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search location") },
                modifier = Modifier.fillMaxWidth()
            )
            predictions.forEach { prediction ->
                TextButton(onClick = {
                    query = prediction.getFullText(null).toString()
                    predictions = emptyList()

                    /* Fetch coordinates for selected place */
                    val placeId = prediction.placeId
                    val placeFields = listOf(Place.Field.LAT_LNG)
                    val request = FetchPlaceRequest.builder(placeId, placeFields).build()

                    placesClient.fetchPlace(request)
                        .addOnSuccessListener { response ->
                            response.place.latLng?.let { latLng ->
                                cameraPositionState.position =
                                    CameraPosition.fromLatLngZoom(latLng, 15f)
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Search error", Toast.LENGTH_SHORT).show()
                        }
                }) { Text(prediction.getFullText(null).toString()) }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        /* 2️⃣  Map + FloatingActionButton */
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
                    val icon = remember { bitmapDescriptorFromVector(context, R.drawable.icon_car_loc) }
                    Marker(
                        state = MarkerState(position = it),
                        title = "Your location",
                        icon = icon
                    )
                }
            }

            /* ➕ Button View My Trips */
            FloatingActionButton(
                onClick = { showTripsDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .size(56.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.car_icon),
                    contentDescription = "View my trips",
                    tint = Color.White
                )
            }
        }

        /* 3️⃣  Bottom navigation bar */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = onNavigateBack, modifier = Modifier.size(50.dp)) {
                Icon(
                    painter = painterResource(R.drawable.home),
                    contentDescription = "Home",
                    tint = Color.LightGray
                )
            }
            IconButton(onClick = {}, modifier = Modifier.size(50.dp)) {
                Icon(
                    painter = painterResource(R.drawable.map),
                    contentDescription = "Map",
                    tint = Color.Black
                )
            }
            IconButton(onClick = onNavigateToSwitchPage, modifier = Modifier.size(50.dp)) {
                Icon(
                    painter = painterResource(R.drawable.garage),
                    contentDescription = "Garage",
                    tint = Color.LightGray
                )
            }
            IconButton(onClick = onNavigateToWalletPage, modifier = Modifier.size(50.dp)) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = "Settings",
                    tint = Color.LightGray
                )
            }
        }
    }

    /* 4️⃣  Trips dialog */
    if (showTripsDialog) {
        AlertDialog(
            onDismissRequest = { showTripsDialog = false },
            confirmButton = {
                TextButton(onClick = { showTripsDialog = false }) { Text("Close") }
            },
            title = { Text("My Last 10 Trips") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    mockTrips.forEach { trip -> Text("• $trip") }
                }
            }
        )
    }
}

/* ---------- LOCATION PERMISSION ------------------------------------------------------------ */

@Composable
fun RequestLocationPermission(onPermissionGranted: () -> Unit) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) onPermissionGranted() else Toast.makeText(
            context,
            "Location permission is required to show the map.",
            Toast.LENGTH_LONG
        ).show()
    }

    LaunchedEffect(Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            onPermissionGranted()
        } else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
