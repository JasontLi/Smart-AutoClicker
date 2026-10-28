package com.buzbuz.smartautoclicker.feature.dumb.config.ui.scenario.actionlist

import com.buzbuz.smartautoclicker.feature.dumb.config.data.EditBookmark
import org.junit.Assert.*
import org.junit.Test

class BookmarkEntryJumpTests {
    private val bookmarks = listOf(EditBookmark("first", 35, "First"), EditBookmark("last", 70, "Last"))

    @Test fun waitsForActionsAndBookmarksInEitherArrivalOrder() {
        for (actionsFirst in listOf(true, false)) {
            val jump = BookmarkEntryJump()
            assertNull(jump.target(true, actionsFirst, if (actionsFirst) null else bookmarks))
            assertEquals(70L, jump.target(true, true, bookmarks))
        }
    }
    @Test fun jumpsOnlyOnceUntilEditorPageIsEnteredAgain() {
        val jump = BookmarkEntryJump()
        assertEquals(70L, jump.target(true, true, bookmarks))
        assertNull(jump.target(true, true, bookmarks + EditBookmark("new", 90, "New")))
        jump.reset()
        assertEquals(90L, jump.target(true, true, bookmarks + EditBookmark("new", 90, "New")))
    }
    @Test fun disabledAndEmptyScenariosDoNotJumpWhenLaterEdited() {
        val jump = BookmarkEntryJump()
        assertNull(jump.target(false, true, bookmarks))
        assertNull(jump.target(true, true, bookmarks))
        jump.reset()
        assertNull(jump.target(true, true, emptyList()))
        assertNull(jump.target(true, true, bookmarks))
    }
    @Test fun lastMeansInsertionOrderRatherThanHighestActionIdOrRenamedBookmark() {
        val jump = BookmarkEntryJump()
        assertEquals(35L, jump.target(true, true, bookmarks.reversed()))
        jump.reset()
        assertEquals(70L, jump.target(true, true, bookmarks.map { it.copy(name = "Renamed") }))
    }
}
