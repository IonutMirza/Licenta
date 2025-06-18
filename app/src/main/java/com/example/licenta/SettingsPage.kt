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
    var notificationsEnabled by remember { mutableStateOf(true) }
    var darkModeEnabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean("dark_mode", false))
    }
    var autoConnectOBD by remember { mutableStateOf(true) }
    var locationTrackingEnabled by remember { mutableStateOf(true) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showScoreDialog by remember { mutableStateOf(false) }

    val isDark = darkModeEnabled
    val backgroundColor = if (isDark) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDark) Color.White else Color.Black

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
                        onClick = { showScoreDialog = true },
                        modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.info),
                            contentDescription = "Info",
                            tint = textColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                ProfileSection(user, textColor)
                Spacer(modifier = Modifier.height(8.dp))

                AuthButtons(user, context, textColor)
                Spacer(modifier = Modifier.height(24.dp))

                SettingToggle("🔔 Notifications", notificationsEnabled, textColor) { notificationsEnabled = it }
                SettingToggle("🌙 Dark Mode", darkModeEnabled, textColor) {
                    darkModeEnabled = it
                    prefs.edit().putBoolean("dark_mode", it).apply()
                }
                SettingToggle("🔌 Auto-connect OBD", autoConnectOBD, textColor) { autoConnectOBD = it }
                SettingToggle("📍 Location Tracking", locationTrackingEnabled, textColor) { locationTrackingEnabled = it }
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

                Spacer(modifier = Modifier.height(16.dp))
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
                onDismissRequest = { showScoreDialog = false },
                title = { Text("Scor utilizator", color = textColor) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("5/5 ⭐", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = textColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Total curse efectuate: 0", fontSize = 16.sp, color = textColor)
                    }
                },
                confirmButton = {
                    Button(onClick = { showScoreDialog = false }) {
                        Text("Închide", color = textColor)
                    }
                },
                containerColor = backgroundColor
            )
        }
    }
}

@Composable
fun ProfileSection(user: FirebaseUser?, textColor: Color) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var imageUri by rememberSaveable {
        mutableStateOf(prefs.getString("profile_image_uri", null))
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        imageUri = uri?.toString()
        prefs.edit().putString("profile_image_uri", uri?.toString()).apply()
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = imageUri?.let { rememberAsyncImagePainter(it) }
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
        Text(text = user?.email ?: "No email", fontSize = 16.sp, color = textColor)
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
