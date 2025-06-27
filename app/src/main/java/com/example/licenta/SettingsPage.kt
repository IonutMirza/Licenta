package com.example.licenta

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase



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
            Toast.makeText(context, "Eroare la afisearea statisticilor user-ului", Toast.LENGTH_SHORT).show()
            onStatsLoaded(0, 0.0)
        }
}

fun disableAllAppNotifications(ctx: Context) {
    androidx.work.WorkManager.getInstance(ctx)
        .cancelAllWorkByTag("app_notification")

    androidx.core.app.NotificationManagerCompat.from(ctx).cancelAll()
}


@Composable
fun SettingsPage(
    onNavigateBack: () -> Unit,
    onNavigateToGaragePage: () -> Unit,
    onNavigateToMapPage: () -> Unit,
    onLogoutNavigateToSignUp: () -> Unit
) {
    val ctx   = LocalContext.current
    val user  = FirebaseAuth.getInstance().currentUser
    val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var notificationsEnabled   by remember { mutableStateOf(prefs.getBoolean("notifications_enabled", true)) }
    var darkModeEnabled        by rememberSaveable { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    var locationTrackingEnabled by remember { mutableStateOf(prefs.getBoolean("location_tracking_enabled", true)) }

    var showPwdDialog  by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showLbDialog   by remember { mutableStateOf(false) }

    var tripCount by remember { mutableStateOf(0L) }
    var avgScore  by remember { mutableStateOf(0.0) }
    var infoLoaded by remember { mutableStateOf(false) }

    data class RankRow(val uid: String, val avg: Double, val trips: Long, val username: String)
    val leaderboard = remember { mutableStateListOf<RankRow>() }
    var lbLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(user?.uid) {
        user?.uid?.let { uid ->
            Firebase.firestore.collection("user-stats").document(uid).get()
                .addOnSuccessListener { doc ->
                    tripCount = doc.getLong("tripCount") ?: 0
                    val total = doc.getDouble("totalScore") ?: 0.0
                    avgScore  = if (tripCount > 0) total / tripCount else 0.0
                    infoLoaded = true
                }
        }
    }

    LaunchedEffect(Unit) {
        val db = Firebase.firestore
        db.collection("user-stats").get().addOnSuccessListener { snap ->
            val rows = snap.documents.mapNotNull { d ->
                val trips = d.getLong("tripCount") ?: return@mapNotNull null
                if (trips == 0L) return@mapNotNull null
                val total = d.getDouble("totalScore") ?: 0.0
                RankRow(d.id, total / trips, trips, "")
            }
            db.collection("users").get().addOnSuccessListener { usnap ->
                val emailMap = usnap.associate { it.id to (it.getString("email")?.substringBefore("@") ?: "User") }
                leaderboard.clear()
                leaderboard.addAll(
                    rows.map { it.copy(username = emailMap[it.uid] ?: "-") }
                        .sortedWith(
                            compareByDescending<RankRow> { it.avg }
                                .thenByDescending { it.trips }
                                .thenBy { it.username.lowercase() }
                        )
                )
                lbLoaded = true
            }
        }
    }

    val bg = if (darkModeEnabled) Color(0xFF1E1E1E) else Color.White
    val fg = if (darkModeEnabled) Color.White else Color.Black
    var showHelpDialog by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize().background(bg), color = bg) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth()) {

                    Text(
                        "Settings",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = fg,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    IconButton(
                        onClick = { showLbDialog = true },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Image(painterResource(R.drawable.trofeu), contentDescription = "Clasament")
                    }

                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { showInfoDialog = true }) {
                            Image(painterResource(R.drawable.info), contentDescription = "Statistici personale")
                        }
                        IconButton(onClick = { showHelpDialog = true }) {
                            Image(painterResource(R.drawable.help), contentDescription = "Ghid utilizare")
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                ProfileSection(user, fg)
                Spacer(Modifier.height(8.dp))
                AuthButtons(user, ctx, fg)
                Spacer(Modifier.height(24.dp))

                SettingToggle("🔔 Notificări", notificationsEnabled, fg) {
                    notificationsEnabled = it
                    prefs.edit().putBoolean("notifications_enabled", it).apply()
                    if (!it) disableAllAppNotifications(ctx)
                }
                SettingToggle("🌙 Mod Întunecat", darkModeEnabled, fg) {
                    darkModeEnabled = it
                    prefs.edit().putBoolean("dark_mode", it).apply()
                }
                SettingToggle("📍 Locația actuală", locationTrackingEnabled, fg) {
                    locationTrackingEnabled = it
                    prefs.edit().putBoolean("location_tracking_enabled", it).apply()
                    if (!it) disableAllAppNotifications(ctx)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Button(
                    onClick = { showPwdDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) { Text("Schimbă Parola", color = fg) }

                Button(
                    onClick = {
                        FirebaseAuth.getInstance().signOut()
                        Toast.makeText(ctx, "Deconectare cu succes", Toast.LENGTH_SHORT).show()
                        onLogoutNavigateToSignUp()
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Deconectare", color = Color.White) }

                Spacer(Modifier.height(30.dp))
            }
        }

        SettingsBottomNavigationBar(onNavigateBack, onNavigateToMapPage, onNavigateToGaragePage)

        if (showPwdDialog)
            ChangePasswordDialog(user, ctx, fg) { showPwdDialog = false }

        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                containerColor = bg,
                title = { Text("Statistici personale", color = fg) },
                text  = {
                    if (!infoLoaded) Text("Se încarcă…", color = fg)
                    else Column {
                        Text("Total curse: $tripCount", color = fg, fontSize = 14.sp)
                        Text("Scor mediu: ${"%.2f".format(avgScore)} / 5", color = fg, fontSize = 14.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("Închide", color = fg)
                    }
                }
            )
        }

        if (showLbDialog) {
            AlertDialog(
                onDismissRequest = { showLbDialog = false },
                containerColor = bg,
                title = { Text("Clasament utilizatori", color = fg) },
                text = {
                    if (!lbLoaded) Text("Se încarcă…", color = fg)
                    else {
                        var rank = 0; var last = -1.0
                        Column {
                            leaderboard.forEachIndexed { idx, row ->
                                if (row.avg != last) rank = idx + 1
                                last = row.avg
                                Text(
                                    "$rank.  ${row.username}  •  ${"%.2f".format(row.avg)} / 5  •  ${row.trips} curse",
                                    color = fg, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLbDialog = false }) {
                        Text("Închide", color = fg)
                    }
                }
            )
        }

        if (showHelpDialog) {

            val scroll = rememberScrollState()

            AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                containerColor   = bg,
                title = { Text("Ghid rapid de utilizare", color = fg) },

                text = {
                    Column(Modifier.verticalScroll(scroll)) {

                        Text("🚙  Home", color = fg, fontWeight = FontWeight.Bold)
                        Text("• Conectare OBD – buton „Connect to OBD”.",  color = fg)
                        Text("• Vizualizare date live (RPM, viteză, consum).", color = fg)
                        Text("• Citire / ștergere coduri eroare ECU.", color = fg)
                        Text("• Adăugare / selectare mașină.", color = fg)
                        Spacer(Modifier.height(8.dp))

                        Text("🗺️  Map", color = fg, fontWeight = FontWeight.Bold)
                        Text("• Căutare locație și afișare pe hartă.", color = fg)
                        Text("• Adăugare locație favorită ⭐.", color = fg)
                        Text("• Detectare mișcare: 🚫 No-Move, 🚶 Walking, 🚗 Drive.", color = fg)
                        Text("• Ultimele 10 curse cu scor, viteză max și traseu.", color = fg)
                        Spacer(Modifier.height(8.dp))

                        Text("🚗  Garage", color = fg, fontWeight = FontWeight.Bold)
                        Text("• Adaugă / editează date: Rovinietă, ITP, RCA.", color = fg)
                        Text("• Buton ℹ️ deschide dialog pentru detalii service / note.", color = fg)
                        Text("• Setează ora notificării de expirare.", color = fg)
                        Spacer(Modifier.height(8.dp))

                        Text("⚙️  Settings", color = fg, fontWeight = FontWeight.Bold)
                        Text("• 🏆 Clasament – scor mediu + nr. curse.", color = fg)
                        Text("• ℹ️ Info – statistici personale instant.", color = fg)
                        Text("• Resetare / schimbare parolă.", color = fg)
                        Text("• Verificare email.", color = fg)
                        Text("• Schimbare poză de profil.", color = fg)
                        Text("• Dark-mode, notificări, location-tracking.", color = fg)
                        Spacer(Modifier.height(8.dp))

                        Text("📈  Scorul călătoriilor", color = fg, fontWeight = FontWeight.Bold)
                        Text("• Pornim de la 5.0 per călătorie.", color = fg)
                        Text("• 🚀 Accelerare bruscă – penalizare −0.1.", color = fg)
                        Text("• 🛑 Frânare bruscă – penalizare −0.1.", color = fg)
                    }
                },

                confirmButton = {
                    TextButton(onClick = { showHelpDialog = false }) {
                        Text("Închide", color = fg)
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
                Toast.makeText(context, "Eroare la salvarea imaginii", Toast.LENGTH_SHORT).show()
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
            Text("Selectează o Imagine de Profil", color = textColor)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text  = emailToUsername(user?.email),
            fontSize = 16.sp,
            color = textColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (user?.isEmailVerified == true) "Email Verificat ✅" else "Email Neverificat ❌",
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
                        Toast.makeText(context, "Email-ul de verificare a fost trimis", Toast.LENGTH_SHORT).show()
                    else
                        Toast.makeText(context, "Eroare la trimiterea email-ului de verificare", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Verifică Email-ul", color = textColor)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    Button(
        onClick = {
            user?.email?.let { email ->
                FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Email-ul de resetare a parolei a fost trimis", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Eroare: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Resetează Parola", color = textColor)
    }
}

@Composable
fun ChangePasswordDialog(user: FirebaseUser?, context: Context, textColor: Color, onDismiss: () -> Unit) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schimbă Parola", color = textColor) },
        text = {
            Column {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    label = { Text("Parola curentă", color = textColor) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Noua Parolă", color = textColor) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirmă noua parolă", color = textColor) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (newPassword != confirmPassword) {
                    Toast.makeText(context, "Parolele nu se potrivesc", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                val email = user?.email
                if (email != null) {
                    val credential = EmailAuthProvider.getCredential(email, oldPassword)
                    user.reauthenticate(credential)
                        .addOnSuccessListener {
                            user.updatePassword(newPassword)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Parola a fost actualizată cu succes", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "Eroare la actualizare: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Parola curentă e greșită", Toast.LENGTH_SHORT).show()
                        }
                }
            }) {
                Text("Salvare", color = textColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anulare", color = textColor)
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
            IconButton(onClick = {  }, modifier = Modifier.size(50.dp)) {
                Icon(painter = painterResource(id = R.drawable.settings), contentDescription = "Settings", tint = Color.Black)
            }
        }
    }
}
