package de.danoeh.antennapod.net.download.service.feed.remote

import android.util.Log
import android.webkit.URLUtil
import de.danoeh.antennapod.model.download.DownloadRequest

class DefaultDownloaderFactory : DownloaderFactory {
    companion object {
        private const val TAG = "DefaultDwnldrFactory"
    }

    override fun create(request: DownloadRequest): Downloader? {
        if (!URLUtil.isHttpUrl(request.getSource()) && !URLUtil.isHttpsUrl(request.getSource())) {
            Log.e(TAG, "Could not find appropriate downloader for " + request.getSource())
            return null
        }
        return HttpDownloader(request)
    }
}
