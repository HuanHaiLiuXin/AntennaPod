package de.danoeh.antennapod.net.download.service.episode.autodownload

import org.junit.Assert.assertEquals
import org.junit.Test

import java.text.SimpleDateFormat

class APCleanupAlgorithmTest {

    @Test
    @Throws(Exception::class)
    fun testCalcMostRecentDateForDeletion() {
        val algo = APCleanupAlgorithm(24)
        val curDateForTest = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse("2018-11-13T14:08:56-0800")
        val resExpected = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse("2018-11-12T14:08:56-0800")
        val resActual = algo.calcMostRecentDateForDeletion(curDateForTest!!)
        assertEquals("cutoff for retaining most recent 1 day", resExpected, resActual)
    }
}
