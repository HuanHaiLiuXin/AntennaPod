package de.danoeh.antennapod.net.download.service.feed.remote

import de.danoeh.antennapod.model.download.DownloadRequest

interface DownloaderFactory {
    fun create(request: DownloadRequest): Downloader?
}
