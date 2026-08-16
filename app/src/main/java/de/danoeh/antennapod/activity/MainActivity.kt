package de.danoeh.antennapod.activity

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RelativeLayout
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import androidx.media3.session.MediaController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import de.danoeh.antennapod.R
import de.danoeh.antennapod.event.EpisodeDownloadEvent
import de.danoeh.antennapod.event.FeedUpdateRunningEvent
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.event.StreamingConfirmationEvent
import de.danoeh.antennapod.model.download.DownloadStatus
import de.danoeh.antennapod.net.download.service.feed.FeedUpdateManagerImpl
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager
import de.danoeh.antennapod.net.common.NetworkUtils
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.playback.cast.CastEnabledActivity
import de.danoeh.antennapod.playback.service.PlaybackController
import de.danoeh.antennapod.storage.databasemaintenanceservice.DatabaseMaintenanceWorker
import de.danoeh.antennapod.storage.importexport.AutomaticDatabaseExportWorker
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter
import de.danoeh.antennapod.ui.common.BottomSheetBackPressedCallback
import de.danoeh.antennapod.ui.common.LockableBottomSheetBehavior
import de.danoeh.antennapod.ui.common.NavigationToolbarActivity
import de.danoeh.antennapod.ui.common.ThemeSwitcher
import de.danoeh.antennapod.ui.common.ThemeUtils
import de.danoeh.antennapod.ui.discovery.DiscoveryFragment
import de.danoeh.antennapod.ui.screen.FavoritesFragment
import de.danoeh.antennapod.ui.screen.AddFeedFragment
import de.danoeh.antennapod.ui.screen.AllEpisodesFragment
import de.danoeh.antennapod.ui.screen.InboxFragment
import de.danoeh.antennapod.ui.screen.PlaybackHistoryFragment
import de.danoeh.antennapod.ui.screen.SearchFragment
import de.danoeh.antennapod.ui.screen.download.CompletedDownloadsFragment
import de.danoeh.antennapod.ui.screen.download.DownloadLogFragment
import de.danoeh.antennapod.ui.screen.drawer.BottomNavigation
import de.danoeh.antennapod.ui.screen.drawer.NavDrawerFragment
import de.danoeh.antennapod.ui.screen.drawer.NavigationNames
import de.danoeh.antennapod.ui.screen.episode.ItemPagerFragment
import de.danoeh.antennapod.ui.screen.feed.FeedItemlistFragment
import de.danoeh.antennapod.ui.screen.home.HomeFragment
import de.danoeh.antennapod.ui.screen.playback.audio.AudioPlayerFragment
import de.danoeh.antennapod.ui.screen.preferences.PreferenceActivity
import de.danoeh.antennapod.ui.screen.queue.QueueFragment
import de.danoeh.antennapod.ui.screen.rating.RatingDialogManager
import de.danoeh.antennapod.ui.screen.subscriptions.SubscriptionFragment
import de.danoeh.antennapod.ui.statistics.StatisticsFragment
import org.apache.commons.lang3.ArrayUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

import java.util.HashMap
import java.util.Objects

/**
 * The activity that is shown when the user launches the app.
 */
class MainActivity : CastEnabledActivity(), NavigationToolbarActivity {

    companion object {
        private const val TAG = "MainActivity"
        const val MAIN_FRAGMENT_TAG = "main"

        const val PREF_NAME = "MainActivityPrefs"
        const val PREF_IS_FIRST_LAUNCH = "prefMainActivityIsFirstLaunch"

        const val EXTRA_REFRESH_ON_START = "refresh_on_start"
        const val KEY_GENERATED_VIEW_ID = "generated_view_id"
    }

    private var drawerLayout: DrawerLayout? = null
    private var drawerToggle: ActionBarDrawerToggle? = null
    private var bottomNavigation: BottomNavigation? = null
    private var navDrawer: View? = null
    private var sheetBehavior: LockableBottomSheetBehavior<FragmentContainerView>? = null
    private lateinit var bottomSheetBackPressedCallback: BottomSheetBackPressedCallback
    private lateinit var openDefaultPageBackPressedCallback: OnBackPressedCallback
    private var lastTheme = 0
    private var systemBarInsets: Insets = Insets.NONE

