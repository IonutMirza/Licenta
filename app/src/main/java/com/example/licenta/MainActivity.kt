package com.example.licenta

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
//import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.padding
//import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.example.licenta.ui.theme.LicentaTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.initialize

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
                "Location permission is required to show your position.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        == PackageManager.PERMISSION_GRANTED
    ) {
        onPermissionGranted()
    } else {
        LaunchedEffect(Unit) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Firebase.initialize(this) // Inițializează Firebase
        auth = FirebaseAuth.getInstance()

        setContent {
            var currentScreen by remember { mutableStateOf(if (auth.currentUser != null) "home" else "signUp") }

            // Navigare între ecrane
            when (currentScreen) {
                "signUp" -> SignUpPage(onNavigateToHomePage = { currentScreen = "home" })
                "home" -> HomePage(
                    onLogout = {
                        auth.signOut()
                        currentScreen = "signUp"
                    },
                    onNavigateToMapPage = { currentScreen = "map" },
                    onNavigateToSwitchPage = { currentScreen = "switch" },
                    onNavigateToWalletPage = { currentScreen = "wallet" }
                )
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    LicentaTheme {
        Greeting("Android")
    }
}