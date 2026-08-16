package de.danoeh.antennapod.ui.echo.screen

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.common.Converter
import de.danoeh.antennapod.ui.echo.EchoConfig
import de.danoeh.antennapod.ui.echo.R
import de.danoeh.antennapod.ui.echo.background.StripesBackground
import de.danoeh.antennapod.ui.echo.databinding.SimpleEchoScreenBinding
import de.danoeh.antennapod.ui.episodes.PlaybackSpeedUtils
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

import java.util.Calendar

class QueueScreen(context: Context, layoutInflater: LayoutInflater) : EchoScreen(context) {
    companion object {
        private const val TAG = "QueueScreen"
    }

    private val viewBinding: SimpleEchoScreenBinding
    private var disposable: Disposable? = null

    init {
        viewBinding = SimpleEchoScreenBinding.inflate(layoutInflater)
        viewBinding.backgroundImage.setImageDrawable(StripesBackground(context))
    }

    private fun display(queueNumEpisodes: Int, queueSecondsLeft: Long) {
        viewBinding.largeLabel.setText(String.format(getEchoLanguage(), "%d", queueSecondsLeft / 3600))
        viewBinding.belowLabel.setText(context.getResources().getQuantityString(
                R.plurals.echo_queue_hours_waiting, queueNumEpisodes, queueNumEpisodes))

        val dec31 = Calendar.getInstance()
        dec31.set(Calendar.DAY_OF_MONTH, 31)
        dec31.set(Calendar.MONTH, Calendar.DECEMBER)
        val daysUntilNextYear = Math.max(1,
                dec31.get(Calendar.DAY_OF_YEAR) - Calendar.getInstance().get(Calendar.DAY_OF_YEAR) + 1)
        val secondsPerDay = queueSecondsLeft / daysUntilNextYear
        val timePerDay = Converter.getDurationStringLocalized(
                getLocalizedResources(getEchoLanguage()), secondsPerDay * 1000, true)
        val hoursPerDay = secondsPerDay / 3600.0
        val nextYear = EchoConfig.RELEASE_YEAR + 1
        if (hoursPerDay < 1.5) {
            viewBinding.aboveLabel.setText(R.string.echo_queue_title_clean)
            viewBinding.smallLabel.setText(
                    context.getString(R.string.echo_queue_hours_clean, timePerDay, nextYear))
        } else if (hoursPerDay <= 24) {
            viewBinding.aboveLabel.setText(R.string.echo_queue_title_many)
            viewBinding.smallLabel.setText(
                    context.getString(R.string.echo_queue_hours_normal, timePerDay, nextYear))
        } else {
            viewBinding.aboveLabel.setText(R.string.echo_queue_title_many)
            viewBinding.smallLabel.setText(context.getString(R.string.echo_queue_hours_much, timePerDay, nextYear))
        }
    }

    override fun getView(): View {
        return viewBinding.getRoot()
    }

    override fun postInvalidate() {
        viewBinding.backgroundImage.postInvalidate()
    }

    override fun startLoading(statisticsResult: DBReader.StatisticsResult) {
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Observable.fromCallable<ArrayList<FeedItem>> { DBReader.getQueue() }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ queue ->
                    var queueSecondsLeft = 0L
                    for (item in queue) {
                        var playbackSpeed = 1f
                        if (UserPreferences.timeRespectsSpeed()) {
                            playbackSpeed = PlaybackSpeedUtils.getCurrentPlaybackSpeed(item.getMedia())
                        }
                        if (item.getMedia() != null) {
                            val itemTimeLeft = item.getMedia()!!.getDuration() - item.getMedia()!!.getPosition()
                            queueSecondsLeft += (itemTimeLeft / playbackSpeed).toLong()
                        }
                    }
                    queueSecondsLeft /= 1000
                    display(queue.size, queueSecondsLeft)
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }
}
