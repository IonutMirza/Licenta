package com.example.licenta

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import java.io.File
import android.net.Uri
import com.example.licenta.utils.refreshAllUserStats
import com.example.licenta.utils.refreshUserStatsFromTrips
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import android.util.Log

private fun emailToUsername(email: String?): String {
    return email?.substringBefore("@") ?: "-"
}


fun fetchUserStats(
    context: Context,
    onStatsLoaded: (tripCount: Long, avgScore: Double) -> Unit
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    Firebase.firestore.collection("user-stats")
        .document(uid)
        .get()
        .addOnSuccessListener { doc ->
            val tripCount = doc.getLong("tripCount") ?: 0L
            val totalScore = doc.getDouble("totalScore") ?: 0.0
            val avgScore = if (tripCount > 0) totalScore / tripCount else 0.0
            onStatsLoaded(tripCount, avgScore)
        }
        .addOnFailureListener {
            Toast.makeText(context, "Failed to load user stats", Toast.LENGTH_SHORT).show()
            onStatsLoaded(0, 0.0)
        }
}

fun disableAllAppNotifications(ctx: Context) {
    // 1) Anulează orice WorkManager cu tag-ul nostru
    androidx.work.WorkManager.getInstance(ctx)
        .cancelAllWorkByTag("app_notification")

    // 2) Şterge notificările deja afişate
    androidx.core.app.NotificationManagerCompat.from(ctx).cancelAll()
}

