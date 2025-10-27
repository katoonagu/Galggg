package com.galggg.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.galggg.ui.data.ConfigState
import com.galggg.ui.data.ConnectionState
import com.galggg.ui.data.Server
import com.galggg.ui.theme.VpnTheme
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun homeScreen_noConfig_showsNeedConfigMessage() {
        composeTestRule.setContent {
            VpnTheme(darkTheme = true) {
                HomeScreen(
                    connectionState = ConnectionState.DISCONNECTED,
                    configState = ConfigState.NOT_LOADED,
                    currentServer = null,
                    onPowerClick = {},
                    onConfigLoad = {},
                    onSettingsClick = {},
                    onPremiumClick = {}
                )
            }
        }
        
        // Verify that the "need config" message is displayed
        composeTestRule
            .onNodeWithText("Перед включением сначала загрузите конфиг!", substring = true)
            .assertIsDisplayed()
    }
    
    @Test
    fun homeScreen_configLoaded_showsConfigLoadedMessage() {
        composeTestRule.setContent {
            VpnTheme(darkTheme = true) {
                HomeScreen(
                    connectionState = ConnectionState.DISCONNECTED,
                    configState = ConfigState.LOADED,
                    currentServer = null,
                    onPowerClick = {},
                    onConfigLoad = {},
                    onSettingsClick = {},
                    onPremiumClick = {}
                )
            }
        }
        
        // Verify that the "config loaded" message is displayed
        composeTestRule
            .onNodeWithText("Конфиг подклёчен!", substring = true)
            .assertIsDisplayed()
    }
    
    @Test
    fun homeScreen_connected_showsProtectedChip() {
        composeTestRule.setContent {
            VpnTheme(darkTheme = true) {
                HomeScreen(
                    connectionState = ConnectionState.CONNECTED,
                    configState = ConfigState.LOADED,
                    currentServer = Server("de-fra-1", "Germany", "Frankfurt", "🇩🇪", 32),
                    onPowerClick = {},
                    onConfigLoad = {},
                    onSettingsClick = {},
                    onPremiumClick = {}
                )
            }
        }
        
        // Verify that the Protected chip is visible when connected
        composeTestRule
            .onNodeWithText("Protected")
            .assertIsDisplayed()
    }
    
    @Test
    fun homeScreen_disconnected_protectedChipNotVisible() {
        composeTestRule.setContent {
            VpnTheme(darkTheme = true) {
                HomeScreen(
                    connectionState = ConnectionState.DISCONNECTED,
                    configState = ConfigState.LOADED,
                    currentServer = null,
                    onPowerClick = {},
                    onConfigLoad = {},
                    onSettingsClick = {},
                    onPremiumClick = {}
                )
            }
        }
        
        // Verify that the Protected chip is NOT visible when disconnected
        composeTestRule
            .onNodeWithText("Protected")
            .assertDoesNotExist()
    }
    
    @Test
    fun homeScreen_allComponentsPresent() {
        composeTestRule.setContent {
            VpnTheme(darkTheme = true) {
                HomeScreen(
                    connectionState = ConnectionState.DISCONNECTED,
                    configState = ConfigState.LOADED,
                    currentServer = null,
                    onPowerClick = {},
                    onConfigLoad = {},
                    onSettingsClick = {},
                    onPremiumClick = {}
                )
            }
        }
        
        // Verify all main components are present
        composeTestRule.onNodeWithText("SECRET VPN").assertIsDisplayed()
        composeTestRule.onNodeWithText("Загрузить конфиг").assertIsDisplayed()
        composeTestRule.onNodeWithText("Сервер").assertIsDisplayed()
    }
}

