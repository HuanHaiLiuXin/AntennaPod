package de.danoeh.antennapod.net.sync.serviceinterface

class SyncServiceException : Exception {
    companion object {
        private const val serialVersionUID = 1L
    }

    constructor(message: String) : super(message) {
    }

    constructor(cause: Throwable) : super(cause) {
    }
}
