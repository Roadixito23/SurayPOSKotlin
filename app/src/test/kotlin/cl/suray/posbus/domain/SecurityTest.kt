package cl.suray.posbus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSecurityStore : SecurityStore {
    override var password: String? = null
    override var settingsPassword: String? = null
    override var comprobanteSettingsPassword: String? = null
    override var failedAttempts: Int = 0
    override var lastLockTime: Long = 0L
}

class PasswordManagerTest {
    @Test fun defaultsAreUnified() {
        val m = PasswordManager(FakeSecurityStore())
        assertTrue(m.verifyLogin("123456"))
        assertFalse(m.verifyLogin("232323"))
        assertTrue(m.verifySettings("654321"))
    }

    @Test fun seedDefaultsOnlyFillsMissing() {
        val s = FakeSecurityStore().apply { password = "999999" }
        PasswordManager(s).seedDefaults()
        assertEquals("999999", s.password)
        assertEquals("654321", s.settingsPassword)
        assertNull(s.comprobanteSettingsPassword)
    }

    @Test fun comprobanteDefaultsToReversedSettingsPassword() {
        val s = FakeSecurityStore().apply { settingsPassword = "123789" }
        val m = PasswordManager(s)
        assertEquals("987321", m.comprobantePassword)
        assertTrue(m.setComprobantePassword(" 111222 "))
        assertEquals("111222", m.comprobantePassword)
    }

    @Test fun lengthRules() {
        val m = PasswordManager(FakeSecurityStore())
        assertFalse(m.setPassword("12345")); assertTrue(m.setPassword("1234567"))
        assertFalse(m.setSettingsPassword("abc")); assertTrue(m.setSettingsPassword("abcdef"))
        assertFalse(m.setComprobantePassword("1234567")); assertFalse(m.setComprobantePassword("12345"))
        assertTrue(m.verifyLogin("1234567")); assertTrue(m.verifySettings("abcdef"))
    }

    @Test fun rejectedChangeKeepsOldValue() {
        val s = FakeSecurityStore().apply { password = "111111" }
        PasswordManager(s).setPassword("1")
        assertEquals("111111", s.password)
    }
}

class LockoutPolicyTest {
    private val store = FakeSecurityStore()
    private val clock = FakeClock(1_000_000L)
    private val policy = LockoutPolicy(store, clock)

    @Test fun firstTwoFailuresDoNotLock() {
        assertEquals(0, policy.onFailure()); assertEquals(0, policy.onFailure())
        assertFalse(policy.isLocked)
        assertEquals(2, policy.failedAttempts)
    }

    @Test fun lockoutDurationsGrowAndCap() {
        val applied = (1..8).map { policy.onFailure() }
        assertEquals(listOf(0, 0, 5, 15, 30, 60, 150, 150), applied)
    }

    @Test fun remainingCountsDownAndExpires() {
        repeat(3) { policy.onFailure() }
        assertEquals(5, policy.remainingSeconds())
        clock.millis += 2_000
        assertEquals(3, policy.remainingSeconds())
        assertTrue(policy.isLocked)
        clock.millis += 3_000
        assertEquals(0, policy.remainingSeconds())
        assertFalse(policy.isLocked)
    }

    @Test fun lockSurvivesRestartWithoutRestartingTheClock() {
        repeat(3) { policy.onFailure() }
        clock.millis += 4_000
        val reopened = LockoutPolicy(store, clock) // simula reabrir la pantalla
        assertEquals(1, reopened.remainingSeconds())
    }

    @Test fun backwardsClockNeverExceedsDuration() {
        repeat(3) { policy.onFailure() }
        clock.millis -= 10_000_000
        assertEquals(5, policy.remainingSeconds())
    }

    @Test fun successResetsEverything() {
        repeat(4) { policy.onFailure() }
        policy.onSuccess()
        assertEquals(0, store.failedAttempts); assertEquals(0L, store.lastLockTime)
        assertEquals(0, policy.remainingSeconds())
        assertEquals(0, policy.onFailure())
    }
}

class SumUpConfigTest {
    @Test fun needsEnabledAndKey() {
        assertFalse(SumUpConfig.isReady(false, "k"))
        assertFalse(SumUpConfig.isReady(true, null))
        assertFalse(SumUpConfig.isReady(true, "  "))
        assertTrue(SumUpConfig.isReady(true, "k"))
    }
}
