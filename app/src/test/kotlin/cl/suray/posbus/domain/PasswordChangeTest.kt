package cl.suray.posbus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordChangeTest {
    @Test fun wrongCurrentComesFirst() {
        assertEquals(PasswordChangeError.CurrentWrong, PasswordChange.validate(false, "12", "99", exactLength = false))
    }

    @Test fun tooShortNewPassword() {
        assertEquals(PasswordChangeError.InvalidLength, PasswordChange.validate(true, "12345", "12345", exactLength = false))
    }

    @Test fun mismatchedRepeat() {
        assertEquals(PasswordChangeError.Mismatch, PasswordChange.validate(true, "123456", "123457", exactLength = false))
    }

    @Test fun validChangeAcceptsSixOrMoreCharacters() {
        assertNull(PasswordChange.validate(true, "123456", "123456", exactLength = false))
        assertNull(PasswordChange.validate(true, "123456789012", "123456789012", exactLength = false))
    }

    @Test fun comprobanteRequiresExactlySix() {
        assertEquals(PasswordChangeError.InvalidLength, PasswordChange.validate(true, "1234567", "1234567", exactLength = true))
        assertEquals(PasswordChangeError.InvalidLength, PasswordChange.validate(true, "12345", "12345", exactLength = true))
        assertNull(PasswordChange.validate(true, "123456", "123456", exactLength = true))
    }

    @Test fun comprobanteTrimsBeforeCountingLikePasswordManager() {
        assertNull(PasswordChange.validate(true, " 123456 ", " 123456 ", exactLength = true))
    }

    @Test fun lengthErrorBeatsMismatch() {
        assertEquals(PasswordChangeError.InvalidLength, PasswordChange.validate(true, "123", "456", exactLength = false))
    }

    @Test fun messagesDependOnTheLengthRule() {
        assertEquals("Debe tener al menos 6 caracteres", PasswordChange.message(PasswordChangeError.InvalidLength, false))
        assertEquals("Debe tener exactamente 6 caracteres", PasswordChange.message(PasswordChangeError.InvalidLength, true))
        assertEquals("Las claves no coinciden", PasswordChange.message(PasswordChangeError.Mismatch, false))
    }
}
