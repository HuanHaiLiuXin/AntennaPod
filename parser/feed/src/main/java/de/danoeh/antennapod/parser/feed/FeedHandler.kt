package de.danoeh.antennapod.parser.feed

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.parser.feed.util.TypeGetter
import org.apache.commons.io.input.XmlStreamReader
import org.xml.sax.InputSource
import org.xml.sax.SAXException

import java.io.File
import java.io.IOException
import java.io.Reader

import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory

class FeedHandler {
    @Throws(SAXException::class, IOException::class,
            ParserConfigurationException::class, UnsupportedFeedtypeException::class)
    fun parseFeed(feed: Feed): FeedHandlerResult {
        val tg = TypeGetter()
        val type = tg.getType(feed)
        val handler = SyndHandler(feed, type)

        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = true
        val saxParser: SAXParser = factory.newSAXParser()
        val file = File(feed.getLocalFileUrl())
        val inputStreamReader: Reader = XmlStreamReader(file)
        val inputSource = InputSource(inputStreamReader)

        saxParser.parse(inputSource, handler)
        inputStreamReader.close()
        return FeedHandlerResult(handler.state.feed, handler.state.alternateUrls, handler.state.redirectUrl)
    }
}
