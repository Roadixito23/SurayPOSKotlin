package cl.suray.posbus.domain

import cl.suray.posbus.data.prefs.AppPrefs
import cl.suray.posbus.data.prefs.Ruta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesTest {
    private val ruta = Ruta("Aysén", "Coyhaique")

    @Test fun directionToggles() {
        assertEquals(Direction("Aysén", "Coyhaique"), Routes.direction(ruta, invertida = false))
        assertEquals(Direction("Coyhaique", "Aysén"), Routes.direction(ruta, invertida = true))
    }

    @Test fun defaultRouteIsAysenCoyhaique() {
        assertEquals(listOf(ruta), AppPrefs.DEFAULT_RUTAS)
    }

    @Test fun passengerTicketShowsDestination() {
        val d = Routes.direction(ruta, false)
        listOf("Público General", "Escolar General", "Adulto Mayor").forEach {
            assertEquals("Destino: Coyhaique", Routes.ticketLine(it, d))
        }
        assertEquals("Destino: Aysén", Routes.ticketLine("Público General", Routes.direction(ruta, true)))
    }

    @Test fun intermediateTicketShowsSegment() {
        val d = Routes.direction(ruta, false)
        listOf("Int. hasta 15 Km", "Int. hasta 50 Km", "Escolar Intermedio").forEach {
            assertEquals("Intermedio Aysén - Coyhaique", Routes.ticketLine(it, d))
        }
        assertEquals("Intermedio Coyhaique - Aysén", Routes.ticketLine("Int. hasta 15 Km", Routes.direction(ruta, true)))
    }

    @Test fun intermediateRuleMatchesDefaultFares() {
        val intermedio = AppPrefs.DEFAULT_WEEKDAY_FARES.map { it.nombre }.filter(Routes::isIntermedio)
        assertEquals(listOf("Int. hasta 15 Km", "Int. hasta 50 Km", "Escolar Intermedio"), intermedio)
        assertFalse(Routes.isIntermedio("Escolar General"))
    }

    @Test fun validation() {
        assertNull(Routes.validate("Puerto Chacabuco", "Coyhaique"))
        assertNotNull(Routes.validate("", "Coyhaique"))
        assertNotNull(Routes.validate("Aysén", "  "))
        assertNotNull(Routes.validate("Aysén", "aysén"))
        assertNotNull(Routes.validate("a".repeat(Routes.NAME_MAX + 1), "Coyhaique"))
        assertNull(Routes.validate("a".repeat(Routes.NAME_MAX), "Coyhaique"))
    }

    @Test fun ticketLayoutCarriesRouteLine() {
        val t = TicketLayouts.regular("Público General", 3600.0, "01-000001", "TARIFA ÚNICA", 0L, reprint = false, routeLine = "Destino: Coyhaique")
        assertEquals("Destino: Coyhaique", t.routeLine)
        assertTrue(TicketLayouts.regular("Público General", 3600.0, "x", "TARIFA ÚNICA", 0L, reprint = false).routeLine == null)
    }
}
