package de.danoeh.antennapod.net.sync.gpoddernet

class GpodnetServiceBadStatusCodeException : GpodnetServiceException {
    private val statusCode: Int

    constructor(message: String, statusCode: Int) : super(message) {
        this.statusCode = statusCode
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
