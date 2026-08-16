package de.test.antennapod.dialogs

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.ui.screen.AllEpisodesFragment
import de.test.antennapod.EspressoTestUtils
import de.test.antennapod.ui.UITestUtils
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import de.test.antennapod.EspressoTestUtils.waitForView
import de.test.antennapod.NthMatcher.first
import org.hamcrest.CoreMatchers.allOf

/**
 * User interface tests for share dialog.
 */
@RunWith(AndroidJUnit4::class)
class ShareDialogTest {

    @get:Rule
    val activityRule = IntentsTestRule(MainActivity::class.java, false, false)

    protected lateinit var context: Context

    @Before
    @Throws(Exception::class)
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        EspressoTestUtils.clearPreferences()
        EspressoTestUtils.clearDatabase()
        EspressoTestUtils.setLaunchScreen(AllEpisodesFragment.TAG)
        val uiTestUtils = UITestUtils(context)
        uiTestUtils.setup()
        uiTestUtils.addLocalFeedData(true)

        activityRule.launchActivity(Intent())

        val allEpisodesMatcher: Matcher<View> = Matchers.allOf(withId(R.id.recyclerView), isDisplayed(),
                hasMinimumChildCount(2))
        onView(isRoot()).perform(waitForView(allEpisodesMatcher, 1000L))
        onView(allEpisodesMatcher).perform(actionOnItemAtPosition(0, click()))
        onView(first(EspressoTestUtils.actionBarOverflow())).perform(click())
    }

    @Test
    fun testShareDialogDisplayed() {
        onView(withText(R.string.share_label)).perform(click())
        onView(allOf(isDisplayed(), withText(R.string.share_label)))
    }

}
