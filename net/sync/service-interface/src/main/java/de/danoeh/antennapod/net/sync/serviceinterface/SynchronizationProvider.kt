package de.danoeh.antennapod.net.sync.serviceinterface

enum class SynchronizationProvider(private val identifier: String) {
    GPODDER_NET("GPODDER_NET"),
    NEXTCLOUD_GPODDER("NEXTCLOUD_GPODDER");

    fun getIdentifier(): String {
        return identifier
    }

    companion object {
        @JvmStatic
        fun fromIdentifier(provider: String?): SynchronizationProvider? {
            for (synchronizationProvider in SynchronizationProvider.values()) {
                if (synchronizationProvider.getIdentifier() == provider) {
                    return synchronizationProvider
                }
            }
            return null
        }
    }
}
