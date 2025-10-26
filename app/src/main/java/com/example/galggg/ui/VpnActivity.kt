package com.example.galggg.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.galggg.ui.bridge.VpnControllerFactory
import com.galggg.ui.bridge.VpnControllerProvider
import com.example.galggg.R

/**
 * Экран-хост: подменяет контроллер на наш и показывает Compose UI.
 */
class VpnActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        VpnControllerProvider.set(VpnControllerFactory {
            GalgggVpnController(applicationContext)
        })

        setContentView(R.layout.activity_vpn)
    }
}
