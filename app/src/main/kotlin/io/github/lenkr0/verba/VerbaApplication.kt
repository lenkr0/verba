package io.github.lenkr0.verba

import android.app.Application
import android.content.Context
import io.github.lenkr0.verba.device.EInkDevices
import io.github.lenkr0.verba.settings.SettingsRepository
import io.github.lenkr0.verba.settings.SharedPreferencesStore

class VerbaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Hand-wired dependencies; small enough that a DI framework isn't worth its size. */
class AppContainer(context: Context) {
    val settings = SettingsRepository(
        SharedPreferencesStore(context.getSharedPreferences("settings", Context.MODE_PRIVATE)),
    )
    val isEInkDevice: Boolean = EInkDevices.isCurrentDevice()
}

val Context.appContainer: AppContainer
    get() = (applicationContext as VerbaApplication).container
