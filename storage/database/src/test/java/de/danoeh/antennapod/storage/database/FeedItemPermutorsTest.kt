package de.danoeh.antennapod.storage.database

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import de.danoeh.antennapod.model.feed.SortOrder
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.util.ArrayList
import java.util.Calendar

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.storage.preferences.UserPreferences

/**
 * Test class for FeedItemPermutors.
 */
@RunWith(RobolectricTestRunner::class)
class FeedItemPermutorsTest {

    @Test
    fun testEnsureNonNullPermutors() {
        val context = InstrumentationRegistry.getInstrumentation().getContext()
        UserPreferences.init(context)
        for (sortOrder in SortOrder.values()) {
            assertNotNull("The permutor for SortOrder " + sortOrder + " is unexpectedly null",
                    FeedItemPermutors.getPermutor(sortOrder))
        }
    }

    @Test
    fun testPermutorForRule_EPISODE_TITLE_ASC() {
        val permutor = FeedItemPermutors.getPermutor(SortOrder.EPISODE_TITLE_A_Z)

        val itemList = getTestList()
        assertTrue(checkIdOrder(itemList, 1L, 3L, 2L)) // before sorting
        permutor.reorder(itemList)
        assertTrue(checkIdOrder(itemList, 1L, 2L, 3L)) // after sorting
    }

    @Test
    fun testPermutorForRule_EPISODE_TITLE_ASC_NullTitle() {
        val permutor = FeedItemPermutors.getPermutor(SortOrder.EPISODE_TITLE_A_Z)

        val itemList = getTestList()
        itemList[2] // itemId 2
                .setTitle(null)
        assertTrue(checkIdOrder(itemList, 1L, 3L, 2L)) // before sorting
        permutor.reorder(itemList)
        assertTrue(checkIdOrder(itemList, 2L, 1L, 3L)) // after sorting
    }


    @Test
    fun testPermutorForRule_EPISODE_TITLE_DESC() {
        val permutor = FeedItemPermutors.getPermutor(SortOrder.EPISODE_TITLE_Z_A)

        val itemList = getTestList()
        assertTrue(checkIdOrder(itemList, 1L, 3L, 2L)) // before sorting
        permutor.reorder(itemList)
        assertTrue(checkIdOrder(itemList, 3L, 2L, 1L)) // after sorting
    }

    @Test
    fun testPermutorForRule_DATE_ASC() {
        val permutor = FeedItemPermutors.getPermutor(SortOrder.DATE_OLD_NEW)

        val itemList = getTestList()
        assertTrue(checkIdOrder(itemList, 1L, 3L, 2L)) // before sorting
        permutor.reorder(itemList)
        assertTrue(checkIdOrder(itemList, 1L, 2L, 3L)) // after sorting
    }

    @Test
    fun testPermutorForRule_DATE_ASC_NulPubDatel() {
        val permutor = FeedItemPermutors.getPermutor(SortOrder.DATE_OLD_NEW)

        val itemList = getTestList()
        itemList[2] // itemId 2
                .setPubDate(null)
        assertTrue(checkIdOrder(itemList, 1L, 3L, 2L)) // before sorting
        permutor.reorder(itemList)
        assertTrue(checkIdOrder(itemList, 2L, 1L, 3L)) // after sorting
    }

    @Test
    fun testPermutorForRule_DATE_DESC() {
        val permutor = FeedItemPermutors.getPermutor(SortOrder.DATE_NEW_OLD)

        val itemList = getTestList()
        assertTrue(checkIdOrder(itemList, 1L, 3L, 2L)) // before sorting
        permutor.reorder(itemList)
        assertTrue(checkIdOrder(itemList, 3L, 2L, 1L)) // after sorting
    }

    @Test
    fun testPermutorForRule_DURATION_ASC() {
        val permutor = FeedItemPermutors.getPermutor(SortOrder.DURATION_SHORT_LONG)

        val itemList = getTestList()
        assertTrue(checkIdOrder(itemList, 1L, 3L, 2L)) // before sorting
        permutor.reorder(itemList)
        assertTrue(checkIdOrder(itemList, 1L, 2L, 3L)) // after sorting
    }

    @Test
    fun testPermutorForRule_DURATION_DESC() {
        val permutor = FeedItemPermutors.getPermutor(SortOrder.DURATION_LONG_SHORT)

        val itemList = getTestList()
        assertTrue(checkIdOrder(itemList, 1L, 3L, 2L)) // before sorting
        permutor.reorder(itemList)
        assertTrue(checkIdOrder(itemList, 3L, 2L, 1L)) // after sorting
    }

