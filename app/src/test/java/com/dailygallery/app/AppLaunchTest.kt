package com.dailygallery.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The app starts on the PC (Robolectric), with no device or emulator. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AppLaunchTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensWithTheAppName() {
        compose.onNodeWithText("Daily Gallery").assertIsDisplayed()
    }
}
