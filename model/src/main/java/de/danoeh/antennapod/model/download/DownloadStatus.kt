package de.danoeh.antennapod.model.download

class DownloadStatus {
    private val state: Int
    private val progress: Int

    constructor(state: Int, progress: Int) {
        this.state = state
        this.progress = progress
    }

    fun getState(): Int {
        return state
    }

    fun getProgress(): Int {
        return progress
    }

    companion object {
        const val STATE_QUEUED = 0
        const val STATE_COMPLETED = 1 // Both successful and not successful
        const val STATE_RUNNING = 2
    }
}
