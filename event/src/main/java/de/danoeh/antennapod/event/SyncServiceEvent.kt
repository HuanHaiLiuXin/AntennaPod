package de.danoeh.antennapod.event

class SyncServiceEvent {
    private val messageResId: Int

    constructor(messageResId: Int) {
        this.messageResId = messageResId
    }

    fun getMessageResId(): Int {
        return messageResId
    }
}
