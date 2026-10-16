package com.dailygallery.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailygallery.app.ui.theme.DailyGalleryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ViewerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val services = TestServices()

    @Test
    fun tappingThePictureOpensItFullScreenAndCloseComesBack() {
        services.useSavedImages(compose.activity)
        val graph = services.graph(compose.activity)
        compose.setContent { DailyGalleryTheme { Surface { AppNavigation(graph) } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("French, 1859–1891").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithContentDescription("Large painting of people in a crowded park, brushstrokes are dots.").performClick()
        compose.onNodeWithText("A Sunday on La Grande Jatte — 1884, Georges Seurat").assertIsDisplayed()
        compose.onNodeWithText("Pinch or double-tap to zoom").assertIsDisplayed()
        compose.activity.screenshot("viewer")

        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("TODAY · FRIDAY 16 OCTOBER").assertIsDisplayed()
    }
}
