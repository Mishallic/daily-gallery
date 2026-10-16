package com.dailygallery.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.dailygallery.app.AppGraph
import com.dailygallery.app.domain.ArtImage
import com.dailygallery.app.ui.gallery.GalleryScreen
import com.dailygallery.app.ui.gallery.SavedScreen
import com.dailygallery.app.ui.today.DayScreen
import com.dailygallery.app.ui.today.TodayScreen
import com.dailygallery.app.ui.viewer.ImageViewer
import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.reflect.KClass

@Serializable
object TodayRoute

@Serializable
object GalleryRoute

@Serializable
object SavedRoute

/** An artwork opened from the gallery ([date] set) or from the saved list. */
@Serializable
data class DayRoute(val key: String, val date: String? = null)

@Serializable
data class ViewerRoute(val url: String, val largeUrl: String, val caption: String, val description: String)

private enum class Tab(val label: String, val icon: ImageVector, val route: Any, val type: KClass<*>) {
    TODAY("Today", GalleryIcons.Frame, TodayRoute, TodayRoute::class),
    GALLERY("Gallery", GalleryIcons.Grid, GalleryRoute, GalleryRoute::class),
    SAVED("Saved", Icons.Filled.Favorite, SavedRoute, SavedRoute::class),
}

/** The app's screens: three tabs (Today, Gallery, Saved), an artwork's page, and the viewer. */
@Composable
fun AppNavigation(graph: AppGraph) {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val tab = Tab.entries.firstOrNull { current?.destination?.hasRoute(it.type) == true }
    val openImage = { image: ArtImage, caption: String ->
        nav.navigate(ViewerRoute(image.url, image.largeUrl, caption, image.altText ?: caption))
    }

    Scaffold(
        // The pages draw behind the status bar themselves; only the tab bar's height is padding.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (tab != null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = item == tab,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = TodayRoute, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable<TodayRoute> {
                TodayScreen(graph, onOpenImage = openImage)
            }
            composable<GalleryRoute> {
                GalleryScreen(graph, onOpen = { day -> nav.navigate(DayRoute(day.entry.key, day.date.toString())) })
            }
            composable<SavedRoute> {
                SavedScreen(graph, onOpen = { entry -> nav.navigate(DayRoute(entry.key)) })
            }
            composable<DayRoute> { backStack ->
                val route = backStack.toRoute<DayRoute>()
                val entry = graph.entry(route.key)
                if (entry == null) {
                    LaunchedEffect(Unit) { nav.popBackStack() } // no longer in the schedule or the saved list
                } else {
                    DayScreen(graph, entry, route.date?.let(LocalDate::parse), openImage, onBack = { nav.popBackStack() })
                }
            }
            composable<ViewerRoute> { backStack ->
                val route = backStack.toRoute<ViewerRoute>()
                ImageViewer(route.url, route.largeUrl, route.caption, route.description, onClose = { nav.popBackStack() })
            }
        }
    }
}
