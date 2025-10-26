# composeui (Jetpack Compose module)

This module contains a native Compose implementation of the VPN UI that mirrors your Figma/Vite export.
It ships as an **Android library** so it can be dropped into an existing Java/Kotlin app without a rewrite.

## Import into existing project

1. Copy the `composeui/` folder to the root of your Android project.
2. In `settings.gradle`, add: `include(":composeui")`
3. In your app module's `build.gradle`, add: `implementation(project(":composeui"))`
4. If your `MainActivity` is Java-based, you can host the UI using the provided `VpnComposeView`.

### Example XML to host Compose

```xml
<com.galggg.ui.bridge.VpnComposeView
    android:layout_width="match_parent"
    android:layout_height="match_parent"/>
```

Or change your `MainActivity` to `ComponentActivity` and call `setContent { VpnTheme { VpnApp() } }`.