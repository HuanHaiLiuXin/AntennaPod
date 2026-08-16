package de.danoeh.antennapod.parser.feed.util

import android.util.Log

import de.danoeh.antennapod.parser.feed.UnsupportedFeedtypeException
import org.apache.commons.io.input.XmlStreamReader
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory

import java.io.File
import java.io.IOException
import java.io.Reader

import de.danoeh.antennapod.model.feed.Feed

/** Gets the type of a specific feed by reading the root element. */
class TypeGetter {

    enum class Type {
        RSS20, RSS091, ATOM, INVALID
    }

    fun getType(feed: Feed): Type {
        val factory: XmlPullParserFactory
        if (feed.getLocalFileUrl() != null) {
            var reader: Reader? = null
            try {
                factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = true
                val xpp = factory.newPullParser()
                reader = createReader(feed)
                xpp.setInput(reader)
                var eventType = xpp.eventType

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        val tag = xpp.name
                        when (tag) {
                            ATOM_ROOT -> {
                                feed.setType(Feed.TYPE_ATOM1)
                                Log.d(TAG, "Recognized type Atom")

                                val strLang = xpp.getAttributeValue("http://www.w3.org/XML/1998/namespace", "lang")
                                if (strLang != null) {
                                    feed.setLanguage(strLang)
                                }

                                return Type.ATOM
                            }
                            RSS_ROOT -> {
                                val strVersion = xpp.getAttributeValue(null, "version")
                                if (strVersion == null) {
                                    feed.setType(Feed.TYPE_RSS2)
                                    Log.d(TAG, "Assuming type RSS 2.0")
                                    return Type.RSS20
                                } else if (strVersion == "2.0") {
                                    feed.setType(Feed.TYPE_RSS2)
                                    Log.d(TAG, "Recognized type RSS 2.0")
                                    return Type.RSS20
                                } else if (strVersion == "0.91" || strVersion == "0.92") {
                                    Log.d(TAG, "Recognized type RSS 0.91/0.92")
                                    return Type.RSS091
                                }
                                throw UnsupportedFeedtypeException("Unsupported rss version")
                            }
                            else -> {
                                Log.d(TAG, "Type is invalid: " + tag)
                                throwExceptionIfWebsite(feed)
                                throw UnsupportedFeedtypeException(tag, null)
                            }
                        }
                    } else {
                        try {
                            eventType = xpp.next()
                        } catch (e: RuntimeException) {
                            // Apparently this happens on some devices...
                            throw UnsupportedFeedtypeException("Unable to get type")
                        }
                    }
                }
            } catch (e: XmlPullParserException) {
                e.printStackTrace()
                throwExceptionIfWebsite(feed)
                throw UnsupportedFeedtypeException(e.message)

            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                if (reader != null) {
                    try {
                        reader.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
        Log.d(TAG, "Type is invalid")
        throw UnsupportedFeedtypeException("Unknown problem when trying to determine feed type")
    }

    private fun createReader(feed: Feed): Reader? {
        val reader: Reader
        try {
            reader = XmlStreamReader(File(feed.getLocalFileUrl()))
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
        return reader
    }

    private fun throwExceptionIfWebsite(feed: Feed) {
        try {
            val document = Jsoup.parse(File(feed.getLocalFileUrl()))
            val titleElement = document.head().getElementsByTag("title").first()
            if (titleElement != null) {
                throw UnsupportedFeedtypeException("html", "Website title: \"" + titleElement.text() + "\"")
            }
            val firstChild = document.children().first()
            throw UnsupportedFeedtypeException(if (firstChild != null) firstChild.tagName() else "?", null)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "TypeGetter"

        private const val ATOM_ROOT = "feed"
        private const val RSS_ROOT = "rss"
    }
}
