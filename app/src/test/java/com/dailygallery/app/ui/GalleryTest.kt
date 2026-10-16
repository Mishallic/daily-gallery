package com.dailygallery.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
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

/** The tabs: the gallery of earlier days, and saving artworks. Today is La Grande Jatte, yesterday The Red Kerchief. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class GalleryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val services = TestServices()

    private fun showApp() {
        services.useSavedImages(compose.activity)
        val graph = services.graph(compose.activity)
        compose.setContent { DailyGalleryTheme { Surface { AppNavigation(graph) } } }
        waitFor("French, 1859–1891")
    }

    private fun waitFor(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun tab(label: String) = compose.onNode(hasText(label) and isSelectable())

    @Test
    fun theGalleryListsEarlierDaysAndOpensOne() {
        showApp()
        tab("Gallery").performClick()
        compose.onNodeWithText("YESTERDAY").assertIsDisplayed()
        compose.onNodeWithText("The Red Kerchief").assertIsDisplayed()
        compose.activity.screenshot("gallery")

        compose.onNodeWithText("The Red Kerchief").performClick()
        waitFor("French, 1840–1926")
        compose.onNodeWithText("YESTERDAY · THURSDAY 15 OCTOBER").assertIsDisplayed()
        compose.activity.screenshot("gallery-day")

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("An artwork for every day, newest first.").assertIsDisplayed()
    }

    @Test
    fun aSavedArtworkIsListedUnderSavedUntilItIsUnsaved() {
        showApp()
        tab("Saved").performClick()
        compose.onNodeWithText("Nothing saved yet", substring = true).assertIsDisplayed()

        tab("Today").performClick()
        compose.onNodeWithContentDescription("Save").performClick()
        tab("Saved").performClick()
        compose.onNodeWithText("A Sunday on La Grande Jatte — 1884").assertIsDisplayed()
        compose.activity.screenshot("saved")

        compose.onNodeWithText("A Sunday on La Grande Jatte — 1884").performClick()
        compose.onNodeWithText("SAVED").assertIsDisplayed()
        compose.onNodeWithContentDescription("Save").performClick() // unsave
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Nothing saved yet", substring = true).assertIsDisplayed()
    }
}
