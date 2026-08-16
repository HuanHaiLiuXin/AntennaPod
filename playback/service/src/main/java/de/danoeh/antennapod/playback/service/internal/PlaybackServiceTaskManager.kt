package de.danoeh.antennapod.playback.service.internal

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

import de.danoeh.antennapod.ui.chapters.ChapterUtils
import de.danoeh.antennapod.ui.widget.WidgetUpdater
import io.reactivex.rxjava3.disposables.Disposable

import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

import de.danoeh.antennapod.model.playback.Playable
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers


/**
 * Manages the background tasks of PlaybackSerivce, i.e.
 * the sleep timer, the position saver, the widget updater and
 * the queue loader.
 * <p/>
 * The PlaybackServiceTaskManager(PSTM) uses a callback object (PSTMCallback)
 * to notify the PlaybackService about updates from the running tasks.
 */
class PlaybackServiceTaskManager {
    companion object {
        private const val TAG = "PlaybackServiceTaskMgr"

        /**
         * Update interval of position saver in milliseconds.
         */
        const val POSITION_SAVER_WAITING_INTERVAL = 5000
        /**
         * Notification interval of widget updater in milliseconds.
         */
        const val WIDGET_UPDATER_NOTIFICATION_INTERVAL = 1000

        private const val SCHED_EX_POOL_SIZE = 2
    }

    private val schedExecutor: ScheduledThreadPoolExecutor

    private var positionSaverFuture: ScheduledFuture<*>? = null
    private var widgetUpdaterFuture: ScheduledFuture<*>? = null
    @Volatile
    private var chapterLoaderFuture: Disposable? = null

    private val context: Context
    private val callback: PSTMCallback

    /**
     * Sets up a new PSTM. This method will also start the queue loader task.
     *
     * @param context
     * @param callback A PSTMCallback object for notifying the user about updates. Must not be null.
     */
    constructor(context: Context,
                callback: PSTMCallback) {
        this.context = context
        this.callback = callback
        schedExecutor = ScheduledThreadPoolExecutor(SCHED_EX_POOL_SIZE) { r ->
            val t = Thread(r)
            t.setPriority(Thread.MIN_PRIORITY)
            t
        }
    }

    /**
     * Starts the position saver task. If the position saver is already active, nothing will happen.
     */
    @Synchronized
    fun startPositionSaver() {
        if (!isPositionSaverActive()) {
            var positionSaver = Runnable { callback.positionSaverTick() }
            positionSaver = useMainThreadIfNecessary(positionSaver)
            positionSaverFuture = schedExecutor.scheduleWithFixedDelay(positionSaver,
                    POSITION_SAVER_WAITING_INTERVAL.toLong(),
                    POSITION_SAVER_WAITING_INTERVAL.toLong(), TimeUnit.MILLISECONDS)

            Log.d(TAG, "Started PositionSaver")
        } else {
            Log.d(TAG, "Call to startPositionSaver was ignored.")
        }
    }

    /**
     * Returns true if the position saver is currently running.
     */
    @Synchronized
    fun isPositionSaverActive(): Boolean {
        return positionSaverFuture != null && !positionSaverFuture!!.isCancelled() && !positionSaverFuture!!.isDone()
    }

    /**
     * Cancels the position saver. If the position saver is not running, nothing will happen.
     */
    @Synchronized
    fun cancelPositionSaver() {
        if (isPositionSaverActive()) {
            positionSaverFuture!!.cancel(false)
            Log.d(TAG, "Cancelled PositionSaver")
        }
    }

    /**
     * Starts the widget updater task. If the widget updater is already active, nothing will happen.
     */
    @Synchronized
    fun startWidgetUpdater() {
        if (!isWidgetUpdaterActive() && !schedExecutor.isShutdown()) {
            var widgetUpdater = Runnable { requestWidgetUpdate() }
            widgetUpdater = useMainThreadIfNecessary(widgetUpdater)
            widgetUpdaterFuture = schedExecutor.scheduleWithFixedDelay(widgetUpdater,
                    WIDGET_UPDATER_NOTIFICATION_INTERVAL.toLong(),
                    WIDGET_UPDATER_NOTIFICATION_INTERVAL.toLong(), TimeUnit.MILLISECONDS)
            Log.d(TAG, "Started WidgetUpdater")
        } else {
            Log.d(TAG, "Call to startWidgetUpdater was ignored.")
        }
    }

    /**
     * Retrieves information about the widget state in the calling thread and then displays it in a background thread.
     */
    @Synchronized
    fun requestWidgetUpdate() {
        val state = callback.requestWidgetState()
        if (!schedExecutor.isShutdown()) {
            schedExecutor.execute { WidgetUpdater.updateWidget(context, state) }
        } else {
            Log.d(TAG, "Call to requestWidgetUpdate was ignored.")
        }
    }

    /**
     * Returns true if the widget updater is currently running.
     */
    @Synchronized
    fun isWidgetUpdaterActive(): Boolean {
        return widgetUpdaterFuture != null && !widgetUpdaterFuture!!.isCancelled() && !widgetUpdaterFuture!!.isDone()
    }

    /**
     * Cancels the widget updater. If the widget updater is not running, nothing will happen.
     */
    @Synchronized
    fun cancelWidgetUpdater() {
        if (isWidgetUpdaterActive()) {
            widgetUpdaterFuture!!.cancel(false)
            Log.d(TAG, "Cancelled WidgetUpdater")
        }
    }

    /**
     * Starts a new thread that loads the chapter marks from a playable object. If another chapter loader is already active,
     * it will be cancelled first.
     * On completion, the callback's onChapterLoaded method will be called.
     */
    @Synchronized
    fun startChapterLoader(media: Playable) {
        if (chapterLoaderFuture != null) {
            chapterLoaderFuture!!.dispose()
            chapterLoaderFuture = null
        }

        if (media.getChapters() == null) {
            chapterLoaderFuture = Completable.create { emitter ->
                ChapterUtils.loadChapters(media, context, false)
                emitter.onComplete()
            }
                    .subscribeOn(Schedulers.computation())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ callback.onChapterLoaded(media) },
                            { throwable -> Log.d(TAG, "Error loading chapters: " + Log.getStackTraceString(throwable)) })
        }
    }


    /**
     * Cancels all tasks. The PSTM will be in the initial state after execution of this method.
     */
    @Synchronized
    fun cancelAllTasks() {
        cancelPositionSaver()
        cancelWidgetUpdater()

        if (chapterLoaderFuture != null) {
            chapterLoaderFuture!!.dispose()
            chapterLoaderFuture = null
        }
    }

    /**
     * Cancels all tasks and shuts down the internal executor service of the PSTM. The object should not be used after
     * execution of this method.
     */
    fun shutdown() {
        cancelAllTasks()
        schedExecutor.shutdownNow()
    }

    private fun useMainThreadIfNecessary(runnable: Runnable): Runnable {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Called in main thread => ExoPlayer is used
            // Run on ui thread even if called from schedExecutor
            val handler = Handler(Looper.getMainLooper())
            return Runnable { handler.post(runnable) }
        } else {
            return runnable
        }
    }

    interface PSTMCallback {
        fun positionSaverTick()

        fun requestWidgetState(): WidgetUpdater.WidgetState

        fun onChapterLoaded(media: Playable)
    }
}
