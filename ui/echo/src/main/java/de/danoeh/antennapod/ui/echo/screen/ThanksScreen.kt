package de.danoeh.antennapod.ui.echo.screen

import android.content.Context
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.ui.echo.EchoConfig
import de.danoeh.antennapod.ui.echo.R
import de.danoeh.antennapod.ui.echo.background.RotatingSquaresBackground
import de.danoeh.antennapod.ui.echo.databinding.SimpleEchoScreenBinding

import java.text.SimpleDateFormat
import java.util.Date

class ThanksScreen(context: Context, layoutInflater: LayoutInflater) : EchoScreen(context) {
    private val viewBinding: SimpleEchoScreenBinding

    init {
        viewBinding = SimpleEchoScreenBinding.inflate(layoutInflater)
        viewBinding.aboveLabel.setText("")
        viewBinding.largeLabel.setText(R.string.echo_thanks_large)

        viewBinding.smallLabel.setText(R.string.echo_thanks_now_favorite)
        viewBinding.backgroundImage.setImageDrawable(RotatingSquaresBackground(context))
    }

    override fun getView(): View {
        return viewBinding.getRoot()
    }

    override fun postInvalidate() {
        viewBinding.backgroundImage.postInvalidate()
    }

    override fun startLoading(statisticsResult: DBReader.StatisticsResult) {
        if (statisticsResult.oldestDate < EchoConfig.jan1()) {
            val skeleton = DateFormat.getBestDateTimePattern(getEchoLanguage(), "MMMM yyyy")
            val dateFormat = SimpleDateFormat(skeleton, getEchoLanguage())
            val dateFrom = dateFormat.format(Date(statisticsResult.oldestDate))
            viewBinding.belowLabel.setText(context.getString(R.string.echo_thanks_we_are_glad_old, dateFrom))
        } else {
            viewBinding.belowLabel.setText(R.string.echo_thanks_we_are_glad_new)
        }
    }
}
