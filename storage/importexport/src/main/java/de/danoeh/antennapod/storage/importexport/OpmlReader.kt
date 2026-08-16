package de.danoeh.antennapod.storage.importexport

import android.text.TextUtils
import android.util.Log

import de.danoeh.antennapod.storage.preferences.BuildConfig
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory

import java.io.IOException
import java.io.Reader
import java.util.ArrayList

/**
 * Reads OPML documents.
 */
class OpmlReader {
    companion object {
        private const val TAG = "OpmlReader"
    }

    private var isInOpml = false

    /**
     * Reads an Opml document and returns a list of all OPML elements it can
     * find
     */
    @Throws(XmlPullParserException::class, IOException::class)
    fun readDocument(reader: Reader): ArrayList<OpmlElement> {
        val elementList = ArrayList<OpmlElement>()
        val factory = XmlPullParserFactory.newInstance()
        factory.setNamespaceAware(true)
        val xpp = factory.newPullParser()
        xpp.setInput(reader)
        var eventType = xpp.getEventType()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_DOCUMENT -> {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Reached beginning of document")
                    }
                }
                XmlPullParser.START_TAG -> {
                    if (xpp.getName() == OpmlSymbols.OPML) {
                        isInOpml = true
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Reached beginning of OPML tree.")
                        }
                    } else if (isInOpml && xpp.getName() == OpmlSymbols.OUTLINE) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Found new Opml element")
                        }
                        val element = OpmlElement()

                        val title = xpp.getAttributeValue(null, OpmlSymbols.TITLE)
                        if (!TextUtils.isEmpty(title)) {
                            Log.i(TAG, "Using title: " + title)
                            element.setText(title)
                        } else {
                            Log.i(TAG, "Title not found, using text")
                            element.setText(xpp.getAttributeValue(null, OpmlSymbols.TEXT))
                        }
                        element.setXmlUrl(xpp.getAttributeValue(null, OpmlSymbols.XMLURL))
                        element.setHtmlUrl(xpp.getAttributeValue(null, OpmlSymbols.HTMLURL))
                        element.setType(xpp.getAttributeValue(null, OpmlSymbols.TYPE))
                        if (!TextUtils.isEmpty(element.getXmlUrl())) {
                            if (TextUtils.isEmpty(element.getText())) {
                                Log.i(TAG, "Opml element has no text attribute.")
                                element.setText(element.getXmlUrl())
                            }
                            elementList.add(element)
                        } else {
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "Skipping element because of missing xml url")
                            }
                        }
                    }
                }
                else -> {
                }
            }
            eventType = xpp.next()
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Parsing finished.")
        }

        return elementList
    }

}
