package cl.suray.posbus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ButtonColorSpecTest {
    @Test fun roundTrip() {
        val s = ButtonColorSpec(10, 20, 30, 0.5f, 1, 2, 3)
        assertEquals(s, ButtonColorSpec.fromJson(s.toJson()))
    }

    @Test fun readsFlutterJsonWithDoubles() {
        val s = ButtonColorSpec.fromJson("""{"bgRed":239.0,"bgGreen":83.0,"bgBlue":80.0,"bgOpacity":0.7,"borderRed":0.0,"borderGreen":0.0,"borderBlue":0.0}""")!!
        assertEquals(ButtonColorDefaults.spec(ButtonKind.Weekday, 0), s)
    }

    @Test fun clampsAndRejectsGarbage() {
        val s = ButtonColorSpec.fromJson("""{"bgRed":999,"bgGreen":-5,"bgBlue":1,"bgOpacity":4,"borderRed":0,"borderGreen":0,"borderBlue":0}""")!!
        assertEquals(255, s.bgR); assertEquals(0, s.bgG); assertEquals(1f, s.bgOpacity, 0f)
        assertNull(ButtonColorSpec.fromJson("no es json"))
        assertNull(ButtonColorSpec.fromJson("""{"bgRed":1}"""))
    }

    @Test fun defaultsMatchFlutter() {
        assertEquals(ButtonColorSpec(239, 83, 80, 0.7f, 0, 0, 0), ButtonColorDefaults.spec(ButtonKind.Weekday, 0))
        assertEquals(ButtonColorSpec(189, 189, 189, 0.7f, 100, 181, 246), ButtonColorDefaults.spec(ButtonKind.Sunday, 0))
        assertEquals(255, ButtonColorDefaults.spec(ButtonKind.Otros, 1).bgR)
        assertNotNull(ButtonColorDefaults.spec(ButtonKind.Weekday, 99))
        assertEquals("sunday_3", ButtonColorSpec.storageKey(ButtonKind.Sunday, 3))
    }
}
