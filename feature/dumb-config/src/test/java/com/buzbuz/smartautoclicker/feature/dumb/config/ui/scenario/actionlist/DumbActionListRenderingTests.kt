package com.buzbuz.smartautoclicker.feature.dumb.config.ui.scenario.actionlist

import android.graphics.Point
import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction
import com.buzbuz.smartautoclicker.feature.dumb.config.R
import com.buzbuz.smartautoclicker.feature.dumb.config.ui.actions.copy.toDumbActionDetails
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class DumbActionListRenderingTests {
    @Test fun actionNameHasUsableWidthAndDoesNotOverlapIcons() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.AppTheme)
        for (widthDp in listOf(280, 360, 400)) {
            val parent = FrameLayout(context)
            val adapter = DumbActionListAdapter({}, {}, {})
            val action = DumbAction.DumbPause(
                id = Identifier(databaseId = 35), scenarioId = Identifier(databaseId = 1),
                name = "进入关卡 / Long action name", pauseDurationMs = 100,
            )
            adapter.submitList(listOf(action.toDumbActionDetails(context)))
            val holder = adapter.onCreateViewHolder(parent, 0)
            adapter.onBindViewHolder(holder, 0)
            val row = holder.itemView
            val density = context.resources.displayMetrics.density
            val width = (widthDp * density).toInt()
            row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((80 * density).toInt(), View.MeasureSpec.EXACTLY))
            row.layout(0, 0, row.measuredWidth, row.measuredHeight)
            val name = row.findViewById<TextView>(R.id.action_name)
            val handle = row.findViewById<View>(R.id.btn_reorder)
            val bookmark = row.findViewById<View>(R.id.edit_bookmark)
            val icon = row.findViewById<View>(R.id.action_type_icon)
            assertTrue("name width at $widthDp dp was ${name.width}", name.width >= 100 * density)
            assertTrue("handle overlaps name", handle.right <= name.left)
            assertTrue("name overlaps bookmark", name.right <= bookmark.left)
            assertTrue("bookmark overlaps type icon", bookmark.right <= icon.left)
            assertTrue("type icon outside row", icon.right <= width)
            for (id in listOf(R.id.action_duration, R.id.action_repeat)) {
                val text = row.findViewById<View>(id)
                if (text.visibility == View.VISIBLE) assertEquals("visible text columns must share width", name.width, text.width)
            }
            // The same layout is reused in copy/selection screens without editor controls.
            bookmark.visibility = View.GONE
            handle.visibility = View.GONE
            row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((80 * density).toInt(), View.MeasureSpec.EXACTLY))
            row.layout(0, 0, row.measuredWidth, row.measuredHeight)
            assertTrue("name width without editor controls", name.width >= 100 * density)
            assertTrue("name overlaps icon without editor controls", name.right <= icon.left)
        }
    }

    @Test fun savedAndNewActionsCanBindAndHighlightInEditor() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.AppTheme)
        val adapter = DumbActionListAdapter({}, {}, {})
        val parent = FrameLayout(context)
        val action = DumbAction.DumbPause(
            id = Identifier(databaseId = 35), scenarioId = Identifier(databaseId = 1),
            name = "Imported action", pauseDurationMs = 100,
        )
        val click = DumbAction.DumbClick(
            id = Identifier(databaseId = 36), scenarioId = action.scenarioId,
            name = "Saved click", repeatCount = 1, isRepeatInfinite = false,
            repeatDelayMs = 0, position = Point(100, 200), pressDurationMs = 50,
        )
        adapter.submitList(listOf(action.toDumbActionDetails(context), click.toDumbActionDetails(context)))
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        assertNotNull(holder.itemView.background)
        adapter.highlight(35)
        adapter.onBindViewHolder(holder, 0)
        assertTrue(holder.itemView.isActivated)
        adapter.highlight(null)
        adapter.onBindViewHolder(holder, 0)
        assertFalse(holder.itemView.isActivated)
        adapter.onBindViewHolder(holder, 1)
        assertFalse(holder.itemView.isActivated)
        // Bind a newly created click after the position picker and save callback.
        val newAction = click.copy(id = Identifier(99, asTemporary = true))
        holder.onBind(newAction.toDumbActionDetails(context)) {}
        holder.bindBookmark(false, false) {}
        assertFalse(holder.itemView.isActivated)
    }
}
