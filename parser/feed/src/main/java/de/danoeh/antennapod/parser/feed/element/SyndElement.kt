package de.danoeh.antennapod.parser.feed.element

import de.danoeh.antennapod.parser.feed.namespace.Namespace

/** Defines a XML Element that is pushed on the tagstack */
open class SyndElement {
    private var name: String? = null
    private var namespace: Namespace? = null

    constructor(name: String?, namespace: Namespace?) {
        this.name = name
        this.namespace = namespace
    }

    fun getNamespace(): Namespace? {
        return namespace
    }

    fun getName(): String? {
        return name
    }
}
