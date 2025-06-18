package com.example.licenta

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.licenta.model.Car
import com.example.licenta.ui.theme.LicentaTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.Firebase
import com.google.firebase.initialize
import com.google.android.libraries.places.api.Places

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // — Initialize Firebase & Google Places
        Firebase.initialize(this)
        auth = FirebaseAuth.getInstance()

        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_api_key))
        }

        // — Google Sign-In setup
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // — Jetpack Compose UI
        setContent {
            LicentaTheme {
                val context = LocalContext.current
                var currentScreen by remember { mutableStateOf(if (auth.currentUser != null) "home" else "signUp") }
                var selectedCar by remember { mutableStateOf<Car?>(null) }

                // — Google Sign-In Launcher
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    try {
                        val account = task.getResult(ApiException::class.java)
                        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                        auth.signInWithCredential(credential)
                            .addOnCompleteListener { authResult ->
                                if (authResult.isSuccessful) {
                                    currentScreen = "home"
                                } else {
                                    Toast.makeText(context, "Google sign-in failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                    } catch (e: ApiException) {
                        Toast.makeText(context, "Google sign-in error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                // — Navigation Between Screens
                when (currentScreen) {
                    "signUp" -> {
                        SignUpPage(
                            onNavigateToHomePage = { currentScreen = "home" },
                            onGoogleSignInClick = {
                                val signInIntent = googleSignInClient.signInIntent
                                launcher.launch(signInIntent)
                            }
                        )
                    }

                    "home" -> {
                        HomePage(
                            onLogout = {
                                auth.signOut()
                                googleSignInClient.signOut()
                                currentScreen = "signUp"
                            },
                            onNavigateToMapPage = { currentScreen = "map" },
                            onNavigateToGaragePage = { currentScreen = "garage" },
                            onNavigateToSettingsPage = { currentScreen = "settings" },
                            selectedCar = selectedCar,
                            onCarSelected = { car -> selectedCar = car }
                        )
                    }

                    "map" -> {
                        MapPage(
                            onNavigateToGaragePage = { currentScreen = "garage" },
                            onNavigateBack = { currentScreen = "home" },
                            onNavigateToSettingsPage = { currentScreen = "settings" }
                        )
                    }

                    "garage" -> {
                        GaragePage(
                            onNavigateToMapPage = { currentScreen = "map" },
                            onNavigateBack = { currentScreen = "home" },
                            onNavigateToSettingsPage = { currentScreen = "settings" },
                            onCarSelected = { car -> selectedCar = car }
                        )
                    }

                    "settings" -> {
                        SettingsPage(
                            onNavigateToMapPage = { currentScreen = "map" },
                            onNavigateToGaragePage = { currentScreen = "garage" },
                            onNavigateBack = { currentScreen = "home" },
                            onLogoutNavigateToSignUp = {
                                auth.signOut()
                                googleSignInClient.signOut()
                                currentScreen = "signUp"
                            }
                        )
                    }
                }
            }
        }
    }
}
