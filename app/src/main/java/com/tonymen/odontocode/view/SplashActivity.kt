package com.tonymen.odontocode.view

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            // Usuario autenticado, redirigir al MainActivity
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            // No hay usuario autenticado, redirigir al LoginActivity
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish() // Cierra el SplashActivity para que no regrese a esta pantalla
    }
}
