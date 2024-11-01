// OdontoCodeTheme.kt
package com.tonymen.odontocode.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Transparent

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryVariant,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryVariant,
    background = LightBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    onBackground = LightOnBackground,
    surfaceVariant = Transparent,
    tertiary = LightRegisterButtonColor,
    onTertiary = LightOnRegisterButtonColor,
    error = LightForgotPasswordButtonColor,
    onError = LightOnForgotPasswordButtonColor
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryVariant,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryVariant,
    background = DarkBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    onBackground = DarkOnBackground,
    surfaceVariant = Transparent,
    tertiary = DarkRegisterButtonColor,
    onTertiary = DarkOnRegisterButtonColor,
    error = DarkForgotPasswordButtonColor,
    onError = DarkOnForgotPasswordButtonColor
)

// Función del tema de la aplicación
@Composable
fun OdontoCodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),  // Detecta si el tema del sistema es oscuro
    content: @Composable () -> Unit  // Contenido Composable que usará este tema
) {
    // Determina el esquema de colores según el tema oscuro o claro
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    // Aplica el esquema de colores y la tipografía
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,  // Puedes definir una tipografía personalizada si es necesario
        content = content  // Aplica el contenido Composable
    )
}