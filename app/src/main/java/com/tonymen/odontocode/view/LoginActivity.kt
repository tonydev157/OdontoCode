package com.tonymen.odontocode.view

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.tonymen.odontocode.R
import com.tonymen.odontocode.data.User
import com.tonymen.odontocode.data.UserType
import com.tonymen.odontocode.ui.theme.OdontoCodeTheme

class LoginActivity : ComponentActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var isLoading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OdontoCodeTheme {
                LoginScreen(
                    onLoginClick = { email, password ->
                        if (email.isBlank() || password.isBlank()) {
                            Toast.makeText(this, "Correo o contraseña vacíos", Toast.LENGTH_LONG).show()
                        } else {
                            isLoading = true
                            loginUser(email, password)
                        }
                    },
                    onRegisterClick = {
                        val intent = Intent(this, RegisterActivity::class.java)
                        startActivity(intent)
                    },
                    isLoading = isLoading
                )
            }
        }
    }

    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                isLoading = false
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        checkUserApproval(userId)
                    } else {
                        Toast.makeText(this, "Error al obtener el ID del usuario", Toast.LENGTH_LONG).show()
                    }
                } else {
                    when (task.exception) {
                        is FirebaseAuthInvalidUserException -> {
                            Toast.makeText(this, "Correo inexistente", Toast.LENGTH_LONG).show()
                        }
                        is FirebaseAuthInvalidCredentialsException -> {
                            Toast.makeText(this, "Contraseña incorrecta", Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            Toast.makeText(this, "Error al iniciar sesión: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
    }

    private fun checkUserApproval(userId: String) {
        val currentDeviceId = getCurrentDeviceId() // Obtener el ID del dispositivo actual
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val user = document.toObject<User>()
                    if (user != null) {
                        if (user.userType == UserType.ADMIN) {
                            Toast.makeText(this, "Bienvenido, Admin", Toast.LENGTH_LONG).show()
                            navigateToMainActivity()
                        } else if (user.userType == UserType.USER) {
                            if (user.approved) {
                                if (!user.activeSession) {
                                    // Si no hay sesión activa, actualizar a true
                                    firestore.collection("users").document(userId)
                                        .update("activeSession", true)
                                        .addOnSuccessListener {
                                            Toast.makeText(this, "Inicio de sesión exitoso", Toast.LENGTH_LONG).show()
                                            navigateToMainActivity()
                                        }
                                } else {
                                    Toast.makeText(this, "Tu cuenta está activa en otro dispositivo. Cierra la sesión para continuar.", Toast.LENGTH_LONG).show()
                                    FirebaseAuth.getInstance().signOut()
                                }
                            } else {
                                Toast.makeText(this, "Tu cuenta aún no está activada", Toast.LENGTH_LONG).show()
                                FirebaseAuth.getInstance().signOut()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Error al procesar el usuario", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Usuario no encontrado en la base de datos", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al verificar el usuario: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }



    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    // Obtener ID único del dispositivo
    private fun getCurrentDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }
}

@Composable
fun LoginScreen(
    onLoginClick: (String, String) -> Unit = { _, _ -> },
    onRegisterClick: () -> Unit = {},
    isLoading: Boolean
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Fondos adaptados a ambos temas
    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor) // Fondo que se adapta al tema claro y oscuro
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.odontocode_logo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(100.dp)
                    .padding(bottom = 16.dp)
            )

            Text(
                text = "Bienvenido a OdontoCode",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = textColor, // El color se adapta al tema
                    textAlign = TextAlign.Center
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Campo de texto para el correo
            OutlinedTextField(
                value = email,
                onValueChange = {
                    if (it.length <= 50) {
                        email = it
                    }
                },
                label = { Text("Correo Electrónico", color = textColor) }, // Adaptado al color del tema
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                textStyle = TextStyle(color = textColor), // Texto adaptado al tema
                enabled = !isLoading,
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_email),
                        contentDescription = "Email Icon",
                        tint = textColor // Adaptado al tema
                    )
                }
            )

            // Campo de texto para la contraseña
            OutlinedTextField(
                value = password,
                onValueChange = {
                    if (it.length <= 30) {
                        password = it
                    }
                },
                label = { Text("Contraseña", color = textColor) }, // Adaptado al tema
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                textStyle = TextStyle(color = textColor), // Texto adaptado al tema
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                enabled = !isLoading,
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_password),
                        contentDescription = "Password Icon",
                        tint = textColor // Adaptado al tema
                    )
                },
                trailingIcon = {
                    Icon(
                        painter = painterResource(
                            id = if (passwordVisible) R.drawable.ic_eye else R.drawable.ic_eye_off
                        ),
                        contentDescription = "Toggle Password Visibility",
                        tint = textColor, // Adaptado al tema
                        modifier = Modifier.clickable { passwordVisible = !passwordVisible }
                    )
                }
            )

            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                Button(
                    onClick = {
                        onLoginClick(email, password)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = !isLoading
                ) {
                    Text("Iniciar Sesión", color = MaterialTheme.colorScheme.onPrimary)
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onRegisterClick,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    enabled = !isLoading
                ) {
                    Text("Crear Cuenta", style = MaterialTheme.typography.bodyLarge, color = textColor)
                }
            }
        }
    }
}

