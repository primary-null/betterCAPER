package com.bettercaper.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.bettercaper.viewmodel.AppUiState
import org.junit.Rule
import org.junit.Test

class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dashboardScreen_rendersMoreButton_andOpensMenu() {
        var navigatedToSpoofing = false

        composeTestRule.setContent {
            DashboardScreen(
                uiState = AppUiState(),
                onRefresh = {},
                onSelectInterface = {},
                onDismissInterfaceDialog = {},
                onNavigateEvents = {},
                onNavigateCommand = {},
                onNavigateWifi = {},
                onNavigateBluetooth = {},
                onNavigateModules = {},
                onNavigateSpoofing = { navigatedToSpoofing = true },
                onNavigateProxies = {},
                onNavigateWebUi = {},
                onNavigateAirgeddon = {},
                onNavigateCaplets = {},
                onNavigateWifiBle = {},
                onNavigatePcaps = {},
                onExecuteCommand = {},
                onStartScan = {},
                onDisconnect = {}
            )
        }

        // Initially menu is closed, verify 'Spoofing & MAC' is not displayed
        composeTestRule.onNodeWithText("Spoofing & MAC").assertDoesNotExist()

        // Click the "More" button in the top app bar
        composeTestRule.onNodeWithContentDescription("More").performClick()

        // Now "Spoofing & MAC" should be in the dropdown menu
        composeTestRule.onNodeWithText("Spoofing & MAC").assertExists().assertIsDisplayed()

        // Click Spoofing & MAC
        composeTestRule.onNodeWithText("Spoofing & MAC").performClick()

        // Verify callback
        assert(navigatedToSpoofing)
    }
}
