package de.danoeh.antennapod.net.download.serviceinterface

import android.os.Bundle
import de.danoeh.antennapod.model.download.DownloadRequest
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.net.common.UrlChecker

class DownloadRequestBuilder {
    private val destination: String
    private var source: String?
    private val title: String?
    private var username: String? = null
    private var password: String? = null
    private var lastModified: String? = null
    private val feedfileId: Long
    private val feedfileType: Int
    private val arguments = Bundle()
    private var initiatedByUser = true

    constructor(destination: String, media: FeedMedia) {
        this.destination = destination
        this.source = UrlChecker.prepareUrl(media.getDownloadUrl()!!)
        this.title = media.getHumanReadableIdentifier()
        this.feedfileId = media.getId()
        this.feedfileType = FeedMedia.FEEDFILETYPE_FEEDMEDIA
    }

    constructor(destination: String, feed: Feed) {
        this.destination = destination
        this.source = if (feed.isLocalFeed()) feed.getDownloadUrl() else UrlChecker.prepareUrl(feed.getDownloadUrl()!!)
        this.title = feed.getHumanReadableIdentifier()
        this.feedfileId = feed.getId()
        this.feedfileType = Feed.FEEDFILETYPE_FEED
        arguments.putInt(DownloadRequest.REQUEST_ARG_PAGE_NR, feed.getPageNr())
    }

    fun withInitiatedByUser(initiatedByUser: Boolean): DownloadRequestBuilder {
        this.initiatedByUser = initiatedByUser
        return this
    }

    fun setSource(source: String) {
        this.source = source
    }

    fun setForce(force: Boolean) {
        if (force) {
            lastModified = null
        }
    }

    fun lastModified(lastModified: String?): DownloadRequestBuilder {
        this.lastModified = lastModified
        return this
    }

    fun withAuthentication(username: String?, password: String?): DownloadRequestBuilder {
        this.username = username
        this.password = password
        return this
    }

    fun build(): DownloadRequest {
        return DownloadRequest(destination, source!!, title!!, feedfileId, feedfileType,
                lastModified, username, password, false, arguments, initiatedByUser)
    }
}
