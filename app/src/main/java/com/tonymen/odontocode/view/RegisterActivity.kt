package com.tonymen.odontocode.view

import android.os.Bundle
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tonymen.odontocode.data.User
import com.tonymen.odontocode.data.UserType
import com.tonymen.odontocode.ui.theme.OdontoCodeTheme
import com.tonymen.odontocode.R

class RegisterActivity : ComponentActivity() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OdontoCodeTheme {
                var isLoading by remember { mutableStateOf(false) }
                RegisterScreen(
                    onRegisterClick = { name, email, idCard, password ->
                        isLoading = true
                        if (isValidCedula(idCard)) {
                            registerUser(name, email, idCard, password) { success ->
                                isLoading = false
                                if (!success) {
                                    Toast.makeText(this, "Error al registrar usuario", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            isLoading = false
                            Toast.makeText(this, "Cédula inválida", Toast.LENGTH_LONG).show()
                        }
                    },
                    isLoading = isLoading
                )
            }
        }
    }

    private fun registerUser(
        name: String, email: String, idCard: String, password: String,
        onComplete: (Boolean) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = task.result?.user?.uid
                    if (userId != null) {
                        saveUserToFirestore(userId, name, email, idCard, false) { success ->
                            if (success) {
                                onComplete(true)
                            } else {
                                Toast.makeText(this, "Error al guardar los datos en Firestore", Toast.LENGTH_LONG).show()
                                onComplete(false)
                            }
                        }
                    } else {
                        Toast.makeText(this, "Error al obtener el UID del usuario", Toast.LENGTH_LONG).show()
                        onComplete(false)
                    }
                } else {
                    Toast.makeText(this, "Error al registrar usuario: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    onComplete(false)
                }
            }
    }

    private fun saveUserToFirestore(userId: String, name: String, email: String, idCard: String, approved: Boolean, onComplete: (Boolean) -> Unit) {
        val user = User(id = userId, name = name, email = email, userType = UserType.USER, ci = idCard, approved = approved)
        firestore.collection("users").document(userId).set(user)
            .addOnSuccessListener {
                Toast.makeText(this, "Usuario registrado exitosamente", Toast.LENGTH_LONG).show()
                finish()
                onComplete(true)
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error al guardar usuario en Firestore: ${exception.message}", Toast.LENGTH_LONG).show()
                onComplete(false)
            }
    }
}

@Composable
fun RegisterScreen(onRegisterClick: (String, String, String, String) -> Unit, isLoading: Boolean) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var idCard by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.odontocode_logo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(100.dp)
                    .padding(bottom = 16.dp)
            )

            Text(
                text = "Crear Cuenta",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Nombre Completo
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre Completo") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_person),
                        contentDescription = "Name Icon",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                },
                enabled = !isLoading
            )

            // Correo Electrónico
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo Electrónico") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_email),
                        contentDescription = "Email Icon",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                },
                enabled = !isLoading
            )

            // Cédula de Identidad
            OutlinedTextField(
                value = idCard,
                onValueChange = {
                    if (it.all { char -> char.isDigit() } && it.length <= 10) idCard = it
                },
                label = { Text("Cédula de Identidad (Ecuador)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_id_card),
                        contentDescription = "ID Card Icon",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                },
                trailingIcon = if (!isValidCedula(idCard) && idCard.isNotEmpty()) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.ic_error),
                            contentDescription = "Invalid ID",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else null,
                enabled = !isLoading
            )

            // Contraseña
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_password),
                        contentDescription = "Password Icon",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                },
                trailingIcon = {
                    Icon(
                        painter = painterResource(if (passwordVisible) R.drawable.ic_eye else R.drawable.ic_eye_off),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.clickable { passwordVisible = !passwordVisible }
                    )
                },
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (name.isBlank() || email.isBlank() || idCard.isBlank() || password.isBlank()) {
                        Toast.makeText(
                            context,
                            "Todos los campos son obligatorios",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        onRegisterClick(name, email, idCard, password)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = !isLoading
            ) {
                Text("Registrar Cuenta", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
// Función de validación de cédula que faltaba
fun isValidCedula(idCard: String): Boolean {
    if (idCard.length != 10) return false

    val digits = idCard.map { it.toString().toIntOrNull() ?: return false }
    if (digits[0] !in 0..2 || digits[1] !in 0..9) return false

    val coef = listOf(2, 1, 2, 1, 2, 1, 2, 1, 2)
    val total = coef.zip(digits.take(9)).sumOf { (c, d) ->
        val product = c * d
        if (product >= 10) product - 9 else product
    }

    val checkDigit = (10 - (total % 10)) % 10
    return checkDigit == digits.last()
}