@Composable
fun SettingsPage(
    onNavigateBack: () -> Unit,
    onNavigateToGaragePage: () -> Unit,
    onNavigateToMapPage: () -> Unit,
    onLogoutNavigateToSignUp: () -> Unit
) {
    val context = LocalContext.current
    val user = FirebaseAuth.getInstance().currentUser
    val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var notificationsEnabled by remember {
        mutableStateOf(prefs.getBoolean("notifications_enabled", true))
    }

    var darkModeEnabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean("dark_mode", false))
    }
    var locationTrackingEnabled by remember {
        mutableStateOf(prefs.getBoolean("location_tracking_enabled", true))
    }

    var showPasswordDialog by remember { mutableStateOf(false) }
    var showScoreDialog by remember { mutableStateOf(false) }

    val isDark = darkModeEnabled
    val backgroundColor = if (isDark) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDark) Color.White else Color.Black
    var totalTrips by remember { mutableStateOf(0L) }
    var avgScore by remember { mutableStateOf(0.0) }
    var isLeaderboardLoading by remember { mutableStateOf(false) }   // ↙ flag de „loading”
    var isScoreLoading by remember { mutableStateOf(false) }




    LaunchedEffect(user?.uid) {
        user?.uid?.let { uid ->
            refreshUserStatsFromTrips(uid) {           // ⇢ generează / actualizează doc-ul
                Firebase.firestore                      //    „user-stats/<uid>”
                    .collection("user-stats")
                    .document(uid)
                    .addSnapshotListener { doc, _ ->
                        if (doc != null && doc.exists()) {
                            val trips = doc.getLong("tripCount") ?: 0L
                            val total = doc.getDouble("totalScore") ?: 0.0
                            totalTrips = trips
                            avgScore   = if (trips > 0) total / trips else 0.0
                        }
                    }
            }
        }
    }

    var showLeaderboardDialog by remember { mutableStateOf(false) }
    data class RankRow(
        val uid: String,
        val avg: Double,
        val trips: Long,
        var username: String = " - ")
    val leaderboard = remember { mutableStateListOf<RankRow>() }


    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Settings",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    IconButton(
                        onClick = {
                            val uid = FirebaseAuth.getInstance().currentUser?.uid
                            if (uid != null) {
                                showScoreDialog = true
                                isScoreLoading = true

                                fetchUserStats(context) { trips, avg ->
                                    totalTrips = trips
                                    avgScore = avg
                                    isScoreLoading = false
                                }
                            } else {
                                Toast.makeText(context, "User not found", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.info),
                            contentDescription = "Info",
                            tint = textColor
                        )
                    }

                    IconButton(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .size(32.dp),
                    onClick = {
                        // 1. Deschidem dialogul instant
                        showLeaderboardDialog = true
                        isLeaderboardLoading  = true
                        leaderboard.clear()

                        // 2. Pornim încărcarea în background
                        refreshAllUserStats {
                            Firebase.firestore.collection("user-stats").get()
                                .addOnSuccessListener { statsSnap ->
                                    val tempRows = statsSnap.documents.mapNotNull { d ->
                                        val trips = d.getLong("tripCount") ?: return@mapNotNull null
                                        val total = d.getDouble("totalScore") ?: 0.0
                                        RankRow(
                                            uid    = d.id,
                                            avg    = if (trips > 0) total / trips else 0.0,
                                            trips  = trips
                                        )
                                    }

                                    // ─ aducem username-urile
                                    Firebase.firestore.collection("users").get()
                                        .addOnSuccessListener { userSnap ->
                                            val map = userSnap.documents.associate {
                                                it.id to emailToUsername(it.getString("email"))
                                            }

                                            leaderboard.clear()
                                            leaderboard.addAll(
                                                tempRows
                                                    .onEach { it.username = map[it.uid] ?: "-" }
                                                    .sortedWith(
                                                        compareByDescending<RankRow> { it.avg }
                                                            .thenByDescending { it.trips }
                                                            .thenBy { it.username.lowercase() }
                                                    )
                                            )
                                            isLeaderboardLoading = false   // gata!
                                        }
                                }
                        }
                    }
                    ) {
                    Icon(
                        painter = painterResource(id = R.drawable.trofeu),
                        contentDescription = "Leaderboard",
                        tint = textColor
                    )
                }

                }

                Spacer(modifier = Modifier.height(24.dp))

                ProfileSection(user, textColor)
                Spacer(modifier = Modifier.height(8.dp))

                AuthButtons(user, context, textColor)
                Spacer(modifier = Modifier.height(24.dp))

                SettingToggle("🔔 Notifications", notificationsEnabled, textColor) { enabled ->
                    notificationsEnabled = enabled
                    prefs.edit().putBoolean("notifications_enabled", enabled).apply()

                    if (enabled) {
                        Toast.makeText(context, "Notificările au fost activate", Toast.LENGTH_SHORT).show()
                    } else {
                        disableAllAppNotifications(context)           // ⬅️ funcţie adăugată la pasul 3
                        Toast.makeText(context, "Toate notificările au fost dezactivate", Toast.LENGTH_SHORT).show()
                    }
                }

                SettingToggle("🌙 Dark Mode", darkModeEnabled, textColor) {
                    darkModeEnabled = it
                    prefs.edit().putBoolean("dark_mode", it).apply()
                }

                SettingToggle("📍 Location Tracking", locationTrackingEnabled, textColor) { enabled ->
                    locationTrackingEnabled = enabled
                    prefs.edit().putBoolean("location_tracking_enabled", enabled).apply()

                    if (enabled) {
                        Toast.makeText(context, "Tracking-ul locației a fost activat", Toast.LENGTH_SHORT).show()
                    } else {
                        disableAllAppNotifications(context)           // ⬅️ funcţie adăugată la pasul 3
                        Toast.makeText(context, "Tracking-ul locației a fost dezactivat", Toast.LENGTH_SHORT).show()
                    }
                }

            }

            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = { showPasswordDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) { Text("Change Password", color = textColor) }

                Button(
                    onClick = {
                        FirebaseAuth.getInstance().signOut()
                        Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                        onLogoutNavigateToSignUp()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Log Out", color = Color.White) }

                Spacer(modifier = Modifier.height(30.dp))
            }
        }

        SettingsBottomNavigationBar(
            onNavigateBack = onNavigateBack,
            onNavigateToMapPage = onNavigateToMapPage,
            onNavigateToGaragePage = onNavigateToGaragePage
        )

        if (showPasswordDialog) {
            ChangePasswordDialog(user, context, textColor) {
                showPasswordDialog = false
            }
        }

        if (showScoreDialog) {
            AlertDialog(
                onDismissRequest = {
                    showScoreDialog = false
                    isScoreLoading = false
                },
                title = { Text("Scor utilizator", color = textColor) },
                text = {
                    if (isScoreLoading) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Se încarcă…", color = textColor)
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                String.format("%.1f/5 ⭐", avgScore),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Total curse efectuate: $totalTrips",
                                fontSize = 16.sp,
                                color = textColor
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showScoreDialog = false
                            isScoreLoading = false
                        }
                    ) {
                        Text("Închide", color = textColor)
                    }
                },
                containerColor = backgroundColor
            )
        }

        if (showLeaderboardDialog) if (showLeaderboardDialog) {
            AlertDialog(
                onDismissRequest = {
                    showLeaderboardDialog = false
                    isLeaderboardLoading  = false      // reset pt. următoarea deschidere
                },
                containerColor = backgroundColor,
                title = { Text("Clasament utilizatori", color = textColor) },
                text = {
                    when {
                        isLeaderboardLoading -> {               // 🔄 spinner
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text("Se încarcă…", color = textColor)
                            }
                        }

                        leaderboard.isEmpty() -> {              // fallback
                            Text("Nicio înregistrare disponibilă", color = textColor)
                        }

                        else -> {                               // rezultatele
                            var rank = 0
                            var lastPair: Pair<Double, Long>? = null

                            Column {
                                leaderboard.forEachIndexed { index, row ->
                                    val pair = row.avg to row.trips
                                    if (pair != lastPair) rank = index + 1
                                    lastPair = pair

                                    Text(
                                        "$rank.  ${row.username}  •  " +
                                                "${"%.2f".format(row.avg)}/5  •  ${row.trips} curse",
                                        color = textColor,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLeaderboardDialog = false
                            isLeaderboardLoading  = false
                        }
                    ) {
                        Text("Închide", color = textColor)
                    }
                }
            )
        }
    }
}

