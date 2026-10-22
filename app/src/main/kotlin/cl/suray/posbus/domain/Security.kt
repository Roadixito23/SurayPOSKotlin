package cl.suray.posbus.domain

/** Claves y estado de bloqueo (`password`, `settings_password`, `comprobanteSettingsPassword`, `failedAttempts`, `lastLockTime`). */
interface SecurityStore {
    var password: String?
    var settingsPassword: String?
    var comprobanteSettingsPassword: String?
    var failedAttempts: Int
    /** Epoch en ms del último bloqueo; 0 si no hay. */
    var lastLockTime: Long
}

/**
 * Claves en texto plano, con el mismo formato que Flutter para poder importar sus backups.
 * Valores por defecto unificados: app `123456`, ajustes `654321` (Flutter usaba además `232323`
 * en algunas pantallas cuando la clave aún no existía).
 */
class PasswordManager(private val store: SecurityStore) {

    /** Equivale a `_initDefaultPasswords`: solo siembra lo que falta. */
    fun seedDefaults() {
        if (store.password == null) store.password = DEFAULT_PASSWORD
        if (store.settingsPassword == null) store.settingsPassword = DEFAULT_SETTINGS_PASSWORD
    }

    val password: String get() = store.password ?: DEFAULT_PASSWORD
    val settingsPassword: String get() = store.settingsPassword ?: DEFAULT_SETTINGS_PASSWORD

    /** Sin clave propia, la del comprobante es la de Ajustes escrita al revés. */
    val comprobantePassword: String
        get() = store.comprobanteSettingsPassword ?: settingsPassword.reversed()

    fun verifyLogin(input: String) = input == password
    fun verifySettings(input: String) = input == settingsPassword
    fun verifyComprobante(input: String) = input == comprobantePassword

    fun setPassword(new: String): Boolean = (new.length >= MIN_LENGTH).also { if (it) store.password = new }

    fun setSettingsPassword(new: String): Boolean =
        (new.length >= MIN_LENGTH).also { if (it) store.settingsPassword = new }

    /** La clave del comprobante exige exactamente 6 caracteres (tras `trim`). */
    fun setComprobantePassword(new: String): Boolean {
        val v = new.trim()
        if (v.length != COMPROBANTE_LENGTH) return false
        store.comprobanteSettingsPassword = v
        return true
    }

    companion object {
        const val DEFAULT_PASSWORD = "123456"
        const val DEFAULT_SETTINGS_PASSWORD = "654321"
        const val MIN_LENGTH = 6
        const val COMPROBANTE_LENGTH = 6
    }
}

/**
 * Bloqueo progresivo: 3 intentos libres; desde el tercero fallido se bloquea
 * 5, 15, 30, 60 y luego 150 s. El estado sobrevive al reinicio de la app.
 *
 * A diferencia de Flutter, reabrir la pantalla no reinicia el reloj del bloqueo: se mide
 * siempre desde `lastLockTime`, que solo se escribe al producirse el fallo.
 */
class LockoutPolicy(private val store: SecurityStore, private val clock: Clock = SystemClock) {

    val failedAttempts: Int get() = store.failedAttempts

    /** Segundos que faltan de bloqueo (0 = libre). */
    fun remainingSeconds(): Int {
        if (store.failedAttempts < FREE_ATTEMPTS || store.lastLockTime <= 0L) return 0
        val duration = durationMs(store.failedAttempts)
        // Si el reloj retrocedió, nunca se espera más que la duración del bloqueo.
        val elapsed = (clock.nowMillis() - store.lastLockTime).coerceAtLeast(0L)
        return if (elapsed < duration) ((duration - elapsed) / 1000).toInt() else 0
    }

    val isLocked: Boolean get() = remainingSeconds() > 0

    /** Registra un intento fallido; devuelve los segundos de bloqueo aplicados (0 si aún hay intentos libres). */
    fun onFailure(): Int {
        store.failedAttempts += 1
        if (store.failedAttempts < FREE_ATTEMPTS) return 0
        store.lastLockTime = clock.nowMillis()
        return (durationMs(store.failedAttempts) / 1000).toInt()
    }

    fun onSuccess() {
        store.failedAttempts = 0
        store.lastLockTime = 0L
    }

    private fun durationMs(failed: Int): Long =
        LOCK_SECONDS[(failed - FREE_ATTEMPTS).coerceIn(0, LOCK_SECONDS.lastIndex)] * 1000L

    companion object {
        const val FREE_ATTEMPTS = 3
        val LOCK_SECONDS = intArrayOf(5, 15, 30, 60, 150)
    }
}

/** SumUp solo se ofrece si está activo y la clave de afiliado fue escrita en Ajustes (no hay clave en el código). */
object SumUpConfig {
    fun isReady(enabled: Boolean, affiliateKey: String?): Boolean = enabled && !affiliateKey.isNullOrBlank()
}
