package com.example.licenta

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WalletPage (
    onNavigateBack: () -> Unit,
    onNavigateToSwitchPage: () -> Unit,
    onNavigateToMapPage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Wallet",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Buton Home - Gri
            IconButton(
                onClick = { onNavigateBack() },
                modifier = Modifier.size(50.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.home), // Imaginea pentru Home
                    contentDescription = "Home",
                    tint = Color.LightGray,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Buton Map
            IconButton(
                onClick = {onNavigateToMapPage() },
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
                onClick = {onNavigateToSwitchPage() },
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
                onClick = {},
                modifier = Modifier.size(50.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.wallet), // Imaginea pentru Wallet
                    contentDescription = "Wallet",
                    tint = Color.Black, // Deschis pentru celelalte butoane
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}