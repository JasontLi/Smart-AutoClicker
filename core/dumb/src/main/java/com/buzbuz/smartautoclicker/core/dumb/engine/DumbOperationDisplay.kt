package com.buzbuz.smartautoclicker.core.dumb.engine

import android.content.Context
import android.os.SystemClock
import com.buzbuz.smartautoclicker.core.common.actions.model.BuiltGesture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Observation of submitted ordinary-scenario gestures; never changes dispatch or waits for UI. */
data class DumbOperationTrace(
    val gesture: BuiltGesture,
    val startedAt: Long,
    val endedAt: Long? = null,
)

@Singleton
class DumbOperationDisplay @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("DumbOperationDisplay", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(preferences.getBoolean("enabled", false))
    val enabled: StateFlow<Boolean> = _enabled
    private val _trace = MutableStateFlow<DumbOperationTrace?>(null)
    val trace: StateFlow<DumbOperationTrace?> = _trace

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("enabled", enabled).apply()
        _enabled.value = enabled
        if (!enabled) clear()
    }

    fun begin(gesture: BuiltGesture): DumbOperationTrace? {
        if (!_enabled.value) return null
        return DumbOperationTrace(gesture, SystemClock.uptimeMillis()).also { _trace.value = it }
    }

    fun end(trace: DumbOperationTrace?) {
        if (trace != null && _trace.value === trace) _trace.value = trace.copy(endedAt = SystemClock.uptimeMillis())
    }

    fun clear() { _trace.value = null }
}
