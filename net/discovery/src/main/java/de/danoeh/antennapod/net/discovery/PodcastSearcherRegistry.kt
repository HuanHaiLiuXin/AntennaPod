package de.danoeh.antennapod.net.discovery

import io.reactivex.rxjava3.core.Single

import java.util.ArrayList

class PodcastSearcherRegistry private constructor() {

    class SearcherInfo(@JvmField val searcher: PodcastSearcher, @JvmField val weight: Float)

    companion object {
        private var searchProviders: List<SearcherInfo>? = null

        @JvmStatic
        @Synchronized
        fun getSearchProviders(): List<SearcherInfo> {
            if (searchProviders == null) {
                searchProviders = ArrayList<SearcherInfo>().apply {
                    add(SearcherInfo(CombinedSearcher(), 0.0f))
                    add(SearcherInfo(FyydPodcastSearcher(), 0.0f))
                    add(SearcherInfo(ItunesPodcastSearcher(), 1.0f))
                    add(SearcherInfo(PodcastIndexPodcastSearcher(), 1.0f))
                }
            }
            return searchProviders!!
        }

        @JvmStatic
        fun lookupUrl(url: String): Single<String> {
            for (searchProviderInfo in getSearchProviders()) {
                if (searchProviderInfo.searcher.javaClass != CombinedSearcher::class.java
                        && searchProviderInfo.searcher.urlNeedsLookup(url)) {
                    return searchProviderInfo.searcher.lookupUrl(url)
                }
            }
            return Single.just(url)
        }

        @JvmStatic
        fun urlNeedsLookup(url: String): Boolean {
            for (searchProviderInfo in getSearchProviders()) {
                if (searchProviderInfo.searcher.javaClass != CombinedSearcher::class.java
                        && searchProviderInfo.searcher.urlNeedsLookup(url)) {
                    return true
                }
            }
            return false
        }
    }
}
