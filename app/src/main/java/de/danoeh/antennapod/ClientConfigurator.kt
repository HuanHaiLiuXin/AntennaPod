package de.danoeh.antennapod

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import de.danoeh.antennapod.net.download.service.episode.autodownload.AutoDownloadManagerImpl
import de.danoeh.antennapod.net.download.service.feed.FeedUpdateManagerImpl
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager
import de.danoeh.antennapod.net.sync.service.SynchronizationQueueImpl
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences
import de.danoeh.antennapod.storage.preferences.UsageStatistics
import de.danoeh.antennapod.net.common.UserAgentInterceptor
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.net.common.AntennapodHttpClient
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.net.download.service.feed.DownloadServiceInterfaceImpl
import de.danoeh.antennapod.net.common.NetworkUtils
import de.danoeh.antennapod.net.ssl.SslProviderInstaller
import de.danoeh.antennapod.storage.database.PodDBAdapter

import de.danoeh.antennapod.ui.notifications.NotificationUtils
import java.io.File

class ClientConfigurator {
    companion object {
        private var initialized = false

        @Synchronized
        @JvmStatic
        fun initialize(context: Context) {
            if (initialized) {
                return
            }
            try {
                val packageInfo: PackageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0)
                UserAgentInterceptor.USER_AGENT = "AntennaPod/" + packageInfo.versionName
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
            PodDBAdapter.init(context)
            UserPreferences.init(context)
            SynchronizationCredentials.init(context)
            SynchronizationSettings.init(context)
            UsageStatistics.init(context)
            PlaybackPreferences.init(context)
            SslProviderInstaller.install(context)
            NetworkUtils.init(context)
            DownloadServiceInterface.setImpl(DownloadServiceInterfaceImpl())
            FeedUpdateManager.setInstance(FeedUpdateManagerImpl())
            AutoDownloadManager.setInstance(AutoDownloadManagerImpl())
            SynchronizationQueue.setInstance(SynchronizationQueueImpl(context))
            AntennapodHttpClient.setCacheDirectory(File(context.getCacheDir(), "okhttp"))
            AntennapodHttpClient.setProxyConfig(UserPreferences.getProxyConfig())
            SleepTimerPreferences.init(context)
            NotificationUtils.createChannels(context)
            initialized = true
        }
    }
}
