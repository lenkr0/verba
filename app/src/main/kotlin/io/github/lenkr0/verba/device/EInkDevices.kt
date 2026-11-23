package io.github.lenkr0.verba.device

import android.os.Build

/** Recognizes e-readers so Verba can suggest its E-ink mode on first start. */
object EInkDevices {
    // Onyx Boox devices report "ONYX" as manufacturer and/or "BOOX" in brand or model
    private val markers = listOf("onyx", "boox")

    fun matches(vararg buildValues: String?): Boolean =
        buildValues.any { value -> value != null && markers.any { value.contains(it, ignoreCase = true) } }

    fun isCurrentDevice(): Boolean = matches(Build.MANUFACTURER, Build.BRAND, Build.MODEL)
}
