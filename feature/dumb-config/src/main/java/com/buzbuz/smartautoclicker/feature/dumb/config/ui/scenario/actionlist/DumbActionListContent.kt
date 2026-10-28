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

import android.content.Context
import android.widget.Toast
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.implementation.EditBookmarkDialog
import androidx.core.view.doOnNextLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.buzbuz.smartautoclicker.feature.dumb.config.data.EditBookmark
import com.buzbuz.smartautoclicker.feature.dumb.config.data.findTarget
import com.buzbuz.smartautoclicker.feature.dumb.config.databinding.ContentDumbActionsBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import android.view.LayoutInflater
import android.view.ViewGroup

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper

import com.buzbuz.smartautoclicker.core.common.overlays.dialog.implementation.navbar.NavBarDialogContent
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.implementation.navbar.viewModels
import com.buzbuz.smartautoclicker.core.ui.bindings.lists.setEmptyText
import com.buzbuz.smartautoclicker.core.ui.bindings.lists.updateState
import com.buzbuz.smartautoclicker.core.ui.databinding.IncludeLoadableListBinding
import com.buzbuz.smartautoclicker.feature.dumb.config.R
import com.buzbuz.smartautoclicker.feature.dumb.config.di.DumbConfigViewModelsEntryPoint
import com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions.DumbActionCreator
import com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions.DumbActionUiFlowListener
import com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions.copy.DumbActionDetails
import com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions.onCopyDumbActionSelected
import com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions.startDumbActionCreationUiFlow
import com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions.startDumbActionEditionUiFlow

import kotlinx.coroutines.launch

class DumbActionListContent(appContext: Context) : NavBarDialogContent(appContext) {

    /** View model for the container dialog. */
    private val viewModel: DumbActionListViewModel by viewModels(
        entryPoint = DumbConfigViewModelsEntryPoint::class.java,
        creator = { dumbActionListViewModel() },
    )

    /** View binding for all views in this content. */
    private lateinit var viewBinding: IncludeLoadableListBinding
    /** Adapter for the list of actions. */
    private lateinit var dumbActionsAdapter: DumbActionListAdapter

    private lateinit var operationSwitch: com.google.android.material.materialswitch.MaterialSwitch
    private var bookmarks: List<EditBookmark> = emptyList()
    private val entryJump = BookmarkEntryJump()
    private var actionsReady = false
    private var bookmarksReady = false
    private var pendingJump: Long? = null
    private var highlightJob: Job? = null

    private lateinit var createCopyActionUiFlowListener: DumbActionUiFlowListener
    private lateinit var updateActionUiFlowListener: DumbActionUiFlowListener
    private lateinit var dumbActionCreator: DumbActionCreator

    /** TouchHelper applied to [dumbActionsAdapter] allowing to drag and drop the items. */
    private val itemTouchHelper = ItemTouchHelper(DumbActionReorderTouchHelper())

    override fun floatingActionButtonsAreAvailable(): Boolean = true

    override fun onCreateView(container: ViewGroup): ViewGroup {
        dumbActionsAdapter = DumbActionListAdapter(
            actionClickedListener = ::onDumbActionClicked,
            actionReorderListener = viewModel::updateDumbActionOrder,
            bookmarkClickedListener = ::onBookmarkClicked,
        )

        val content = ContentDumbActionsBinding.inflate(LayoutInflater.from(context), container, false)
        operationSwitch = content.showOperationTraces
        operationSwitch.isChecked = viewModel.operationDisplayEnabled.value
        operationSwitch.setOnCheckedChangeListener { _, checked -> viewModel.setOperationDisplayEnabled(checked) }
        content.editBookmarks.setOnClickListener { debounceUserInteraction { showBookmarks() } }
        viewBinding = content.actionList.apply {
            setEmptyText(
                id = R.string.message_empty_dumb_action_list,
                secondaryId = R.string.message_empty_secondary_dumb_action_list,
            )
            list.apply {
                addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
                itemTouchHelper.attachToRecyclerView(this)
                adapter = dumbActionsAdapter
            }
        }

        return content.root
    }