    @Test
    fun testPermutorForRule_size_asc() {
        val permutor = FeedItemPermutors.getPermutor(SortOrder.SIZE_SMALL_LARGE)

        val itemList = getTestList()
        assertTrue(checkIdOrder(itemList, 1L, 3L, 2L)) // before sorting
        permutor.reorder(itemList)
        assertTrue(checkIdOrder(itemList, 1L, 2L, 3L)) // after sorting
    }

    @Test
    fun testPermutorForRule_size_desc() {
        val permutor = FeedItemPermutors.getPermutor(SortOrder.SIZE_LARGE_SMALL)

        val itemList = getTestList()
        assertTrue(checkIdOrder(itemList, 1L, 3L, 2L)) // before sorting
        permutor.reorder(itemList)
        assertTrue(checkIdOrder(itemList, 3L, 2L, 1L)) // after sorting
    }

    @Test
    fun testPermutorForRule_DURATION_DESC_NullMedia() {
        val permutor = FeedItemPermutors.getPermutor(SortOrder.DURATION_LONG_SHORT)

        val itemList = getTestList()
        itemList[1] // itemId 3
                .setMedia(null)
        assertTrue(checkIdOrder(itemList, 1L, 3L, 2L)) // before sorting
        permutor.reorder(itemList)
        assertTrue(checkIdOrder(itemList, 2L, 1L, 3L)) // after sorting
    }

    @Test
    fun testPermutorForRule_FEED_TITLE_ASC() {
        val permutor = FeedItemPermutors.getPermutor(SortOrder.FEED_TITLE_A_Z)

        val itemList = getTestList()
        assertTrue(checkIdOrder(itemList, 1L, 3L, 2L)) // before sorting
        permutor.reorder(itemList)
        assertTrue(checkIdOrder(itemList, 1L, 2L, 3L)) // after sorting
    }

    @Test
    fun testPermutorForRule_FEED_TITLE_DESC() {
        val permutor = FeedItemPermutors.getPermutor(SortOrder.FEED_TITLE_Z_A)

        val itemList = getTestList()
        assertTrue(checkIdOrder(itemList, 1L, 3L, 2L)) // before sorting
        permutor.reorder(itemList)
        assertTrue(checkIdOrder(itemList, 3L, 2L, 1L)) // after sorting
    }

    @Test
    fun testPermutorForRule_FEED_TITLE_DESC_NullTitle() {
        val permutor = FeedItemPermutors.getPermutor(SortOrder.FEED_TITLE_Z_A)

        val itemList = getTestList()
        itemList[1] // itemId 3
            .getFeed()!!.setTitle(null)
        assertTrue(checkIdOrder(itemList, 1L, 3L, 2L)) // before sorting
        permutor.reorder(itemList)
        assertTrue(checkIdOrder(itemList, 2L, 1L, 3L)) // after sorting
    }

    /**
     * Generates a list with test data.
     */
    private fun getTestList(): ArrayList<FeedItem> {
        val itemList = ArrayList<FeedItem>()

        val calendar = Calendar.getInstance()
        calendar.set(2019, 0, 1)  // January 1st
        val feed1 = Feed(null, null, "Feed title 1")
        val feedItem1 = FeedItem(1L, "Title 1", null, null, calendar.getTime(), 0, feed1)
        val feedMedia1 = FeedMedia(0L, feedItem1, 1000, 0, 100, null, null, null,
                System.currentTimeMillis(), null, 0, 0L)
        feedItem1.setMedia(feedMedia1)
        itemList.add(feedItem1)

        calendar.set(2019, 2, 1)  // March 1st
        val feed2 = Feed(null, null, "Feed title 3")
        val feedItem2 = FeedItem(3L, "Title 3", null, null, calendar.getTime(), 0, feed2)
        val feedMedia2 = FeedMedia(0L, feedItem2, 3000, 0, 300, null, null, null,
                System.currentTimeMillis(), null, 0, 0L)
        feedItem2.setMedia(feedMedia2)
        itemList.add(feedItem2)

        calendar.set(2019, 1, 1)  // February 1st
        val feed3 = Feed(null, null, "Feed title 2")
        val feedItem3 = FeedItem(2L, "Title 2", null, null, calendar.getTime(), 0, feed3)
        val feedMedia3 = FeedMedia(0L, feedItem3, 2000, 0, 200, null, null, null,
                System.currentTimeMillis(), null, 0, 0L)
        feedItem3.setMedia(feedMedia3)
        itemList.add(feedItem3)

        return itemList
    }

    /**
     * Checks if both lists have the same size and the same ID order.
     *
     * @param itemList Item list.
     * @param ids      List of IDs.
     * @return `true` if both lists have the same size and the same ID order.
     */
    private fun checkIdOrder(itemList: List<FeedItem>, vararg ids: Long): Boolean {
        if (itemList.size != ids.size) {
            return false
        }

        for (i in ids.indices) {
            if (itemList[i].getId() != ids[i]) {
                return false
            }
        }
        return true
    }
}
