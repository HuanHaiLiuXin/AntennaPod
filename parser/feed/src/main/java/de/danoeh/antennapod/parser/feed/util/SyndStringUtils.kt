package de.danoeh.antennapod.parser.feed.util

class SyndStringUtils private constructor() {

    companion object {
        /**
         * Trims all whitespace from beginning and ending of a String. {{@link String#trim()}} only trims spaces.
         */
        @JvmStatic
        fun trimAllWhitespace(string: String?): String {
            return string!!.replace(Regex("(^\\s*)|(\\s*$)"), "")
        }
    }
}
