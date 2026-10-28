/*
 * Copyright (C) 2024 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.feature.dumb.config.ui.scenario.actionlist

import com.buzbuz.smartautoclicker.core.dumb.engine.DumbOperationDisplay
import android.content.Context
import android.graphics.Point
import androidx.lifecycle.ViewModel

import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.buzbuz.smartautoclicker.feature.dumb.config.domain.DumbEditionRepository
import com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions.copy.DumbActionDetails
import com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions.copy.toDumbActionDetails

import dagger.hilt.android.qualifiers.ApplicationContext
import com.buzbuz.smartautoclicker.feature.dumb.config.data.EditBookmark
import com.buzbuz.smartautoclicker.feature.dumb.config.data.EditBookmarkStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class DumbActionListViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val dumbEditionRepository: DumbEditionRepository,
    private val bookmarkStore: EditBookmarkStore,
    private val operationDisplay: DumbOperationDisplay,
) : ViewModel() {

    val autoJumpEnabled = bookmarkStore.autoJumpEnabled
    fun setAutoJumpEnabled(enabled: Boolean) = bookmarkStore.setAutoJumpEnabled(enabled)

    val operationDisplayEnabled = operationDisplay.enabled
    fun setOperationDisplayEnabled(enabled: Boolean) = operationDisplay.setEnabled(enabled)

    private val userModifications: StateFlow<DumbScenario?> =
        dumbEditionRepository.editedDumbScenario

    /** The list of dumb actions for the scenario. */
    val dumbActionsDetails: Flow<List<DumbActionDetails>> = userModifications
        .map { dumbScenario ->
            dumbScenario?.dumbActions?.map { it.toDumbActionDetails(context) } ?: emptyList()
        }

    val bookmarks: Flow<List<EditBookmark>> = userModifications
        .map { it?.id?.databaseId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id != null && id > 0) bookmarkStore.observe(id) else flowOf(emptyList()) }

    fun canBookmark(action: DumbAction): Boolean = userModifications.value?.let { scenario ->
        scenario.id.isInDatabase() && action.id.isInDatabase() && action.scenarioId == scenario.id &&
            scenario.dumbActions.any { it.id == action.id }
    } == true

    suspend fun addBookmark(action: DumbAction, name: String): Boolean {
        if (!canBookmark(action)) return false
        return bookmarkStore.add(action.scenarioId.databaseId, action.id.databaseId, name)
    }

    suspend fun deleteBookmarksForAction(action: DumbAction): Boolean {
        if (!canBookmark(action)) return false
        return bookmarkStore.deleteForAction(action.scenarioId.databaseId, action.id.databaseId)
    }

    suspend fun renameBookmark(bookmark: EditBookmark, name: String): Boolean =
        userModifications.value?.let { bookmarkStore.rename(it.id.databaseId, bookmark.id, name) } ?: false

    suspend fun deleteBookmark(bookmark: EditBookmark): Boolean =
        userModifications.value?.let { bookmarkStore.delete(it.id.databaseId, bookmark.id) } ?: false

    suspend fun retargetBookmark(bookmark: EditBookmark, action: DumbAction): Boolean {
        if (!canBookmark(action)) return false
        return bookmarkStore.retarget(action.scenarioId.databaseId, bookmark.id, action.id.databaseId)
    }

    val canCopyAction: Flow<Boolean> = dumbEditionRepository.actionsToCopy
        .map { it.isNotEmpty() }

    fun createNewDumbClick(context: Context, position: Point): DumbAction.DumbClick =
        dumbEditionRepository.dumbActionBuilder.createNewDumbClick(context, position)

    fun createNewDumbPause(context: Context): DumbAction.DumbPause =
        dumbEditionRepository.dumbActionBuilder.createNewDumbPause(context)

    fun createNewDumbGesture(context: Context): DumbAction.DumbGesture =
        dumbEditionRepository.dumbActionBuilder.createNewDumbGesture(context)

    fun createDumbActionCopy(actionToCopy: DumbAction): DumbAction =
        dumbEditionRepository.dumbActionBuilder.createNewDumbActionFrom(actionToCopy)

    fun addNewDumbAction(dumbAction: DumbAction) {
        dumbEditionRepository.addNewDumbAction(dumbAction)
    }

    fun updateDumbAction(dumbAction: DumbAction) {
        dumbEditionRepository.updateDumbAction(dumbAction)
    }

    fun updateDumbActionOrder(actions: List<DumbActionDetails>) {
        dumbEditionRepository.updateDumbActions(actions.map { it.action })
    }

    fun deleteDumbAction(dumbAction: DumbAction) {
        val actionList = userModifications.value?.dumbActions ?: return

        val actionIndex = actionList.indexOfFirst { it.id == dumbAction.id }
        if (actionIndex == -1) return

        userModifications.value?.copy(
            dumbActions = actionList.toMutableList().apply {
                removeAt(actionIndex)
            }
        )?.let {
            dumbEditionRepository.updateDumbScenario(it)
        }
    }
}