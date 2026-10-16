package com.dailygallery.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.dailygallery.app.AppGraph
import com.dailygallery.app.domain.ArtImage
import com.dailygallery.app.ui.today.TodayScreen
import com.dailygallery.app.ui.viewer.ImageViewer
import kotlinx.serialization.Serializable

@Serializable
object TodayRoute

@Serializable
data class ViewerRoute(val url: String, val largeUrl: String, val caption: String, val description: String)

/** The app's screens and how they lead to each other. */
@Composable
fun AppNavigation(graph: AppGraph) {
    val nav = rememberNavController()
    val openImage = { image: ArtImage, caption: String ->
        nav.navigate(ViewerRoute(image.url, image.largeUrl, caption, image.altText ?: caption))
    }

    NavHost(nav, startDestination = TodayRoute) {
        composable<TodayRoute> {
            TodayScreen(graph, onOpenImage = openImage)
        }
        composable<ViewerRoute> { entry ->
            val route = entry.toRoute<ViewerRoute>()
            ImageViewer(route.url, route.largeUrl, route.caption, route.description, onClose = { nav.popBackStack() })
        }
    }
}
