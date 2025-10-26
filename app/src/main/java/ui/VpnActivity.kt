package .ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.galggg.ui.bridge.VpnControllerFactory
import com.galggg.ui.bridge.VpnControllerProvider
import .R

class VpnActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Перед показом UI подменяем контроллер на наш, работающий с бэком
        VpnControllerProvider.set(VpnControllerFactory {
            GalgggVpnController(
                // TODO: подай зависимости ядра сюда
            )
        })

        setContentView(R.layout.activity_vpn)
    }
}
