package de.test.antennapod.ui

import android.content.Intent
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.test.antennapod.EspressoTestUtils
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import de.test.antennapod.EspressoTestUtils.clickPreference
import de.test.antennapod.EspressoTestUtils.waitForView
import org.hamcrest.Matchers.allOf

@RunWith(AndroidJUnit4::class)
class FeedSettingsTest {
    private lateinit var uiTestUtils: UITestUtils
    private lateinit var feed: Feed

    @get:Rule
    val activityRule = IntentsTestRule(MainActivity::class.java, false, false)

    @Before
    @Throws(Exception::class)
    fun setUp() {
        uiTestUtils = UITestUtils(InstrumentationRegistry.getInstrumentation().getTargetContext())
        uiTestUtils.setup()

        EspressoTestUtils.clearPreferences()
        EspressoTestUtils.clearDatabase()

        uiTestUtils.addLocalFeedData(false)
        feed = uiTestUtils.hostedFeeds.get(0)
        val intent = Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), MainActivity::class.java)
        intent.putExtra(MainActivityStarter.EXTRA_FEED_ID, feed.getId())
        activityRule.launchActivity(intent)
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        uiTestUtils.tearDown()
    }

    @Test
    fun testClickFeedSettings() {
        onView(isRoot()).perform(waitForView(allOf(isDescendantOfA(withId(R.id.appBar)),
                withText(feed.getTitle()), isDisplayed()), 1000L))
        onView(withId(R.id.butShowSettings)).perform(click())

        clickPreference(R.string.keep_updated)

        clickPreference(R.string.authentication_label)
        onView(withText(R.string.cancel_label)).perform(click())

        clickPreference(R.string.playback_speed)
        onView(withText(R.string.cancel_label)).perform(click())

        clickPreference(R.string.pref_feed_skip)
        onView(withText(R.string.cancel_label)).perform(click())

        clickPreference(R.string.pref_auto_delete_playback_title)
        onView(withText(R.string.cancel_label)).perform(click())

        clickPreference(R.string.feed_volume_adapdation)
        onView(withText(R.string.cancel_label)).perform(click())
    }
}
