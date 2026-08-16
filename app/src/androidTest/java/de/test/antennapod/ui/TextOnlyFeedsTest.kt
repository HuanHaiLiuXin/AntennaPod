package de.test.antennapod.ui

import android.content.Intent
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.model.feed.Feed
import de.test.antennapod.EspressoTestUtils
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import java.io.IOException

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withText
import de.test.antennapod.EspressoTestUtils.waitForView
import org.hamcrest.CoreMatchers.allOf

/**
 * Test UI for feeds that do not have media files
 */
@RunWith(AndroidJUnit4::class)
class TextOnlyFeedsTest {

    private lateinit var uiTestUtils: UITestUtils

    @get:Rule
    val activityRule = IntentsTestRule(MainActivity::class.java, false, false)

    @Before
    @Throws(IOException::class)
    fun setUp() {
        EspressoTestUtils.clearPreferences()
        EspressoTestUtils.clearDatabase()

        uiTestUtils = UITestUtils(InstrumentationRegistry.getInstrumentation().getTargetContext())
        uiTestUtils.setHostTextOnlyFeeds(true)
        uiTestUtils.setup()

    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        uiTestUtils.tearDown()
    }

    @Test
    @Throws(Exception::class)
    fun testMarkAsPlayedList() {
        uiTestUtils.addLocalFeedData(false)
        val feed = uiTestUtils.hostedFeeds.get(0)
        EspressoTestUtils.setLaunchScreen("" + feed.getId())
        activityRule.launchActivity(Intent())
        onView(withText(feed.getItemAtIndex(0)!!.getTitle())).perform(click())
        onView(isRoot()).perform(waitForView(withText(R.string.mark_read_no_media_label), 3000L))
        onView(allOf(withText(R.string.mark_read_no_media_label), isDisplayed())).perform(click())
        EspressoTestUtils.waitForViewToDisappear(withText(R.string.mark_read_no_media_label), 3000L)
    }

}
