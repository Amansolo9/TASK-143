package com.learnmart.app.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.learnmart.app.ui.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented test verifying the app starts on the login screen
 * and does not expose protected content without authentication.
 */
@HiltAndroidTest
class NavigationGuardTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun app_starts_on_login_not_dashboard() {
        // Login screen should be visible
        composeTestRule.onNodeWithText("LearnMart").assertIsDisplayed()
        composeTestRule.onNodeWithText("Login").assertIsDisplayed()
        // Dashboard content should NOT be visible
        composeTestRule.onNodeWithText("LearnMart Dashboard").assertDoesNotExist()
    }

    @Test
    fun unauthenticated_user_cannot_see_admin_content() {
        // Without login, admin screens should not be reachable
        composeTestRule.onNodeWithText("User Management").assertDoesNotExist()
        composeTestRule.onNodeWithText("Policies").assertDoesNotExist()
        composeTestRule.onNodeWithText("Audit Log").assertDoesNotExist()
    }
}
