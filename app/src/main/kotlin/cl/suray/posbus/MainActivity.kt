package cl.suray.posbus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cl.suray.posbus.ui.SurayNavHost
import cl.suray.posbus.ui.theme.SurayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as SurayApp).container
        setContent { SurayTheme { SurayNavHost(container) } }
    }
}
