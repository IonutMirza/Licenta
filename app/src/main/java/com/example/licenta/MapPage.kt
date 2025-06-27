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
import androidx.compose.foundation.lazy.LazyColumn
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.TextButton
import kotlin.math.abs


fun updateUserTripStats(tripScore: Float) {
    val uid = Firebase.auth.currentUser?.uid ?: return
    val userStatsRef = Firebase.firestore.collection("user-stats").document(uid)

    userStatsRef.get()
        .addOnSuccessListener { doc ->
            if (doc != null && doc.exists()) {
                userStatsRef.update(
                    mapOf(
                        "tripCount" to FieldValue.increment(1),
                        "totalScore" to FieldValue.increment(tripScore.toDouble())
                    )
                )
            } else {
                val newStats = mapOf(
                    "tripCount" to 1,
                    "totalScore" to tripScore
                )
                userStatsRef.set(newStats)
            }
        }
}

private fun sendTripNotification(ctx: Context, trip: TripRecord) {
    val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    if (!prefs.getBoolean("notifications_enabled", true)) return
    val chanId = "trip_channel"
    val nm = NotificationManagerCompat.from(ctx)

    if (Build.VERSION.SDK_INT >= 26 &&
        nm.getNotificationChannel(chanId) == null) {
        nm.createNotificationChannel(
            NotificationChannel(
                chanId,
                "Cursă finalizată",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED) {
        return
    }

    val msg = buildString {
        append("Ai finalizat o călătorie de ")
        append(String.format("%.1f km în %d min", trip.distanceMeters/1000f,
            (trip.endTimeMs-trip.startTimeMs)/60000 ))
        append("\nScor: ${trip.score}/5  |  Viteză medie: ")
        append(String.format("%.1f", trip.avgSpeedKmh))
        append(" km/h")
    }

    val notif = NotificationCompat.Builder(ctx, chanId)
        .setSmallIcon(R.drawable.car_trip)
        .setContentTitle("Călătorie salvată")
        .setContentText(msg)
        .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
        .setAutoCancel(true)
        .build()

    nm.notify(trip.id.hashCode(), notif)
}



data class TripPoint(val lat: Double, val lng: Double)

fun loadTripPath(tripId: Long, onLoaded: (List<LatLng>) -> Unit) {
    Firebase.firestore
        .collection("trips")
        .document(tripId.toString())
        .collection("path")
        .orderBy("index")
        .get()
        .addOnSuccessListener { result ->
            val path = result.mapNotNull { doc ->
                val lat = doc.getDouble("lat") ?: return@mapNotNull null
                val lng = doc.getDouble("lng") ?: return@mapNotNull null
                LatLng(lat, lng)
            }
            onLoaded(path)
        }
        .addOnFailureListener {
            onLoaded(emptyList())
        }
}

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


data class TripRecord(
    val id: Long,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val distanceMeters: Float,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val hardBrakes: Int = 0,
    val hardAccelerations: Int = 0,
    val score: Float = 5.0f
) {
    fun formattedDescription(): String {
        val distKm = String.format("%.1f", distanceMeters / 1000f)
        val durMin = (endTimeMs - startTimeMs) / 60000
        val avg    = String.format("%.1f", avgSpeedKmh)
        val max    = String.format("%.0f", maxSpeedKmh)
        return buildString {
            append("· $distKm km • $durMin min\n")
            append("  avg $avg km/h • max $max km/h\n")
            append("  🚀 x$hardAccelerations  🛑 x$hardBrakes\n")
            append("  Rating: $score/5 ⭐")
        }
    }

}


data class Favorite(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

fun saveTripToFirestoreTopLevel(trip: TripRecord) {
    val uid = Firebase.auth.currentUser?.uid ?: return
    val data = hashMapOf(
        "uid" to uid,
        "startTimeMs" to trip.startTimeMs,
        "endTimeMs" to trip.endTimeMs,
        "distanceMeters" to trip.distanceMeters,
        "avgSpeedKmh" to trip.avgSpeedKmh,
        "maxSpeedKmh" to trip.maxSpeedKmh,
        "hardBrakes" to trip.hardBrakes,
        "hardAccelerations" to trip.hardAccelerations,
        "score" to trip.score,
        "tripId" to trip.id,
        "createdAt" to FieldValue.serverTimestamp()
    )
    Firebase.firestore
        .collection("trips")
        .document(trip.id.toString())
        .set(data)
        .addOnSuccessListener {
        }
        .addOnFailureListener { e ->
        }
}

fun saveTripPathToFirestore(tripId: Long, path: List<LatLng>) {
    val uid = Firebase.auth.currentUser?.uid ?: return
    val batch = Firebase.firestore.batch()
    val tripDoc = Firebase.firestore.collection("trips").document(tripId.toString())
    path.forEachIndexed { index, latLng ->
        val pointData = hashMapOf(
            "lat" to latLng.latitude,
            "lng" to latLng.longitude,
            "index" to index,
            "uid" to uid
        )
        val pointRef = tripDoc.collection("path").document(index.toString())
        batch.set(pointRef, pointData)
    }
    batch.commit()
}


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
                val id = doc.getLong("tripId") ?: continue
                val start = doc.getLong("startTimeMs") ?: continue
                val end = doc.getLong("endTimeMs") ?: continue
                val dist = doc.getDouble("distanceMeters")?.toFloat() ?: continue
                val avg  = doc.getDouble("avgSpeedKmh")?.toFloat() ?: continue
                val max  = doc.getDouble("maxSpeedKmh")?.toFloat() ?: continue
                val brakes = doc.getLong("hardBrakes")?.toInt() ?: 0
                val accels = doc.getLong("hardAccelerations")?.toInt() ?: 0
                val score = doc.getDouble("score")?.toFloat() ?: 5.0f

                list.add(
                    TripRecord(
                        id = id,
                        startTimeMs = start,
                        endTimeMs = end,
                        distanceMeters = dist,
                        avgSpeedKmh = avg,
                        maxSpeedKmh = max,
                        hardBrakes = brakes,
                        hardAccelerations = accels,
                        score = score
                    )
                )
            }
            onLoaded(list)
        }
        .addOnFailureListener {
            onLoaded(emptyList())
        }
}

