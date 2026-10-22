package cl.suray.posbus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsInputTest {
    @Test fun terminalIdRange() {
        assertEquals(1, SettingsInput.terminalId("1"))
        assertEquals(99, SettingsInput.terminalId(" 99 "))
        assertNull(SettingsInput.terminalId("0"))
        assertNull(SettingsInput.terminalId("100"))
        assertNull(SettingsInput.terminalId("abc"))
        assertNull(SettingsInput.terminalId(""))
    }

    @Test fun priceAcceptsThousandsSeparator() {
        assertEquals(3600.0, SettingsInput.price("3600")!!, 0.0)
        assertEquals(4300.0, SettingsInput.price("4.300")!!, 0.0)
        assertNull(SettingsInput.price("0"))
        assertNull(SettingsInput.price("-5"))
        assertNull(SettingsInput.price("12,5"))
        assertNull(SettingsInput.price(""))
    }

    @Test fun blankNamesAndKeysRejected() {
        assertNull(SettingsInput.fareName("   "))
        assertEquals("Adulto Mayor", SettingsInput.fareName(" Adulto Mayor "))
        assertNull(SettingsInput.affiliateKey(""))
        assertEquals("k", SettingsInput.affiliateKey(" k "))
    }

    @Test fun pdfMarginBounds() {
        assertEquals(69f, SettingsInput.pdfMargin("69")!!, 0f)
        assertEquals(12.5f, SettingsInput.pdfMargin("12,5")!!, 0f)
        assertNull(SettingsInput.pdfMargin("-1"))
        assertNull(SettingsInput.pdfMargin("500"))
        assertNull(SettingsInput.pdfMargin("x"))
    }
}
