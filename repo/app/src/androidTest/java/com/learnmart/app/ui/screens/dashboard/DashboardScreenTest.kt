package com.learnmart.app.ui.screens.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.learnmart.app.ui.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI test for verifying the app launches
 * and the login screen renders correctly on a device/emulator.
 */
@HiltAndroidTest
class DashboardScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun app_launches_and_shows_login_screen() {
        composeTestRule.onNodeWithText("LearnMart").assertIsDisplayed()
    }

    @Test
    fun login_screen_shows_username_field() {
        composeTestRule.onNodeWithText("Username").assertIsDisplayed()
    }

    @Test
    fun login_screen_shows_password_field() {
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
    }

    @Test
    fun login_screen_shows_login_button() {
        composeTestRule.onNodeWithText("Login").assertIsDisplayed()
    }
}
