package io.github.lenkr0.verba.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import io.github.lenkr0.verba.AppContainer
import io.github.lenkr0.verba.language.Languages
import io.github.lenkr0.verba.ocr.OcrActivity
import io.github.lenkr0.verba.ui.entry.EntryScreen
import io.github.lenkr0.verba.ui.entry.EntryViewModel
import io.github.lenkr0.verba.ui.home.HomeActions
import io.github.lenkr0.verba.ui.home.HomeScreen
import io.github.lenkr0.verba.ui.home.HomeState
import io.github.lenkr0.verba.ui.home.HomeViewModel
import io.github.lenkr0.verba.ui.review.ReviewScreen
import io.github.lenkr0.verba.ui.review.ReviewViewModel
import io.github.lenkr0.verba.ui.settings.DictionariesScreen
import io.github.lenkr0.verba.ui.settings.SettingsScreen
import io.github.lenkr0.verba.ui.theme.LocalEInkMode
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data object DictionariesRoute : NavKey

@Serializable
data class EntryRoute(val id: String) : NavKey

@Serializable
data object ReviewRoute : NavKey

/** Where the app opens: the home screen, or straight to the dictionaries (from the capture sheet). */
enum class StartScreen { Home, Dictionaries }

private val noTransition: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
    { EnterTransition.None togetherWith ExitTransition.None }

private val noPredictiveTransition: AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform =
    { EnterTransition.None togetherWith ExitTransition.None }

@Composable
fun VerbaNavigation(container: AppContainer, start: StartScreen = StartScreen.Home, entryId: String? = null) {
    val backStack = when {
        entryId != null -> rememberNavBackStack(HomeRoute, EntryRoute(entryId))
        start == StartScreen.Dictionaries -> rememberNavBackStack(HomeRoute, SettingsRoute, DictionariesRoute)
        else -> rememberNavBackStack(HomeRoute)
    }
    val eInk = LocalEInkMode.current

    // Predictive back works with or without animation: in E-ink mode the
    // previous screen simply appears when the gesture completes.
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = if (eInk) noTransition else defaultTransitionSpec(),
        popTransitionSpec = if (eInk) noTransition else defaultPopTransitionSpec(),
        predictivePopTransitionSpec = if (eInk) noPredictiveTransition else defaultPredictivePopTransitionSpec(),
        entryProvider = entryProvider {
            entry<HomeRoute> {
                val context = LocalContext.current
                // The system photo picker: no storage permission needed
                val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) {
                        context.startActivity(
                            Intent(context, OcrActivity::class.java)
                                .setAction(Intent.ACTION_SEND)
                                .setType("image/*")
                                .putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                .apply { clipData = ClipData.newRawUri(null, uri) },
                        )
                    }
                }
                val viewModel = viewModel {
                    HomeViewModel(container.vocabulary, container.deletions, container.review, container.reviewLimits)
                }
                val entries by viewModel.entries.collectAsStateWithLifecycle()
                val filter by viewModel.filter.collectAsStateWithLifecycle()
                val languages by viewModel.languages.collectAsStateWithLifecycle()
                val sources by viewModel.sources.collectAsStateWithLifecycle()
                val pending by viewModel.pendingDeletion.collectAsStateWithLifecycle()
                val review by viewModel.reviewCounts.collectAsStateWithLifecycle()
                HomeScreen(
                    HomeState(entries, filter, languages, sources, pending, review),
                    HomeActions(
                        onOpenSettings = { backStack.add(SettingsRoute) },
                        onOpenEntry = { backStack.add(EntryRoute(it)) },
                        onQuery = viewModel::setQuery,
                        onToggleLanguage = viewModel::toggleLanguage,
                        onToggleSource = viewModel::toggleSource,
                        onToggleStatus = viewModel::toggleStatus,
                        onStartReview = { backStack.add(ReviewRoute) },
                        onOpenImage = {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onUndo = viewModel::undo,
                        onUndoDismissed = viewModel::dismissUndo,
                    ),
                )
            }
            entry<EntryRoute> { key ->
                val viewModel = viewModel(key = key.id) {
                    EntryViewModel(
                        key.id,
                        container.vocabulary,
                        container.dictionaryLookup,
                        container.settings,
                        container.deletions,
                        container.review,
                    )
                }
                EntryScreen(
                    viewModel = viewModel,
                    onBack = { if (backStack.lastOrNull() == key) backStack.removeLastOrNull() },
                    // After a merge, the merged-into word replaces this one
                    onOpenEntry = { id ->
                        if (backStack.lastOrNull() == key) backStack.removeLastOrNull()
                        backStack.add(EntryRoute(id))
                    },
                )
            }
            entry<ReviewRoute> {
                val viewModel = viewModel { ReviewViewModel(container.review, container.reviewLimits) }
                ReviewScreen(
                    viewModel = viewModel,
                    onBack = { if (backStack.lastOrNull() == ReviewRoute) backStack.removeLastOrNull() },
                    onEdit = { backStack.add(EntryRoute(it)) },
                )
            }
            entry<SettingsRoute> {
                val installed by container.dictionaryStore.installed.collectAsStateWithLifecycle()
                SettingsScreen(
                    repository = container.settings,
                    ocrModels = container.ocrModels,
                    transfers = container.transfers,
                    suggestedTargets = (installed.map { it.targetLanguage } + Languages.defaultTarget() + "en").distinct(),
                    onOpenDictionaries = { backStack.add(DictionariesRoute) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<DictionariesRoute> {
                val settings by container.settings.settings.collectAsStateWithLifecycle()
                DictionariesScreen(
                    dictionaries = container.dictionaries,
                    targetLanguage = settings.targetLanguage,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}
