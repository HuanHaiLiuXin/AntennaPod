package de.test.antennapod.ui

import android.content.Intent
import android.os.Build
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.test.antennapod.EspressoTestUtils
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import java.io.IOException
import java.util.Collections

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import de.test.antennapod.EspressoTestUtils.clickBottomNavItem
import de.test.antennapod.EspressoTestUtils.clickBottomNavOverflow
import de.test.antennapod.EspressoTestUtils.waitForView
import org.hamcrest.Matchers.allOf
import org.junit.Assume.assumeTrue

/**
 * User interface tests for MainActivity bottom navigation.
 */
@RunWith(AndroidJUnit4::class)
class BottomNavigationTest {

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
        UserPreferences.setBottomNavigationEnabled(true)
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        uiTestUtils.tearDown()
    }

    @Test
    @Throws(Exception::class)
    fun testClickBottomNavigation() {
        assumeTrue(Build.VERSION.SDK_INT >= 30) // Unclear why this crashes on old Android versions
        uiTestUtils.addLocalFeedData(false)
        UserPreferences.setDrawerItemOrder(Collections.emptyList(), Collections.emptyList())
        activityRule.launchActivity(Intent())

        clickBottomNavItem(R.string.home_label_short)
        onView(isRoot()).perform(waitForView(allOf(isDescendantOfA(withId(R.id.toolbar)),
                withText(R.string.home_label)), 1000L))

        clickBottomNavItem(R.string.queue_label_short)
        onView(isRoot()).perform(waitForView(allOf(isDescendantOfA(withId(R.id.toolbar)),
                withText(R.string.queue_label)), 1000L))

        clickBottomNavItem(R.string.inbox_label_short)
        onView(isRoot()).perform(waitForView(allOf(isDescendantOfA(withId(R.id.toolbar)),
                withText(R.string.inbox_label)), 1000L))

        clickBottomNavItem(R.string.subscriptions_label_short)
        onView(isRoot()).perform(waitForView(allOf(isDescendantOfA(withId(R.id.toolbar)),
                withText(R.string.subscriptions_label)), 1000L))

        clickBottomNavOverflow(R.string.episodes_label)
        onView(isRoot()).perform(waitForView(allOf(isDescendantOfA(withId(R.id.toolbar)),
                withText(R.string.episodes_label)), 1000L))

        clickBottomNavOverflow(R.string.downloads_label)
        onView(isRoot()).perform(waitForView(allOf(isDescendantOfA(withId(R.id.toolbar)),
                withText(R.string.downloads_label)), 1000L))

        clickBottomNavOverflow(R.string.playback_history_label)
        onView(isRoot()).perform(waitForView(allOf(isDescendantOfA(withId(R.id.toolbar)),
                withText(R.string.playback_history_label)), 1000L))

        clickBottomNavOverflow(R.string.add_feed_label)
        onView(isRoot()).perform(waitForView(allOf(isDescendantOfA(withId(R.id.toolbar)),
                withText(R.string.add_feed_label)), 1000L))
    }
}
