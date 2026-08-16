package de.danoeh.antennapod.ui.screen.onlinefeedview

import android.net.Uri
import androidx.collection.ArrayMap
import android.text.TextUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.io.File
import java.io.IOException

/**
 * Finds RSS/Atom URLs in a HTML document using the auto-discovery techniques described here:
 *
 * http://www.rssboard.org/rss-autodiscovery
 *
 * http://blog.whatwg.org/feed-autodiscovery
 */
class FeedDiscoverer {

    /**
     * Discovers links to RSS and Atom feeds in the given File which must be a HTML document.
     *
     * @return A map which contains the feed URLs as keys and titles as values (the feed URL is also used as a title if
     * a title cannot be found).
     */
    @Throws(IOException::class)
    fun findLinks(`in`: File, baseUrl: String): Map<String, String> {
        return findLinks(Jsoup.parse(`in`), baseUrl)
    }

    /**
     * Discovers links to RSS and Atom feeds in the given File which must be a HTML document.
     *
     * @return A map which contains the feed URLs as keys and titles as values (the feed URL is also used as a title if
     * a title cannot be found).
     */
    fun findLinks(`in`: String, baseUrl: String): Map<String, String> {
        return findLinks(Jsoup.parse(`in`), baseUrl)
    }

    private fun findLinks(document: Document, baseUrl: String): Map<String, String> {
        val res = ArrayMap<String, String>()
        val links: Elements = document.head().getElementsByTag("link")
        for (link in links) {
            val rel = link.attr("rel")
            val href = link.attr("href")
            if (!TextUtils.isEmpty(href) &&
                    (rel == "alternate" || rel == "feed")) {
                val type = link.attr("type")
                if (type == MIME_RSS || type == MIME_ATOM) {
                    val title = link.attr("title")
                    val processedUrl = processURL(baseUrl, href)
                    if (processedUrl != null) {
                        res[processedUrl] = if (TextUtils.isEmpty(title)) href else title
                    }
                }
            }
        }
        return res
    }

    private fun processURL(baseUrl: String, strUrl: String): String? {
        val uri = Uri.parse(strUrl)
        if (uri.isRelative()) {
            val res = Uri.parse(baseUrl).buildUpon().path(strUrl).build()
            return res?.toString()
        }
        return strUrl
    }

    companion object {
        private const val MIME_RSS = "application/rss+xml"
        private const val MIME_ATOM = "application/atom+xml"
    }
}
