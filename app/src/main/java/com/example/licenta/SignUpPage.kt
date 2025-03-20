package com.example.licenta


import android.widget.Toast
//import androidx.activity.compose.rememberLauncherForActivityResult
//import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
//import com.google.android.gms.auth.api.signin.GoogleSignIn
//import com.google.android.gms.auth.api.signin.GoogleSignInOptions
//import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.auth.GoogleAuthProvider


@Composable
fun SignUpPage(onNavigateToHomePage: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Drive like a Pro", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        Text(if (isSignUp) "Create an Account" else "Login", fontSize = 20.sp)

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (isSignUp) {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(context, "Account Created!", Toast.LENGTH_SHORT).show()
                                onNavigateToHomePage()
                            } else {
                                Toast.makeText(context, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
                                onNavigateToHomePage()
                            } else {
                                Toast.makeText(context, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isSignUp) "Sign Up" else "Login")
        }

        TextButton(onClick = { isSignUp = !isSignUp }) {
            Text(if (isSignUp) "Already have an account? Log in" else "Don't have an account? Sign up")
        }

        /*// Câmp de text pentru email
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Câmp de text pentru parolă
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            visualTransformation = PasswordVisualTransformation()
        )*/

        Spacer(modifier = Modifier.height(24.dp))

        // Buton pentru autentificare
        Button(
            onClick = { onNavigateToHomePage()
                /*if (isSignUp) {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(context, "Account created successfully!", Toast.LENGTH_SHORT).show()
                                onNavigateToHomePage()
                            } else {
                                Toast.makeText(context, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
                                onNavigateToHomePage()
                            } else {
                                Toast.makeText(context, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                }*/
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            Text( "Sign Up")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Buton Google
        OutlinedButton(
            onClick = { onNavigateToHomePage() },
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFFF0F0F0),
                contentColor = Color.Black
            ),
            border = BorderStroke(1.dp, Color.Gray),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.google),
                contentDescription = "Google Logo",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Continue with Google")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Buton Apple
        OutlinedButton(
            onClick = { onNavigateToHomePage() },
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFFF0F0F0),
                contentColor = Color.Black
            ),
            border = BorderStroke(1.dp, Color.Gray),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.apple),
                contentDescription = "Apple Logo",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Continue with Apple")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Schimbă între SignUp și LogIn
    }
}
