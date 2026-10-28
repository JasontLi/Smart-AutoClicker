package com.buzbuz.smartautoclicker.core.dumb.engine

import android.content.Context
import android.graphics.Point
import androidx.test.core.app.ApplicationProvider
import com.buzbuz.smartautoclicker.core.common.actions.gesture.builder.AndroidGestureBuilder
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class DumbOperationDisplayTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun gesture() = AndroidGestureBuilder.createNewBuilder(null).let { builder ->
        builder.build(listOf(builder.newFinger(0, 0).press(Point(100, 200), 100).release().build()!!), "Click")!!
    }
    @Before fun clear() { context.getSharedPreferences("DumbOperationDisplay", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun disabledByDefaultAndSettingPersistsAcrossInstances() {
        val display = DumbOperationDisplay(context)
        assertFalse(display.enabled.value)
        assertNull(display.begin(gesture()))
        display.setEnabled(true)
        assertTrue(DumbOperationDisplay(context).enabled.value)
    }
    @Test fun traceKeepsExactBuiltGestureAndDoesNotMutateIt() {
        val display = DumbOperationDisplay(context)
        display.setEnabled(true)
        val gesture = gesture()
        val duration = gesture.chains[0][0].getStroke(0).duration
        val trace = display.begin(gesture)!!
        assertSame(gesture, display.trace.value!!.gesture)
        display.end(trace)
        assertNotNull(display.trace.value!!.endedAt)
        assertEquals(duration, gesture.chains[0][0].getStroke(0).duration)
    }
    @Test fun disablingOrStoppingClearsTraceAndLateCompletionCannotRestoreIt() {
        val display = DumbOperationDisplay(context)
        display.setEnabled(true)
        val trace = display.begin(gesture())
        display.setEnabled(false)
        display.end(trace)
        assertNull(display.trace.value)
        display.setEnabled(true)
        val second = display.begin(gesture())
        display.clear()
        display.end(second)
        assertNull(display.trace.value)
    }
}
