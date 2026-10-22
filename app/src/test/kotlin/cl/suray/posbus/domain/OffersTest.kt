package cl.suray.posbus.domain

import cl.suray.posbus.data.prefs.CargoData
import cl.suray.posbus.data.prefs.LastTransaction
import cl.suray.posbus.data.prefs.OfferEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OffersTest {
    private fun e(n: String, v: String) = OfferEntry(n, v)

    @Test fun totalsMultiplyQuantityByValue() {
        assertEquals(3 * 1500L + 2 * 1000L, Offers.total(listOf(e("3", "1500"), e("2", "1.000"))))
        assertEquals(0L, Offers.subtotal(e("", "500")))
    }

    @Test fun validationDropsBlankLinesAndRejectsIncomplete() {
        val ok = Offers.validate(listOf(e("2", "1500"), e("", ""))) as Offers.Result.Ok
        assertEquals(1, ok.entries.size)
        assertEquals(3000L, ok.total)
        assertTrue(Offers.validate(listOf(e("", ""))) is Offers.Result.Invalid)
        assertTrue(Offers.validate(listOf(e("2", ""))) is Offers.Result.Invalid)
        assertTrue(Offers.validate(listOf(e("0", "100"))) is Offers.Result.Invalid)
    }

    @Test fun cargoValidation() {
        assertNull(CargoInput.validate("Caja", "5000", "Juan"))
        assertEquals("Completa los campos obligatorios", CargoInput.validate("", "5000", "Juan"))
        assertEquals("El precio debe ser mayor a cero", CargoInput.validate("Caja", "0", "Juan"))
        assertEquals("Coyhaique", CargoInput.toggleDestination("Aysén"))
        assertEquals("Aysén", CargoInput.toggleDestination("Coyhaique"))
    }

    @Test fun offerTicketText() {
        val t = TicketLayouts.offer(listOf(e("3", "1500")), "01-000009", 0L, reprint = false)
        assertEquals(listOf("3" to "\$1.500"), t.rows)
        assertEquals("Total: \$4.500", t.totalLine)
        assertEquals("N° 01-000009", t.numberLine)
    }

    @Test fun cargoTicketText() {
        val d = CargoData("Aysén", "Caja", 5000.0, "Juan", "")
        val c = TicketLayouts.cargo(d, "01-000010", CargoCopy.Cargo, 0L, reprint = true)
        assertEquals("COPIA CARGA (REIMPRESIÓN)", c.caption)
        assertEquals("\$5.000", c.precioLine)
        assertNull(c.telefono)
        assertEquals("COPIA CLIENTE", TicketLayouts.cargo(d, "x", CargoCopy.Client, 0L, false).caption)
    }

    @Test fun lastTransactionKinds() {
        val cargo = LastTransaction("Cargo: Juan", 5000.0, "01-1", destinatario = "Juan", articulo = "Caja", destino = "Aysén")
        assertTrue(cargo.isCargo)
        assertEquals("Caja", cargo.cargoData()!!.articulo)
        assertTrue(LastTransaction("Oferta Ruta", 100.0, "01-2").isOffer)
        assertNull(LastTransaction("Cargo: X", 1.0, "01-3").cargoData())
    }
}
