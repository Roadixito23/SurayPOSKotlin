package cl.suray.posbus.ui

import android.content.Context
import android.os.Vibrator

/** Vibración corta de error; no hace nada si el equipo no tiene vibrador. */
@Suppress("DEPRECATION")
fun Context.vibrateError(locked: Boolean) {
    val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    if (!v.hasVibrator()) return
    if (locked) v.vibrate(longArrayOf(0, 250, 120, 250, 120, 250), -1) else v.vibrate(200)
}
