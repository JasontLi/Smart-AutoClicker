package com.buzbuz.smartautoclicker.feature.dumb.config.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class EditBookmarkStoreTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun action(id: Long, scenario: Long = 1) = DumbAction.DumbPause(
        id = Identifier(databaseId = id), scenarioId = Identifier(databaseId = scenario), name = "Same name", pauseDurationMs = 100,
    )

    @Before fun clear() {
        context.getSharedPreferences("DumbEditBookmarks", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun persistsMultipleBookmarksAcrossStoreRecreation() = runBlocking {
        val store = EditBookmarkStore(context)
        assertTrue(store.add(1, 35, "Battle"))
        assertTrue(store.add(1, 40, "Reward"))
        val reopened = EditBookmarkStore(context).read(1)
        assertEquals(listOf(35L, 40L), reopened.map { it.actionId })
        assertEquals(listOf("Battle", "Reward"), reopened.map { it.name })
        assertNotEquals(reopened[0].id, reopened[1].id)
    }

    @Test fun renameDeleteAndRetargetAreScopedToOneScenario() = runBlocking {
        val store = EditBookmarkStore(context)
        store.add(1, 35, "Old")
        store.add(2, 50, "Copy")
        val bookmark = store.read(1).single()
        assertTrue(store.rename(1, bookmark.id, "New"))
        assertTrue(store.retarget(1, bookmark.id, 40))
        assertEquals(EditBookmark(bookmark.id, 40, "New"), store.read(1).single())
        assertEquals("Copy", store.read(2).single().name)
        assertTrue(store.delete(1, bookmark.id))
        assertTrue(store.read(1).isEmpty())
        assertEquals(1, store.read(2).size)
    }

    @Test fun insertionDeletionAndReorderingPreserveIdentityWithoutChangingActions() {
        val bookmark = EditBookmark("bookmark", 35, "Target")
        val target = action(35)
        val original = listOf(action(10), target, action(40))
        assertEquals(1, bookmark.findTarget(1, original))
        assertEquals(2, bookmark.findTarget(1, listOf(action(5)) + original))
        assertEquals(0, bookmark.findTarget(1, original.drop(1)))
        assertEquals(1, bookmark.findTarget(1, original.reversed()))
        assertEquals(target, original[1])
        assertEquals(100L, target.pauseDurationMs)
    }

    @Test fun deletedTargetNeverResolvesToSameOrdinalNameOrParameters() {
        val bookmark = EditBookmark("bookmark", 35, "Target")
        assertEquals(-1, bookmark.findTarget(1, listOf(action(10), action(36), action(40))))
        assertEquals(-1, bookmark.findTarget(1, listOf(action(35, scenario = 2))))
        val temporary = action(36).copy(id = Identifier(35, asTemporary = true))
        assertEquals(-1, bookmark.findTarget(1, listOf(temporary)))
    }

    @Test fun copiesAndImportsWithNewIdsDoNotInheritBookmarks() = runBlocking {
        val store = EditBookmarkStore(context)
        store.add(1, 35, "Original")
        assertTrue(store.read(2).isEmpty())
        assertEquals(-1, store.read(1).single().findTarget(2, listOf(action(50, 2))))
    }

    @Test fun autoJumpDefaultsOnAndUserPreferencePersistsWithoutChangingBookmarks() = runBlocking {
        val store = EditBookmarkStore(context)
        store.add(1, 35, "Original")
        val before = store.read(1)
        assertTrue(store.autoJumpEnabled.value)
        store.setAutoJumpEnabled(false)
        assertFalse(EditBookmarkStore(context).autoJumpEnabled.value)
        assertEquals(before, store.read(1))
        store.setAutoJumpEnabled(true)
        assertTrue(EditBookmarkStore(context).autoJumpEnabled.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun temporaryIdsCannotBePersisted() { runBlocking { EditBookmarkStore(context).add(1, 0, "Temporary") } }
}
