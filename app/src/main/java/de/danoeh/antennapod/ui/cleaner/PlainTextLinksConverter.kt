package de.danoeh.antennapod.ui.cleaner

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

import java.util.ArrayList
import java.util.regex.Matcher
import java.util.regex.Pattern

class PlainTextLinksConverter {
    companion object {
        private val HTTP_LINK_REGEX = Pattern.compile(
                "(?:https?://(?:www\\.)?|www\\.)" + // http(s)://[www.] OR www.
                        "[-a-zA-Z0-9@:%._+~#=]{1,256}" + // Domain name
                        "\\.[a-zA-Z]{2,6}\\b" + // Top-level domain
                        "[-a-zA-Z0-9@:%_+.*~#?!&$/=()\\[\\],;]*", // Path, query params
                Pattern.CASE_INSENSITIVE
        )
        val NOT_ALLOWED_END_CHARS: List<String> = listOf(
                ".", ",", ";", ":", "?", "!", ")", "(", "[", "]", "-", "_", "~", "#", "@", "$", "*", "+")

        private const val STARTS_WITH_HTTP = "(?i)https?://.*"
        private const val ANCHOR_TAG = "a"
        private const val ANCHOR_ADDRESS = "href"

        /**
         * Provided text can be an HTML document or plain text.
         * It may contain a mixture of plain-text links and HTML links.
         * Only plain-text links will be converted to HTML {@code <a>} tags.
         */
        @JvmStatic
        fun convertLinksToHtml(text: String?): String? {
            if (text == null || text.isEmpty()) {
                return text
            }
            try {
                val doc = Jsoup.parse(text)
                convertLinksToHtml(doc)
                return doc.body().html()
            } catch (e: Exception) {
                return text
            }
        }

        @JvmStatic
        fun convertLinksToHtml(doc: Document?) {
            if (doc == null) {
                return
            }
            NodeTraversor.traverse(LinkConvertingVisitor(), doc.body())
        }

        /**
         * Ensures that URLs are only converted if they are not already part of an existing anchor tag.
         * Document structure remains untouched, logic affects only [TextNode] - leaf element with no tags in it.
         * One [TextNode] is replaced with multiple [Element]s:
         * <li>[TextNode] with text before the link</li>
         * <li>[Element] with the link tag</li>
         * <li>[TextNode] with text after the link</li>
         */
        private class LinkConvertingVisitor : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                if (node !is TextNode) {
                    return
                } else if (isInsideAnchor(node)) {
                    return
                }
                val textNode = node
                val originalText = textNode.getWholeText()
                val matcher = HTTP_LINK_REGEX.matcher(originalText)

                if (!matcher.find()) {
                    return
                }
                val newNodes: MutableList<Node> = ArrayList()
                var lastEnd = 0
                matcher.reset()

                while (matcher.find()) {
                    val url = matcher.group()
                    if (endsWithPunctuation(url)) {
                        continue
                    }
                    if (matcher.start() > lastEnd) {
                        newNodes.add(TextNode(originalText.substring(lastEnd, matcher.start())))
                    }
                    newNodes.add(link(url))
                    lastEnd = matcher.end()
                }

                if (lastEnd < originalText.length) {
                    newNodes.add(TextNode(originalText.substring(lastEnd)))
                }

                if (!newNodes.isEmpty()) {
                    val parent = textNode.parent()
                    if (parent is Element) {
                        val index = textNode.siblingIndex()
                        textNode.remove()
                        parent.insertChildren(index, newNodes)
                    }
                }
            }

            private fun link(detectedUrl: String): Element {
                var url = detectedUrl
                if (!detectedUrl.matches(Regex(STARTS_WITH_HTTP))) {
                    url = "https://" + url
                }
                return Element(ANCHOR_TAG).attr(ANCHOR_ADDRESS, url).text(detectedUrl)
            }

            override fun tail(node: Node, depth: Int) {
                //not needed
            }
        }

        private fun isInsideAnchor(node: Node): Boolean {
            var current: Node? = node
            while (current != null) {
                if (current is Element) {
                    if (ANCHOR_TAG.equals(current.tagName(), ignoreCase = true)) {
                        return true
                    }
                }
                current = current.parent()
            }
            return false
        }

        private fun endsWithPunctuation(url: String): Boolean {
            for (endChar in NOT_ALLOWED_END_CHARS) {
                if (url.endsWith(endChar)) {
                    return true
                }
            }
            return false
        }
    }
}
