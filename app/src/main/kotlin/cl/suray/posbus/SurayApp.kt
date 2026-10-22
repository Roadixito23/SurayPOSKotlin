package cl.suray.posbus

import android.app.Application

class SurayApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