@SuppressLint("MissingPermission")
@Composable
fun MapPage(
    onNavigateBack: () -> Unit,
    onNavigateToGaragePage: () -> Unit,
    onNavigateToSettingsPage: () -> Unit
) {

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {  }

    val context = LocalContext.current
    val apiKey = context.getString(R.string.google_api_key)

    LaunchedEffect(Unit) {
        if (!Places.isInitialized()) {
            Places.initialize(context, apiKey)
        }
    }
    val placesClient = remember { Places.createClient(context) }

    val cameraPositionState = rememberCameraPositionState()
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }

    var currentSpeedKmh by remember { mutableStateOf(0f) }
    var status by remember { mutableStateOf("Fără Mișcare") }
    var statusEmoji by remember { mutableStateOf("🛑") }
    var stopStartTimestamp by remember { mutableStateOf<Long?>(null) }
    var lastDriveTimestamp by remember { mutableStateOf<Long?>(null) }

    var query by remember { mutableStateOf("") }
    var predictions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }

    var selectedLocation by remember { mutableStateOf<LatLng?>(null) }
    var showNavigateDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var favoriteName by remember { mutableStateOf("") }

    var isFavoriteSelected by remember { mutableStateOf(false) }

    val tripsList = remember { mutableStateListOf<TripRecord>() }
    var showTripsDialog by remember { mutableStateOf(false) }

    var tripStartTimestamp by remember { mutableStateOf<Long?>(null) }
    var tripDistanceMeters by remember { mutableStateOf(0f) }
    var tripMaxSpeedKmh by remember { mutableStateOf(0f) }
    val tripPath = remember { mutableStateListOf<LatLng>() }

    var prevSpeedKmh by remember { mutableStateOf<Float?>(null) }
    var hardBrakes by remember { mutableStateOf(0) }
    var hardAccelerations by remember { mutableStateOf(0) }

    val selectedTripPath = remember { mutableStateListOf<LatLng>() }
    var selectedTripId by remember { mutableStateOf<Long?>(null) }

    val tripPathCache = remember { mutableStateMapOf<Long, List<LatLng>>() }
    val tripsDialogMaxHeight = 380.dp
    var showFullMapDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loadLast10TripsFromFirestoreTopLevel { loaded ->
            tripsList.clear()
            tripsList.addAll(loaded)
        }
    }

    val favoritesList = remember { mutableStateListOf<Favorite>() }
    var showFavoritesDialog by remember { mutableStateOf(false) }
    var favoriteToDelete by remember { mutableStateOf<Favorite?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

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
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val darkModeEnabled = remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    val isDark = darkModeEnabled.value

    val backgroundColor = if (isDark) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDark) Color.White else Color.Black

    RequestLocationPermission {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("location_tracking_enabled", true)) {
            Toast.makeText(context, "Tracking-ul locației este dezactivat", Toast.LENGTH_SHORT).show()
            return@RequestLocationPermission
        }

        startLocationUpdates(context) { lat, lng, speedKmh, timestamp ->
            val newLatLng = LatLng(lat, lng)
            if (tripStartTimestamp != null) {
                tripPath.add(newLatLng)
            }
            currentLocation = newLatLng
            cameraPositionState.position = CameraPosition.fromLatLngZoom(newLatLng, 15f)
            currentSpeedKmh = speedKmh

            if (speedKmh >= 10f && prevSpeedKmh != null && abs(speedKmh - prevSpeedKmh!!) > 3f){
            lastDriveTimestamp = timestamp
                stopStartTimestamp = null
                status = "Drive"
                statusEmoji = "🚗"

                if (tripStartTimestamp == null) {
                    tripStartTimestamp = timestamp
                    tripDistanceMeters = 0f
                    tripMaxSpeedKmh = speedKmh
                    hardBrakes = 0
                    hardAccelerations = 0
                } else {
                    tripDistanceMeters += 5f
                    if (speedKmh > tripMaxSpeedKmh) tripMaxSpeedKmh = speedKmh

                    prevSpeedKmh?.let { prev ->
                        val delta = speedKmh - prev
                        if (delta > 20) hardAccelerations++
                        if (delta < -20) hardBrakes++
                    }
                }
                prevSpeedKmh = speedKmh
            } else {
                prevSpeedKmh = speedKmh
                val now = timestamp

                if (stopStartTimestamp == null) {
                    stopStartTimestamp = now
                }

                val elapsed = now - stopStartTimestamp!!
                if (elapsed >= 5 * 60 * 1000) {
                    status = "Mers pe jos"
                    statusEmoji = "🚶"
                } else if (lastDriveTimestamp != null) {
                    status = "Condus"
                    statusEmoji = "🚗"
                } else {
                    status = "Nicio Mișcare"
                    statusEmoji = "🛑"
                }
            }
        }
    }


    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .background(backgroundColor)
        ){
        Column {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    isFavoriteSelected = false
                },
                label = { Text("Caută o locație", color= textColor) },
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val locationTrackingEnabled = remember { mutableStateOf(prefs.getBoolean("location_tracking_enabled", true)) }

            GoogleMap(
                modifier = Modifier.matchParentSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = locationTrackingEnabled.value),
                uiSettings = MapUiSettings(zoomControlsEnabled = true),
                onMapClick = { latLng ->
                    selectedLocation = latLng
                    isFavoriteSelected = false
                }
            ) {
                currentLocation?.let { loc ->
                    val iconCurrent =
                        bitmapDescriptorFromVector(context, R.drawable.icon_car_loc)
                    Marker(
                        state = MarkerState(position = loc),
                        title = "Locația ta",
                        icon = iconCurrent
                    )
                }

                selectedLocation?.let { dest ->
                    val iconRes = if (isFavoriteSelected) {
                        R.drawable.fav_loc
                    } else {
                        R.drawable.car_loc_red
                    }
                    val iconDest = bitmapDescriptorFromVector(context, iconRes)
                    Marker(
                        state = MarkerState(position = dest),
                        title = "Destinație",
                        icon = iconDest,
                        onClick = {
                            showNavigateDialog = true
                            true
                        }
                    )
                }
                if (selectedTripPath.isNotEmpty()) {
                    Polyline(
                        points = selectedTripPath.toList(),
                        color = Color.Blue,
                        width = 6f
                    )
                }

            }

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
                    contentDescription = "Vizualizează cursele",
                    tint = Color.White
                )
            }

            FloatingActionButton(
                onClick = {
                    val uid = Firebase.auth.currentUser?.uid
                    if (uid == null) {
                        Toast.makeText(
                            context,
                            "Te rog să te conectezi pentru a vedea locațiile favorite",
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
                                    "Eroare la încărcarea locațiilor favorite.",
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
                    contentDescription = "Vizualizare locații favorite"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        selectedLocation?.let {
            Button(
                onClick = { showNameDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = textColor
                )
            )
            {
                Text("Adaugă la favorite")

            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(backgroundColor),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$statusEmoji  $status",
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
                Spacer(modifier = Modifier.width(16.dp))
                val displayedSpeed = when (status) {
                    "Condus", "Mers pe jos" -> currentSpeedKmh
                    else -> 0f
                }

                Text(
                    text = String.format("%.0f km/h", displayedSpeed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
            }

            if (tripStartTimestamp != null) {
                Button(
                    onClick = {
                        val now = System.currentTimeMillis()
                        val durationMs = now - tripStartTimestamp!!
                        if (durationMs >= 10000) {
                            val avgSpeed =
                                if (durationMs > 0) (tripDistanceMeters / (durationMs / 1000f)) * 3.6f else 0f
                            val penalties = hardBrakes + hardAccelerations
                            val score = (5.0f - 0.1f * penalties).coerceIn(0f, 5.0f)
                            val trip = TripRecord(
                                id = System.currentTimeMillis(),
                                startTimeMs = tripStartTimestamp!!,
                                endTimeMs = now,
                                distanceMeters = tripDistanceMeters,
                                avgSpeedKmh = avgSpeed,
                                maxSpeedKmh = tripMaxSpeedKmh,
                                hardBrakes = hardBrakes,
                                hardAccelerations = hardAccelerations,
                                score = score
                            )
                            saveTripToFirestoreTopLevel(trip)
                            saveTripPathToFirestore(trip.id, tripPath.toList())
                            sendTripNotification(context, trip)
                            updateUserTripStats(score)
                            Toast.makeText(context, "Cursă salvată manual", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Cursa este prea scurtă ca sa fie salvată", Toast.LENGTH_SHORT).show()
                        }
                        tripStartTimestamp = null
                        tripDistanceMeters = 0f
                        tripMaxSpeedKmh = 0f
                        hardBrakes = 0
                        hardAccelerations = 0
                        prevSpeedKmh = null
                        tripPath.clear()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Text("Salvează Cursa", color = textColor)
                }
            }

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
            IconButton(onClick = { }, modifier = Modifier.size(50.dp)) {
                Icon(
                    painter = painterResource(R.drawable.map),
                    contentDescription = "Map",
                    tint = Color.Black
                )
            }
            IconButton(onClick = onNavigateToGaragePage, modifier = Modifier.size(50.dp)) {
                Icon(
                    painter = painterResource(R.drawable.garage),
                    contentDescription = "Garage",
                    tint = Color.LightGray
                )
            }
            IconButton(onClick = onNavigateToSettingsPage, modifier = Modifier.size(50.dp)) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = "Settings",
                    tint = Color.LightGray
                )
            }
        }
            }
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            containerColor = backgroundColor,
            title = { Text("Adaugă numele locației favorite", color=textColor) },
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
                            "Trebuie să fi conectat",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else if (favoriteName.isBlank()) {
                        Toast.makeText(
                            context,
                            "Numele nu poate fi gol",
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
                                    "Salvat la favorite!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(
                                    context,
                                    "Eroare în salvarea la favorite",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                    favoriteName = ""
                    showNameDialog = false
                }) {
                    Text("Salvare")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    favoriteName = ""
                    showNameDialog = false
                }) {
                    Text("Anulare")
                }
            }
        )
    }

    if (showNavigateDialog && selectedLocation != null) {
        AlertDialog(
            onDismissRequest = { showNavigateDialog = false },
            containerColor = backgroundColor,
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
                    Text(text = "Navigare în Google Maps", color = textColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNavigateDialog = false }) {
                    Text("Anulare")
                }
            },
            title = { Text("Navigare către destinație", color=textColor) },
            text = { Text("Se deschide Google Maps pentru direcții către destinație.") }
        )
    }
        if (showTripsDialog) {
            AlertDialog(
                onDismissRequest = { showTripsDialog = false },
                containerColor   = backgroundColor,
                confirmButton = {
                    TextButton(onClick = { showTripsDialog = false }) { Text("Close") }
                },
                title = { Text("Ultimele 10 curse ale mele", color = textColor) },
                text  = {
                    if (tripsList.isEmpty()) {
                        Text("Nicio cursă salvată", color = textColor)
                    } else {
                        LazyColumn(
                            state  = rememberLazyListState(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = tripsDialogMaxHeight),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {

                            itemsIndexed(tripsList, key = { _, t -> t.id }) { index, trip ->

                                val miniMapPath = tripPathCache[trip.id] ?: emptyList()
                                LaunchedEffect(miniMapPath.isEmpty()) {
                                    if (miniMapPath.isEmpty()) {
                                        loadTripPath(trip.id) { path ->
                                            tripPathCache[trip.id] = path
                                        }
                                    }
                                }

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (index % 2 == 0)
                                            Color(0xFFBBDEFB)
                                        else
                                            Color(0xFFC8E6C9)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {

                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "Cursa ${index+1}",
                                                fontWeight = FontWeight.Bold,
                                                color      = Color.Black
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                trip.formattedDescription(),
                                                fontSize = 12.sp,
                                                color    = Color.DarkGray
                                            )
                                        }

                                        Spacer(Modifier.width(6.dp))

                                        val miniCam = rememberCameraPositionState()

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.width(90.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(90.dp)
                                                    .clip(MaterialTheme.shapes.small)
                                            ) {
                                                GoogleMap(
                                                    modifier            = Modifier.matchParentSize(),
                                                    cameraPositionState = miniCam,
                                                    uiSettings = MapUiSettings(
                                                        zoomControlsEnabled   = false,
                                                        scrollGesturesEnabled = false,
                                                        tiltGesturesEnabled   = false,
                                                        zoomGesturesEnabled   = false
                                                    ),
                                                    properties = MapProperties(mapType = MapType.NORMAL)
                                                ) {
                                                    if (miniMapPath.isNotEmpty()) {
                                                        Polyline(
                                                            points = miniMapPath,
                                                            color  = Color.Red,
                                                            width  = 4f
                                                        )

                                                        Marker(
                                                            state = MarkerState(miniMapPath.first()),
                                                            title = "Start",
                                                            icon  = BitmapDescriptorFactory.defaultMarker(
                                                                BitmapDescriptorFactory.HUE_GREEN
                                                            )
                                                        )

                                                        Marker(
                                                            state = MarkerState(miniMapPath.last()),
                                                            title = "Sfârșit",
                                                            icon  = BitmapDescriptorFactory.defaultMarker(
                                                                BitmapDescriptorFactory.HUE_RED
                                                            )
                                                        )

                                                        LaunchedEffect(Unit) {
                                                            val b = LatLngBounds.builder().apply {
                                                                miniMapPath.forEach { include(it) }
                                                            }.build()
                                                            miniCam.move(CameraUpdateFactory.newLatLngBounds(b, 20))
                                                        }
                                                    }
                                                }
                                            }

                                            TextButton(
                                                onClick = {
                                                    selectedTripPath.clear()
                                                    val cached = tripPathCache[trip.id]
                                                    if (!cached.isNullOrEmpty()) {
                                                        selectedTripPath.addAll(cached)
                                                        showFullMapDialog = true
                                                    } else {
                                                        loadTripPath(trip.id) { path ->
                                                            tripPathCache[trip.id] = path
                                                            selectedTripPath.clear()
                                                            selectedTripPath.addAll(path)
                                                            showFullMapDialog = true
                                                        }
                                                    }
                                                },
                                                contentPadding = PaddingValues(0.dp),
                                                modifier       = Modifier.fillMaxWidth()
                                            ) {
                                                Text(" Harta mare", fontSize = 12.sp, color = Color.Black)
                                            }
                                        }

                                    }
                                }
                            }
                        }
                    }
                }
            )
        }

        if (showFullMapDialog) {
            val scrH = LocalContext.current.resources.displayMetrics.heightPixels.dp

            AlertDialog(
                onDismissRequest = { showFullMapDialog = false },
                confirmButton = {
                    TextButton(onClick = { showFullMapDialog = false }) {
                        Text("Închide")
                    }
                },
                title = { Text("Vizualizare cursă întreagă") },
                text = {
                    if (selectedTripPath.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            val bigCam = rememberCameraPositionState()
                            GoogleMap(
                                modifier = Modifier.matchParentSize(),
                                cameraPositionState = bigCam,
                                uiSettings = MapUiSettings(zoomControlsEnabled = true)
                            ) {
                                Polyline(
                                    points = selectedTripPath,
                                    color = Color.Blue,
                                    width = 6f
                                )

                                Marker(
                                    state = MarkerState(selectedTripPath.first()),
                                    title = "Start",
                                    icon = BitmapDescriptorFactory.defaultMarker(
                                        BitmapDescriptorFactory.HUE_GREEN
                                    )
                                )
                                Marker(
                                    state = MarkerState(selectedTripPath.last()),
                                    title = "Sfârșit",
                                    icon = BitmapDescriptorFactory.defaultMarker(
                                        BitmapDescriptorFactory.HUE_RED
                                    )
                                )

                                LaunchedEffect(Unit) {
                                    val bounds = LatLngBounds.builder().apply {
                                        selectedTripPath.forEach { include(it) }
                                    }.build()
                                    bigCam.move(
                                        CameraUpdateFactory.newLatLngBounds(bounds, 40)
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }

    if (showFavoritesDialog) {
        AlertDialog(
            onDismissRequest = { showFavoritesDialog = false },
            containerColor = backgroundColor,
            title = { Text("Favoritele tale", color=textColor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (favoritesList.isEmpty()) {
                        Text(text = "Nu sunt favorite încă.")
                    } else {
                        favoritesList.forEach { fav ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                                IconButton(onClick = {
                                    favoriteToDelete = fav
                                    showDeleteConfirmDialog = true
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Șterge",
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
                    Text("Închide")
                }
            }
        )
    }

    if (showDeleteConfirmDialog && favoriteToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Șterge Favorite", color=textColor) },
            text = { Text("Ești sigur că vrei să ștergi \"${favoriteToDelete!!.name}\"?") },
            containerColor = backgroundColor,
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
                                "Favorită ștearsă",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                context,
                                "Eroare la ștergerea favoritei",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    favoriteToDelete = null
                    showDeleteConfirmDialog = false
                }) {
                    Text("Da")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    favoriteToDelete = null
                    showDeleteConfirmDialog = false
                }) {
                    Text("Nu")
                }
            }
        )
    }
}
}

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
                    "Permisiunile pentru locație sunt necesare",
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
