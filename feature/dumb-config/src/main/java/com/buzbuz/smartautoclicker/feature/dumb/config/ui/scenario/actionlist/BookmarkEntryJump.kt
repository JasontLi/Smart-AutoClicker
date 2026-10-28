package com.buzbuz.smartautoclicker.feature.dumb.config.ui.scenario.actionlist

import com.buzbuz.smartautoclicker.feature.dumb.config.data.EditBookmark

/** One entry jump after both asynchronous inputs load, never on subsequent edits or scrolling. */
internal class BookmarkEntryJump {
    private var consumed = false

    fun reset() { consumed = false }

    fun target(enabled: Boolean, actionsReady: Boolean, bookmarks: List<EditBookmark>?): Long? {
        if (consumed || !actionsReady || bookmarks == null) return null
        consumed = true
        return if (enabled) bookmarks.lastOrNull()?.actionId else null
    }
}
