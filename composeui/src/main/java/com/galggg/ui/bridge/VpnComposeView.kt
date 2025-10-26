package com.galggg.ui.bridge

import android.content.Context
import android.util.AttributeSet
import androidx.compose.ui.platform.ComposeView
import com.galggg.ui.navigation.VpnApp
import com.galggg.ui.theme.VpnTheme

// A custom view that can be inflated from XML and hosts the Compose app.
class VpnComposeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ComposeView(context, attrs) {
    init {
        setContent {
            VpnTheme {
                VpnApp()
            }
        }
    }
}