package com.bettercaper.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.bettercaper.viewmodel.AppUiState
import com.bettercaper.viewmodel.ConnectParams
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class ConnectScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun connectScreen_initialState_showsAllFields() {
        composeTestRule.setContent {
            ConnectScreen(
                uiState = AppUiState(),
                onConnect = {},
                onClearError = {},
                onClearFingerprint = { _, _ -> },
                onConnected = {}
            )
        }

        // Verify SSH fields exist
        composeTestRule.onNodeWithText("Host / IP").assertExists()
        composeTestRule.onNodeWithText("22").assertExists() // Default port
        composeTestRule.onNodeWithText("Username").assertExists()
        composeTestRule.onNodeWithText("Password").assertExists()

        // Verify API fields exist
        composeTestRule.onNodeWithText("8081").assertExists() // Default API port
        composeTestRule.onNodeWithText("user", substring = true).assertExists() // Default API user/pass

        // Verify Connect button
        composeTestRule.onNodeWithText("Connect").assertExists().assertHasClickAction()
    }

    @Test
    fun connectScreen_canInputCredentials_andClickConnect() {
        var connectParams: ConnectParams? = null

        composeTestRule.setContent {
            ConnectScreen(
                uiState = AppUiState(),
                onConnect = { params -> connectParams = params },
                onClearError = {},
                onClearFingerprint = { _, _ -> },
                onConnected = {}
            )
        }

        // Input Host
        composeTestRule.onNodeWithText("Host / IP").performScrollTo().performTextInput("192.168.1.100")
        composeTestRule.onNodeWithText("192.168.1.100").performScrollTo().assertExists()

        // Input SSH User
        composeTestRule.onNodeWithText("Username").performScrollTo().performTextInput("testuser")
        composeTestRule.onNodeWithText("testuser").performScrollTo().assertExists()

        // Input SSH Pass
        composeTestRule.onNodeWithText("Password").performScrollTo().performTextInput("testpass")

        // Click Connect
        composeTestRule.onNodeWithText("Connect").performScrollTo().performClick()

        // Verify callback was triggered with right params
        assertNotNull(connectParams)
        assertEquals("192.168.1.100", connectParams?.sshHost)
        assertEquals(22, connectParams?.sshPort)
        assertEquals("testuser", connectParams?.sshUsername)
        assertEquals("testpass", connectParams?.sshPassword)
        assertEquals(8081, connectParams?.remoteApiPort)
    }

    @Test
    fun connectScreen_showsErrorDialog_whenErrorState() {
        composeTestRule.setContent {
            ConnectScreen(
                uiState = AppUiState(error = "Connection Failed!"),
                onConnect = {},
                onClearError = {},
                onClearFingerprint = { _, _ -> },
                onConnected = {}
            )
        }

        composeTestRule.onNodeWithText("Connection Error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connection Failed!").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").assertIsDisplayed().assertHasClickAction()
    }
}