@Composable
fun ProfileSection(user: FirebaseUser?, textColor: Color) {
    val context = LocalContext.current
    val imageFile = File(context.filesDir, "profile_picture.jpg")
    val imageUriState = remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        if (imageFile.exists()) {
            imageUriState.value = android.net.Uri.fromFile(imageFile).buildUpon()
                .appendQueryParameter("ts", imageFile.lastModified().toString())
                .build()
        }
    }


    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val outputStream = imageFile.outputStream()
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()

                imageUriState.value = android.net.Uri.fromFile(imageFile).buildUpon()
                    .appendQueryParameter("ts", System.currentTimeMillis().toString())
                    .build()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        }
    }



    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = imageUriState.value?.let { rememberAsyncImagePainter(it) }
                ?: painterResource(id = R.drawable.default_profile),
            contentDescription = "Profile Picture",
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color.Gray)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { launcher.launch("image/*") }) {
            Text("Select Profile Picture", color = textColor)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text  = emailToUsername(user?.email),
            fontSize = 16.sp,
            color = textColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (user?.isEmailVerified == true) "Email Verified ✅" else "Email Not Verified ❌",
            color = if (user?.isEmailVerified == true) Color.Green else Color.Red,
            fontSize = 14.sp
        )
    }
}

@Composable
fun AuthButtons(user: FirebaseUser?, context: Context, textColor: Color) {
    if (user?.isEmailVerified == false) {
        Button(
            onClick = {
                user.sendEmailVerification().addOnCompleteListener {
                    if (it.isSuccessful)
                        Toast.makeText(context, "Verification email sent", Toast.LENGTH_SHORT).show()
                    else
                        Toast.makeText(context, "Failed to send verification email", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Verify Email", color = textColor)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    Button(
        onClick = {
            user?.email?.let { email ->
                FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Password reset email sent", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Reset Password", color = textColor)
    }
}

@Composable
fun ChangePasswordDialog(user: FirebaseUser?, context: Context, textColor: Color, onDismiss: () -> Unit) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Password", color = textColor) },
        text = {
            Column {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    label = { Text("Current Password", color = textColor) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password", color = textColor) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm New Password", color = textColor) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (newPassword != confirmPassword) {
                    Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                val email = user?.email
                if (email != null) {
                    val credential = EmailAuthProvider.getCredential(email, oldPassword)
                    user.reauthenticate(credential)
                        .addOnSuccessListener {
                            user.updatePassword(newPassword)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Password updated successfully", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "Update failed: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Wrong current password", Toast.LENGTH_SHORT).show()
                        }
                }
            }) {
                Text("Save", color = textColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textColor)
            }
        }
    )
}

@Composable
fun SettingToggle(label: String, state: Boolean, textColor: Color, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 18.sp, modifier = Modifier.weight(1f), color = textColor)
        Switch(checked = state, onCheckedChange = onToggle)
    }
}


@Composable
fun SettingsBottomNavigationBar(
    onNavigateBack: () -> Unit,
    onNavigateToMapPage: () -> Unit,
    onNavigateToGaragePage: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = onNavigateBack, modifier = Modifier.size(50.dp)) {
                Icon(painter = painterResource(id = R.drawable.home), contentDescription = "Home", tint = Color.LightGray)
            }
            IconButton(onClick = onNavigateToMapPage, modifier = Modifier.size(50.dp)) {
                Icon(painter = painterResource(id = R.drawable.map), contentDescription = "Map", tint = Color.LightGray)
            }
            IconButton(onClick = onNavigateToGaragePage, modifier = Modifier.size(50.dp)) {
                Icon(painter = painterResource(id = R.drawable.garage), contentDescription = "Garage", tint = Color.LightGray)
            }
            IconButton(onClick = { /* Already on Settings */ }, modifier = Modifier.size(50.dp)) {
                Icon(painter = painterResource(id = R.drawable.settings), contentDescription = "Settings", tint = Color.Black)
            }
        }
    }
}
