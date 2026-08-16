package de.test.antennapod.ui

import android.content.Intent
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.ui.screen.queue.QueueFragment
import de.test.antennapod.EspressoTestUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withText
import de.test.antennapod.NthMatcher
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.endsWith

/**
 * User interface tests for queue fragment.
 */
@RunWith(AndroidJUnit4::class)
class QueueFragmentTest {

    @get:Rule
    val activityRule = IntentsTestRule(MainActivity::class.java, false, false)

    @Before
    fun setUp() {
        EspressoTestUtils.clearPreferences()
        EspressoTestUtils.clearDatabase()
        EspressoTestUtils.setLaunchScreen(QueueFragment.TAG)
        activityRule.launchActivity(Intent())
    }

    @Test
    fun testLockEmptyQueue() {
        onView(NthMatcher.first(EspressoTestUtils.actionBarOverflow())).perform(click())
        onView(withText(R.string.lock_queue)).perform(click())
        onView(allOf(withClassName(endsWith("Button")), withText(R.string.lock_queue))).perform(click())
        onView(NthMatcher.first(EspressoTestUtils.actionBarOverflow())).perform(click())
        onView(withText(R.string.lock_queue)).perform(click())
    }

    @Test
    fun testSortEmptyQueue() {
        onView(NthMatcher.first(EspressoTestUtils.actionBarOverflow())).perform(click())
        onView(withText(R.string.sort)).perform(click())
        onView(withText(R.string.random)).perform(click())
    }

    @Test
    fun testKeepEmptyQueueSorted() {
        onView(NthMatcher.first(EspressoTestUtils.actionBarOverflow())).perform(click())
        onView(withText(R.string.sort)).perform(click())
        onView(withText(R.string.keep_sorted)).perform(click())
    }
}
