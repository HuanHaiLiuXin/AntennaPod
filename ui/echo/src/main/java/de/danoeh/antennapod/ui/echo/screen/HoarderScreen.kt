package de.danoeh.antennapod.ui.echo.screen

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.StatisticsItem
import de.danoeh.antennapod.ui.echo.R
import de.danoeh.antennapod.ui.echo.background.WavesBackground
import de.danoeh.antennapod.ui.echo.databinding.SimpleEchoScreenBinding

import java.util.ArrayList

class HoarderScreen(context: Context, layoutInflater: LayoutInflater) : EchoScreen(context) {
    private val viewBinding: SimpleEchoScreenBinding

    init {
        viewBinding = SimpleEchoScreenBinding.inflate(layoutInflater)
        viewBinding.aboveLabel.setText(R.string.echo_hoarder_title)
        viewBinding.backgroundImage.setImageDrawable(WavesBackground(context))
    }

    private fun display(playedActivePodcasts: Int, totalActivePodcasts: Int, randomUnplayedActivePodcast: String) {
        val percentagePlayed = (100.0 * playedActivePodcasts / totalActivePodcasts).toInt()
        if (percentagePlayed < 25) {
            viewBinding.largeLabel.setText(R.string.echo_hoarder_emoji_cabinet)
            viewBinding.belowLabel.setText(R.string.echo_hoarder_subtitle_hoarder)
            viewBinding.smallLabel.setText(context.getString(R.string.echo_hoarder_comment_hoarder,
                    percentagePlayed, totalActivePodcasts))
        } else if (percentagePlayed < 75) {
            viewBinding.largeLabel.setText(R.string.echo_hoarder_emoji_check)
            viewBinding.belowLabel.setText(R.string.echo_hoarder_subtitle_medium)
            viewBinding.smallLabel.setText(context.getString(R.string.echo_hoarder_comment_medium,
                    percentagePlayed, totalActivePodcasts, randomUnplayedActivePodcast))
        } else {
            viewBinding.largeLabel.setText(R.string.echo_hoarder_emoji_clean)
            viewBinding.belowLabel.setText(R.string.echo_hoarder_subtitle_clean)
            viewBinding.smallLabel.setText(context.getString(R.string.echo_hoarder_comment_clean,
                    percentagePlayed, totalActivePodcasts))
        }
    }

    override fun getView(): View {
        return viewBinding.getRoot()
    }

    override fun postInvalidate() {
        viewBinding.backgroundImage.postInvalidate()
    }

    override fun startLoading(statisticsResult: DBReader.StatisticsResult) {
        var totalActivePodcasts = 0
        var playedActivePodcasts = 0
        var randomUnplayedActivePodcast = ""
        val unplayedActive = ArrayList<String>()
        for (item in statisticsResult.feedTime) {
            if (item.feed.getPreferences()!!.getKeepUpdated()) {
                totalActivePodcasts++
                if (item.timePlayed > 0) {
                    playedActivePodcasts++
                } else if (item.hasRecentUnplayed) {
                    var title = item.feed.getTitle()
                    if (TextUtils.isEmpty(title)) {
                        title = item.feed.getFeedIdentifier()
                    }
                    unplayedActive.add(title!!)
                }
            }
        }
        if (!unplayedActive.isEmpty()) {
            randomUnplayedActivePodcast = unplayedActive.get((Math.random() * unplayedActive.size).toInt())
        }
        display(playedActivePodcasts, totalActivePodcasts, randomUnplayedActivePodcast)
    }
}
