package com.learnmart.app.ui.screens.login

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.learnmart.app.ui.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for LoginScreen.
 * Verifies rendering, input fields, button state, and error handling.
 */
@HiltAndroidTest
class LoginScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginScreen_displaysAllElements() {
        composeTestRule.onNodeWithText("LearnMart").assertIsDisplayed()
        composeTestRule.onNodeWithText("Username").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
        composeTestRule.onNodeWithText("Login").assertIsDisplayed()
    }

    @Test
    fun loginScreen_loginButtonIsEnabled() {
        composeTestRule.onNodeWithText("Login").assertIsEnabled()
    }

    @Test
    fun loginScreen_canTypeInUsernameField() {
        composeTestRule.onNodeWithText("Username").performClick()
        composeTestRule.onNodeWithText("Username").performTextInput("admin")
        composeTestRule.onNodeWithText("admin").assertIsDisplayed()
    }

    @Test
    fun loginScreen_invalidLoginShowsError() {
        composeTestRule.onNodeWithText("Username").performTextInput("nonexistent")
        composeTestRule.onNodeWithText("Password").performTextInput("wrong")
        composeTestRule.onNodeWithText("Login").performClick()

        // Wait for the error to appear
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(
                androidx.compose.ui.test.hasText("Invalid username or credential")
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Invalid username or credential").assertIsDisplayed()
    }
}
