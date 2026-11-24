package io.github.lenkr0.verba

import android.app.Application
import android.content.Context
import io.github.lenkr0.verba.data.Deletions
import io.github.lenkr0.verba.data.VerbaDatabase
import io.github.lenkr0.verba.dictionary.Dictionaries
import io.github.lenkr0.verba.dictionary.DictionaryDownloader
import io.github.lenkr0.verba.dictionary.DictionaryLookup
import io.github.lenkr0.verba.dictionary.DictionaryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File
import io.github.lenkr0.verba.data.VocabRepository
import io.github.lenkr0.verba.device.EInkDevices
import io.github.lenkr0.verba.language.Languages
import io.github.lenkr0.verba.review.ReviewLimits
import io.github.lenkr0.verba.review.ReviewRepository
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
        defaultTargetLanguage = Languages.defaultTarget(),
    )
    val database: VerbaDatabase by lazy { VerbaDatabase.create(context) }
    val vocabulary: VocabRepository by lazy { VocabRepository(database) }
    val review: ReviewRepository by lazy { ReviewRepository(database) }
    val reviewLimits: Flow<ReviewLimits> = settings.settings
        .map { ReviewLimits(it.newPerDay, it.reviewsPerDay) }
        .distinctUntilChanged()
    val deletions = Deletions()

    /** Lives as long as the app process; for work that outlives a screen, like downloads. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val dictionaryStore = DictionaryStore(File(context.filesDir, "dictionaries"))
    val dictionaryLookup = DictionaryLookup(dictionaryStore)
    val dictionaries = Dictionaries(
        dictionaryStore,
        DictionaryDownloader(dictionaryStore, userAgent = "Verba/${BuildConfig.VERSION_NAME}"),
        scope,
    )
    val isEInkDevice: Boolean = EInkDevices.isCurrentDevice()
}

val Context.appContainer: AppContainer
    get() = (applicationContext as VerbaApplication).container
