package de.danoeh.antennapod.net.sync.gpoddernet.model

class GpodnetPodcast {
    private var url: String = ""
    private var title: String = ""
    private var description: String = ""
    private var subscribers: Int = 0
    private var logoUrl: String? = null
    private var website: String? = null
    private var mygpoLink: String? = null
    private var author: String? = null

    constructor(url: String, title: String, description: String, subscribers: Int,
                logoUrl: String?, website: String?, mygpoLink: String?, author: String?) {
        this.url = url
        this.title = title
        this.description = description
        this.subscribers = subscribers
        this.logoUrl = logoUrl
        this.website = website
        this.mygpoLink = mygpoLink
        this.author = author
    }

    override fun toString(): String {
        return "GpodnetPodcast [url=" + url + ", title=" + title +
                ", description=" + description + ", subscribers=" +
                subscribers + ", logoUrl=" + logoUrl + ", website=" + website +
                ", mygpoLink=" + mygpoLink + "]"
    }

    fun getUrl(): String {
        return url
    }

    fun getTitle(): String {
        return title
    }

    fun getDescription(): String {
        return description
    }

    fun getSubscribers(): Int {
        return subscribers
    }

    fun getLogoUrl(): String? {
        return logoUrl
    }

    fun getWebsite(): String? {
        return website
    }

    fun getAuthor(): String? {
        return author
    }

    fun getMygpoLink(): String? {
        return mygpoLink
    }

}
