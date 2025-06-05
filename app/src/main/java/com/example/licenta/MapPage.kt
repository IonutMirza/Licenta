package com.example.licenta

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
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

// ===== Adaugă aceste importuri suplimentare =====
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

/* ------------------ UTILS: Transformă vector drawable în BitmapDescriptor ------------------ */
fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor {
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

/* ------------ START LOCATION UPDATES -> trimite lat, lng, speed(km/h), timestamp ------------- */
@SuppressLint("MissingPermission")
fun startLocationUpdates(
    context: Context,
    onNewData: (lat: Double, lng: Double, speedKmh: Float, timestamp: Long) -> Unit
) {
    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
        .setMinUpdateDistanceMeters(1f)
        .build()

    val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.firstOrNull()?.let { loc ->
                val speedKmh = loc.speed * 3.6f
                onNewData(loc.latitude, loc.longitude, speedKmh, loc.time)
            }
        }
    }
    fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
}

/* ----------------------------- TRIP RECORD data class (hardcodate) --------------------------- */
data class TripRecord(
    val id: Long,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val distanceMeters: Float,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float
) {
    fun formattedDescription(): String {
        val distKm = String.format("%.1f", distanceMeters / 1000f)
        val durMin = (endTimeMs - startTimeMs) / 60000
        val avg = String.format("%.1f", avgSpeedKmh)
        return "· $distKm km • $durMin min • avg $avg km/h"
    }
}

/* ----------------------------- DATA CLASS Favorite ------------------------------------------ */
data class Favorite(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)



// ====== 2.b Funcții pentru Firestore: ======

/**
 * Salvează un TripRecord în colecția top‐level "trips".
 */
fun saveTripToFirestoreTopLevel(trip: TripRecord) {
    val uid = Firebase.auth.currentUser?.uid ?: return
    val data = hashMapOf(
        "uid" to uid,
        "startTimeMs" to trip.startTimeMs,
        "endTimeMs" to trip.endTimeMs,
        "distanceMeters" to trip.distanceMeters,
        "avgSpeedKmh" to trip.avgSpeedKmh,
        "maxSpeedKmh" to trip.maxSpeedKmh,
        "createdAt" to FieldValue.serverTimestamp()
    )
    Firebase.firestore
        .collection("trips")
        .add(data)
        .addOnSuccessListener {
            // Trip salvat cu succes
        }
        .addOnFailureListener { e ->
            // Eroare la salvare
        }
}

/**
 * Încarcă ultimele 10 TripRecord‐uri ale utilizatorului curent din colecția top‐level "trips".
 */
fun loadLast10TripsFromFirestoreTopLevel(onLoaded: (List<TripRecord>) -> Unit) {
    val uid = Firebase.auth.currentUser?.uid ?: return
    Firebase.firestore
        .collection("trips")
        .whereEqualTo("uid", uid)
        .orderBy("startTimeMs", Query.Direction.DESCENDING)
        .limit(10)
        .get()
        .addOnSuccessListener { querySnapshot ->
            val list = mutableListOf<TripRecord>()
            for (doc in querySnapshot.documents) {
                val start = doc.getLong("startTimeMs") ?: continue
                val end = doc.getLong("endTimeMs") ?: continue
                val dist = doc.getDouble("distanceMeters")?.toFloat() ?: continue
                val avg  = doc.getDouble("avgSpeedKmh")?.toFloat() ?: continue
                val max  = doc.getDouble("maxSpeedKmh")?.toFloat() ?: continue
                list.add(
                    TripRecord(
                        id = doc.id.hashCode().toLong(),
                        startTimeMs = start,
                        endTimeMs = end,
                        distanceMeters = dist,
                        avgSpeedKmh = avg,
                        maxSpeedKmh = max
                    )
                )
            }
            onLoaded(list)
        }
        .addOnFailureListener {
            onLoaded(emptyList())
        }
}



// ====== 3. Composable MapPage cu integrare Firestore ======

