package de.test.antennapod.ui

import android.content.Context
import android.content.Intent
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.model.download.DownloadError
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.screen.download.CompletedDownloadsFragment
import de.test.antennapod.EspressoTestUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import java.util.Collections
import java.util.Date
import java.util.concurrent.ExecutionException

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import de.test.antennapod.EspressoTestUtils.waitForView
import de.test.antennapod.EspressoTestUtils.waitForViewGlobally
import org.hamcrest.CoreMatchers.not
import org.hamcrest.Matchers.allOf

@RunWith(AndroidJUnit4::class)
class DownloadLogTest {
    @get:Rule
    val activityRule = IntentsTestRule(MainActivity::class.java, false, false)
    private lateinit var context: Context
    private lateinit var completedDownloadsIntent: Intent
    private lateinit var feed: Feed
    private lateinit var media: FeedMedia

    @Before
    @Throws(Exception::class)
    fun setUp() {
        EspressoTestUtils.clearPreferences()
        EspressoTestUtils.clearDatabase()
        context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        completedDownloadsIntent = Intent(context, MainActivity::class.java)
        completedDownloadsIntent.putExtra(MainActivityStarter.EXTRA_FRAGMENT_TAG, CompletedDownloadsFragment.TAG)

        feed = Feed(0L, "last modified", "@@Feed title@@", "link", "description", "payment link",
                "@author@", "language", "type", "feedIdentifier", "http://localhost/cover.png",
                "/sdcard/abc", "http://localhost/feed.xml", 0L)
        val item = FeedItem(0L, "title", "identifier", "link", Date(), FeedItem.UNPLAYED, feed)
        media = FeedMedia(item, "http://localhost/media.mp3", 10000L, "mime type")
        item.setMedia(media)
        feed.setItems(Collections.singletonList(item))
        feed = FeedDatabaseWriter.updateFeed(context, feed, false)
    }

    @Test
    fun testExistingSubscribedFeed() {
        val result = DownloadResult("@@Title@@", feed.getId(),
                Feed.FEEDFILETYPE_FEED, false, DownloadError.ERROR_IO_ERROR, "@@reason@@")
        openDialog(result)
        // Open feed
        onView(withText(R.string.download_log_open_feed)).perform(click())
        waitForViewGlobally(withText(feed.getAuthor()), 2000L)
    }

    @Test
    @Throws(InterruptedException::class, ExecutionException::class)
    fun testExistingNonSubscribedFeed() {
        DBWriter.setFeedState(context, feed, Feed.STATE_NOT_SUBSCRIBED)!!.get()
        val result = DownloadResult("@@Title@@", feed.getId(),
                Feed.FEEDFILETYPE_FEED, false, DownloadError.ERROR_IO_ERROR, "@@reason@@")
        openDialog(result)
        // Opens online feed view
        onView(withText(R.string.download_log_open_feed)).perform(click())
        waitForViewGlobally(withText(feed.getAuthor()), 2000L)
        onView(isRoot()).perform(waitForView(allOf(withText(R.string.subscribe_label), isDisplayed()), 2000L))
    }

    @Test
    fun testNonExistingFeed() {
        val result = DownloadResult("@@Title@@", feed.getId() + 1,
                Feed.FEEDFILETYPE_FEED, false, DownloadError.ERROR_IO_ERROR, "@@reason@@")
        openDialog(result)
        // Does not have button
        onView(withText(R.string.download_log_open_feed)).check(matches(not(isDisplayed())))
    }

    @Test
    fun testExistingMedia() {
        val result = DownloadResult("@@Title@@", media.getId(),
                FeedMedia.FEEDFILETYPE_FEEDMEDIA, false, DownloadError.ERROR_IO_ERROR, "@@reason@@")
        openDialog(result)
        // Opens feed
        onView(withText(R.string.download_log_open_feed)).perform(click())
        waitForViewGlobally(withText(feed.getAuthor()), 2000L)
    }

    @Test
    fun testNonExistingMedia() {
        val result = DownloadResult("@@Title@@", media.getId() + 1,
                FeedMedia.FEEDFILETYPE_FEEDMEDIA, false, DownloadError.ERROR_IO_ERROR, "@@reason@@")
        openDialog(result)
        // Does not have button
        onView(withText(R.string.download_log_open_feed)).check(matches(not(isDisplayed())))
    }

    internal fun openDialog(result: DownloadResult) {
        DBWriter.addDownloadStatus(result)
        activityRule.launchActivity(completedDownloadsIntent)
        onView(withContentDescription(R.string.downloads_log_label)).perform(click())
        onView(isRoot()).perform(waitForView(allOf(withText(result.getTitle()), isDisplayed()), 1000L))
        onView(withText(result.getTitle())).perform(click())
        onView(isRoot()).perform(waitForView(allOf(withText(result.getReasonDetailed()), isDisplayed()), 1000L))
    }
}
