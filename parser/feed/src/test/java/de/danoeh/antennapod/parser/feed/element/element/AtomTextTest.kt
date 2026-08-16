package de.danoeh.antennapod.parser.feed.element.element

import de.danoeh.antennapod.parser.feed.element.AtomText
import de.danoeh.antennapod.parser.feed.namespace.Atom
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit test for [AtomText].
 */
@RunWith(RobolectricTestRunner::class)
class AtomTextTest {

    @Test
    fun testProcessingHtml() {
        for (pair in TEST_DATA) {
            val atomText = AtomText("", Atom(), AtomText.TYPE_HTML)
            atomText.setContent(pair[0])
            assertEquals(pair[1], atomText.getProcessedContent())
        }
    }

    companion object {
        private val TEST_DATA = arrayOf(
                arrayOf("&gt;", ">"),
                arrayOf(">", ">"),
                arrayOf("&lt;Fran&ccedil;ais&gt;", "<Français>"),
                arrayOf("ßÄÖÜ", "ßÄÖÜ"),
                arrayOf("&quot;", "\""),
                arrayOf("&szlig;", "ß"),
                arrayOf("&#8217;", "’"),
                arrayOf("&#x2030;", "‰"),
                arrayOf("&euro;", "€"))
    }
}