@SuppressLint("MissingPermission")
@Composable
fun MapPage(
    onNavigateBack: () -> Unit,
    onNavigateToSwitchPage: () -> Unit,
    onNavigateToWalletPage: () -> Unit
) {
    val context = LocalContext.current
    val apiKey = context.getString(R.string.google_api_key)

    // 1) Inițializează Google Places API (o singură dată)
    LaunchedEffect(Unit) {
        if (!Places.isInitialized()) {
            Places.initialize(context, apiKey)
        }
    }
    val placesClient = remember { Places.createClient(context) }

    // 2) Starea camerei și a locației curente
    val cameraPositionState = rememberCameraPositionState()
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }

    // 3) Viteză & status & pauze ≤ 5 min
    var currentSpeedKmh by remember { mutableStateOf(0f) }
    var status by remember { mutableStateOf("No Move") }
    var statusEmoji by remember { mutableStateOf("🛑") }
    var stopStartTimestamp by remember { mutableStateOf<Long?>(null) }
    var lastDriveTimestamp by remember { mutableStateOf<Long?>(null) }

    // 4) Search / Autocomplete state
    var query by remember { mutableStateOf("") }
    var predictions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }

    // 5) Pin roșu destinație + dialoguri + salvare favorite
    var selectedLocation by remember { mutableStateOf<LatLng?>(null) }
    var showNavigateDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var favoriteName by remember { mutableStateOf("") }

    // FLAG: dacă marker-ul curent este un favorit
    var isFavoriteSelected by remember { mutableStateOf(false) }

    // 6) My Last 10 Trips (din Firestore)
    val tripsList = remember { mutableStateListOf<TripRecord>() }
    var showTripsDialog by remember { mutableStateOf(false) }

    // Încarcă ultimele 10 tripuri la lansare:
    LaunchedEffect(Unit) {
        loadLast10TripsFromFirestoreTopLevel { loaded ->
            tripsList.clear()
            tripsList.addAll(loaded)
        }
    }

    // 7) Favorites list + dialog de vizualizare + ștergere
    val favoritesList = remember { mutableStateListOf<Favorite>() }
    var showFavoritesDialog by remember { mutableStateOf(false) }
    var favoriteToDelete by remember { mutableStateOf<Favorite?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // ─── Logica autocomplete (exact cum era inițial) ───
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

    // ==== Cerem permisiunea și pornim GPS-ul ====
    RequestLocationPermission {
        startLocationUpdates(context) { lat, lng, speedKmh, timestamp ->
            val newLatLng = LatLng(lat, lng)
            currentLocation = newLatLng
            cameraPositionState.position = CameraPosition.fromLatLngZoom(newLatLng, 15f)
            currentSpeedKmh = speedKmh

            if (speedKmh >= 10f) {
                lastDriveTimestamp = timestamp
                stopStartTimestamp = null
                status = "Drive"
                statusEmoji = "🚗"
            } else {
                val now = timestamp
                if (lastDriveTimestamp != null) {
                    if (stopStartTimestamp == null) {
                        stopStartTimestamp = now
                    }
                    val elapsed = now - (stopStartTimestamp ?: now)
                    if (elapsed >= 5 * 60 * 1000) {
                        status = "Walking"
                        statusEmoji = "🚶"
                    } else {
                        status = "Drive"
                        statusEmoji = "🚗"
                    }
                } else {
                    if (stopStartTimestamp == null) {
                        stopStartTimestamp = now
                    }
                    val elapsed = now - (stopStartTimestamp ?: now)
                    if (elapsed >= 5 * 60 * 1000) {
                        status = "Walking"
                        statusEmoji = "🚶"
                    } else {
                        status = "No Move"
                        statusEmoji = "🛑"
                    }
                }
            }
        }
    }

    /* ─────────────── UI-ul principal ────────────── */
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // — 1️⃣ Search bar + sugestii
        Column {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    isFavoriteSelected = false
                },
                label = { Text("Search location") },
                modifier = Modifier.fillMaxWidth()
            )
            predictions.forEach { prediction ->
                TextButton(onClick = {
                    query = prediction.getFullText(null).toString()
                    predictions = emptyList()
                    val placeId = prediction.placeId
                    val placeFields = listOf(Place.Field.LAT_LNG)
                    val fetchRequest =
                        FetchPlaceRequest.builder(placeId, placeFields).build()
                    placesClient.fetchPlace(fetchRequest)
                        .addOnSuccessListener { response ->
                            response.place.latLng?.let { latLng ->
                                selectedLocation = latLng
                                isFavoriteSelected = false
                                cameraPositionState.position =
                                    CameraPosition.fromLatLngZoom(latLng, 15f)
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Search error", Toast.LENGTH_SHORT)
                                .show()
                        }
                }) {
                    Text(text = prediction.getFullText(null).toString())
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // — 2️⃣ HARTĂ + Markere + 2 FAB-uri
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            GoogleMap(
                modifier = Modifier.matchParentSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = true),
                uiSettings = MapUiSettings(zoomControlsEnabled = true),
                onMapClick = { latLng ->
                    selectedLocation = latLng
                    isFavoriteSelected = false
                }
            ) {
                // Marker pentru locația curentă (icon_car_loc)
                currentLocation?.let { loc ->
                    val iconCurrent =
                        bitmapDescriptorFromVector(context, R.drawable.icon_car_loc)
                    Marker(
                        state = MarkerState(position = loc),
                        title = "Your Location",
                        icon = iconCurrent
                    )
                }

                // Marker roșu sau fav_pin pentru destinație
                selectedLocation?.let { dest ->
                    val iconRes = if (isFavoriteSelected) {
                        R.drawable.fav_loc
                    } else {
                        R.drawable.car_loc_red
                    }
                    val iconDest = bitmapDescriptorFromVector(context, iconRes)
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

            // Buton “View Trips” (FAB stânga-jos)
            FloatingActionButton(
                onClick = { showTripsDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 66.dp)
                    .size(56.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.car_icon),
                    contentDescription = "View Trips",
                    tint = Color.White
                )
            }

            // Buton “View Favorites” (FAB dreapta-jos)
            FloatingActionButton(
                onClick = {
                    val uid = Firebase.auth.currentUser?.uid
                    if (uid == null) {
                        Toast.makeText(
                            context,
                            "Please log in to see favorites",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Firebase.firestore
                            .collection("fav-locations")
                            .whereEqualTo("uid", uid)
                            .get()
                            .addOnSuccessListener { querySnapshot ->
                                favoritesList.clear()
                                for (doc in querySnapshot.documents) {
                                    val name = doc.getString("name") ?: continue
                                    val lat = doc.getDouble("latitude") ?: continue
                                    val lng = doc.getDouble("longitude") ?: continue
                                    favoritesList.add(
                                        Favorite(
                                            id = doc.id,
                                            name = name,
                                            latitude = lat,
                                            longitude = lng
                                        )
                                    )
                                }
                                showFavoritesDialog = true
                            }
                            .addOnFailureListener {
                                Toast.makeText(
                                    context,
                                    "Error loading favorites.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 66.dp)
                    .size(56.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.fav_loc1),
                    contentDescription = "View Favorites"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // — 3️⃣ Buton “Add to Favorites” (sub hartă)
        selectedLocation?.let {
            Button(
                onClick = { showNameDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Add to Favorites")
            }
        }

        // — 4️⃣ STATUS + EMOJI + VITEZĂ
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$statusEmoji  $status",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = String.format("%.0f km/h", currentSpeedKmh),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // — 5️⃣ Bara de navigare jos (Home, Map, Garage, Settings)
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
            IconButton(onClick = { /* deja suntem pe hartă */ }, modifier = Modifier.size(50.dp)) {
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

    // ─────────────── Dialog: “Nume Favorite” ───────────────
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Enter Favorite Name") },
            text = {
                OutlinedTextField(
                    value = favoriteName,
                    onValueChange = { favoriteName = it },
                    label = { Text("Ex: Home, Work…") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val uid = Firebase.auth.currentUser?.uid
                    if (uid == null) {
                        Toast.makeText(
                            context,
                            "You must be logged in",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else if (favoriteName.isBlank()) {
                        Toast.makeText(
                            context,
                            "Name cannot be empty",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val data = hashMapOf(
                            "name" to favoriteName,
                            "latitude" to selectedLocation!!.latitude,
                            "longitude" to selectedLocation!!.longitude,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "uid" to uid
                        )
                        Firebase.firestore
                            .collection("fav-locations")
                            .add(data)
                            .addOnSuccessListener {
                                Toast.makeText(
                                    context,
                                    "Saved to Favorites!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(
                                    context,
                                    "Error saving favorite",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                    favoriteName = ""
                    showNameDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    favoriteName = ""
                    showNameDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ─────────────── Dialog: “Navigate” ───────────────
    if (showNavigateDialog && selectedLocation != null) {
        AlertDialog(
            onDismissRequest = { showNavigateDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val lat = selectedLocation!!.latitude
                    val lng = selectedLocation!!.longitude
                    val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
                    val mapIntent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        gmmIntentUri
                    ).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    if (mapIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(mapIntent)
                    } else {
                        Toast.makeText(
                            context,
                            "Google Maps nu este instalat",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    showNavigateDialog = false
                }) {
                    Text(text = "Navigate în Google Maps")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNavigateDialog = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Navigare către destinație") },
            text = { Text("Se deschide Google Maps pentru direcții către destinație.") }
        )
    }

    // ─────────────── Dialog: “My Last 10 Trips” ───────────────
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tripsList.isEmpty()) {
                        Text(text = "No trips yet.")
                    } else {
                        tripsList.forEach { trip ->
                            Text(text = trip.formattedDescription())
                        }
                    }
                }
            }
        )
    }

    // ─────────── Dialog: “View Favorites” ───────────
    if (showFavoritesDialog) {
        AlertDialog(
            onDismissRequest = { showFavoritesDialog = false },
            title = { Text("Your Favorites") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (favoritesList.isEmpty()) {
                        Text(text = "No favorites yet.")
                    } else {
                        favoritesList.forEach { fav ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1) Clic pe nume => centrăm harta și punem fav_pin
                                Text(
                                    text = fav.name,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            val pos = LatLng(fav.latitude, fav.longitude)
                                            selectedLocation = pos
                                            isFavoriteSelected = true
                                            cameraPositionState.position =
                                                CameraPosition.fromLatLngZoom(pos, 15f)
                                            showFavoritesDialog = false
                                        }
                                )
                                // 2) Iconiță “X” pentru ștergere
                                IconButton(onClick = {
                                    favoriteToDelete = fav
                                    showDeleteConfirmDialog = true
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete",
                                        tint = Color.Red
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFavoritesDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // ─────────── Dialog: Confirmare ștergere favorite ───────────
    if (showDeleteConfirmDialog && favoriteToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Remove Favorite") },
            text = { Text("Do you want to remove \"${favoriteToDelete!!.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    Firebase.firestore
                        .collection("fav-locations")
                        .document(favoriteToDelete!!.id)
                        .delete()
                        .addOnSuccessListener {
                            favoritesList.remove(favoriteToDelete!!)
                            Toast.makeText(
                                context,
                                "Favorite removed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                context,
                                "Error removing favorite",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    favoriteToDelete = null
                    showDeleteConfirmDialog = false
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    favoriteToDelete = null
                    showDeleteConfirmDialog = false
                }) {
                    Text("No")
                }
            }
        )
    }
}

/* ------------------------------ Request Location Permission ------------------------------ */
@Composable
fun RequestLocationPermission(onPermissionGranted: () -> Unit) {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { granted ->
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
