package com.buzbuz.smartautoclicker.feature.dumb.config.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.os.SystemClock
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.buzbuz.smartautoclicker.core.common.actions.gesture.builder.AndroidGestureBuilder
import com.buzbuz.smartautoclicker.core.dumb.engine.DumbOperationTrace
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DumbOperationTraceTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun gesture(swipe: Boolean) = AndroidGestureBuilder.createNewBuilder(null).let { builder ->
        val finger = builder.newFinger(0, 0).press(Point(100, 100), if (swipe) 0 else 100)
        if (swipe) finger.move(Point(300, 100), 1000)
        builder.build(listOf(finger.release().build()!!), "Test")!!
    }
    private fun draw(trace: DumbOperationTrace): Bitmap {
        val view = TraceView(context) {}
        view.layout(0, 0, 400, 300)
        view.update(trace)
        return Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }
    private fun colored(bitmap: Bitmap, from: Int, to: Int) = (from..to).sumOf { x ->
        (60..140).count { y -> bitmap.getPixel(x, y) ushr 24 != 0 }
    }
    @Test fun windowNeverConsumesTouchOrFocus() {
        val params = DumbOperationTraceOverlay.windowParameters()
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, params.type)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
    }
    @Test fun stationaryClickHasVisibleCircleAtSubmittedPosition() {
        val bitmap = draw(DumbOperationTrace(gesture(false), SystemClock.uptimeMillis()))
        assertTrue(colored(bitmap, 60, 140) > 0)
        assertEquals(0, colored(bitmap, 250, 350))
    }
    @Test fun swipeOnlyDrawsElapsedPortionAndOldTraceDisappears() {
        val gesture = gesture(true)
        val now = SystemClock.uptimeMillis()
        val bitmap = draw(DumbOperationTrace(gesture, now - 500))
        assertTrue(colored(bitmap, 100, 200) > 0)
        assertEquals(0, colored(bitmap, 270, 330))
        val expired = draw(DumbOperationTrace(gesture, now - 3000, endedAt = now - 1500))
        assertEquals(0, colored(expired, 0, 399))
    }
}