    override fun onCreate(savedInstanceState: Bundle?) {
        lastTheme = ThemeSwitcher.getNoTitleTheme(this)
        setTheme(lastTheme)
        if (savedInstanceState != null) {
            ensureGeneratedViewIdGreaterThan(savedInstanceState.getInt(KEY_GENERATED_VIEW_ID, 0))
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        checkFirstLaunch()

        drawerLayout = findViewById(R.id.drawer_layout)
        navDrawer = findViewById(R.id.navDrawerFragment)
        bottomNavigation = object : BottomNavigation(findViewById(R.id.bottomNavigationView)) {
            override fun onItemSelected(@IdRes itemId: Int) {
                sheetBehavior!!.setState(BottomSheetBehavior.STATE_COLLAPSED)
                if (itemId == R.id.bottom_navigation_settings) {
                    startActivity(Intent(this@MainActivity, PreferenceActivity::class.java))
                    return
                }
                loadFragment(NavigationNames.getBottomNavigationFragmentTag(itemId)!!, null)
            }
        }
        if (UserPreferences.isBottomNavigationEnabled()) {
            bottomNavigation!!.buildMenu()
            if (drawerLayout == null) { // Tablet mode
                navDrawer!!.setVisibility(View.GONE)
            } else {
                drawerLayout!!.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
            drawerLayout = null
            bottomNavigation!!.onCreateView()
        } else {
            bottomNavigation!!.hide()
            bottomNavigation = null
            setNavDrawerSize()
        }
        openDefaultPageBackPressedCallback = OpenDefaultPageBackPressedCallback()
        if (drawerLayout != null) {
            drawerLayout!!.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
                override fun onDrawerOpened(drawerView: View) {
                    updateMainBackCallbackEnabledState()
                }

                override fun onDrawerClosed(drawerView: View) {
                    updateMainBackCallbackEnabledState()
                }
            })
        }

        // Consume navigation bar insets - we apply them in setPlayerVisible()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_view)) { v, insets ->
            systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            updateInsets()
            WindowInsetsCompat.Builder(insets)
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.NONE)
                    .build()
        }

        val fm = getSupportFragmentManager()
        fm.addOnBackStackChangedListener { updateMainBackCallbackEnabledState() }
        if (fm.findFragmentByTag(MAIN_FRAGMENT_TAG) == null) {
            if (UserPreferences.DEFAULT_PAGE_REMEMBER != UserPreferences.getDefaultPage()) {
                loadFragment(UserPreferences.getDefaultPage()!!, null)
            } else {
                val lastFragment = NavDrawerFragment.getLastNavFragment(this)
                if (ArrayUtils.contains(getResources().getStringArray(R.array.nav_drawer_section_tags), lastFragment)) {
                    loadFragment(lastFragment!!, null)
                } else {
                    try {
                        loadFeedFragmentById(Integer.parseInt(lastFragment).toLong(), null)
                    } catch (e: NumberFormatException) {
                        // it's not a number, this happens if we removed
                        // a label from the NAV_DRAWER_TAGS
                        // give them a nice default...
                        loadFragment(HomeFragment.TAG, null)
                    }
                }
            }
        }

        val transaction = fm.beginTransaction()
        val navDrawerFragment = NavDrawerFragment()
        transaction.replace(R.id.navDrawerFragment, navDrawerFragment, NavDrawerFragment.TAG)
        val audioPlayerFragment = AudioPlayerFragment()
        transaction.replace(R.id.audioplayerFragment, audioPlayerFragment, AudioPlayerFragment.TAG)
        transaction.commit()

        val bottomSheet = findViewById<FragmentContainerView>(R.id.audioplayerFragment)
        @Suppress("UNCHECKED_CAST")
        sheetBehavior = BottomSheetBehavior.from(bottomSheet) as LockableBottomSheetBehavior<FragmentContainerView>
        sheetBehavior!!.setHideable(false)
        sheetBehavior!!.addBottomSheetCallback(bottomSheetCallback)
        bottomSheetBackPressedCallback = BottomSheetBackPressedCallback(false, sheetBehavior!!, bottomSheet)

        FeedUpdateManager.getInstance()!!.restartUpdateAlarm(this, false)
        SynchronizationQueue.getInstance()!!.syncIfNotSyncedRecently()
        AutomaticDatabaseExportWorker.enqueueIfNeeded(this, false)
        DatabaseMaintenanceWorker.enqueueIfNeeded(this)

        WorkManager.getInstance(this)
                .getWorkInfosByTagLiveData(FeedUpdateManagerImpl.WORK_TAG_FEED_UPDATE)
                .observe(this) { workInfos ->
                    var isRefreshingFeeds = false
                    for (workInfo in workInfos) {
                        if (workInfo.state == WorkInfo.State.RUNNING) {
                            isRefreshingFeeds = true
                        } else if (workInfo.state == WorkInfo.State.ENQUEUED) {
                            isRefreshingFeeds = true
                        }
                    }
                    EventBus.getDefault().postSticky(FeedUpdateRunningEvent(isRefreshingFeeds))
                }
        WorkManager.getInstance(this)
                .getWorkInfosByTagLiveData(DownloadServiceInterface.WORK_TAG)
                .observe(this) { workInfos ->
                    val updatedEpisodes = HashMap<String, DownloadStatus>()
                    for (workInfo in workInfos) {
                        var downloadUrl: String? = null
                        for (tag in workInfo.tags) {
                            if (tag.startsWith(DownloadServiceInterface.WORK_TAG_EPISODE_URL)) {
                                downloadUrl = tag.substring(DownloadServiceInterface.WORK_TAG_EPISODE_URL.length)
                            }
                        }
                        if (downloadUrl == null) {
                            continue
                        }
                        var status: Int
                        if (workInfo.state == WorkInfo.State.RUNNING) {
                            status = DownloadStatus.STATE_RUNNING
                        } else if (workInfo.state == WorkInfo.State.ENQUEUED
                                || workInfo.state == WorkInfo.State.BLOCKED) {
                            status = DownloadStatus.STATE_QUEUED
                        } else {
                            status = DownloadStatus.STATE_COMPLETED
                        }
                        var progress = workInfo.progress.getInt(DownloadServiceInterface.WORK_DATA_PROGRESS, -1)
                        if (progress == -1 && status != DownloadStatus.STATE_COMPLETED) {
                            status = DownloadStatus.STATE_QUEUED
                            progress = 0
                        }
                        if (updatedEpisodes.containsKey(downloadUrl) && status == DownloadStatus.STATE_COMPLETED) {
                            continue // In case of a duplicate, prefer running/queued over completed
                        }
                        updatedEpisodes.put(downloadUrl, DownloadStatus(status, progress))
                    }
                    DownloadServiceInterface.get()!!.setCurrentDownloads(updatedEpisodes)
                    EventBus.getDefault().postSticky(EpisodeDownloadEvent(updatedEpisodes))
                }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateInsets()
    }

    /**
     * View.generateViewId stores the current ID in a static variable.
     * When the process is killed, the variable gets reset.
     * This makes sure that we do not get ID collisions
     * and therefore errors when trying to restore state from another view.
     */
    private fun ensureGeneratedViewIdGreaterThan(minimum: Int) {
        while (View.generateViewId() <= minimum) {
            // Generate new IDs
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_GENERATED_VIEW_ID, View.generateViewId())
    }

    private val bottomSheetCallback = AntennaPodBottomSheetCallback()

    private inner class AntennaPodBottomSheetCallback : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(view: View, state: Int) {
            if (state == BottomSheetBehavior.STATE_COLLAPSED) {
                onSlide(view, 0.0f)
                bottomSheetBackPressedCallback.isEnabled = false
            } else if (state == BottomSheetBehavior.STATE_EXPANDED) {
                onSlide(view, 1.0f)
                bottomSheetBackPressedCallback.isEnabled = true
            } else if (state == BottomSheetBehavior.STATE_HIDDEN) {
                PlaybackController.bindToMedia3Service(this@MainActivity) { controller ->
                    controller.clearMediaItems()
                    controller.stop()
                }
                PlaybackPreferences.writeNoMediaPlaying()
                setPlayerVisible(false)
                bottomSheetBackPressedCallback.isEnabled = false
            }
        }

        override fun onSlide(view: View, slideOffset: Float) {
            val audioPlayer = getSupportFragmentManager()
                    .findFragmentByTag(AudioPlayerFragment.TAG) as AudioPlayerFragment?
            if (audioPlayer == null) {
                return
            }

            if (slideOffset == 0.0f) { //STATE_COLLAPSED
                audioPlayer.scrollToPage(AudioPlayerFragment.POS_COVER)
            }

            audioPlayer.fadePlayerToToolbar(slideOffset)
        }
    }

    override fun setupToolbarToggle(toolbar: MaterialToolbar, displayUpArrow: Boolean) {
        if (drawerLayout != null) { // Tablet layout does not have a drawer
            if (drawerToggle != null) {
                drawerLayout!!.removeDrawerListener(drawerToggle!!)
            }
            drawerToggle = ActionBarDrawerToggle(this, drawerLayout, toolbar,
                    R.string.drawer_open, R.string.drawer_close)
            drawerLayout!!.addDrawerListener(drawerToggle!!)
            drawerToggle!!.syncState()
            drawerToggle!!.setDrawerIndicatorEnabled(!displayUpArrow)
            drawerToggle!!.setToolbarNavigationClickListener { getSupportFragmentManager().popBackStack() }
        } else if (!displayUpArrow) {
            toolbar.setNavigationIcon(null)
        } else {
            toolbar.setNavigationIcon(ThemeUtils.getDrawableFromAttr(this, R.attr.homeAsUpIndicator))
            toolbar.setNavigationOnClickListener { getSupportFragmentManager().popBackStack() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (drawerLayout != null && drawerToggle != null) {
            drawerLayout!!.removeDrawerListener(drawerToggle!!)
        }
        if (bottomNavigation != null) {
            bottomNavigation!!.onDestroyView()
        }
    }

    private fun checkFirstLaunch() {
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(PREF_IS_FIRST_LAUNCH, true)) {
            FeedUpdateManager.getInstance()!!.restartUpdateAlarm(this, true)

            val edit = prefs.edit()
            edit.putBoolean(PREF_IS_FIRST_LAUNCH, false)
            edit.apply()
        }
    }

    fun isDrawerOpen(): Boolean {
        return drawerLayout != null && navDrawer != null && drawerLayout!!.isDrawerOpen(navDrawer!!)
    }

    fun getBottomSheet(): LockableBottomSheetBehavior<FragmentContainerView> {
        return sheetBehavior!!
    }

    private fun updateInsets() {
        setPlayerVisible(findViewById<View>(R.id.audioplayerFragment).getVisibility() == View.VISIBLE)
    }

    fun setPlayerVisible(visible: Boolean) {
        getBottomSheet().setLocked(!visible)
        findViewById<View>(R.id.audioplayerFragment).setVisibility(if (visible) View.VISIBLE else View.GONE)
        if (visible) {
            bottomSheetCallback.onStateChanged(null!!, getBottomSheet().getState()) // Update toolbar visibility
        } else {
            getBottomSheet().setState(BottomSheetBehavior.STATE_COLLAPSED)
        }
        val bottomPaddingView = findViewById<View>(R.id.bottom_padding)
        var params = bottomPaddingView.getLayoutParams() as ViewGroup.MarginLayoutParams
        params.height = systemBarInsets.bottom
        bottomPaddingView.setLayoutParams(params)

        val externalPlayerHeight = getResources().getDimension(R.dimen.external_player_height).toInt()
        val mainView = findViewById<FragmentContainerView>(R.id.main_content_view)
        params = mainView.getLayoutParams() as ViewGroup.MarginLayoutParams
        params.setMargins(systemBarInsets.left, 0, systemBarInsets.right, if (visible) externalPlayerHeight else 0)
        mainView.setLayoutParams(params)
        sheetBehavior!!.setPeekHeight(externalPlayerHeight)
        sheetBehavior!!.setHideable(true)
        sheetBehavior!!.setGestureInsetBottomIgnored(true)

        val playerView = findViewById<FragmentContainerView>(R.id.playerFragment)
        val playerParams = playerView.getLayoutParams() as ViewGroup.MarginLayoutParams
        playerParams.setMargins(systemBarInsets.left, 0, systemBarInsets.right, 0)
        playerView.setLayoutParams(playerParams)
        val playerContent = findViewById<RelativeLayout>(R.id.playerContent)
        playerContent.setPadding(systemBarInsets.left, systemBarInsets.top, systemBarInsets.right, 0)
    }

    fun createFragmentInstance(tag: String, args: Bundle?): Fragment {
        Log.d(TAG, "loadFragment(tag: $tag, args: $args)")
        var fragment: Fragment
        var newArgs = args
        when (tag) {
            HomeFragment.TAG -> fragment = HomeFragment()
            QueueFragment.TAG -> fragment = QueueFragment()
            InboxFragment.TAG -> fragment = InboxFragment()
            AllEpisodesFragment.TAG -> fragment = AllEpisodesFragment()
            CompletedDownloadsFragment.TAG -> fragment = CompletedDownloadsFragment()
            PlaybackHistoryFragment.TAG -> fragment = PlaybackHistoryFragment()
            FavoritesFragment.TAG -> fragment = FavoritesFragment()
            AddFeedFragment.TAG -> fragment = AddFeedFragment()
            SubscriptionFragment.TAG -> fragment = SubscriptionFragment()
            StatisticsFragment.TAG -> fragment = StatisticsFragment()
            DiscoveryFragment.TAG -> fragment = DiscoveryFragment()
            else -> {
                // default to home screen
                fragment = HomeFragment()
                newArgs = null
            }
        }
        if (newArgs != null) {
            fragment.setArguments(newArgs)
        }
        return fragment
    }

    fun loadFragment(tag: String, args: Bundle?) {
        NavDrawerFragment.saveLastNavFragment(this, tag)
        if (bottomNavigation != null) {
            bottomNavigation!!.updateSelectedItem(tag)
        }
        loadFragment(createFragmentInstance(tag, args))
    }

    fun loadFeedFragmentById(feedId: Long, args: Bundle?) {
        val fragment = FeedItemlistFragment.newInstance(feedId)
        if (args != null) {
            fragment.setArguments(args)
        }
        NavDrawerFragment.saveLastNavFragment(this, feedId.toString())
        loadFragment(fragment)
    }

    fun loadFragment(fragment: Fragment) {
        val fragmentManager = getSupportFragmentManager()
        // Clear all synchronously to avoid conflicting with predictive back gesture cancellation
        fragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val t = fragmentManager.beginTransaction()
        t.replace(R.id.main_content_view, fragment, MAIN_FRAGMENT_TAG)
        // TODO: we have to allow state loss here
        // since this function can get called from an AsyncTask which
        // could be finishing after our app has already committed state
        // and is about to get shutdown.  What we *should* do is
        // not commit anything in an AsyncTask, but that's a bigger
        // change than we want now.
        t.commitNowAllowingStateLoss()

        if (drawerLayout != null) { // Tablet layout does not have a drawer
            drawerLayout!!.closeDrawer(navDrawer!!)
        }
        updateMainBackCallbackEnabledState()
    }

    fun loadChildFragment(fragment: Fragment, navigationTag: String?) {
        Objects.requireNonNull(fragment)
        if (navigationTag != null && bottomNavigation != null) {
            bottomNavigation!!.updateSelectedItem(navigationTag)
        }
        getSupportFragmentManager().beginTransaction()
                .hide(getSupportFragmentManager().findFragmentByTag(MAIN_FRAGMENT_TAG)!!)
                .add(R.id.main_content_view, fragment, MAIN_FRAGMENT_TAG)
                .addToBackStack(null)
                .commit()
        updateMainBackCallbackEnabledState()
    }

    fun loadChildFragment(fragment: Fragment) {
        loadChildFragment(fragment, null)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (drawerToggle != null) { // Tablet layout does not have a drawer
            drawerToggle!!.syncState()
        }
    }

    private fun restartActivity() {
        finish()
        startActivity(Intent(this, MainActivity::class.java))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (drawerToggle != null) { // Tablet layout does not have a drawer
            drawerToggle!!.onConfigurationChanged(newConfig)
        }
        setNavDrawerSize()

        @StyleRes val requiredTheme = ThemeSwitcher.getNoTitleTheme(this)
        if (requiredTheme != lastTheme) {
            restartActivity()
        }
    }

    private fun setNavDrawerSize() {
        if (drawerToggle == null) { // Tablet layout does not have a drawer
            return
        }
        val screenPercent = getResources().getInteger(R.integer.nav_drawer_screen_size_percent) * 0.01f
        val width = (getScreenWidth() * screenPercent).toInt()
        val maxWidth = getResources().getDimension(R.dimen.nav_drawer_max_screen_size).toInt()

        navDrawer!!.getLayoutParams().width = Math.min(width, maxWidth)
    }

    private fun getScreenWidth(): Int {
        val displayMetrics = DisplayMetrics()
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics)
        return displayMetrics.widthPixels
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        if (getBottomSheet().getState() == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetCallback.onSlide(null!!, 1.0f)
        }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        RatingDialogManager(this).showIfNeeded()
        onBackPressedDispatcher.addCallback(this, openDefaultPageBackPressedCallback)
        onBackPressedDispatcher.addCallback(this, bottomSheetBackPressedCallback)
    }

    override fun onResume() {
        super.onResume()
        handleNavIntent()

        val hasBottomNavigation = bottomNavigation != null
        if (lastTheme != ThemeSwitcher.getNoTitleTheme(this)
                || hasBottomNavigation != UserPreferences.isBottomNavigationEnabled()) {
            restartActivity()
        }
        if (UserPreferences.getHiddenDrawerItems().contains(NavDrawerFragment.getLastNavFragment(this))) {
            loadFragment(UserPreferences.getDefaultPage()!!, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        lastTheme = ThemeSwitcher.getNoTitleTheme(this) // Don't recreate activity when a result is pending
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Glide.get(this).trimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Glide.get(this).clearMemory()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle != null && drawerToggle!!.onOptionsItemSelected(item)) { // Tablet layout does not have a drawer
            return true
        } else if (item.getItemId() == android.R.id.home) {
            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                getSupportFragmentManager().popBackStack()
            }
            return true
        } else {
            return super.onOptionsItemSelected(item)
        }
    }

    private fun updateMainBackCallbackEnabledState() {
        val defaultPage = UserPreferences.getDefaultPage()
        val shouldEnable = getSupportFragmentManager().getBackStackEntryCount() > 0
                || (NavDrawerFragment.getLastNavFragment(this) != defaultPage
                && UserPreferences.DEFAULT_PAGE_REMEMBER != defaultPage)
                || (UserPreferences.backButtonOpensDrawer() && drawerLayout != null
                && bottomNavigation == null && !drawerLayout!!.isDrawerOpen(navDrawer!!))
        openDefaultPageBackPressedCallback.isEnabled = shouldEnable
    }

    inner class OpenDefaultPageBackPressedCallback : OnBackPressedCallback(false) {

        override fun handleOnBackPressed() {
            val defaultPage = UserPreferences.getDefaultPage()
            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                getSupportFragmentManager().popBackStack()
            } else if (NavDrawerFragment.getLastNavFragment(this@MainActivity) != defaultPage
                    && UserPreferences.DEFAULT_PAGE_REMEMBER != defaultPage) {
                loadFragment(defaultPage!!, null)
            } else if (UserPreferences.backButtonOpensDrawer() && drawerLayout != null && bottomNavigation == null) {
                drawerLayout!!.openDrawer(navDrawer!!)
            } else {
                finish()
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: MessageEvent) {
        Log.d(TAG, "onEvent($event)")
        val snackbar: Snackbar
        if (getBottomSheet().getState() == BottomSheetBehavior.STATE_EXPANDED) {
            snackbar = Snackbar.make(findViewById(android.R.id.content), event.message, Snackbar.LENGTH_LONG)
            if (findViewById<View>(R.id.bottomNavigationView).getVisibility() == View.VISIBLE) {
                snackbar.setAnchorView(findViewById(R.id.bottomNavigationView))
            }
        } else {
            snackbar = Snackbar.make(findViewById(R.id.main_content_view), event.message, Snackbar.LENGTH_LONG)
            if (findViewById<View>(R.id.audioplayerFragment).getVisibility() == View.VISIBLE) {
                snackbar.setAnchorView(findViewById(R.id.audioplayerFragment))
            }
        }
        snackbar.show()

        if (event.action != null) {
            snackbar.setAction(event.actionText) { event.action!!.accept(this) }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onStreamingConfirmation(event: StreamingConfirmationEvent) {
        MaterialAlertDialogBuilder(this)
                .setTitle(R.string.stream_label)
                .setMessage(if (NetworkUtils.isNetworkRestricted() && NetworkUtils.isVpnOverWifi())
                    getString(R.string.confirm_mobile_streaming_notification_message)
                            + "\n\n" + getString(R.string.confirm_mobile_download_dialog_message_vpn)
                else getString(R.string.confirm_mobile_streaming_notification_message))
                .setPositiveButton(R.string.confirm_mobile_streaming_button_once, { dialog, which ->
                    PlaybackController.bindToMedia3Service(this) { it.play() }
                })
                .setNegativeButton(R.string.confirm_mobile_streaming_button_always, { dialog, which ->
                    UserPreferences.setAllowMobileStreaming(true)
                    PlaybackController.bindToMedia3Service(this) { it.play() }
                })
                .setNeutralButton(R.string.cancel_label, null)
                .show()
    }

    private fun handleNavIntent() {
        Log.d(TAG, "handleNavIntent()")
        val intent = getIntent()
        if (intent.hasExtra(MainActivityStarter.EXTRA_EPISODE_ID)) {
            val episodeId = intent.getLongExtra(MainActivityStarter.EXTRA_EPISODE_ID, 0)
            if (episodeId <= 0) {
                return
            }
            loadChildFragment(ItemPagerFragment.newInstance(episodeId))
            sheetBehavior!!.setState(BottomSheetBehavior.STATE_COLLAPSED)
        } else if (intent.hasExtra(MainActivityStarter.EXTRA_FEED_ID)) {
            val feedId = intent.getLongExtra(MainActivityStarter.EXTRA_FEED_ID, 0)
            val args = intent.getBundleExtra(MainActivityStarter.EXTRA_FRAGMENT_ARGS)
            if (feedId > 0) {
                if (intent.getBooleanExtra(MainActivityStarter.EXTRA_CLEAR_BACK_STACK, true)) {
                    loadFeedFragmentById(feedId, args)
                } else {
                    loadChildFragment(FeedItemlistFragment.newInstance(feedId))
                }
            }
            sheetBehavior!!.setState(BottomSheetBehavior.STATE_COLLAPSED)
        } else if (intent.hasExtra(MainActivityStarter.EXTRA_FRAGMENT_TAG)) {
            val tag = intent.getStringExtra(MainActivityStarter.EXTRA_FRAGMENT_TAG)
            val args = intent.getBundleExtra(MainActivityStarter.EXTRA_FRAGMENT_ARGS)
            if (tag != null) {
                if (intent.getBooleanExtra(MainActivityStarter.EXTRA_CLEAR_BACK_STACK, true)) {
                    loadFragment(tag, null)
                } else {
                    loadChildFragment(createFragmentInstance(tag, args), tag)
                }
            }
            sheetBehavior!!.setState(BottomSheetBehavior.STATE_COLLAPSED)
        } else if (intent.getBooleanExtra(MainActivityStarter.EXTRA_OPEN_PLAYER, false)) {
            sheetBehavior!!.setState(BottomSheetBehavior.STATE_EXPANDED)
            bottomSheetCallback.onSlide(null!!, 1.0f)
        } else {
            handleDeeplink(intent.getData())
        }

        if (intent.getBooleanExtra(MainActivityStarter.EXTRA_OPEN_DRAWER, false) && drawerLayout != null) {
            drawerLayout!!.open()
        }
        if (intent.getBooleanExtra(MainActivityStarter.EXTRA_OPEN_DOWNLOAD_LOGS, false)) {
            DownloadLogFragment().show(getSupportFragmentManager(), DownloadLogFragment.TAG)
        }
        if (intent.getBooleanExtra(EXTRA_REFRESH_ON_START, false)) {
            FeedUpdateManager.getInstance()!!.runOnceOrAsk(this)
        }
        // to avoid handling the intent twice when the configuration changes
        setIntent(Intent(this@MainActivity, MainActivity::class.java))
        updateMainBackCallbackEnabledState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavIntent()
    }

    /**
     * Handles the deep link incoming via App Actions.
     * Performs an in-app search or opens the relevant feature of the app
     * depending on the query.
     *
     * @param uri incoming deep link
     */
    private fun handleDeeplink(uri: Uri?) {
        if (uri == null || uri.getPath() == null) {
            return
        }
        Log.d(TAG, "Handling deeplink: $uri")
        when (uri.getPath()) {
            "/deeplink/search" -> {
                val query = uri.getQueryParameter("query")
                if (query == null) {
                    return
                }

                this.loadChildFragment(SearchFragment.newInstance(query))
            }
            "/deeplink/main" -> {
                val feature = uri.getQueryParameter("page")
                if (feature == null) {
                    return
                }
                when (feature) {
                    "DOWNLOADS" -> loadFragment(CompletedDownloadsFragment.TAG, null)
                    "HISTORY" -> loadFragment(PlaybackHistoryFragment.TAG, null)
                    "EPISODES" -> loadFragment(AllEpisodesFragment.TAG, null)
                    "QUEUE" -> loadFragment(QueueFragment.TAG, null)
                    "SUBSCRIPTIONS" -> loadFragment(SubscriptionFragment.TAG, null)
                    "STATISTICS" -> loadFragment(StatisticsFragment.TAG, null)
                    else -> {
                        EventBus.getDefault().post(MessageEvent(getString(R.string.app_action_not_found, feature)))
                        return
                    }
                }
            }
            else -> Unit
        }
    }

    //Hardware keyboard support
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val currentFocus = getCurrentFocus()
        if (currentFocus is EditText) {
            return super.onKeyUp(keyCode, event)
        }

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        var customKeyCode: Int? = null
        EventBus.getDefault().post(event)

        when (keyCode) {
            KeyEvent.KEYCODE_P -> customKeyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_COMMA ->
                customKeyCode = KeyEvent.KEYCODE_MEDIA_REWIND
            KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_PERIOD ->
                customKeyCode = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_W -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                return true
            }
            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_S -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                return true
            }
            KeyEvent.KEYCODE_M -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                return true
            }
            else -> Unit
        }

        if (customKeyCode != null) {
            sendBroadcast(MediaButtonStarter.createIntent(this, customKeyCode))
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}
