package de.danoeh.antennapod.ui.statistics

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.util.Log

import com.google.android.material.appbar.MaterialToolbar
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import de.danoeh.antennapod.ui.common.ConfirmationDialog
import de.danoeh.antennapod.ui.common.NavigationToolbarActivity
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.event.StatisticsEvent
import de.danoeh.antennapod.ui.common.PagedToolbarFragment
import de.danoeh.antennapod.ui.echo.EchoActivity
import de.danoeh.antennapod.ui.echo.EchoConfig
import de.danoeh.antennapod.ui.statistics.downloads.DownloadStatisticsFragment
import de.danoeh.antennapod.ui.statistics.subscriptions.SubscriptionStatisticsFragment
import de.danoeh.antennapod.ui.statistics.years.YearsStatisticsFragment
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus

/**
 * Displays the 'statistics' screen
 */
class StatisticsFragment : PagedToolbarFragment() {
    companion object {
        const val TAG = "StatisticsFragment"
        const val PREF_NAME = "StatisticsActivityPrefs"
        const val PREF_INCLUDE_MARKED_PLAYED = "countAll"
        const val PREF_FILTER_FROM = "filterFrom"
        const val PREF_FILTER_TO = "filterTo"
        private const val KEY_UP_ARROW = "up_arrow"

        private const val POS_SUBSCRIPTIONS = 0
        private const val POS_YEARS = 1
        private const val POS_SPACE_TAKEN = 2
        private const val TOTAL_COUNT = 3
    }

    private var tabLayout: TabLayout? = null
    private var viewPager: ViewPager2? = null
    private var toolbar: MaterialToolbar? = null
    private var displayUpArrow = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        setHasOptionsMenu(true)

        val rootView = inflater.inflate(R.layout.pager_fragment, container, false)
        viewPager = rootView.findViewById(R.id.viewpager)
        toolbar = rootView.findViewById(R.id.toolbar)
        toolbar!!.setTitle(getString(R.string.statistics_label))
        toolbar!!.inflateMenu(R.menu.statistics)
        if (BuildConfig.DEBUG || EchoConfig.isCurrentlyVisible()) {
            toolbar!!.getMenu().findItem(R.id.show_echo)!!.setVisible(true)
        }
        displayUpArrow = getParentFragmentManager().getBackStackEntryCount() != 0
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
        }
        if (getActivity() is NavigationToolbarActivity) {
            (getActivity() as NavigationToolbarActivity).setupToolbarToggle(toolbar!!, displayUpArrow)
        } else {
            toolbar!!.setNavigationOnClickListener { getParentFragmentManager().popBackStack() }
        }
        viewPager!!.setAdapter(StatisticsPagerAdapter(this))
        // Give the TabLayout the ViewPager
        tabLayout = rootView.findViewById(R.id.sliding_tabs)
        super.setupPagedToolbar(toolbar!!, viewPager!!)
        TabLayoutMediator(tabLayout!!, viewPager!!) { tab, position ->
            when (position) {
                POS_SUBSCRIPTIONS -> tab.setText(R.string.subscriptions_label)
                POS_YEARS -> tab.setText(R.string.years_statistics_label)
                POS_SPACE_TAKEN -> tab.setText(R.string.downloads_label)
                else -> Unit
            }
        }.attach()
        return rootView
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.getItemId() == R.id.statistics_reset) {
            confirmResetStatistics()
            return true
        } else if (item.getItemId() == R.id.show_echo) {
            startActivity(Intent(getContext(), EchoActivity::class.java))
        }
        return super.onOptionsItemSelected(item)
    }

    private fun confirmResetStatistics() {
        val conDialog = object : ConfirmationDialog(
                getActivity()!!,
                R.string.statistics_reset_data,
                R.string.statistics_reset_data_msg) {

            override fun onConfirmButtonPressed(dialog: DialogInterface) {
                dialog.dismiss()
                doResetStatistics()
            }
        }
        conDialog.createNewDialog().show()
    }

    private fun doResetStatistics() {
        getContext()!!.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(PREF_INCLUDE_MARKED_PLAYED, false)
                .putLong(PREF_FILTER_FROM, 0)
                .putLong(PREF_FILTER_TO, Long.MAX_VALUE)
                .apply()

        val disposable = Completable.fromFuture(DBWriter.resetStatistics()!!)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ EventBus.getDefault().post(StatisticsEvent()) },
                        { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    class StatisticsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                POS_SUBSCRIPTIONS -> SubscriptionStatisticsFragment()
                POS_YEARS -> YearsStatisticsFragment()
                else -> DownloadStatisticsFragment()
            }
        }

        override fun getItemCount(): Int {
            return TOTAL_COUNT
        }
    }
}
