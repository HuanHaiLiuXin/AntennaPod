package de.test.antennapod.ui

import android.content.Intent
import androidx.test.espresso.Espresso
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.storage.database.PodDBAdapter
import de.danoeh.antennapod.ui.screen.AddFeedFragment
import de.test.antennapod.EspressoTestUtils
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import java.io.IOException

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText

@RunWith(AndroidJUnit4::class)
class AddFeedFragmentTest {

    private lateinit var uiTestUtils: UITestUtils

    @get:Rule
    val activityRule = IntentsTestRule(MainActivity::class.java, false, false)

    @Before
    @Throws(IOException::class)
    fun setUp() {
        EspressoTestUtils.clearPreferences()
        EspressoTestUtils.clearDatabase()
        EspressoTestUtils.setLaunchScreen(AddFeedFragment.TAG)

        activityRule.launchActivity(Intent())

        uiTestUtils = UITestUtils(InstrumentationRegistry.getInstrumentation().getTargetContext())
        uiTestUtils.setup()
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        uiTestUtils.tearDown()
        PodDBAdapter.deleteDatabase()
    }

    @Test
    @Throws(Exception::class)
    fun testAddFeedByUrl() {
        // connect to podcast feed
        uiTestUtils.addHostedFeedData()
        val feed = uiTestUtils.hostedFeeds.get(0)
        onView(withId(R.id.addViaUrlButton)).perform(scrollTo(), click())
        onView(withId(R.id.textInput)).perform(replaceText(feed.getDownloadUrl()))
        onView(withText(R.string.confirm_label)).perform(scrollTo(), click())

        // subscribe podcast
        Espresso.closeSoftKeyboard()
        EspressoTestUtils.waitForViewGlobally(withText(R.string.subscribe_label), 15000L)
        onView(withText(R.string.subscribe_label)).perform(click())

        // wait for podcast feed item list
        EspressoTestUtils.waitForViewGlobally(withId(R.id.butShowSettings), 15000L)
    }
}
