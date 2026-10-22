package cl.suray.posbus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cl.suray.posbus.ui.SurayNavHost
import cl.suray.posbus.ui.theme.SurayTheme

class MainActivity : ComponentActivity() {
    override fun onDestroy() {
        val container = (application as SurayApp).container
        if (container.currentActivity === this) container.currentActivity = null
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as SurayApp).container
        container.currentActivity = this
        setContent { SurayTheme { SurayNavHost(container) } }
    }
}
