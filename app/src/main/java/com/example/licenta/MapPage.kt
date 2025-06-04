// MapPage.kt
package com.example.licenta

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.*


/** Convert a VectorDrawable (in res/drawable) into a BitmapDescriptor for a custom marker. */
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


/** A data class representing one “car trip.” */
data class TripRecord(
    val id: Long,                      // use System.currentTimeMillis() at trip start
    val startTimeMs: Long,
    val endTimeMs: Long,
    val points: List<LatLng>,          // all GPS points recorded
    val distanceMeters: Float,         // total distance in meters
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float
) {
    /** Returns a human‐readable title, e.g. “14 Jun 2025 15:33 – 15:48” */
    fun timeWindowString(): String {
        val fmt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
        val start = fmt.format(Date(startTimeMs))
        val end = fmt.format(Date(endTimeMs))
        return "$start – $end"
    }

    /** Distance in km (formatted to one decimal) */
    fun distanceKmString(): String {
        return String.format(Locale.getDefault(), "%.1f km", distanceMeters / 1000f)
    }

    /** Duration in minutes (rounded) */
    fun durationMin(): Int {
        val diffMs = endTimeMs - startTimeMs
        return ((diffMs / 1000f / 60f).toInt())
    }

    /** Average speed string, e.g. “23 km/h” */
    fun avgSpeedString(): String {
        return String.format(Locale.getDefault(), "%d km/h", avgSpeedKmh.toInt())
    }

    /** Max speed string, e.g. “65 km/h” */
    fun maxSpeedString(): String {
        return String.format(Locale.getDefault(), "%d km/h", maxSpeedKmh.toInt())
    }
}


