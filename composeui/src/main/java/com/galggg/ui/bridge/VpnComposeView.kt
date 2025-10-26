package com.galggg.ui.bridge

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView
import com.galggg.ui.navigation.VpnApp
import com.galggg.ui.theme.VpnTheme

/** Compose container view used by the host app. */
class VpnComposeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AbstractComposeView(context, attrs, defStyle) {

    @Composable
    override fun Content() {
        VpnTheme {
            VpnApp(controller = VpnControllerProvider.get())
        }
    }
}