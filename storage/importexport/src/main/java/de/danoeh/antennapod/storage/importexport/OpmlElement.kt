package de.danoeh.antennapod.storage.importexport

/**
 * Represents a single feed in an OPML file.
 */
class OpmlElement {
    private var text: String? = null
    private var xmlUrl: String? = null
    private var htmlUrl: String? = null
    private var type: String? = null

    constructor() {

    }

    fun getText(): String? {
        return text
    }

    fun setText(text: String?) {
        this.text = text
    }

    fun getXmlUrl(): String? {
        return xmlUrl
    }

    fun setXmlUrl(xmlUrl: String?) {
        this.xmlUrl = xmlUrl
    }

    fun getHtmlUrl(): String? {
        return htmlUrl
    }

    fun setHtmlUrl(htmlUrl: String?) {
        this.htmlUrl = htmlUrl
    }

    fun getType(): String? {
        return type
    }

    fun setType(type: String?) {
        this.type = type
    }

}
