package de.danoeh.antennapod.net.download.service.feed.remote

import java.util.Date
import java.util.concurrent.Callable

import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.model.download.DownloadRequest
import de.danoeh.antennapod.net.download.service.R

/**
 * Downloads files
 */
abstract class Downloader : Callable<Downloader> {
    companion object {
        private const val TAG = "Downloader"
    }

    @Volatile
    private var finished: Boolean = false
    @Volatile
    @JvmField
    var cancelled: Boolean = false
    @JvmField
    var permanentRedirectUrl: String? = null

    val request: DownloadRequest
    val result: DownloadResult

    constructor(request: DownloadRequest) {
        this.request = request
        this.request.setStatusMsg(R.string.download_pending)
        this.cancelled = false
        this.result = DownloadResult(0L, request.getTitle()!!, request.getFeedfileId(), request.getFeedfileType(),
                false, null, Date(), null)
    }

    protected abstract fun download()

    override fun call(): Downloader {
        download()
        finished = true
        return this
    }

    fun getDownloadRequest(): DownloadRequest {
        return request
    }

    fun isFinished(): Boolean {
        return finished
    }

    fun cancel() {
        cancelled = true
    }

}
