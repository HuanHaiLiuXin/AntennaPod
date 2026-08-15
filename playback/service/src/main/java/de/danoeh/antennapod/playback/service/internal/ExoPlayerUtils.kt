package de.danoeh.antennapod.playback.service.internal

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import de.danoeh.antennapod.net.common.RedirectChecker
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import de.danoeh.antennapod.net.common.NetworkUtils
import de.danoeh.antennapod.net.common.UserAgentInterceptor
import de.danoeh.antennapod.playback.base.MediaItemAdapter
import de.danoeh.antennapod.playback.service.R
import de.danoeh.antennapod.storage.preferences.UserPreferences

import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class ExoPlayerUtils {
    companion object {
        @Volatile
        private var simpleCache: SimpleCache? = null

        @JvmStatic
        fun buildPlayer(context: Context): ExoPlayer {
            if (simpleCache == null) {
                simpleCache = SimpleCache(File(context.getCacheDir(), "streaming"),
                        LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024),
                        StandaloneDatabaseProvider(context))
            }
            return ExoPlayer.Builder(context)
                    .setLoadControl(DefaultLoadControl.Builder()
                            .setBufferDurationsMs(
                                    TimeUnit.HOURS.toMillis(1).toInt(),
                                    TimeUnit.HOURS.toMillis(3).toInt(),
                                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
                            .setBackBuffer(TimeUnit.MINUTES.toMillis(5).toInt(), true)
                            .build())
                    .setAudioAttributes(AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                            .build(), true)
                    .setMediaSourceFactory(ApMediaSourceFactory(context, simpleCache!!))
                    .setSeekParameters(SeekParameters.EXACT)
                    .setHandleAudioBecomingNoisy(UserPreferences.isPauseOnHeadsetDisconnect())
                    .build()
        }

        @JvmStatic
        fun releaseCache() {
            if (simpleCache != null) {
                simpleCache!!.release()
                simpleCache = null
            }
        }

        @JvmStatic
        fun translateErrorReason(error: PlaybackException, context: Context): String {
            if (NetworkUtils.wasDownloadBlocked(error)) {
                return context.getString(R.string.download_error_blocked)
            }

            var cause = error.cause
            if (cause is HttpDataSource.HttpDataSourceException) {
                if (cause.cause != null) {
                    cause = cause.cause
                }
            }
            if (cause != null && "Source error" == cause.message) {
                cause = cause.cause
            }
            if (cause != null && cause.message != null) {
                return cause.message!!
            } else if (error.message != null && cause != null) {
                return error.message!! + ": " + cause.javaClass.getSimpleName()
            } else {
                return "Unknown error"
            }
        }
    }

    @OptIn(UnstableApi::class)
    class ApMediaSourceFactory : MediaSource.Factory {
        private var loadErrorHandlingPolicy: LoadErrorHandlingPolicy? = null
        private var drmSessionManagerProvider: DrmSessionManagerProvider? = null
        private val extractorsFactory: DefaultExtractorsFactory
        private val defaultFactory: DefaultMediaSourceFactory
        private val context: Context
        private val simpleCache: SimpleCache
        private val redirectCache = ConcurrentHashMap<String, String>()

        constructor(context: Context, simpleCache: SimpleCache) {
            this.context = context
            this.simpleCache = simpleCache
            this.extractorsFactory = DefaultExtractorsFactory()
            this.extractorsFactory.setConstantBitrateSeekingEnabled(true)
            this.extractorsFactory.setMp3ExtractorFlags(Mp3Extractor.FLAG_DISABLE_ID3_METADATA)
            this.defaultFactory = DefaultMediaSourceFactory(context, extractorsFactory)
        }

        override fun setDrmSessionManagerProvider(
                drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
            this.drmSessionManagerProvider = drmSessionManagerProvider
            return this
        }

        override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory {
            this.loadErrorHandlingPolicy = policy
            return this
        }

        override fun createMediaSource(mediaItem: MediaItem): MediaSource {
            defaultFactory.setDataSourceFactory(buildDataSourceFactory(mediaItem))
            if (loadErrorHandlingPolicy != null) {
                defaultFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy!!)
            }
            if (drmSessionManagerProvider != null) {
                defaultFactory.setDrmSessionManagerProvider(drmSessionManagerProvider!!)
            }
            return defaultFactory.createMediaSource(mediaItem)
        }

        private fun buildDataSourceFactory(mediaItem: MediaItem): DataSource.Factory {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            httpDataSourceFactory.setUserAgent(UserAgentInterceptor.USER_AGENT)
            httpDataSourceFactory.setAllowCrossProtocolRedirects(true)
            httpDataSourceFactory.setKeepPostFor302Redirects(true)
            val authHeader = if (mediaItem.requestMetadata.extras != null)
                mediaItem.requestMetadata.extras!!.getString(
                        MediaItemAdapter.KEY_AUTHORIZATION_HEADER)
            else
                null
            if (authHeader != null) {
                httpDataSourceFactory.setDefaultRequestProperties(
                        Collections.singletonMap("Authorization", authHeader))
            }
            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
            val resolvingFactory = ResolvingDataSource.Factory(dataSourceFactory) { dataSpec ->
                val originalUrl = dataSpec.uri.toString()
                if (!originalUrl.startsWith("http")) {
                    return@Factory dataSpec
                }
                var resolvedUrl = redirectCache[originalUrl]
                if (resolvedUrl == null) {
                    resolvedUrl = RedirectChecker.getFinalUrl(originalUrl)
                    redirectCache.putIfAbsent(originalUrl, resolvedUrl)
                }
                if (resolvedUrl == originalUrl) {
                    return@Factory dataSpec
                }
                dataSpec.withUri(Uri.parse(resolvedUrl))
            }
            val uri = if (mediaItem.localConfiguration != null)
                mediaItem.localConfiguration!!.uri.toString() else ""
            if (uri.startsWith("http")) {
                return CacheDataSource.Factory()
                        .setCache(simpleCache)
                        .setUpstreamDataSourceFactory(resolvingFactory)
            }
            return resolvingFactory
        }

        override fun getSupportedTypes(): IntArray {
            return defaultFactory.getSupportedTypes()
        }
    }
}
