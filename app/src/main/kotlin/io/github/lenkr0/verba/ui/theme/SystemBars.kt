package io.github.lenkr0.verba.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * The app's theme can differ from the system's, so the status and navigation
 * bar icons follow the app, not the system.
 */
fun ComponentActivity.systemBarsFollow(dark: Boolean) {
    val style = if (dark) {
        SystemBarStyle.dark(Color.TRANSPARENT)
    } else {
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    }
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}
