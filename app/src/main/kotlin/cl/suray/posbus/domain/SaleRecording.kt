package cl.suray.posbus.domain

/**
 * Guarda una venta de una vez: el comprobante, la caja y lo que añada [block] (última venta, fecha)
 * se escriben en un único `commit()`. Si algo falla no se escribe nada y el estado en memoria
 * del contador y de la caja se vuelve a leer del almacén, así que no queda un comprobante gastado.
 */
fun <T> recordSale(
    batcher: PrefsBatch,
    comprobante: ComprobanteCounter,
    register: CashRegister,
    block: (comprobante: String) -> T,
): T = try {
    batcher.batch {
        comprobante.increment()
        block(comprobante.formatted)
    }
} catch (e: Throwable) {
    comprobante.reload()
    register.reload()
    throw e
}

/** Anula la última venta y retrocede el comprobante en un solo commit. @return true si había algo que anular. */
fun cancelLastSale(batcher: PrefsBatch, comprobante: ComprobanteCounter, register: CashRegister): Boolean = try {
    batcher.batch {
        register.cancelLast().also { if (it) comprobante.rollbackLast() }
    }
} catch (e: Throwable) {
    comprobante.reload()
    register.reload()
    throw e
}
