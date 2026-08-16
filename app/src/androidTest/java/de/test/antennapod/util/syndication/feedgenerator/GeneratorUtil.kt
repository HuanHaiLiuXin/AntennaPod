package de.test.antennapod.util.syndication.feedgenerator

import org.xmlpull.v1.XmlSerializer

import java.io.IOException

/**
 * Utility methods for FeedGenerator
 */
internal abstract class GeneratorUtil {
    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun addPaymentLink(xml: XmlSerializer, paymentLink: String?, withNamespace: Boolean) {
            val ns = if (withNamespace) "http://www.w3.org/2005/Atom" else null
            xml.startTag(ns, "link")
            xml.attribute(null, "rel", "payment")
            xml.attribute(null, "href", paymentLink)
            xml.attribute(null, "type", "text/html")
            xml.endTag(ns, "link")
        }
    }
}
