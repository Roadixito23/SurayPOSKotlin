package cl.suray.posbus.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoroutinesTest {
    @Test fun successIsReturned() = runTest {
        assertEquals(7, runCatchingCancellable { 7 }.getOrNull())
    }

    @Test fun ordinaryExceptionBecomesFailure() = runTest {
        val r = runCatchingCancellable { error("boom") }
        assertTrue(r.exceptionOrNull() is IllegalStateException)
    }

    @Test fun internalCancellationWhileActiveIsFailure() = runTest {
        val r = runCatchingCancellable { throw CancellationException("interna") }
        assertTrue(r.isFailure)
    }

    @Test fun ownCancellationIsRethrown() = runTest {
        var swallowed = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            runCatchingCancellable { yield(); kotlinx.coroutines.awaitCancellation() }
            swallowed = true // no debe llegar aquí
        }
        job.cancelAndJoin()
        assertTrue(!swallowed)
    }
}
