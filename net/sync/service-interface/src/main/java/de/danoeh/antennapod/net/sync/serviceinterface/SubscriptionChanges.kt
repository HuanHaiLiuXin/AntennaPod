package de.danoeh.antennapod.net.sync.serviceinterface

class SubscriptionChanges {
    private val added: List<String>
    private val removed: List<String>
    private val timestamp: Long

    constructor(added: List<String>,
                removed: List<String>,
                timestamp: Long) {
        this.added = added
        this.removed = removed
        this.timestamp = timestamp
    }

    override fun toString(): String {
        return "SubscriptionChange [added=" + added.toString() +
                ", removed=" + removed.toString() + ", timestamp=" +
                timestamp + "]"
    }

    fun getAdded(): List<String> {
        return added
    }

    fun getRemoved(): List<String> {
        return removed
    }

    fun getTimestamp(): Long {
        return timestamp
    }

}
