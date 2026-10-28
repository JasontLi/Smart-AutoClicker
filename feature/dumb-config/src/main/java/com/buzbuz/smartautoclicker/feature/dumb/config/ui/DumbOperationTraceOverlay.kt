package com.buzbuz.smartautoclicker.feature.dumb.config.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.buzbuz.smartautoclicker.core.dumb.engine.DumbOperationTrace

/** Passive full-screen accessibility overlay: no touch target, focus, or gesture injection. */
internal class DumbOperationTraceOverlay(private val context: Context) {
    private val windows = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: TraceView? = null

    fun show(trace: DumbOperationTrace?) {
        if (trace == null) { close(); return }
        val target = view ?: TraceView(context) { expired ->
            if (view?.trace === expired) close()
        }.also { candidate ->
            try {
                windows.addView(candidate, windowParameters())
                view = candidate
            } catch (error: RuntimeException) {
                Log.w("DumbOperationTrace", "Cannot show operation trace", error)
            }
        }
        if (view === target) target.update(trace)
    }

    fun close() {
        val previous = view ?: return
        view = null
        previous.trace = null
        try { windows.removeViewImmediate(previous) }
        catch (error: RuntimeException) { Log.w("DumbOperationTrace", "Cannot remove operation trace", error) }
    }

    companion object {
        internal fun windowParameters() = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        }
    }
}

internal class TraceView(context: Context, private val onExpired: (DumbOperationTrace) -> Unit) : View(context) {
    var trace: DumbOperationTrace? = null
    private data class Stroke(val measure: PathMeasure, val start: Long, val duration: Long, val color: Int, val x: Float, val y: Float)
    private var strokes: List<Stroke> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private val visiblePath = Path()
    private val point = FloatArray(2)
    private val location = IntArray(2)
    private val radius = 12 * resources.displayMetrics.density

    fun update(value: DumbOperationTrace) {
        if (trace?.gesture !== value.gesture || trace?.startedAt != value.startedAt) {
            var chainOffset = 0L
            val result = mutableListOf<Stroke>()
            value.gesture.chains.forEach { chain ->
                chain.forEach { gesture ->
                    var duration = 0L
                    for (index in 0 until gesture.strokeCount) {
                        val stroke = gesture.getStroke(index)
                        val path = stroke.path
                        val measure = PathMeasure(path, false)
                        val bounds = RectF().also { path.computeBounds(it, true) }
                        val approximation = if (Build.VERSION.SDK_INT >= 26) path.approximate(0.5f) else floatArrayOf()
                        val x = if (approximation.size >= 3) approximation[1] else bounds.left
                        val y = if (approximation.size >= 3) approximation[2] else bounds.top
                        result.add(Stroke(measure, chainOffset + stroke.startTime,
                            stroke.duration, COLORS[index % COLORS.size], x, y))
                        val consumed = if (Build.VERSION.SDK_INT >= 26 && stroke.willContinue() && measure.length == 0f)
                            stroke.startTime else stroke.startTime + stroke.duration
                        duration = maxOf(duration, consumed)
                    }
                    chainOffset += duration
                }
            }
            strokes = result
        }
        trace = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = trace ?: return
        val now = SystemClock.uptimeMillis()
        val age = current.endedAt?.let { now - it } ?: 0L
        if (current.endedAt != null && age >= 1000) {
            post { if (trace === current) onExpired(current) }
            return
        }
        val opacity = if (current.endedAt == null) 1f else (1 - age / 1000f).coerceIn(0f, 1f)
        getLocationOnScreen(location)
        canvas.save()
        canvas.translate(-location[0].toFloat(), -location[1].toFloat())
        val elapsed = now - current.startedAt
        strokes.forEach { stroke ->
            if (elapsed < stroke.start) return@forEach
            val fraction = ((elapsed - stroke.start).toFloat() / stroke.duration.coerceAtLeast(1)).coerceIn(0f, 1f)
            paint.color = stroke.color
            paint.alpha = (opacity * 230).toInt()
            visiblePath.reset()
            if (stroke.measure.length > 0) {
                stroke.measure.getSegment(0f, stroke.measure.length * fraction, visiblePath, true)
                canvas.drawPath(visiblePath, paint)
            }
            if (stroke.measure.getPosTan(stroke.measure.length * fraction, point, null)) {
                canvas.drawCircle(point[0], point[1], radius, paint)
            } else {
                canvas.drawCircle(stroke.x, stroke.y, radius, paint)
            }
        }
        canvas.restore()
        postInvalidateOnAnimation()
    }

    companion object {
        private val COLORS = intArrayOf(Color.rgb(0, 170, 255), Color.rgb(255, 153, 0), Color.rgb(180, 80, 255))
    }
}
