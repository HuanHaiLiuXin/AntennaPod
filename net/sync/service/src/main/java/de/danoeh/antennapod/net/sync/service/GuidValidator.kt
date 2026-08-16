package de.danoeh.antennapod.net.sync.service

class GuidValidator {

    companion object {
        @JvmStatic
        fun isValidGuid(guid: String?): Boolean {
            return guid != null
                    && !guid.trim().isEmpty()
                    && guid != "null"
        }
    }
}
