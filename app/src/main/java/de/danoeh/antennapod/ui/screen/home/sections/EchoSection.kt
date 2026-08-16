package de.danoeh.antennapod.ui.screen.home.sections

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.databinding.HomeSectionEchoBinding
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.StatisticsItem
import de.danoeh.antennapod.ui.echo.EchoActivity
import de.danoeh.antennapod.ui.echo.EchoConfig
import de.danoeh.antennapod.ui.screen.home.HomeFragment
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

class EchoSection : Fragment() {
    private var viewBinding: HomeSectionEchoBinding? = null
    private var disposable: Disposable? = null

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = HomeSectionEchoBinding.inflate(inflater)
        viewBinding!!.titleLabel.setText(getString(R.string.antennapod_echo_year, EchoConfig.RELEASE_YEAR))
        viewBinding!!.echoButton.setOnClickListener { v ->
            startActivity(Intent(getContext(), EchoActivity::class.java)) }
        viewBinding!!.closeButton.setOnClickListener { v -> hideThisYear() }
        updateVisibility()
        return viewBinding!!.getRoot()
    }

    private fun updateVisibility() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Observable.fromCallable(
                {
                    val statisticsResult = DBReader.getStatistics(
                            false, EchoConfig.jan1(), Long.MAX_VALUE)
                    var totalTime = 0L
                    for (feedTime in statisticsResult.feedTime) {
                        totalTime += feedTime.timePlayed
                    }
                    totalTime
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ totalTime ->
                    val shouldShow = totalTime >= 3600 * 10
                    viewBinding!!.getRoot().setVisibility(if (shouldShow) View.VISIBLE else View.GONE)
                    if (!shouldShow) {
                        hideThisYear()
                    }
                }, { error -> error.printStackTrace() })
    }

    internal fun hideThisYear() {
        requireContext().getSharedPreferences(HomeFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putInt(HomeFragment.PREF_HIDE_ECHO, EchoConfig.RELEASE_YEAR).apply()
        if (isVisible()) {
            (getActivity() as MainActivity).loadFragment(HomeFragment.TAG, null)
        }
    }

    override fun onStop() {
        super.onStop()
        if (disposable != null) {
            disposable!!.dispose()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewBinding = null
    }
}
