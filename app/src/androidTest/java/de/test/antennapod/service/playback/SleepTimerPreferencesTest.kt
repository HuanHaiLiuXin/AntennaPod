package de.test.antennapod.service.playback

import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerPreferencesTest {
    @Test
    fun testIsInTimeRange() {
        assertTrue(SleepTimerPreferences.isInTimeRange(0, 10, 8))
        assertTrue(SleepTimerPreferences.isInTimeRange(1, 10, 8))
        assertTrue(SleepTimerPreferences.isInTimeRange(1, 10, 1))
        assertTrue(SleepTimerPreferences.isInTimeRange(20, 10, 8))
        assertTrue(SleepTimerPreferences.isInTimeRange(20, 20, 8))
        assertFalse(SleepTimerPreferences.isInTimeRange(1, 6, 8))
        assertFalse(SleepTimerPreferences.isInTimeRange(1, 6, 6))
        assertFalse(SleepTimerPreferences.isInTimeRange(20, 6, 8))
    }
}
