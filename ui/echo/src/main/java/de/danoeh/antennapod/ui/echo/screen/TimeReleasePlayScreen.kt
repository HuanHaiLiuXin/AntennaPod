package de.danoeh.antennapod.ui.echo.screen

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.ui.common.Converter
import de.danoeh.antennapod.ui.echo.EchoConfig
import de.danoeh.antennapod.ui.echo.R
import de.danoeh.antennapod.ui.echo.background.RotatingSquaresBackground
import de.danoeh.antennapod.ui.echo.databinding.SimpleEchoScreenBinding
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

class TimeReleasePlayScreen(context: Context, layoutInflater: LayoutInflater) : EchoScreen(context) {
    companion object {
        private const val TAG = "TimeReleasePlayScreen"
    }

    private val viewBinding: SimpleEchoScreenBinding
    private var disposable: Disposable? = null

    init {
        viewBinding = SimpleEchoScreenBinding.inflate(layoutInflater)
        viewBinding.aboveLabel.setText(R.string.echo_listened_after_title)
        viewBinding.backgroundImage.setImageDrawable(RotatingSquaresBackground(context))
    }

    private fun display(timeBetweenReleaseAndPlay: Long) {
        if (timeBetweenReleaseAndPlay <= 1000L * 3600 * 24 * 2.5) {
            viewBinding.largeLabel.setText(R.string.echo_listened_after_emoji_run)
            viewBinding.belowLabel.setText(R.string.echo_listened_after_comment_addict)
        } else {
            viewBinding.largeLabel.setText(R.string.echo_listened_after_emoji_yoga)
            viewBinding.belowLabel.setText(R.string.echo_listened_after_comment_easy)
        }
        viewBinding.smallLabel.setText(context.getString(R.string.echo_listened_after_time,
                Converter.getDurationStringLocalized(
                        getLocalizedResources(getEchoLanguage()), timeBetweenReleaseAndPlay, true)))
    }

    override fun getView(): View {
        return viewBinding.getRoot()
    }

    override fun postInvalidate() {
        viewBinding.backgroundImage.postInvalidate()
    }

    override fun startLoading(statisticsResult: DBReader.StatisticsResult) {
        super.startLoading(statisticsResult)
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Observable.fromCallable<Long> {
                    DBReader.getTimeBetweenReleaseAndPlayback(EchoConfig.jan1(), Long.MAX_VALUE) }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ display(it) }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }
}
