package de.danoeh.antennapod.ui.episodeslist

import android.text.TextUtils
import de.danoeh.antennapod.net.common.AntennapodHttpClient
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.net.common.NetworkUtils
import de.danoeh.antennapod.model.feed.FeedMedia
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.Request
import android.util.Log

import java.io.File
import java.io.IOException

abstract class MediaSizeLoader {
    companion object {
        private const val TAG = "MediaSizeLoader"

        @JvmStatic
        fun getFeedMediaSizeObservable(media: FeedMedia): Single<Long> {
            return Single.create<Long> { emitter ->
                if (!NetworkUtils.isEpisodeHeadDownloadAllowed()) {
                    emitter.onSuccess(0L)
                    return@create
                }
                var size = Integer.MIN_VALUE.toLong()
                if (media.isDownloaded()) {
                    val mediaFile = File(media.getLocalFileUrl())
                    if (mediaFile.exists()) {
                        size = mediaFile.length()
                    }
                } else if (!media.checkedOnSizeButUnknown()) {
                    // only query the network if we haven't already checked

                    val url = media.getDownloadUrl()
                    if (TextUtils.isEmpty(url) || !url!!.startsWith("http")) {
                        emitter.onSuccess(0L)
                        return@create
                    }

                    val client = AntennapodHttpClient.getHttpClient()
                    val httpReq = Request.Builder()
                            .url(url)
                            .header("Accept-Encoding", "identity")
                            .head()
                    try {
                        val response = client.newCall(httpReq.build()).execute()
                        if (response.isSuccessful) {
                            val contentLength = response.header("Content-Length")
                            try {
                                size = Integer.parseInt(contentLength).toLong()
                            } catch (e: NumberFormatException) {
                                Log.e(TAG, Log.getStackTraceString(e))
                            }
                        }
                    } catch (e: IOException) {
                        emitter.onSuccess(0L)
                        Log.e(TAG, Log.getStackTraceString(e))
                        return@create // better luck next time
                    }
                }
                Log.d(TAG, "new size: " + size)
                if (size <= 0) {
                    // they didn't tell us the size, but we don't want to keep querying on it
                    media.setCheckedOnSizeButUnknown()
                } else {
                    media.setSize(size)
                }
                emitter.onSuccess(size)
                DBWriter.setMediaDownloadInformation(media)
            }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
        }
    }
}
