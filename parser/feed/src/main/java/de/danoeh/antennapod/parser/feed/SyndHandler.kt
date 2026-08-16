package de.danoeh.antennapod.parser.feed

import android.util.Log

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.parser.feed.element.SyndElement
import de.danoeh.antennapod.parser.feed.namespace.Atom
import de.danoeh.antennapod.parser.feed.namespace.Content
import de.danoeh.antennapod.parser.feed.namespace.DublinCore
import de.danoeh.antennapod.parser.feed.namespace.Itunes
import de.danoeh.antennapod.parser.feed.namespace.Media
import de.danoeh.antennapod.parser.feed.namespace.Namespace
import de.danoeh.antennapod.parser.feed.namespace.PodcastIndex
import de.danoeh.antennapod.parser.feed.namespace.Rss20
import de.danoeh.antennapod.parser.feed.namespace.SimpleChapters
import de.danoeh.antennapod.parser.feed.util.TypeGetter
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/** Superclass for all SAX Handlers which process Syndication formats */
class SyndHandler : DefaultHandler {
    @JvmField
    val state: HandlerState

    constructor(feed: Feed, type: TypeGetter.Type) {
        state = HandlerState(feed)
        if (type == TypeGetter.Type.RSS20 || type == TypeGetter.Type.RSS091) {
            state.defaultNamespaces.push(Rss20())
        }
    }

    @Throws(SAXException::class)
    override fun startElement(uri: String, localName: String, qualifiedName: String,
                              attributes: Attributes) {
        state.contentBuf = StringBuilder()
        val handler = getHandlingNamespace(uri, qualifiedName)
        if (handler != null) {
            val element = handler.handleElementStart(localName, state,
                    attributes)
            state.tagstack.push(element)

        }
    }

    @Throws(SAXException::class)
    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (!state.tagstack.empty()) {
            if (state.getTagstack().size >= 2) {
                if (state.contentBuf != null) {
                    state.contentBuf!!.append(ch, start, length)
                }
            }
        }
    }

    @Throws(SAXException::class)
    override fun endElement(uri: String, localName: String, qualifiedName: String) {
        val handler = getHandlingNamespace(uri, qualifiedName)
        if (handler != null) {
            handler.handleElementEnd(localName, state)
            state.tagstack.pop()

        }
        state.contentBuf = null

    }

    @Throws(SAXException::class)
    override fun endPrefixMapping(prefix: String) {
        if (state.defaultNamespaces.size > 1 && prefix == DEFAULT_PREFIX) {
            state.defaultNamespaces.pop()
        }
    }

    @Throws(SAXException::class)
    override fun startPrefixMapping(prefix: String, uri: String) {
        // Find the right namespace
        if (!state.namespaces.containsKey(uri)) {
            if (uri == Atom.NSURI) {
                if (prefix == DEFAULT_PREFIX) {
                    state.defaultNamespaces.push(Atom())
                } else if (prefix == Atom.NSTAG) {
                    state.namespaces.put(uri, Atom())
                    Log.d(TAG, "Recognized Atom namespace")
                }
            } else if (uri == Content.NSURI
                    && prefix == Content.NSTAG) {
                state.namespaces.put(uri, Content())
                Log.d(TAG, "Recognized Content namespace")
            } else if (uri == Itunes.NSURI
                    && prefix == Itunes.NSTAG) {
                state.namespaces.put(uri, Itunes())
                Log.d(TAG, "Recognized ITunes namespace")
            } else if (uri == SimpleChapters.NSURI
                    && prefix.matches(Regex(SimpleChapters.NSTAG))) {
                state.namespaces.put(uri, SimpleChapters())
                Log.d(TAG, "Recognized SimpleChapters namespace")
            } else if (uri == Media.NSURI
                    && prefix == Media.NSTAG) {
                state.namespaces.put(uri, Media())
                Log.d(TAG, "Recognized media namespace")
            } else if (uri == DublinCore.NSURI
                    && prefix == DublinCore.NSTAG) {
                state.namespaces.put(uri, DublinCore())
                Log.d(TAG, "Recognized DublinCore namespace")
            } else if ((uri == PodcastIndex.NSURI || uri == PodcastIndex.NSURI2)
                    && prefix == PodcastIndex.NSTAG) {
                state.namespaces.put(uri, PodcastIndex())
                Log.d(TAG, "Recognized PodcastIndex namespace")
            }
        }
    }

    private fun getHandlingNamespace(uri: String, qualifiedName: String): Namespace? {
        var handler = state.namespaces[uri]
        if (handler == null && !state.defaultNamespaces.empty()
                && !qualifiedName.contains(":")) {
            handler = state.defaultNamespaces.peek()
        }
        return handler
    }

    @Throws(SAXException::class)
    override fun endDocument() {
        super.endDocument()
        state.getFeed().setItems(state.getItems())
    }

    fun getState(): HandlerState {
        return state
    }

    companion object {
        private const val TAG = "SyndHandler"
        private const val DEFAULT_PREFIX = ""
    }
}
