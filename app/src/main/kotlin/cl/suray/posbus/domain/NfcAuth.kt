package cl.suray.posbus.domain

object NfcAuth {
    /** UID en hex mayúscula separado por `:` (ej. `04:A2:3B:1C`). */
    fun formatUid(bytes: ByteArray): String =
        bytes.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }

    /** Autorizada si no hay tarjetas vinculadas (permite todas) o el UID está en la lista. */
    fun isAuthorized(linked: List<String>, uid: String): Boolean = linked.isEmpty() || uid in linked
}
