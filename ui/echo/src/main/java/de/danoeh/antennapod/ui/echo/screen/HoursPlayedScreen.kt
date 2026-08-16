package de.danoeh.antennapod.ui.echo.screen

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.StatisticsItem
import de.danoeh.antennapod.ui.echo.R
import de.danoeh.antennapod.ui.echo.background.WaveformBackground
import de.danoeh.antennapod.ui.echo.databinding.SimpleEchoScreenBinding

class HoursPlayedScreen(context: Context, layoutInflater: LayoutInflater) : EchoScreen(context) {
    private val viewBinding: SimpleEchoScreenBinding

    init {
        viewBinding = SimpleEchoScreenBinding.inflate(layoutInflater)
        viewBinding.aboveLabel.setText(R.string.echo_hours_this_year)
        viewBinding.backgroundImage.setImageDrawable(WaveformBackground(context))
    }

    private fun display(totalTime: Long, playedPodcasts: Int) {
        viewBinding.largeLabel.setText(String.format(getEchoLanguage(), "%d", totalTime / 3600))
        viewBinding.belowLabel.setText(context.getResources()
                .getQuantityString(R.plurals.echo_hours_podcasts, playedPodcasts, playedPodcasts))
    }

    override fun getView(): View {
        return viewBinding.getRoot()
    }

    override fun postInvalidate() {
        viewBinding.backgroundImage.postInvalidate()
    }

    override fun startLoading(statisticsResult: DBReader.StatisticsResult) {
        var playedPodcasts = 0
        var totalTime = 0L
        for (item in statisticsResult.feedTime) {
            totalTime += item.timePlayed
            if (item.timePlayed > 0) {
                playedPodcasts++
            }
        }
        display(totalTime, playedPodcasts)
    }
}
