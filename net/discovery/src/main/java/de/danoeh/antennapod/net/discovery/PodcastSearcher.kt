package de.danoeh.antennapod.net.discovery

import io.reactivex.rxjava3.core.Single

interface PodcastSearcher {
    fun search(query: String?): Single<List<PodcastSearchResult>>

    fun lookupUrl(resultUrl: String): Single<String>

    fun urlNeedsLookup(resultUrl: String): Boolean

    fun getName(): String
}
