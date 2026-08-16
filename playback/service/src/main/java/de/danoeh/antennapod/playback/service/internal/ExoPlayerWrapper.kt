package de.danoeh.antennapod.playback.service.internal

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.view.SurfaceHolder

import android.annotation.SuppressLint
import androidx.core.util.Consumer

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.AudioAttributes
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.trackselection.MappingTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelectionArray

import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.ui.DefaultTrackNameProvider
import androidx.media3.ui.TrackNameProvider
import de.danoeh.antennapod.net.common.UserAgentInterceptor
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting
import de.danoeh.antennapod.playback.service.R
import de.danoeh.antennapod.net.common.HttpCredentialEncoder
import de.danoeh.antennapod.net.common.NetworkUtils
import de.danoeh.antennapod.model.playback.Playable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable

import java.io.File
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@SuppressLint("UnsafeOptInUsageError")
class ExoPlayerWrapper {
    companion object {
        const val BUFFERING_STARTED = -1
        const val BUFFERING_ENDED = -2
        private const val TAG = "ExoPlayerWrapper"
    }

    private val context: Context
    private val bufferingUpdateDisposable: Disposable
    private lateinit var exoPlayer: ExoPlayer
    private var mediaSource: MediaSource? = null
    private var audioSeekCompleteListener: Runnable? = null
    private var audioCompletionListener: Runnable? = null
    private var audioErrorListener: Consumer<String?>? = null
    private var bufferingUpdateListener: Consumer<Int>? = null
    private lateinit var playbackParameters: PlaybackParameters
    private lateinit var trackSelector: DefaultTrackSelector
    private var simpleCache: SimpleCache? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    constructor(context: Context) {
        this.context = context
        createPlayer()
        playbackParameters = exoPlayer.getPlaybackParameters()
        bufferingUpdateDisposable = Observable.interval(2, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { tickNumber ->
                    if (bufferingUpdateListener != null) {
                        bufferingUpdateListener!!.accept(exoPlayer.getBufferedPercentage())
                    }
                }
    }

    private fun createPlayer() {
        val loadControl = DefaultLoadControl.Builder()
        loadControl.setBufferDurationsMs(TimeUnit.HOURS.toMillis(1).toInt(), TimeUnit.HOURS.toMillis(3).toInt(),
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
        loadControl.setBackBuffer(TimeUnit.MINUTES.toMillis(5).toInt(), true)
        trackSelector = DefaultTrackSelector(context)
        exoPlayer = ExoPlayer.Builder(context, DefaultRenderersFactory(context))
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl.build())
                .build()
        exoPlayer.setSeekParameters(SeekParameters.EXACT)
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (audioCompletionListener != null && playbackState == Player.STATE_ENDED) {
                    audioCompletionListener!!.run()
                } else if (bufferingUpdateListener != null && playbackState == Player.STATE_BUFFERING) {
                    bufferingUpdateListener!!.accept(BUFFERING_STARTED)
                } else if (bufferingUpdateListener != null) {
                    bufferingUpdateListener!!.accept(BUFFERING_ENDED)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (audioErrorListener != null) {
                    if (NetworkUtils.wasDownloadBlocked(error)) {
                        audioErrorListener!!.accept(context.getString(R.string.download_error_blocked))
                    } else {
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
                            audioErrorListener!!.accept(cause.message)
                        } else if (error.message != null && cause != null) {
                            audioErrorListener!!.accept(error.message + ": " + cause.javaClass.getSimpleName())
                        } else {
                            audioErrorListener!!.accept(null)
                        }
                    }
                }
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,
                                                 newPosition: Player.PositionInfo,
                                                 reason: Int) {
                if (audioSeekCompleteListener != null && reason == Player.DISCONTINUITY_REASON_SEEK) {
                    audioSeekCompleteListener!!.run()
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                initLoudnessEnhancer(audioSessionId)
            }
        })
        simpleCache = SimpleCache(File(context.getCacheDir(), "streaming"),
                LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024), StandaloneDatabaseProvider(context))
        initLoudnessEnhancer(exoPlayer.getAudioSessionId())
    }

    fun getCurrentPosition(): Int {
        return exoPlayer.getCurrentPosition().toInt()
    }

    fun getCurrentSpeedMultiplier(): Float {
        return playbackParameters.speed
    }

    fun getCurrentSkipSilence(): Boolean {
        return exoPlayer.getSkipSilenceEnabled()
    }

    fun getDuration(): Int {
        if (exoPlayer.getDuration() == C.TIME_UNSET) {
            return Playable.INVALID_TIME
        }
        return exoPlayer.getDuration().toInt()
    }

    fun isPlaying(): Boolean {
        return exoPlayer.getPlayWhenReady()
    }

    fun pause() {
        exoPlayer.pause()
    }

    @Throws(IllegalStateException::class)
    fun prepare() {
        exoPlayer.setMediaSource(mediaSource!!, false)
        exoPlayer.prepare()
    }

    fun release() {
        bufferingUpdateDisposable.dispose()
        if (exoPlayer != null) {
            exoPlayer.release()
        }
        if (simpleCache != null) {
            simpleCache!!.release()
            simpleCache = null
        }
        audioSeekCompleteListener = null
        audioCompletionListener = null
        audioErrorListener = null
        bufferingUpdateListener = null
    }

    fun reset() {
        exoPlayer.release()
        if (simpleCache != null) {
            simpleCache!!.release()
            simpleCache = null
        }
        createPlayer()
    }

    @Throws(IllegalStateException::class)
    fun seekTo(i: Int) {
        exoPlayer.seekTo(i.toLong())
        if (audioSeekCompleteListener != null) {
            audioSeekCompleteListener!!.run()
        }
    }

    fun setAudioStreamType(i: Int) {
        val a = exoPlayer.getAudioAttributes()
        val b = AudioAttributes.Builder()
        b.setContentType(i)
        b.setFlags(a.flags)
        b.setUsage(a.usage)
        exoPlayer.setAudioAttributes(b.build(), false)
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun setDataSource(s: String, user: String?, password: String?) {
        Log.d(TAG, "setDataSource: " + s)
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        httpDataSourceFactory.setUserAgent(UserAgentInterceptor.USER_AGENT)
        httpDataSourceFactory.setAllowCrossProtocolRedirects(true)
        httpDataSourceFactory.setKeepPostFor302Redirects(true)

        if (!TextUtils.isEmpty(user) && !TextUtils.isEmpty(password)) {
            val requestProperties = HashMap<String, String>()
            requestProperties.put("Authorization", HttpCredentialEncoder.encode(user, password, "ISO-8859-1"))
            httpDataSourceFactory.setDefaultRequestProperties(requestProperties)
        }
        var dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        if (s.startsWith("http")) {
            dataSourceFactory = CacheDataSource.Factory()
                    .setCache(simpleCache!!)
                    .setUpstreamDataSourceFactory(httpDataSourceFactory)
        }
        val extractorsFactory = DefaultExtractorsFactory()
        extractorsFactory.setConstantBitrateSeekingEnabled(true)
        extractorsFactory.setMp3ExtractorFlags(Mp3Extractor.FLAG_DISABLE_ID3_METADATA)
        val f = ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
        val mediaItem = MediaItem.fromUri(Uri.parse(s))
        mediaSource = f.createMediaSource(mediaItem)
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun setDataSource(s: String) {
        setDataSource(s, null, null)
    }

    fun setDisplay(sh: SurfaceHolder?) {
        exoPlayer.setVideoSurfaceHolder(sh)
    }

    fun setPlaybackParams(speed: Float, skipSilence: Boolean) {
        playbackParameters = PlaybackParameters(speed, playbackParameters.pitch)
        exoPlayer.setSkipSilenceEnabled(skipSilence)
        exoPlayer.setPlaybackParameters(playbackParameters)
    }

    fun setVolume(v: Float, v1: Float) {
        if (v > 1) {
            exoPlayer.setVolume(1f)
            try {
                if (loudnessEnhancer != null) {
                    loudnessEnhancer!!.setEnabled(true)
                    loudnessEnhancer!!.setTargetGain((1000 * (v - 1)).toInt())
                }
            } catch (e: Exception) {
                Log.d(TAG, e.toString())
            }
        } else {
            exoPlayer.setVolume(v)
            try {
                if (loudnessEnhancer != null) {
                    loudnessEnhancer!!.setEnabled(false)
                }
            } catch (e: Exception) {
                Log.d(TAG, e.toString())
            }
        }
    }

    fun start() {
        exoPlayer.play()
        // Can't set params when paused - so always set it on start in case they changed
        exoPlayer.setPlaybackParameters(playbackParameters)
    }

    fun stop() {
        exoPlayer.stop()
    }

    fun getAudioTracks(): List<String> {
        val trackNames = ArrayList<String>()
        val trackNameProvider: TrackNameProvider = DefaultTrackNameProvider(context.getResources())
        for (format in getFormats()) {
            trackNames.add(trackNameProvider.getTrackName(format))
        }
        return trackNames
    }

    private fun getFormats(): List<Format> {
        val formats = ArrayList<Format>()
        val trackInfo = trackSelector.getCurrentMappedTrackInfo()
        if (trackInfo == null) {
            return Collections.emptyList()
        }
        val trackGroups = trackInfo.getTrackGroups(getAudioRendererIndex())
        for (i in 0 until trackGroups.length) {
            formats.add(trackGroups.get(i).getFormat(0))
        }
        return formats
    }

    fun setAudioTrack(track: Int) {
        val trackInfo = trackSelector.getCurrentMappedTrackInfo()
        if (trackInfo == null) {
            return
        }
        val trackGroups = trackInfo.getTrackGroups(getAudioRendererIndex())
        val override = DefaultTrackSelector.SelectionOverride(track, 0)
        val params = trackSelector.buildUponParameters()
                .setSelectionOverride(getAudioRendererIndex(), trackGroups, override).build()
        trackSelector.setParameters(params)
    }

    private fun getAudioRendererIndex(): Int {
        for (i in 0 until exoPlayer.getRendererCount()) {
            if (exoPlayer.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
                return i
            }
        }
        return -1
    }

    fun getSelectedAudioTrack(): Int {
        val trackSelections = exoPlayer.getCurrentTrackSelections()
        val availableFormats = getFormats()
        for (i in 0 until trackSelections.length) {
            val track = trackSelections.get(i) as ExoTrackSelection?
            if (track == null) {
                continue
            }
            if (availableFormats.contains(track.getSelectedFormat())) {
                return availableFormats.indexOf(track.getSelectedFormat())
            }
        }
        return -1
    }

    internal fun setOnCompletionListener(audioCompletionListener: Runnable) {
        this.audioCompletionListener = audioCompletionListener
    }

    internal fun setOnSeekCompleteListener(audioSeekCompleteListener: Runnable) {
        this.audioSeekCompleteListener = audioSeekCompleteListener
    }

    internal fun setOnErrorListener(audioErrorListener: Consumer<String?>) {
        this.audioErrorListener = audioErrorListener
    }

    internal fun getVideoWidth(): Int {
        if (exoPlayer.getVideoFormat() == null) {
            return 0
        }
        return exoPlayer.getVideoFormat()!!.width
    }

    internal fun getVideoHeight(): Int {
        if (exoPlayer.getVideoFormat() == null) {
            return 0
        }
        return exoPlayer.getVideoFormat()!!.height
    }

    internal fun setOnBufferingUpdateListener(bufferingUpdateListener: Consumer<Int>) {
        this.bufferingUpdateListener = bufferingUpdateListener
    }

    private fun initLoudnessEnhancer(audioStreamId: Int) {
        if (!VolumeAdaptionSetting.isBoostSupported()) {
            return
        }

        val oldEnhancer = this.loudnessEnhancer
        try {
            val newEnhancer = LoudnessEnhancer(audioStreamId)
            if (oldEnhancer != null) {
                newEnhancer.setEnabled(oldEnhancer.getEnabled())
                if (oldEnhancer.getEnabled()) {
                    newEnhancer.setTargetGain(oldEnhancer.getTargetGain().toInt())
                }
                oldEnhancer.release()
            }
            this.loudnessEnhancer = newEnhancer
        } catch (e: Exception) {
            Log.d(TAG, e.toString())
            this.loudnessEnhancer = null
        }
    }
}