    override fun onViewCreated() {
        dumbActionCreator = DumbActionCreator(
            createNewDumbClick = { position -> viewModel.createNewDumbClick(context, position) },
            createNewDumbPause = { viewModel.createNewDumbPause(context) },
            createNewDumbGesture = { viewModel.createNewDumbGesture(context) },
            createDumbActionCopy = viewModel::createDumbActionCopy,
        )
        createCopyActionUiFlowListener = DumbActionUiFlowListener(
            onDumbActionSaved = viewModel::addNewDumbAction,
            onDumbActionDeleted = {},
            onDumbActionCreationCancelled = {},
        )
        updateActionUiFlowListener = DumbActionUiFlowListener(
            onDumbActionSaved = viewModel::updateDumbAction,
            onDumbActionDeleted = viewModel::deleteDumbAction,
            onDumbActionCreationCancelled = {},
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.dumbActionsDetails.collect(::updateDumbActionList) }
                launch { viewModel.canCopyAction.collect(::updateCopyButtonState) }
                launch { viewModel.operationDisplayEnabled.collect { operationSwitch.isChecked = it } }
                launch { viewModel.bookmarks.collect {
                    bookmarks = it
                    bookmarksReady = true
                    maybeJumpOnEntry()
                    dumbActionsAdapter.updateBookmarks(it.map { bookmark -> bookmark.actionId }.toSet())
                } }
            }
        }
    }

    override fun onPrimaryFloatingActionButtonClicked() {
        debounceUserInteraction {
            dialogController.overlayManager.startDumbActionCreationUiFlow(
                context = context,
                creator = dumbActionCreator,
                listener = createCopyActionUiFlowListener,
            )
        }
    }

    override fun onSecondaryFloatingActionButtonClicked() {
        debounceUserInteraction {
            dialogController.overlayManager.onCopyDumbActionSelected(
                context = context,
                creator = dumbActionCreator,
                listener = createCopyActionUiFlowListener,
            )
        }
    }

    private fun onDumbActionClicked(dumbActionDetails: DumbActionDetails) {
        debounceUserInteraction {
            dialogController.overlayManager.startDumbActionEditionUiFlow(
                context = context,
                dumbAction = dumbActionDetails.action,
                listener = updateActionUiFlowListener,
            )
        }
    }

    private fun updateDumbActionList(newList: List<DumbActionDetails>) {
        viewBinding.updateState(newList)
        dumbActionsAdapter.submitList(newList) {
            actionsReady = true
            maybeJumpOnEntry()
            performPendingJump()
        }
    }

    private fun notify(message: Int) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    private fun open(dialog: EditBookmarkDialog) {
        dialogController.overlayManager.navigateTo(context, dialog)
    }

    private fun choose(title: String, entries: List<String>, selected: (Int) -> Unit) {
        open(EditBookmarkDialog(title = title, entries = entries, onSelected = selected))
    }

    private fun editName(title: Int, initial: String, save: suspend (String) -> Boolean) {
        open(EditBookmarkDialog(title = context.getString(title), nameLabel = context.getString(R.string.edit_bookmark_name), editedName = initial, onName = { name ->
            lifecycleScope.launch {
                if (!save(name)) notify(R.string.edit_bookmark_write_failed)
            }
        }))
    }

    private fun onBookmarkClicked(details: DumbActionDetails) {
        debounceUserInteraction {
            if (!viewModel.canBookmark(details.action)) {
                notify(R.string.edit_bookmark_unsaved)
                return@debounceUserInteraction
            }
            if (bookmarks.any { it.actionId == details.action.id.databaseId }) {
                lifecycleScope.launch {
                    if (!viewModel.deleteBookmarksForAction(details.action)) notify(R.string.edit_bookmark_write_failed)
                }
                return@debounceUserInteraction
            }
            editName(R.string.edit_bookmark_add, details.name) { name ->
                viewModel.addBookmark(details.action, name)
            }
        }
    }

    private fun targetIndex(id: Long): Int {
        val actions = dumbActionsAdapter.currentList.map { it.action }
        val scenarioId = actions.firstOrNull()?.scenarioId?.databaseId ?: return -1
        return EditBookmark("", id, "").findTarget(scenarioId, actions)
    }

    private fun showBookmarks(manage: Boolean = false) {
        val snapshot = bookmarks
        val entries = snapshot.map { bookmark ->
            val index = targetIndex(bookmark.actionId)
            val location = if (index < 0) context.getString(R.string.edit_bookmark_missing)
                else context.getString(R.string.edit_bookmark_position, index + 1, dumbActionsAdapter.currentList[index].name)
            "${bookmark.name}\n$location"
        }
        open(EditBookmarkDialog(
            title = context.getString(R.string.edit_bookmarks),
            entries = entries,
            toggleLabel = context.getString(R.string.edit_bookmark_auto_jump),
            toggleChecked = viewModel.autoJumpEnabled.value,
            onToggle = { enabled ->
                viewModel.setAutoJumpEnabled(enabled)
                if (enabled) { entryJump.reset(); maybeJumpOnEntry() }
            },
            secondaryLabel = if (manage) null else context.getString(R.string.edit_bookmark_manage),
            onSecondary = { showBookmarks(manage = true) },
            onSelected = { index ->
                val bookmark = snapshot[index]
                if (manage || targetIndex(bookmark.actionId) < 0) showBookmarkOptions(bookmark)
                else { pendingJump = bookmark.actionId; performPendingJump() }
            },
        ))
    }

    private fun showBookmarkOptions(bookmark: EditBookmark) {
        val valid = targetIndex(bookmark.actionId) >= 0
        val options = mutableListOf<Int>()
        if (valid) options.add(R.string.edit_bookmark_jump)
        options.addAll(listOf(R.string.edit_bookmark_rename, R.string.edit_bookmark_delete, R.string.edit_bookmark_retarget))
        val title = if (valid) bookmark.name else "${bookmark.name} · ${context.getString(R.string.edit_bookmark_missing)}"
        choose(title, options.map(context::getString)) { index ->
            when (options[index]) {
                R.string.edit_bookmark_jump -> { pendingJump = bookmark.actionId; performPendingJump() }
                R.string.edit_bookmark_rename -> editName(R.string.edit_bookmark_rename, bookmark.name) {
                    viewModel.renameBookmark(bookmark, it)
                }
                R.string.edit_bookmark_delete -> lifecycleScope.launch {
                    if (!viewModel.deleteBookmark(bookmark)) notify(R.string.edit_bookmark_write_failed)
                }
                R.string.edit_bookmark_retarget -> {
                    val actions = dumbActionsAdapter.currentList.filter { viewModel.canBookmark(it.action) }
                    if (actions.isEmpty()) {
                        notify(R.string.edit_bookmark_unsaved)
                    } else choose(context.getString(R.string.edit_bookmark_retarget), actions.map {
                        context.getString(R.string.edit_bookmark_position, targetIndex(it.action.id.databaseId) + 1, it.name)
                    }) { selected -> lifecycleScope.launch {
                        if (!viewModel.retargetBookmark(bookmark, actions[selected].action)) notify(R.string.edit_bookmark_write_failed)
                    } }
                }
            }
        }
    }

    override fun onStop() {
        actionsReady = false
        bookmarksReady = false
        pendingJump = null
        entryJump.reset()
        super.onStop()
    }

    private fun maybeJumpOnEntry() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        val id = entryJump.target(viewModel.autoJumpEnabled.value, actionsReady,
            if (bookmarksReady) bookmarks else null) ?: return
        pendingJump = id
        performPendingJump()
    }

    private fun performPendingJump() {
        val actionId = pendingJump ?: return
        val list = viewBinding.list
        list.post {
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || pendingJump != actionId) return@post
            val index = targetIndex(actionId)
            if (index < 0) {
                pendingJump = null
                notify(R.string.edit_bookmark_missing)
                return@post
            }
            val manager = list.layoutManager as? LinearLayoutManager ?: return@post
            list.doOnNextLayout {
                if (pendingJump != actionId) return@doOnNextLayout
                pendingJump = null
                highlightJob?.cancel()
                dumbActionsAdapter.highlight(actionId)
                highlightJob = lifecycleScope.launch {
                    delay(1800)
                    dumbActionsAdapter.highlight(null)
                }
            }
            manager.scrollToPositionWithOffset(index, list.paddingTop)
            list.requestLayout()
        }
    }

    private fun updateCopyButtonState(enabled: Boolean) {
        dialogController.floatingActionButtons.secondary.apply {
            if (enabled) show() else hide()
        }
    }
}