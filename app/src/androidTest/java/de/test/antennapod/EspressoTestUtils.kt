package de.test.antennapod

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.util.HumanReadables
import androidx.test.espresso.util.TreeIterables
import androidx.test.platform.app.InstrumentationRegistry

import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.playback.service.PlaybackService
import de.danoeh.antennapod.storage.database.PodDBAdapter
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.screen.drawer.NavDrawerFragment
import junit.framework.AssertionFailedError

import org.awaitility.Awaitility
import org.awaitility.core.ConditionTimeoutException
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not

import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class EspressoTestUtils {
    companion object {
        /**
         * Perform action of waiting for a specific view id.
         * https://stackoverflow.com/a/49814995/
         * @param viewMatcher The view to wait for.
         * @param millis The timeout of until when to wait for.
         */
        @JvmStatic
        fun waitForView(viewMatcher: Matcher<View>, millis: Long): ViewAction {
            return object : ViewAction {
                override fun getConstraints(): Matcher<View> {
                    return isRoot()
                }

                override fun getDescription(): String {
                    return "wait for a specific view for " + millis + " millis."
                }

                override fun perform(uiController: UiController, view: View) {
                    uiController.loopMainThreadUntilIdle()
                    val startTime = System.currentTimeMillis()
                    val endTime = startTime + millis

                    do {
                        for (child in TreeIterables.breadthFirstViewTraversal(view)) {
                            // found view with required ID
                            if (viewMatcher.matches(child)) {
                                return
                            }
                        }

                        uiController.loopMainThreadForAtLeast(50L)
                    } while (System.currentTimeMillis() < endTime)

                    // timeout happens
                    throw PerformException.Builder()
                            .withActionDescription(this.getDescription())
                            .withViewDescription(HumanReadables.describe(view))
                            .withCause(TimeoutException())
                            .build()
                }
            }
        }

        /**
         * Wait until a certain view becomes visible, but at the longest until the timeout.
         * Unlike {@link #waitForView(Matcher, long)} it doesn't stick to the initial root view.
         *
         * @param viewMatcher The view to wait for.
         * @param timeoutMillis Maximum waiting period in milliseconds.
         */
        @JvmStatic
        fun waitForViewGlobally(viewMatcher: Matcher<View>, timeoutMillis: Long) {
            val startTime = System.currentTimeMillis()
            val endTime = startTime + timeoutMillis

            do {
                try {
                    onView(viewMatcher).check(matches(isDisplayed()))
                    // no Exception thrown -> check successful
                    return
                } catch (exception: NoMatchingViewException) {
                    // check was not successful "not found" -> continue waiting
                    if (System.currentTimeMillis() >= endTime) {
                        throw exception
                    }
                } catch (exception: AssertionFailedError) {
                    // check was not successful "not found" -> continue waiting
                    if (System.currentTimeMillis() >= endTime) {
                        throw exception
                    }
                }
                try {
                    //noinspection BusyWait
                    Thread.sleep(50L)
                } catch (e: InterruptedException) {
                    break
                }
            } while (true)

            throw RuntimeException("Timeout after " + timeoutMillis + " ms")
        }

        /**
         * Perform action of waiting for a specific view id.
         * https://stackoverflow.com/a/30338665/
         * @param id The id of the child to click.
         */
        @JvmStatic
        fun clickChildViewWithId(@IdRes id: Int): ViewAction {
            return object : ViewAction {
                override fun getConstraints(): Matcher<View>? {
                    return null
                }

                override fun getDescription(): String {
                    return "Click on a child view with specified id."
                }

                override fun perform(uiController: UiController, view: View) {
                    val v = view.findViewById<View>(id)
                    v.performClick()
                }
            }
        }

        @JvmStatic
        fun waitForViewToDisappear(matcher: Matcher<in View>, maxWaitingTimeMs: Long) {
            val endTime = System.currentTimeMillis() + maxWaitingTimeMs
            while (System.currentTimeMillis() <= endTime) {
                try {
                    onView(allOf(matcher, isDisplayed())).check(matches(not(doesNotExist())))
                    Thread.sleep(100L)
                } catch (ex: NoMatchingViewException) {
                    return // view has disappeared
                } catch (e: InterruptedException) {
                    throw RuntimeException(e)
                }
            }
            throw RuntimeException("timeout exceeded") // or whatever exception you want
        }

        /**
         * Clear all app databases.
         */
        @JvmStatic
        fun clearPreferences() {
            val root = InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir().getParentFile()
            val sharedPreferencesFileNames = File(root, "shared_prefs").list()
            for (fileName in sharedPreferencesFileNames!!) {
                System.out.println("Cleared database: " + fileName)
                InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences(
                        fileName.replace(".xml", ""), Context.MODE_PRIVATE).edit().clear().commit()
            }

            InstrumentationRegistry.getInstrumentation().getTargetContext()
                    .getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(MainActivity.PREF_IS_FIRST_LAUNCH, false)
                    .commit()

            PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getInstrumentation().getTargetContext())
                    .edit()
                    .putString(UserPreferences.PREF_UPDATE_INTERVAL_MINUTES, "0")
                    .commit()
        }

        @JvmStatic
        fun setLaunchScreen(tag: String) {
            InstrumentationRegistry.getInstrumentation().getTargetContext()
                    .getSharedPreferences(NavDrawerFragment.PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(NavDrawerFragment.PREF_LAST_FRAGMENT_TAG, tag)
                    .commit()
            PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getInstrumentation().getTargetContext())
                    .edit()
                    .putString(UserPreferences.PREF_DEFAULT_PAGE, UserPreferences.DEFAULT_PAGE_REMEMBER)
                    .commit()
        }

        @JvmStatic
        fun clearDatabase() {
            PodDBAdapter.init(InstrumentationRegistry.getInstrumentation().getTargetContext())
            PodDBAdapter.deleteDatabase()
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            adapter.close()
        }

        @JvmStatic
        fun clickPreference(@StringRes title: Int) {
            onView(withId(R.id.recycler_view)).perform(
                    RecyclerViewActions.actionOnItem(
                            allOf(hasDescendant(withText(title)),
                                    hasDescendant(withId(android.R.id.widget_frame))),
                            click()))
        }

        @JvmStatic
        fun clickBottomNavItem(@StringRes text: Int) {
            onView(allOf(withText(text),
                    isDescendantOfA(withId(R.id.bottomNavigationView)), isDisplayed())).perform(click())
        }

        @JvmStatic
        fun clickBottomNavOverflow(@StringRes text: Int) {
            onView(allOf(withText(R.string.overflow_more),
                    isDescendantOfA(withId(R.id.bottomNavigationView)), isDisplayed())).perform(click())
            onView(allOf(withText(text), isDisplayed())).perform(click())
        }

        @JvmStatic
        fun tryKillPlaybackService() {
            val context = InstrumentationRegistry.getInstrumentation().getTargetContext()
            context.stopService(Intent(context, PlaybackService::class.java))
            try {
                // Android has no reliable way to stop a service instantly.
                // Calling stopSelf marks allows the system to destroy the service but the actual call
                // to onDestroy takes until the next GC of the system, which we can not influence.
                // Try to wait for the service at least a bit.
                Awaitility.await().atMost(10L, TimeUnit.SECONDS).until { !PlaybackService.isRunning }
            } catch (e: ConditionTimeoutException) {
                e.printStackTrace()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }

        @JvmStatic
        fun actionBarOverflow(): Matcher<View> {
            return allOf(isDisplayed(), withContentDescription("More options"))
        }
    }
}
