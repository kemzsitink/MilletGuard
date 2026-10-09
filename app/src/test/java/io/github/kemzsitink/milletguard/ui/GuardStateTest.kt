package io.github.kemzsitink.milletguard.ui

import io.github.kemzsitink.milletguard.AutostartStatusReader.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardStateTest {
    /** Every setup step done and nothing blocked. */
    private val healthy = GuardState(
        gmsInstalled = true,
        canWrite = true,
        protectionEnabled = true,
        gmsPresent = true,
        notificationAllowed = true,
        selfAutostart = Status.ENABLED,
        selfNoRestrict = true,
    )

    @Test
    fun fullySetUpStateIsProtected() {
        assertEquals(Health.Protected, healthy.health)
        assertNull(healthy.nextStep)
        assertEquals(SetupStep.entries.size, healthy.doneCount)
    }

    @Test
    fun healthTakesTheFirstMatchingProblem() {
        assertEquals(Health.GmsMissing, healthy.copy(gmsInstalled = false, canWrite = false).health)
        assertEquals(Health.NoPermission, healthy.copy(canWrite = false, rejectedMessage = "x").health)
        assertEquals(Health.Rejected, healthy.copy(rejectedMessage = "x", gmsPresent = false).health)
        assertEquals(Health.Blocked, healthy.copy(gmsPresent = false, protectionEnabled = false).health)
        assertEquals(Health.Unguarded, healthy.copy(protectionEnabled = false).health)
        assertEquals(Health.Caveat, healthy.copy(selfNoRestrict = false).health)
    }

    @Test
    fun blockedStatusNotificationIsACaveatOnlyInPersistentMode() {
        assertEquals(Health.Caveat, healthy.copy(notificationAllowed = false).health)
        assertEquals(
            Health.Protected,
            healthy.copy(notificationAllowed = false, persistentNotification = false).health,
        )
    }

    @Test
    fun nextStepIsTheFirstUnfinishedOne() {
        val state = healthy.copy(canWrite = false, selfAutostart = Status.DISABLED)
        assertEquals(SetupStep.Permission, state.nextStep)
        assertEquals(SetupStep.Autostart, state.copy(canWrite = true).nextStep)
    }

    @Test
    fun autostartConfirmationCountsOnlyWhileStatusIsHidden() {
        val confirmed = healthy.copy(autostartConfirmed = true)
        assertTrue(confirmed.copy(selfAutostart = Status.UNKNOWN).isDone(SetupStep.Autostart))
        assertFalse(confirmed.copy(selfAutostart = Status.DISABLED).isDone(SetupStep.Autostart))
        assertFalse(confirmed.copy(selfAutostart = Status.PARTIAL).isDone(SetupStep.Autostart))
        assertFalse(healthy.copy(selfAutostart = Status.UNKNOWN).isDone(SetupStep.Autostart))
    }
}
