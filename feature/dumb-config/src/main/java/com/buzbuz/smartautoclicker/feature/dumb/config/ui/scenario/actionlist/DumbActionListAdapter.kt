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

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import com.google.android.material.color.MaterialColors
import android.view.View
import android.view.LayoutInflater
import com.buzbuz.smartautoclicker.feature.dumb.config.R
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

import com.buzbuz.smartautoclicker.feature.dumb.config.databinding.ItemDumbActionBinding
import com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions.copy.DumbActionDetails
import com.buzbuz.smartautoclicker.feature.dumb.config.ui.bindings.onBind

import java.util.Collections

class DumbActionListAdapter(
    private val actionClickedListener: (DumbActionDetails) -> Unit,
    private val actionReorderListener: (List<DumbActionDetails>) -> Unit,
    private val bookmarkClickedListener: (DumbActionDetails) -> Unit,
) : ListAdapter<DumbActionDetails, DumbActionViewHolder>(DumbActionDiffUtilCallback) {

    private var bookmarkedIds: Set<Long> = emptySet()
    private var highlightedId: Long? = null

    fun updateBookmarks(ids: Set<Long>) {
        val changed = bookmarkedIds union ids
        bookmarkedIds = ids
        currentList.forEachIndexed { index, item ->
            if (item.action.id.databaseId in changed) notifyItemChanged(index)
        }
    }

    fun highlight(id: Long?) {
        val oldId = highlightedId
        highlightedId = id
        currentList.forEachIndexed { index, item ->
            if (item.action.id.databaseId == oldId || item.action.id.databaseId == id) notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DumbActionViewHolder =
        DumbActionViewHolder(ItemDumbActionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: DumbActionViewHolder, position: Int) {
        val item = getItem(position)
        holder.onBind(item, actionClickedListener)
        holder.bindBookmark(item.action.id.databaseId in bookmarkedIds, item.action.id.databaseId == highlightedId) {
            bookmarkClickedListener(item)
        }
    }

    /**
     * Swap the position of two events in the list.
     *
     * @param from the position of the click to be moved.
     * @param to the new position of the click to be moved.
     */
    fun moveActions(from: Int, to: Int) {
        val newList = currentList.toMutableList()
        Collections.swap(newList, from, to)
        submitList(newList)
    }

    /** Notify for an item drag and drop completion. */
    fun notifyMoveFinished() {
        actionReorderListener(currentList)
    }
}

/** DiffUtil Callback comparing two ActionItem when updating the [DumbActionListAdapter] list. */
object DumbActionDiffUtilCallback: DiffUtil.ItemCallback<DumbActionDetails>() {

    override fun areItemsTheSame(
        oldItem: DumbActionDetails,
        newItem: DumbActionDetails,
    ): Boolean = oldItem.action.id == newItem.action.id

    override fun areContentsTheSame(
        oldItem: DumbActionDetails,
        newItem: DumbActionDetails,
    ): Boolean = oldItem == newItem
}

/**
 * View holder displaying an action in the [DumbActionListAdapter].
 * @param viewBinding the view binding for this item.
 */
class DumbActionViewHolder(private val viewBinding: ItemDumbActionBinding) : RecyclerView.ViewHolder(viewBinding.root) {

    /**
     * Bind this view holder as a action item.
     *
     * @param details the action to be represented by this item.
     * @param actionClickedListener listener notified upon user click on this item.
     */
    fun bindBookmark(bookmarked: Boolean, highlighted: Boolean, onClick: () -> Unit) {
        // Resolve Material colors with the row's theme before constructing the drawable.
        // A selector containing ?attr drawables can be inflated without a theme by Resources.
        viewBinding.root.background = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_activated),
                ColorDrawable(MaterialColors.getColor(viewBinding.root, R.attr.colorSecondaryContainer)),
            )
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
        viewBinding.root.isActivated = highlighted
        viewBinding.editBookmark.apply {
            visibility = View.VISIBLE
            setImageResource(if (bookmarked) R.drawable.ic_edit_bookmark_filled else R.drawable.ic_edit_bookmark_outline)
            contentDescription = context.getString(if (bookmarked) R.string.edit_bookmark_delete else R.string.edit_bookmark_add)
            setOnClickListener { onClick() }
        }
    }

    fun onBind(details: DumbActionDetails, actionClickedListener: (DumbActionDetails) -> Unit) {
        viewBinding.onBind(details, true, actionClickedListener)
    }
}

/** ItemTouchHelper attached to the adapter */
class DumbActionReorderTouchHelper : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

    /** Tells if the user is currently dragging an item. */
    private var isDragging: Boolean = false

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        isDragging = true
        (recyclerView.adapter as DumbActionListAdapter).moveActions(
            viewHolder.bindingAdapterPosition,
            target.bindingAdapterPosition
        )
        return true
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)

        if (isDragging) {
            (recyclerView.adapter as DumbActionListAdapter).notifyMoveFinished()
            isDragging = false
        }
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { /* Nothing do to */ }
}