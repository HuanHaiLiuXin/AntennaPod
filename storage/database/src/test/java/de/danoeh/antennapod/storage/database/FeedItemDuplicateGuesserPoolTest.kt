package de.danoeh.antennapod.storage.database

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.util.ArrayList

@RunWith(JUnit4::class)
class FeedItemDuplicateGuesserPoolTest {

    @Test
    fun testDuplicateIsConsistent() {
        val feed = Feed("url", null, null)
        val item1 = createItem("id1", "Title", feed)
        val item2 = createItem("id2", "Title", feed)

        val pool = FeedItemDuplicateGuesserPool(ArrayList())
        pool.add(item1)
        assertSame(item1, pool.guessDuplicate(item1))
        assertSame(item1, pool.guessDuplicate(item2))
        pool.add(item2)
        assertSame(item1, pool.guessDuplicate(item1))
        assertSame(item1, pool.guessDuplicate(item2))
    }

    private fun createItem(identifier: String, title: String, feed: Feed): FeedItem {
        val item = FeedItem()
        item.setItemIdentifier(identifier)
        item.setTitle(title)
        item.setMedia(FeedMedia(item, "url-" + title, 2, "mime"))
        item.setFeed(feed)
        return item
    }
}
