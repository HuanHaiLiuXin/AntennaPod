package de.test.antennapod.ui

import android.content.Intent
import androidx.test.rule.ActivityTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.danoeh.antennapod.R
import de.danoeh.antennapod.ui.screen.preferences.PreferenceActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import de.test.antennapod.EspressoTestUtils.clickPreference

@RunWith(AndroidJUnit4::class)
class AboutFragmentTest {

    @get:Rule
    val activityTestRule = ActivityTestRule(PreferenceActivity::class.java, false, false)

    @Test
    fun testAboutNavigation() {
        activityTestRule.launchActivity(Intent())
        clickPreference(R.string.about_pref)
        onView(withText(R.string.about_pref)).check(matches(isDisplayed()))
        onView(withText(R.string.contributors)).check(matches(isDisplayed()))
        onView(withText(R.string.licenses)).check(matches(isDisplayed()))
    }

    @Test
    fun testContributors() {
        activityTestRule.launchActivity(Intent())
        clickPreference(R.string.about_pref)
        clickPreference(R.string.contributors)
        onView(withText(R.string.contributors)).check(matches(isDisplayed()))
    }

    @Test
    fun testLicenses() {
        activityTestRule.launchActivity(Intent())
        clickPreference(R.string.about_pref)
        clickPreference(R.string.licenses)
        onView(withText(R.string.licenses)).check(matches(isDisplayed()))
    }

    @Test
    fun testPrivacyPolicy() {
        activityTestRule.launchActivity(Intent())
        clickPreference(R.string.about_pref)
        onView(withText(R.string.privacy_policy)).check(matches(isDisplayed()))
    }
}
