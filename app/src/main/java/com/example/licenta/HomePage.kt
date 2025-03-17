package com.example.licenta

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
//import com.google.firebase.auth.FirebaseAuth

@Composable
fun HomePage(
    onLogout: () -> Unit,
    onNavigateToMapPage: () -> Unit,
    onNavigateToSwitchPage: () -> Unit,
    onNavigateToWalletPage: () -> Unit
) {
    //val auth = FirebaseAuth.getInstance()
    //val user = auth.currentUser
    // val displayName = user?.displayName ?: user?.email?.split("@")?.get(0) ?: "User" // Extrage numele din email

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
            Text(
                text = "Drive like a Pro",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Hi, bro",
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Buton jos
            Button(
                onClick = { /* Aici poți adăuga acțiunea pentru adăugarea unui punct de acces */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
            ) {
                Text("Add New Car", color = Color.White)
            }

            // Buton de log out
            Button(
                onClick = {
                    //FirebaseAuth.getInstance().signOut()
                    onLogout()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Log Out", color = Color.White)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Buton Home - Gri
                IconButton(
                    onClick = { /* Rămâne pe pagina curentă */ },
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.home), // Imaginea pentru Home
                        contentDescription = "Home",
                        tint = Color.Black,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Buton Map
                IconButton(
                    onClick = { onNavigateToMapPage() }, // Navighează la MapPage
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.map), // Imaginea pentru Map
                        contentDescription = "Map",
                        tint = Color.LightGray, // Deschis pentru celelalte butoane
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Buton Switch
                IconButton(
                    onClick = { onNavigateToSwitchPage() },
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.sageti), // Imaginea pentru Switch
                        contentDescription = "Switch",
                        tint = Color.LightGray, // Deschis pentru celelalte butoane
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Buton Wallet
                IconButton(
                    onClick = { onNavigateToWalletPage() },
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.wallet), // Imaginea pentru Wallet
                        contentDescription = "Wallet",
                        tint = Color.LightGray, // Deschis pentru celelalte butoane
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }


