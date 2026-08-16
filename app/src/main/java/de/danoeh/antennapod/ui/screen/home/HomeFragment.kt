package de.danoeh.antennapod.ui.screen.home

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.databinding.HomeFragmentBinding
import de.danoeh.antennapod.event.FeedListUpdateEvent
import de.danoeh.antennapod.event.FeedUpdateRunningEvent
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.echo.EchoConfig
import de.danoeh.antennapod.ui.screen.SearchFragment
import de.danoeh.antennapod.ui.screen.home.sections.DownloadsSection
import de.danoeh.antennapod.ui.screen.home.sections.EchoSection
import de.danoeh.antennapod.ui.screen.home.sections.EpisodesSurpriseSection
import de.danoeh.antennapod.ui.screen.home.sections.InboxSection
import de.danoeh.antennapod.ui.screen.home.sections.QueueSection
import de.danoeh.antennapod.ui.screen.home.sections.SubscriptionsSection
import de.danoeh.antennapod.ui.screen.home.settingsdialog.HomePreferences
import de.danoeh.antennapod.ui.screen.home.settingsdialog.HomeSectionsSettingsDialog
import de.danoeh.antennapod.ui.common.LiftOnScrollListener
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Shows unread or recently published episodes
 */
class HomeFragment : Fragment(), Toolbar.OnMenuItemClickListener {

    companion object {
        const val TAG = "HomeFragment"
        const val PREF_NAME = "PrefHomeFragment"
        const val PREF_HIDE_ECHO = "HideEcho"

        private const val KEY_UP_ARROW = "up_arrow"
    }

    private var displayUpArrow = false
    private var viewBinding: HomeFragmentBinding? = null
    private var disposable: Disposable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        viewBinding = HomeFragmentBinding.inflate(inflater)
        viewBinding!!.toolbar.inflateMenu(R.menu.home)
        viewBinding!!.toolbar.setOnMenuItemClickListener(this)
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
        }
        viewBinding!!.homeScrollView.setOnScrollChangeListener(LiftOnScrollListener(viewBinding!!.appbar))
        (requireActivity() as MainActivity).setupToolbarToggle(viewBinding!!.toolbar, displayUpArrow)
        populateSectionList()
        updateWelcomeScreenVisibility()

        viewBinding!!.swipeRefresh.setDistanceToTriggerSync(getResources().getInteger(R.integer.swipe_refresh_distance))
        viewBinding!!.swipeRefresh.setOnRefreshListener {
            FeedUpdateManager.getInstance()!!.runOnceOrAsk(requireContext())
        }

        return viewBinding!!.getRoot()
    }

    private fun populateSectionList() {
        viewBinding!!.homeContainer.removeAllViews()

        val prefs: SharedPreferences = requireContext().getSharedPreferences(HomeFragment.PREF_NAME, Context.MODE_PRIVATE)
        if (EchoConfig.isCurrentlyVisible() && prefs.getInt(PREF_HIDE_ECHO, 0) != EchoConfig.RELEASE_YEAR) {
            addSection(EchoSection(), R.id.home_section_echo)
        }

        val sectionTags = HomePreferences.getSortedSectionTags(requireContext())
        for (sectionTag in sectionTags) {
            addSection(getSection(sectionTag), getSectionContainerId(sectionTag))
        }
    }

    private fun addSection(section: Fragment?, id: Int) {
        if (section == null) { // Can happen when stored settings reference a section that no longer exists
            return
        }
        val containerView = FragmentContainerView(requireContext())
        containerView.setId(id)
        viewBinding!!.homeContainer.addView(containerView)
        getChildFragmentManager().beginTransaction().replace(containerView.getId(), section).commit()
    }

    private fun getSectionContainerId(sectionTag: String): Int {
        return when (sectionTag) {
            QueueSection.TAG -> R.id.home_section_queue
            InboxSection.TAG -> R.id.home_section_inbox
            EpisodesSurpriseSection.TAG -> R.id.home_section_surprise
            SubscriptionsSection.TAG -> R.id.home_section_subscriptions
            DownloadsSection.TAG -> R.id.home_section_downloads
            else -> throw IllegalArgumentException("Unknown section tag: " + sectionTag)
        }
    }

    private fun getSection(tag: String): Fragment? {
        return when (tag) {
            QueueSection.TAG -> QueueSection()
            InboxSection.TAG -> InboxSection()
            EpisodesSurpriseSection.TAG -> EpisodesSurpriseSection()
            SubscriptionsSection.TAG -> SubscriptionsSection()
            DownloadsSection.TAG -> DownloadsSection()
            else -> null
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedUpdateRunningEvent) {
        viewBinding!!.swipeRefresh.setRefreshing(event.isFeedUpdateRunning)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.getItemId() == R.id.homesettings_items) {
            HomeSectionsSettingsDialog(requireContext()) { populateSectionList() }.show()
            return true
        } else if (item.getItemId() == R.id.refresh_item) {
            FeedUpdateManager.getInstance()!!.runOnceOrAsk(requireContext())
            return true
        } else if (item.getItemId() == R.id.action_search) {
            (getActivity() as MainActivity).loadChildFragment(SearchFragment.newInstance())
            return true
        }
        return false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (disposable != null) {
            disposable!!.dispose()
        }
        viewBinding = null
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: FeedListUpdateEvent) {
        updateWelcomeScreenVisibility()
    }

    private fun updateWelcomeScreenVisibility() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Observable.fromCallable<Int> { DBReader.getTotalEpisodeCount(FeedItemFilter.unfiltered()) }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ numEpisodes ->
                    val hasEpisodes = numEpisodes != 0
                    viewBinding!!.welcomeContainer.setVisibility(if (hasEpisodes) View.GONE else View.VISIBLE)
                    viewBinding!!.homeContainer.setVisibility(if (hasEpisodes) View.VISIBLE else View.GONE)
                    viewBinding!!.swipeRefresh.setVisibility(if (hasEpisodes) View.VISIBLE else View.GONE)
                    if (!hasEpisodes) {
                        viewBinding!!.homeScrollView.setScrollY(0)
                    }
                    val bottomNav = UserPreferences.isBottomNavigationEnabled()
                    viewBinding!!.arrowBottomIcon.setVisibility(if (bottomNav) View.VISIBLE else View.GONE)
                    viewBinding!!.arrowSidebarIcon.setVisibility(if (bottomNav) View.GONE else View.VISIBLE)
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

}
