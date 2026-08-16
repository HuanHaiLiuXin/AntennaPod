package de.test.antennapod.ui

import android.content.Intent
import android.os.Build
import android.view.View
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.screen.preferences.PreferenceActivity
import de.test.antennapod.EspressoTestUtils
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import java.io.IOException
import java.util.Collections

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.allOf
import org.junit.Assume.assumeTrue

/**
 * User interface tests for MainActivity drawer.
 */
@RunWith(AndroidJUnit4::class)
class NavigationDrawerTest {

    private lateinit var uiTestUtils: UITestUtils

    @get:Rule
    val activityRule = IntentsTestRule(MainActivity::class.java, false, false)

    @Before
    @Throws(IOException::class)
    fun setUp() {
        uiTestUtils = UITestUtils(InstrumentationRegistry.getInstrumentation().getTargetContext())
        uiTestUtils.setup()

        EspressoTestUtils.clearPreferences()
        EspressoTestUtils.clearDatabase()
        UserPreferences.setBottomNavigationEnabled(false)
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        uiTestUtils.tearDown()
    }

    private fun openNavDrawer() {
        onView(isRoot()).perform(EspressoTestUtils.waitForView(withId(R.id.drawer_layout), 1000L))
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
    }

    private fun onDrawerItem(viewMatcher: Matcher<View>): ViewInteraction {
        return onView(allOf(viewMatcher, withId(R.id.txtvTitle)))
    }

    @Test
    @Throws(Exception::class)
    fun testClickNavDrawer() {
        uiTestUtils.addLocalFeedData(false)
        UserPreferences.setDrawerItemOrder(Collections.emptyList(), Collections.emptyList())
        activityRule.launchActivity(Intent())

        // home
        openNavDrawer()
        onDrawerItem(withText(R.string.home_label)).perform(click())
        onView(isRoot()).perform(EspressoTestUtils.waitForView(allOf(isDescendantOfA(withId(R.id.toolbar)),
                withText(R.string.home_label)), 1000L))

        // queue
        openNavDrawer()
        onDrawerItem(withText(R.string.queue_label)).perform(click())
        onView(isRoot()).perform(EspressoTestUtils.waitForView(allOf(isDescendantOfA(withId(R.id.toolbar)),
                withText(R.string.queue_label)), 1000L))

        // Inbox
        openNavDrawer()
        onDrawerItem(withText(R.string.inbox_label)).perform(click())
        onView(isRoot()).perform(EspressoTestUtils.waitForView(allOf(isDescendantOfA(withId(R.id.toolbar)),
                withText(R.string.inbox_label)), 1000L))

        // episodes
        openNavDrawer()
        onDrawerItem(withText(R.string.episodes_label)).perform(click())
        onView(isRoot()).perform(EspressoTestUtils.waitForView(allOf(isDescendantOfA(withId(R.id.toolbar)),
                withText(R.string.episodes_label), isDisplayed()), 1000L))

        // Subscriptions
        openNavDrawer()
        onDrawerItem(withText(R.string.subscriptions_label)).perform(click())
        onView(isRoot()).perform(EspressoTestUtils.waitForView(allOf(isDescendantOfA(withId(R.id.toolbar)),
                withText(R.string.subscriptions_label), isDisplayed()), 1000L))

        // downloads
        openNavDrawer()
        onDrawerItem(withText(R.string.downloads_label)).perform(click())
        onView(isRoot()).perform(EspressoTestUtils.waitForView(allOf(isDescendantOfA(withId(R.id.toolbar)),
                withText(R.string.downloads_label), isDisplayed()), 1000L))

        // playback history
        openNavDrawer()
        onDrawerItem(withText(R.string.playback_history_label)).perform(click())
        onView(isRoot()).perform(EspressoTestUtils.waitForView(allOf(isDescendantOfA(withId(R.id.toolbar)),
                withText(R.string.playback_history_label), isDisplayed()), 1000L))

        // add podcast
        openNavDrawer()
        onView(withId(R.id.nav_list)).perform(swipeUp())
        onDrawerItem(withText(R.string.add_feed_label)).perform(click())
        onView(isRoot()).perform(EspressoTestUtils.waitForView(allOf(isDescendantOfA(withId(R.id.toolbar)),
                withText(R.string.add_feed_label), isDisplayed()), 1000L))

        // podcasts
        for (i in 0 until uiTestUtils.hostedFeeds.size) {
            val f = uiTestUtils.hostedFeeds.get(i)
            openNavDrawer()
            onDrawerItem(withText(f.getTitle())).perform(click())
            onView(isRoot()).perform(EspressoTestUtils.waitForView(allOf(isDescendantOfA(withId(R.id.appBar)),
                    withText(f.getTitle()), isDisplayed()), 1000L))
        }
    }

    @Test
    fun testGoToPreferences() {
        assumeTrue(Build.VERSION.SDK_INT >= 30) // Unclear why this crashes on old Android versions
        activityRule.launchActivity(Intent())
        openNavDrawer()
        onView(withText(R.string.settings_label)).perform(click())
        intended(hasComponent(PreferenceActivity::class.java.getName()))
    }
}
