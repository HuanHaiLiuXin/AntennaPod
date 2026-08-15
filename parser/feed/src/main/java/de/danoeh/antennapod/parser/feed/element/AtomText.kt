package de.danoeh.antennapod.parser.feed.element

import androidx.core.text.HtmlCompat

import de.danoeh.antennapod.parser.feed.namespace.Namespace

/** Represents Atom Element which contains text (content, title, summary). */
class AtomText : SyndElement {
    private val type: String?
    private var content: String? = null

    constructor(name: String, namespace: Namespace, type: String?) : super(name, namespace) {
        this.type = type
    }

    /** Processes the content according to the type and returns it. */
    fun getProcessedContent(): String? {
        if (type == null) {
            return content
        } else if (type == TYPE_HTML) {
            return HtmlCompat.fromHtml(content!!, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        } else if (type == TYPE_XHTML) {
            return content
        } else { // Handle as text by default
            return content
        }
    }

    fun setContent(content: String?) {
        this.content = content
    }

    companion object {
        const val TYPE_HTML = "html"
        private const val TYPE_XHTML = "xhtml"
    }
}
