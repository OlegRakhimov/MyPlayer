package com.example.freelocalplayer.data.room

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure JVM tests: no Android or user data. */
class RoomReadRolloutWindowTest {
    private val now = 1000000L

    @Test fun defaultFlagCannotOpenRoom() {
        assertEquals(RoomReadRolloutWindow.Status.DISABLED,
            RoomReadRolloutWindow.evaluate(false, 0L, now))
        assertEquals(RoomReadRolloutWindow.Status.DISABLED,
            RoomReadRolloutWindow.evaluate(false, now + 60000L, now))
    }

    @Test fun shortFutureLeasePermitsRoom() {
        assertEquals(RoomReadRolloutWindow.Status.ACTIVE,
            RoomReadRolloutWindow.evaluate(true, now + 1L, now))
        assertEquals(RoomReadRolloutWindow.Status.ACTIVE,
            RoomReadRolloutWindow.evaluate(true, now + RoomReadRolloutWindow.MAX_LEASE_MS, now))
    }

    @Test fun expiredOrMissingLeaseMustFailClosed() {
        assertEquals(RoomReadRolloutWindow.Status.EXPIRED_OR_INVALID,
            RoomReadRolloutWindow.evaluate(true, 0L, now))
        assertEquals(RoomReadRolloutWindow.Status.EXPIRED_OR_INVALID,
            RoomReadRolloutWindow.evaluate(true, now, now))
        assertEquals(RoomReadRolloutWindow.Status.EXPIRED_OR_INVALID,
            RoomReadRolloutWindow.evaluate(true, now - 1L, now))
    }

    @Test fun oversizedLeaseCannotBeUsedAsPermanentEnable() {
        assertEquals(RoomReadRolloutWindow.Status.EXPIRED_OR_INVALID,
            RoomReadRolloutWindow.evaluate(true, now + RoomReadRolloutWindow.MAX_LEASE_MS + 1L, now))
        assertEquals(RoomReadRolloutWindow.Status.EXPIRED_OR_INVALID,
            RoomReadRolloutWindow.evaluate(true, Long.MAX_VALUE, now))
    }

    @Test fun backwardClockChangeFailsClosed() {
        val expiry = now + 60000L
        assertEquals(RoomReadRolloutWindow.Status.EXPIRED_OR_INVALID,
            RoomReadRolloutWindow.evaluate(true, expiry, now - 200000L))
    }

    @Test fun forwardClockChangeFailsClosed() {
        assertEquals(RoomReadRolloutWindow.Status.EXPIRED_OR_INVALID,
            RoomReadRolloutWindow.evaluate(true, now + 50000L, now + 50001L))
    }

    @Test fun overflowedRemainingIsRejected() {
        assertEquals(RoomReadRolloutWindow.Status.EXPIRED_OR_INVALID,
            RoomReadRolloutWindow.evaluate(true, Long.MAX_VALUE, Long.MIN_VALUE))
    }
}
