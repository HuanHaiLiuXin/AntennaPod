package de.danoeh.antennapod.parser.feed

class UnsupportedFeedtypeException : Exception {
    private var rootElement: String? = null
    private var msg: String? = null

    constructor(rootElement: String?, message: String?) : super() {
        this.rootElement = rootElement
        this.msg = message
    }

    constructor(message: String?) : super() {
        this.msg = message
    }

    fun getRootElement(): String? {
        return rootElement
    }

    override val message: String
        get() {
            if (msg != null) {
                return msg!!
            } else if (rootElement != null) {
                return "Server returned " + rootElement
            } else {
                return "Unknown type"
            }
        }

    companion object {
        private const val serialVersionUID = 9105878964928170669L
    }
}
