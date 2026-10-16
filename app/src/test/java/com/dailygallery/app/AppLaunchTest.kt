package com.dailygallery.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailygallery.app.ui.TestServices
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The real app starts on the PC (Robolectric) and opens on today's artwork, on saved responses. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AppLaunchTest {
    @get:Rule
    val compose = createEmptyComposeRule()
    private val services = TestServices()
    private val realGraph = DailyGalleryApp.graphFactory

    @After
    fun restore() {
        DailyGalleryApp.graphFactory = realGraph
    }

    @Test
    fun opensOnTodaysArtwork() {
        DailyGalleryApp.graphFactory = { services.graph(it) }
        services.useSavedImages(ApplicationProvider.getApplicationContext())
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("A Sunday on La Grande Jatte — 1884").assertIsDisplayed()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("French, 1859–1891").fetchSemanticsNodes().isNotEmpty() }
        }
    }
}
