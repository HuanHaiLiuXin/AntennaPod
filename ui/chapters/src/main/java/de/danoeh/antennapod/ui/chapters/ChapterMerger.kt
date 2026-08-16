package de.danoeh.antennapod.ui.chapters

import android.text.TextUtils
import android.util.Log
import de.danoeh.antennapod.model.feed.Chapter

import kotlin.math.abs

class ChapterMerger private constructor() {
    companion object {
        private const val TAG = "ChapterMerger"

        /**
         * This method might modify the input data.
         */
        @JvmStatic
        fun merge(chapters1: List<Chapter>?, chapters2: List<Chapter>?): List<Chapter>? {
            Log.d(TAG, "Merging chapters")
            if (chapters1 == null) {
                return chapters2
            } else if (chapters2 == null) {
                return chapters1
            } else if (chapters2.size > chapters1.size) {
                return chapters2
            } else if (chapters2.size < chapters1.size) {
                return chapters1
            } else {
                // Merge chapter lists of same length. Store in chapters2 array.
                // In case the lists can not be merged, return chapters1 array.
                for (i in chapters2.indices) {
                    val chapterTarget = chapters2[i]
                    val chapterOther = chapters1[i]

                    if (abs(chapterTarget.getStart() - chapterOther.getStart()) > 1000) {
                        Log.e(TAG, "Chapter lists are too different. Cancelling merge.")
                        return if (score(chapters1) > score(chapters2)) chapters1 else chapters2
                    }

                    if (TextUtils.isEmpty(chapterTarget.getImageUrl())) {
                        chapterTarget.setImageUrl(chapterOther.getImageUrl())
                    }
                    if (TextUtils.isEmpty(chapterTarget.getLink())) {
                        chapterTarget.setLink(chapterOther.getLink())
                    }
                    if (TextUtils.isEmpty(chapterTarget.getTitle())) {
                        chapterTarget.setTitle(chapterOther.getTitle())
                    }
                }
                return chapters2
            }
        }

        /**
         * Tries to give a score that can determine which list of chapters a user might want to see.
         */
        private fun score(chapters: List<Chapter>): Int {
            var score = 0
            for (chapter in chapters) {
                score = score
                        + (if (TextUtils.isEmpty(chapter.getTitle())) 0 else 1)
                        + (if (TextUtils.isEmpty(chapter.getLink())) 0 else 1)
                        + (if (TextUtils.isEmpty(chapter.getImageUrl())) 0 else 1)
            }
            return score
        }
    }
}
