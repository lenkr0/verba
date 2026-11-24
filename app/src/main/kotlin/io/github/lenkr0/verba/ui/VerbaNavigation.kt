package io.github.lenkr0.verba.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import io.github.lenkr0.verba.ui.home.HomeScreen
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

/** Where the app opens: the home screen, or straight to the dictionaries (from the capture sheet). */
enum class StartScreen { Home, Dictionaries }

private val noTransition: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
    { EnterTransition.None togetherWith ExitTransition.None }

private val noPredictiveTransition: AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform =
    { EnterTransition.None togetherWith ExitTransition.None }

@Composable
fun VerbaNavigation(container: AppContainer, start: StartScreen = StartScreen.Home) {
    val backStack = when (start) {
        StartScreen.Home -> rememberNavBackStack(HomeRoute)
        StartScreen.Dictionaries -> rememberNavBackStack(HomeRoute, SettingsRoute, DictionariesRoute)
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
                val entries by remember { container.vocabulary.entryList() }.collectAsStateWithLifecycle(initialValue = null)
                HomeScreen(entries = entries, onOpenSettings = { backStack.add(SettingsRoute) })
            }
            entry<SettingsRoute> {
                val installed by container.dictionaryStore.installed.collectAsStateWithLifecycle()
                SettingsScreen(
                    repository = container.settings,
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
