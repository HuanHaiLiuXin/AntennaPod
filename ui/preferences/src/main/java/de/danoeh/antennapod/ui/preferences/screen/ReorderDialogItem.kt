package de.danoeh.antennapod.ui.preferences.screen

class ReorderDialogItem(private val viewType: ViewType, private val tag: String, private val title: String) {
    enum class ViewType {
        Section,
        Header
    }

    fun getViewType(): ViewType {
        return viewType
    }

    fun getTitle(): String {
        return title
    }

    fun getTag(): String {
        return tag
    }
}
