package de.danoeh.antennapod.net.sync.gpoddernet.model

import java.util.Locale

class GpodnetDevice {
    private var id: String = ""
    private var caption: String? = null
    private var type: DeviceType = DeviceType.OTHER
    private var subscriptions: Int = 0

    constructor(id: String, caption: String?, type: String?, subscriptions: Int) {
        this.id = id
        this.caption = caption
        this.type = DeviceType.fromString(type)
        this.subscriptions = subscriptions
    }

    override fun toString(): String {
        return "GpodnetDevice [id=" + id + ", caption=" + caption + ", type=" +
                type + ", subscriptions=" + subscriptions + "]"
    }

    enum class DeviceType {
        DESKTOP, LAPTOP, MOBILE, SERVER, OTHER;

        override fun toString(): String {
            return super.toString().lowercase(Locale.US)
        }

        companion object {
            fun fromString(s: String?): DeviceType {
                if (s == null) {
                    return OTHER
                }

                when (s) {
                    "desktop" -> return DESKTOP
                    "laptop" -> return LAPTOP
                    "mobile" -> return MOBILE
                    "server" -> return SERVER
                    else -> return OTHER
                }
            }
        }
    }

    fun getId(): String {
        return id
    }

    fun getCaption(): String? {
        return caption
    }

    fun getType(): DeviceType {
        return type
    }

    fun getSubscriptions(): Int {
        return subscriptions
    }

}
