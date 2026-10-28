package com.buzbuz.smartautoclicker.feature.dumb.config.data

import android.content.Context
import android.content.SharedPreferences
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Editor-only metadata. Never writes to the scenario database or its backup representation. */
data class EditBookmark(val id: String, val actionId: Long, val name: String)

/** IDs are the only locator: never fall back to ordinal, name, or action parameters. */
internal fun EditBookmark.findTarget(scenarioId: Long, actions: List<DumbAction>): Int =
    actions.indexOfFirst { it.scenarioId.databaseId == scenarioId && it.id.isInDatabase() && it.id.databaseId == actionId }

@Singleton
class EditBookmarkStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("DumbEditBookmarks", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _autoJumpEnabled = MutableStateFlow(preferences.getBoolean("auto_jump_enabled", true))
    val autoJumpEnabled: StateFlow<Boolean> = _autoJumpEnabled

    fun setAutoJumpEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("auto_jump_enabled", enabled).apply()
        _autoJumpEnabled.value = enabled
    }


    fun observe(scenarioId: Long): Flow<List<EditBookmark>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key(scenarioId)) trySend(read(scenarioId))
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(read(scenarioId))
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    internal fun read(scenarioId: Long): List<EditBookmark> {
        val array = JSONArray(preferences.getString(key(scenarioId), "[]"))
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            EditBookmark(item.getString("id"), item.getLong("actionId"), item.getString("name"))
        }
    }

    suspend fun add(scenarioId: Long, actionId: Long, name: String): Boolean {
        require(scenarioId > 0 && actionId > 0 && name.isNotBlank())
        return update(scenarioId) { bookmarks ->
            if (bookmarks.any { it.actionId == actionId }) bookmarks
            else bookmarks + EditBookmark(UUID.randomUUID().toString(), actionId, name.trim())
        }
    }

    suspend fun rename(scenarioId: Long, id: String, name: String): Boolean {
        require(name.isNotBlank())
        return update(scenarioId) { bookmarks -> bookmarks.map { if (it.id == id) it.copy(name = name.trim()) else it } }
    }

    suspend fun delete(scenarioId: Long, id: String): Boolean =
        update(scenarioId) { it.filterNot { bookmark -> bookmark.id == id } }

    /** Clears every bookmark for the action, including duplicates saved by older versions. */
    suspend fun deleteForAction(scenarioId: Long, actionId: Long): Boolean =
        update(scenarioId) { it.filterNot { bookmark -> bookmark.actionId == actionId } }

    suspend fun retarget(scenarioId: Long, id: String, actionId: Long): Boolean {
        require(actionId > 0)
        return update(scenarioId) { bookmarks -> bookmarks.map { if (it.id == id) it.copy(actionId = actionId) else it } }
    }

    private suspend fun update(scenarioId: Long, transform: (List<EditBookmark>) -> List<EditBookmark>): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val array = JSONArray()
                transform(read(scenarioId)).forEach { bookmark ->
                    array.put(JSONObject().put("id", bookmark.id).put("actionId", bookmark.actionId).put("name", bookmark.name))
                }
                // Report success only after the independent metadata has reached persistent storage.
                preferences.edit().putString(key(scenarioId), array.toString()).commit()
            }
        }

    private fun key(scenarioId: Long) = "scenario_$scenarioId"
}
