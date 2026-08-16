package de.danoeh.antennapod.storage.importexport

/**
 * Contains symbols for reading and writing OPML documents.
 */
internal class OpmlSymbols private constructor() {
    companion object {
        const val XML_FEATURE_INDENT_OUTPUT = "http://xmlpull.org/v1/doc/features.html#indent-output"

        const val HEAD = "head"
        const val BODY = "body"
        const val TITLE = "title"
        const val OPML = "opml"
        internal const val OUTLINE = "outline"
        internal const val TEXT = "text"
        internal const val XMLURL = "xmlUrl"
        internal const val HTMLURL = "htmlUrl"
        internal const val TYPE = "type"
        internal const val VERSION = "version"
        internal const val DATE_CREATED = "dateCreated"
    }
}
