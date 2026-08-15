package de.danoeh.antennapod.parser.feed.element.namespace

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.parser.feed.FeedHandler
import java.io.File

/**
 * Tests for FeedHandler.
 */
abstract class FeedParserTestHelper {

    companion object {
        /**
         * Returns the File object for a file in the resources folder.
         */
        internal fun getFeedFile(fileName: String): File {
            //noinspection ConstantConditions
            return File(FeedParserTestHelper::class.java.classLoader.getResource(fileName).getFile())
        }

        /**
         * Runs the feed parser on the given file.
         */
        @Throws(Exception::class)
        internal fun runFeedParser(feedFile: File): Feed {
            val handler = FeedHandler()
            val parsedFeed = Feed("http://example.com/feed", null)
            parsedFeed.setLocalFileUrl(feedFile.getAbsolutePath())
            handler.parseFeed(parsedFeed)
            return parsedFeed
        }
    }
}