@SuppressLint("MissingPermission")
@Composable
fun MapPage(
    onNavigateBack: () -> Unit,
    onNavigateToSwitchPage: () -> Unit,
    onNavigateToWalletPage: () -> Unit
) {
    val context = LocalContext.current
    val apiKey = context.getString(R.string.google_api_key)

    // 1️⃣ Initialize Google Places one time
    LaunchedEffect(Unit) {
        if (!Places.isInitialized()) {
            Places.initialize(context, apiKey)
        }
    }
    val placesClient = remember { Places.createClient(context) }

    // 2️⃣ State: camera, current location, speed/status, trip detection
    val cameraPositionState = rememberCameraPositionState()
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }

    var movementStatus by remember { mutableStateOf("No Move") }
    var speedKmh by remember { mutableStateOf(0f) }

    // Threshold: above 10 km/h → “in a car”; below 10 → walking or stationary
    val CAR_THRESHOLD_KMH = 10f

    // Is a trip currently ongoing?
    var inTrip by remember { mutableStateOf(false) }

    // For the current trip we are building:
    val currentTripPoints = remember { mutableStateListOf<LatLng>() }
    var currentTripDistance by remember { mutableStateOf(0f) }
    var currentTripMaxSpeed by remember { mutableStateOf(0f) }
    var currentTripStartTime by remember { mutableStateOf(0L) }
    var lastTripLocationObj by remember { mutableStateOf<Location?>(null) }

    // A list of all finished trips (up to memory)
    val tripsList = remember { mutableStateListOf<TripRecord>() }

    // 3️⃣ Request permission, then start listening for location‐updates
    RequestLocationPermission {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        // 3a) center map once on last known
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            loc?.let {
                val ll = LatLng(it.latitude, it.longitude)
                currentLocation = ll
                cameraPositionState.position = CameraPosition.fromLatLngZoom(ll, 15f)
            }
        }

        // 3b) request continuous updates
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000L
        )
            .setMinUpdateDistanceMeters(1f)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.firstOrNull()?.let { loc ->
                    val newLatLng = LatLng(loc.latitude, loc.longitude)
                    currentLocation = newLatLng

                    // compute speed in km/h from loc.speed (m/s)
                    val newSpeedKmh = loc.speed * 3.6f
                    speedKmh = newSpeedKmh

                    // classify movement
                    movementStatus = when {
                        newSpeedKmh < 0.5f -> "No Move"
                        newSpeedKmh < CAR_THRESHOLD_KMH -> "Walking"
                        else -> "Drive"
                    }

                    // maintain camera zoom but follow location
                    val currentZoom = cameraPositionState.position.zoom
                    cameraPositionState.position =
                        CameraPosition.fromLatLngZoom(newLatLng, currentZoom)

                    // ————— Trip detection logic —————
                    if (!inTrip && newSpeedKmh >= CAR_THRESHOLD_KMH) {
                        // Start a new trip
                        inTrip = true
                        currentTripStartTime = System.currentTimeMillis()
                        currentTripPoints.clear()
                        currentTripPoints.add(newLatLng)
                        currentTripDistance = 0f
                        currentTripMaxSpeed = newSpeedKmh
                        lastTripLocationObj = Location("trip").apply {
                            latitude = loc.latitude
                            longitude = loc.longitude
                        }
                    } else if (inTrip) {
                        // We are already in a trip
                        // Accumulate distance from lastTripLocationObj → loc
                        lastTripLocationObj?.let { prevLoc ->
                            val dist = loc.distanceTo(prevLoc)
                            currentTripDistance += dist
                        }
                        // update max speed
                        if (newSpeedKmh > currentTripMaxSpeed) {
                            currentTripMaxSpeed = newSpeedKmh
                        }
                        // add to our points
                        currentTripPoints.add(newLatLng)
                        // update lastTripLocationObj
                        lastTripLocationObj = Location("trip").apply {
                            latitude = loc.latitude
                            longitude = loc.longitude
                        }

                        // Check if speed dropped below CAR_THRESHOLD → end trip immediately
                        if (newSpeedKmh < CAR_THRESHOLD_KMH) {
                            val tripEndTime = System.currentTimeMillis()
                            // compute duration
                            val durationMs = tripEndTime - currentTripStartTime
                            val durationHrs = durationMs.toFloat() / 1000f / 3600f
                            val avgSpeed = if (durationHrs > 0f) {
                                (currentTripDistance / 1000f) / durationHrs
                            } else {
                                0f
                            }

                            // store TripRecord if distance > 100 m
                            if (currentTripDistance >= 100f) {
                                val tripRecord = TripRecord(
                                    id = currentTripStartTime,
                                    startTimeMs = currentTripStartTime,
                                    endTimeMs = tripEndTime,
                                    points = currentTripPoints.toList(),
                                    distanceMeters = currentTripDistance,
                                    avgSpeedKmh = avgSpeed,
                                    maxSpeedKmh = currentTripMaxSpeed
                                )
                                tripsList.add(0, tripRecord) // newest first
                                // keep only last 10
                                if (tripsList.size > 10) {
                                    tripsList.removeAt(tripsList.size - 1)
                                }
                            }
                            // reset trip
                            inTrip = false
                            currentTripPoints.clear()
                            currentTripDistance = 0f
                            currentTripMaxSpeed = 0f
                            lastTripLocationObj = null
                        }
                    }
                }
            }
        }

        fusedClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    // 4️⃣ Search / autocomplete UI state
    var query by remember { mutableStateOf("") }
    var predictions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }

    // 5️⃣ Destination pin & “navigate” dialog
    var selectedLocation by remember { mutableStateOf<LatLng?>(null) }
    var showNavigateDialog by remember { mutableStateOf(false) }

    // 6️⃣ “My Last 10 Trips” dialog
    var showTripsDialog by remember { mutableStateOf(false) }

    // 7️⃣ Autocomplete whenever query changes
    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            val request = FindAutocompletePredictionsRequest.builder()
                .setQuery(query)
                .build()
            placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener { response ->
                    predictions = response.autocompletePredictions
                }
                .addOnFailureListener {
                    predictions = emptyList()
                }
        } else {
            predictions = emptyList()
        }
    }

    // ───────────── Compose UI ─────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 1️⃣ Search bar + suggestions
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

                    // Fetch place → latLng → place red pin
                    val placeId = prediction.placeId
                    val placeFields = listOf(Place.Field.LAT_LNG)
                    val fetchRequest =
                        FetchPlaceRequest.builder(placeId, placeFields).build()
                    placesClient.fetchPlace(fetchRequest)
                        .addOnSuccessListener { response ->
                            response.place.latLng?.let { latLng ->
                                selectedLocation = latLng
                                cameraPositionState.position =
                                    CameraPosition.fromLatLngZoom(latLng, 15f)
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Search error", Toast.LENGTH_SHORT).show()
                        }
                }) {
                    Text(text = prediction.getFullText(null).toString())
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2️⃣ Map + custom markers + “My Last 10 Trips” FAB + speed/status card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = false),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = true
                ),
                onMapClick = { latLng ->
                    // Tapping on the map places the red “Destination” pin
                    selectedLocation = latLng
                }
            ) {
                // 2a) Blue custom marker for “Your Location”
                currentLocation?.let {
                    val iconCurrent =
                        bitmapDescriptorFromVector(context, R.drawable.icon_car_loc)
                    Marker(
                        state = MarkerState(position = it),
                        title = "Your Location",
                        icon = iconCurrent
                    )
                }

                // 2b) Red custom marker for “Destination”
                selectedLocation?.let { dest ->
                    val iconDest =
                        bitmapDescriptorFromVector(context, R.drawable.icon_car_loc_red)
                    Marker(
                        state = MarkerState(position = dest),
                        title = "Destination",
                        icon = iconDest,
                        onClick = {
                            showNavigateDialog = true
                            true
                        }
                    )
                }
            }

            // 2c) “My Last 10 Trips” FAB (moved up so that speed/status is below)
            FloatingActionButton(
                onClick = { showTripsDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 76.dp) // push up so status is below
                    .size(56.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.car_icon),
                    contentDescription = "View my trips",
                    tint = Color.White
                )
            }

            // 2d) Speed/status display (card) at the bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "$movementStatus (${speedKmh.toInt()} km/h)",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3️⃣ Bottom navigation bar (Home, Map, Garage, Settings)
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
            IconButton(onClick = { /* Already on map */ }, modifier = Modifier.size(50.dp)) {
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

    // ───────────── “Navigate” Dialog for the Destination Pin ─────────────
    if (showNavigateDialog && selectedLocation != null) {
        AlertDialog(
            onDismissRequest = { showNavigateDialog = false },
            title = { Text("Navigate to Destination") },
            text = { Text("Open route in Google Maps?") },
            confirmButton = {
                TextButton(onClick = {
                    val lat = selectedLocation!!.latitude
                    val lng = selectedLocation!!.longitude
                    val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    if (mapIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(mapIntent)
                    } else {
                        Toast.makeText(context, "Google Maps is not installed", Toast.LENGTH_SHORT).show()
                    }
                    showNavigateDialog = false
                }) {
                    Text("Open in Google Maps")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNavigateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ───────────── “My Last 10 Trips” Dialog ─────────────
    if (showTripsDialog) {
        AlertDialog(
            onDismissRequest = { showTripsDialog = false },
            confirmButton = {
                TextButton(onClick = { showTripsDialog = false }) {
                    Text("Close")
                }
            },
            title = { Text("My Last 10 Trips") },
            text = {
                if (tripsList.isEmpty()) {
                    // No recorded trips yet
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No trips recorded yet.")
                    }
                } else {
                    // Show up to 10 trips in a scrollable list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        items(tripsList) { trip ->
                            TripListItem(trip = trip)
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        )
    }
}


/** A small composable that shows one TripRecord: text info + a 150 dp Map preview with polyline */
@Composable
private fun TripListItem(trip: TripRecord) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // 1) Top info row: date/time window
        Text(
            text = trip.timeWindowString(),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 2) Stats row: distance, duration, avg / max speed
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(trip.distanceKmString(), style = MaterialTheme.typography.bodyMedium)
            Text("${trip.durationMin()} min", style = MaterialTheme.typography.bodyMedium)
            Text("Avg: ${trip.avgSpeedString()}", style = MaterialTheme.typography.bodyMedium)
            Text("Max: ${trip.maxSpeedString()}", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3) Mini‐map showing the trip polyline
        // We center the mini‐map on the first point with a modest zoom.
        val startPoint = trip.points.firstOrNull()
        val miniCameraPositionState = rememberCameraPositionState {
            position = if (startPoint != null) {
                CameraPosition.fromLatLngZoom(startPoint, 14f)
            } else {
                CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(vertical = 4.dp)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = miniCameraPositionState,
                properties = MapProperties(isMyLocationEnabled = false),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    scrollGesturesEnabled = false,
                    zoomGesturesEnabled = false,
                    mapToolbarEnabled = false,
                    myLocationButtonEnabled = false
                )
            ) {
                if (trip.points.size >= 2) {
                    Polyline(
                        points = trip.points,
                        color = MaterialTheme.colorScheme.primary,
                        width = 6f
                    )
                }
                // Optionally place a small start/end marker:
                trip.points.firstOrNull()?.let { first ->
                    Marker(
                        state = MarkerState(position = first),
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
                        title = "Start"
                    )
                }
                trip.points.lastOrNull()?.let { last ->
                    Marker(
                        state = MarkerState(position = last),
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                        title = "End"
                    )
                }
            }
        }
    }
}


/** Handles requesting ACCESS_FINE_LOCATION. */
@Composable
fun RequestLocationPermission(onPermissionGranted: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onPermissionGranted()
        } else {
            Toast.makeText(
                context,
                "Location permission is required",
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
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}

