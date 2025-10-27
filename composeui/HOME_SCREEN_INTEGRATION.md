# HomeScreen Integration Guide

## Overview

The new `HomeScreen` has been implemented according to the Figma designs (nodes 22:8 and 25:51) with full Material3 Compose UI and matches the Secret VPN visual specification.

## What Was Implemented

### 1. **UI Components**
- `BigVpnButton` - Enhanced power button with double rings, animations, and pulsing effect during connection
- `ProtectedChip` - Animated chip that appears when VPN is connected
- `ConfigCard` - Card for loading VPN config files with file picker integration
- `ServerCard` - Server display card with country indicator panel
- `BottomNavBar` - Bottom navigation with home, settings, and premium icons

### 2. **State Management**
- Added `ConfigState` enum to track config loading status
- Enhanced `HomeUiState` with `configState` field
- Added `loadConfig()`, `toggleConnection()`, and helper methods to `HomeViewModel`

### 3. **Theme & Resources**
- Updated color scheme to match Figma (`#181a1e` background, `#202128` surfaces)
- Added custom VPN button colors (disconnected gray, connected teal)
- Created 6 VectorDrawable icons (power, config, server, home, settings, dollar)
- Added Russian string resources

## How to Use

### In Your Navigation/Activity

```kotlin
import com.galggg.ui.screens.HomeScreen
import com.galggg.ui.vm.HomeViewModel
import com.galggg.ui.bridge.VpnControllerProvider

@Composable
fun MainNavigation() {
    val viewModel = remember {
        HomeViewModel(VpnControllerProvider.get())
    }
    val uiState by viewModel.ui.collectAsState()
    
    HomeScreen(
        connectionState = uiState.connectionState,
        configState = uiState.configState,
        currentServer = uiState.currentServer,
        onPowerClick = { viewModel.toggleConnection() },
        onConfigLoad = { uri -> viewModel.loadConfig(uri) },
        onSettingsClick = { /* Navigate to settings */ },
        onPremiumClick = { /* Navigate to premium screen */ }
    )
}
```

### State Flow

1. **Initial State**: `ConfigState.NOT_LOADED`, button disabled, shows "Перед включением сначала загрузите конфиг!"
2. **User clicks ConfigCard**: File picker opens
3. **User selects file**: `viewModel.loadConfig(uri)` called → `ConfigState.LOADED`
4. **Button enabled**: User can now click power button
5. **User clicks power**: `viewModel.toggleConnection()` → starts VPN connection
6. **Connected**: Button turns teal, "Protected" chip appears, server info shown

## Integration with Existing VPN Logic

The `HomeViewModel` uses the existing `VpnController` interface, so no changes are needed to your VPN service logic:

- **Connect**: `controller.connect(serverId, protocol)` 
- **Disconnect**: `controller.disconnect()`
- **State updates**: Automatically collected from `controller.connectionState` flow

## Config Loading (MVP)

Currently, config loading is simplified:
- Selecting a file marks `ConfigState.LOADED` after 500ms simulation
- In production, you would:
  1. Read the file content from URI
  2. Parse/validate the config (JSON/conf/yaml)
  3. Store config for VPN service
  4. Update state based on success/failure

## Visual Comparison with Figma

Compare implementation with reference screenshots:
- **Disconnected**: Figma node 22:8 - Gray button, no config message
- **Connected**: Figma node 25:51 - Teal button, Protected chip visible, country code "GE"

## Running Tests

```bash
# UI tests
./gradlew :composeui:connectedAndroidTest

# Preview screens in Android Studio
# Open any component file and click the Preview tab
```

## Preview Examples

All components have `@Preview` annotations:
- `HomeScreenNoConfigPreview` - Initial state
- `HomeScreenConfigLoadedPreview` - Config loaded, ready to connect
- `HomeScreenConnectedPreview` - VPN connected state
- Individual component previews in each component file

## File Structure

```
composeui/src/main/
├── java/com/galggg/ui/
│   ├── components/
│   │   └── BigVpnButton.kt          # Enhanced power button
│   ├── home/components/
│   │   ├── ProtectedChip.kt        # Animated "Protected" chip
│   │   ├── ConfigCard.kt           # Config upload card
│   │   ├── ServerCard.kt           # Server info card
│   │   └── BottomNavBar.kt         # Bottom navigation
│   ├── screens/
│   │   └── HomeScreen.kt           # Main home screen
│   ├── vm/
│   │   └── ViewModels.kt           # Enhanced HomeViewModel
│   ├── data/
│   │   └── Models.kt               # Added ConfigState
│   └── theme/
│       └── Theme.kt                # Updated colors
└── res/
    ├── drawable/
    │   ├── ic_power.xml
    │   ├── ic_config.xml
    │   ├── ic_server.xml
    │   ├── ic_home.xml
    │   ├── ic_settings.xml
    │   └── ic_dollar.xml
    └── values/
        └── strings.xml             # Russian strings
```

## Notes

- All colors use tokens from MaterialTheme (no hardcoded values)
- Components are reusable with proper `modifier` parameters
- Animations use Compose's animation APIs
- Accessibility: contentDescription on all icons, proper touch targets
- Dark theme only (matches Figma design)

