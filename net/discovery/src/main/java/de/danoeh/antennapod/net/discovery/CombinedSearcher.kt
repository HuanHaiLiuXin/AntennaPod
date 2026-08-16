package de.danoeh.antennapod.net.discovery

import android.text.TextUtils
import android.util.Log
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.concurrent.CountDownLatch

class CombinedSearcher() : PodcastSearcher {

    override fun search(query: String?): Single<List<PodcastSearchResult>> {
        val disposables = ArrayList<Disposable>()
        val singleResults = ArrayList<List<PodcastSearchResult>?>(
                Collections.nCopies(PodcastSearcherRegistry.getSearchProviders().size, null))
        val latch = CountDownLatch(PodcastSearcherRegistry.getSearchProviders().size)
        for (i in 0 until PodcastSearcherRegistry.getSearchProviders().size) {
            val searchProviderInfo = PodcastSearcherRegistry.getSearchProviders()[i]
            val searcher = searchProviderInfo.searcher
            if (searchProviderInfo.weight <= 0.00001f || searcher.javaClass == CombinedSearcher::class.java) {
                latch.countDown()
                continue
            }
            val index = i
            disposables.add(searcher.search(query).subscribe({ e ->
                singleResults[index] = e
                latch.countDown()
            }, { throwable ->
                Log.d(TAG, Log.getStackTraceString(throwable))
                latch.countDown()
            }))
        }

        return Single.create { subscriber ->
            latch.await()
            val results = weightSearchResults(singleResults)
            subscriber.onSuccess(results)
        }
                .doOnDispose {
                    for (disposable in disposables) {
                        disposable.dispose()
                    }
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    private fun weightSearchResults(singleResults: List<List<PodcastSearchResult>?>): List<PodcastSearchResult> {
        val resultRanking = HashMap<String?, Float>()
        val urlToResult = HashMap<String?, PodcastSearchResult>()
        for (i in singleResults.indices) {
            val providerPriority = PodcastSearcherRegistry.getSearchProviders()[i].weight
            val providerResults = singleResults[i]
            if (providerResults == null) {
                continue
            }
            for (position in providerResults.indices) {
                val result = providerResults[position]
                urlToResult[result.feedUrl] = result

                var ranking = 0f
                if (resultRanking.containsKey(result.feedUrl)) {
                    ranking = resultRanking[result.feedUrl]!!
                }
                ranking += 1f / (position + 1f)
                resultRanking[result.feedUrl] = ranking * providerPriority
            }
        }
        val sortedResults = ArrayList(resultRanking.entries)
        Collections.sort(sortedResults) { o1, o2 -> java.lang.Double.compare(o2.value.toDouble(), o1.value.toDouble()) }

        val results = ArrayList<PodcastSearchResult>()
        for (res in sortedResults) {
            results.add(urlToResult[res.key]!!)
        }
        return results
    }

    override fun lookupUrl(url: String): Single<String> {
        return PodcastSearcherRegistry.lookupUrl(url)
    }

    override fun urlNeedsLookup(url: String): Boolean {
        return PodcastSearcherRegistry.urlNeedsLookup(url)
    }

    override fun getName(): String {
        val names = ArrayList<String>()
        for (i in 0 until PodcastSearcherRegistry.getSearchProviders().size) {
            val searchProviderInfo = PodcastSearcherRegistry.getSearchProviders()[i]
            val searcher = searchProviderInfo.searcher
            if (searchProviderInfo.weight > 0.00001f && searcher.javaClass != CombinedSearcher::class.java) {
                names.add(searcher.getName())
            }
        }
        return TextUtils.join(", ", names)
    }

    companion object {
        private const val TAG = "CombinedSearcher"
    }
}
