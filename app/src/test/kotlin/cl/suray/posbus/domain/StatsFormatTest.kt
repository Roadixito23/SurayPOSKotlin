package cl.suray.posbus.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsFormatTest {
    @Test fun numbers() {
        assertEquals("850", StatsFormat.number(850.0))
        assertEquals("12.500", StatsFormat.number(12500.0))
        assertEquals("2M", StatsFormat.number(1_500_000.0))
    }

    @Test fun shortLabels() {
        assertEquals("0", StatsFormat.short(0.0))
        assertEquals("850", StatsFormat.short(850.0))
        assertEquals("13K", StatsFormat.short(12_500.0))
        assertEquals("1.5M", StatsFormat.short(1_500_000.0))
    }
}
