package com.nylio.ssokssok.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nylio.ssokssok.AppContainer
import com.nylio.ssokssok.data.StudyConfig
import com.nylio.ssokssok.ui.browse.BrowseScreen
import com.nylio.ssokssok.ui.deck.DeckScreen
import com.nylio.ssokssok.ui.editor.CardEditorScreen
import com.nylio.ssokssok.ui.home.HomeScreen
import com.nylio.ssokssok.ui.importer.ImportScreen
import com.nylio.ssokssok.ui.review.ReviewScreen
import com.nylio.ssokssok.ui.settings.SettingsScreen
import com.nylio.ssokssok.ui.stats.StatsScreen

object Routes {
    const val HOME = "home"
    const val BROWSE = "browse"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val DECK = "deck/{deckId}"
    const val EDITOR = "editor?cardId={cardId}&deckId={deckId}"
    const val IMPORT = "import?deckId={deckId}"
    const val REVIEW = "review?decks={decks}&dir={dir}&mode={mode}&size={size}&practice={practice}&tough={tough}"

    fun deck(id: Long) = "deck/$id"
    fun editor(cardId: Long? = null, deckId: Long? = null) = "editor?cardId=${cardId ?: -1}&deckId=${deckId ?: -1}"
    fun import(deckId: Long? = null) = "import?deckId=${deckId ?: -1}"
    fun review(cfg: StudyConfig) =
        "review?decks=${cfg.deckIds?.joinToString(",") ?: ""}&dir=${cfg.direction.name}&mode=${cfg.answerMode.name}" +
            "&size=${cfg.sessionSize}&practice=${cfg.practice}&tough=${cfg.toughOnly}"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val TOP_LEVEL = listOf(
    TopLevel(Routes.HOME, "Study", Icons.Outlined.School),
    TopLevel(Routes.BROWSE, "Cards", Icons.Outlined.Search),
    TopLevel(Routes.STATS, "Progress", Icons.Outlined.Insights),
)

/** ViewModel built from the app container, scoped to the current navigation entry. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    container: AppContainer,
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM = viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })

@Composable
fun SsokApp(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = TOP_LEVEL.any { it.route == route }

    val incoming by container.incomingImport.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) {
        if (incoming != null && route?.startsWith("import") != true) nav.navigate(Routes.import())
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { if (showBar) BottomBar(nav, route) },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(inner).consumeWindowInsets(inner),
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { it / 12 } },
        ) {
            composable(Routes.HOME, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
                HomeScreen(container, nav)
            }
            composable(Routes.BROWSE, enterTransition = { fadeIn(tween(180)) }, exitTransition = { ExitTransition.None }) {
                BrowseScreen(container, nav)
            }
            composable(Routes.STATS, enterTransition = { fadeIn(tween(180)) }, exitTransition = { ExitTransition.None }) {
                StatsScreen(container)
            }
            composable(Routes.SETTINGS) { SettingsScreen(container, nav) }
            composable(Routes.DECK, arguments = listOf(navArgument("deckId") { type = NavType.LongType })) { entry ->
                DeckScreen(container, nav, entry.arguments!!.getLong("deckId"))
            }
            composable(
                Routes.EDITOR,
                arguments = listOf(
                    navArgument("cardId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("deckId") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { entry ->
                val cardId = entry.arguments!!.getLong("cardId").takeIf { it > 0 }
                val deckId = entry.arguments!!.getLong("deckId").takeIf { it > 0 }
                CardEditorScreen(container, nav, cardId, deckId)
            }
            composable(
                Routes.IMPORT,
                arguments = listOf(navArgument("deckId") { type = NavType.LongType; defaultValue = -1L }),
            ) { entry ->
                ImportScreen(container, nav, entry.arguments!!.getLong("deckId").takeIf { it > 0 })
            }
            composable(
                Routes.REVIEW,
                arguments = listOf(
                    navArgument("decks") { type = NavType.StringType; defaultValue = "" },
                    navArgument("dir") { type = NavType.StringType; defaultValue = "RECOGNIZE" },
                    navArgument("mode") { type = NavType.StringType; defaultValue = "FLIP" },
                    navArgument("size") { type = NavType.IntType; defaultValue = 20 },
                    navArgument("practice") { type = NavType.BoolType; defaultValue = false },
                    navArgument("tough") { type = NavType.BoolType; defaultValue = false },
                ),
                enterTransition = { fadeIn(tween(250)) },
            ) { entry ->
                val a = entry.arguments!!
                val decks = a.getString("decks").orEmpty().split(",").mapNotNull { it.toLongOrNull() }
                val cfg = StudyConfig(
                    deckIds = decks.ifEmpty { null },
                    direction = enumValueOfOr(a.getString("dir"), com.nylio.ssokssok.data.DirectionMode.RECOGNIZE),
                    answerMode = enumValueOfOr(a.getString("mode"), com.nylio.ssokssok.data.AnswerMode.FLIP),
                    sessionSize = a.getInt("size"),
                    practice = a.getBoolean("practice"),
                    toughOnly = a.getBoolean("tough"),
                )
                ReviewScreen(container, nav, cfg)
            }
        }
    }
}

inline fun <reified T : Enum<T>> enumValueOfOr(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

@Composable
private fun BottomBar(nav: NavHostController, route: String?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        TOP_LEVEL.forEach { item ->
            NavigationBarItem(
                selected = route == item.route,
                onClick = {
                    if (route != item.route) {
                        nav.navigate(item.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}
