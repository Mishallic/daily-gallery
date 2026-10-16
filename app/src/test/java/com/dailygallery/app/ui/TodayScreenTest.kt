package com.dailygallery.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailygallery.app.ui.theme.DailyGalleryTheme
import com.dailygallery.app.ui.today.TodayScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Today's page on the PC (Robolectric), on saved responses. Screenshots go to build/screenshots. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class TodayScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val services = TestServices()

    private fun showToday(dark: Boolean = false) {
        services.useSavedImages(compose.activity)
        val graph = services.graph(compose.activity)
        compose.setContent { DailyGalleryTheme(dark = dark) { Surface { TodayScreen(graph, onOpenImage = {}) } } }
    }

    private fun waitFor(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun scrollTo(text: String) =
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text, substring = true))

    @Test
    fun todayShowsTheArtworkThenItsStoryFromTheMuseumAndWikipedia() {
        showToday()
        compose.onNodeWithText("A Sunday on La Grande Jatte — 1884").assertIsDisplayed()
        compose.onNodeWithText("TODAY · FRIDAY 16 OCTOBER").assertIsDisplayed()
        waitFor("French, 1859–1891")
        compose.activity.screenshot("today-top")

        scrollTo("The story")
        compose.onNodeWithText("In Ferris Bueller’s Day Off", substring = true).assertIsDisplayed()
        compose.activity.screenshot("today-story")
        scrollTo("Source: Wikipedia")
    }

    @Test
    fun everySectionIsThereAndTheBarJumpsToIt() {
        showToday()
        waitFor("French, 1859–1891")
        for (title in listOf("Materials & technique", "Through history", "What critics wrote", "About the artist", "Sources")) {
            scrollTo(title)
        }
        scrollTo("Oil paint, canvas")
        compose.activity.screenshot("today-materials")
        scrollTo("Show all 7 owners")
        compose.onNodeWithText("Show all 7 owners").performClick()
        scrollTo("Given to the Art Institute, 1926.")
        compose.activity.screenshot("today-history")
        scrollTo("Written about in")
        compose.onNodeWithText("Show all 117 publications").assertExists()

        // The bar jumps to a section and marks it, not the one peeking out above it.
        compose.onNodeWithText("Critics").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("What critics wrote").assertIsDisplayed()
        compose.onNodeWithText("On this work").assertIsDisplayed()
        compose.onNode(hasText("Critics") and isSelected()).assertExists()
        compose.activity.screenshot("today-critics")
    }

    @Test
    fun offlineTheLabelStillShowsAndTryAgainLoadsTheRest() {
        services.museumsUp = false
        showToday()
        waitFor("You’re offline")
        compose.onNodeWithText("A Sunday on La Grande Jatte — 1884").assertIsDisplayed()
        compose.activity.screenshot("today-offline")

        services.museumsUp = true
        compose.onNodeWithText("Try again").performClick()
        waitFor("French, 1859–1891")
        scrollTo("The story")
    }

    @Test
    fun withoutWikipediaTheMuseumsTextShowsAndSaysWhatIsMissing() {
        services.wikipediaUp = false
        showToday()
        waitFor("French, 1859–1891")
        scrollTo("Wikipedia couldn’t be reached")
        compose.activity.screenshot("today-no-wikipedia")
    }

    @Test
    fun theDarkThemeKeepsTheGalleryLook() {
        showToday(dark = true)
        waitFor("French, 1859–1891")
        compose.activity.screenshot("today-dark")
    }
}
