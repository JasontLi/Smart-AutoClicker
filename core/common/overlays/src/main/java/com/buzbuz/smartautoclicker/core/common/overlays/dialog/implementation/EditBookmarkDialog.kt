package com.buzbuz.smartautoclicker.core.common.overlays.dialog.implementation

import com.google.android.material.materialswitch.MaterialSwitch
import android.text.InputFilter
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.buzbuz.smartautoclicker.core.common.overlays.base.BaseOverlay
import com.buzbuz.smartautoclicker.core.common.overlays.manager.OverlayManager
import com.buzbuz.smartautoclicker.core.common.overlays.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Uses the same overlay navigation/lifecycle as the existing MoveToDialog. */
class EditBookmarkDialog(
    private val title: String,
    private val nameLabel: String = title,
    private val entries: List<String>? = null,
    private var editedName: String = "",
    private val onSelected: (Int) -> Unit = {},
    private val onName: (String) -> Unit = {},
    private val secondaryLabel: String? = null,
    private val onSecondary: () -> Unit = {},
    private val toggleLabel: String? = null,
    private var toggleChecked: Boolean = false,
    private val onToggle: (Boolean) -> Unit = {},
) : BaseOverlay(R.style.AppTheme, recreateOnRotation = true) {
    private var dialog: AlertDialog? = null

    override fun onCreate() {
        val builder = MaterialAlertDialogBuilder(context).setTitle(title)
            .setNegativeButton(android.R.string.cancel) { _, _ -> back() }
            .setOnCancelListener { back() }
            .setOnKeyListener { _, key, event ->
                if (key == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    back()
                    true
                } else false
            }
        if (secondaryLabel != null) builder.setNeutralButton(secondaryLabel) { _, _ -> back(); onSecondary() }
        if (toggleLabel != null) {
            builder.setView(MaterialSwitch(context).apply {
                text = toggleLabel
                val padding = (24 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding / 2, padding, padding / 2)
                isChecked = toggleChecked
                setOnCheckedChangeListener { _, checked -> toggleChecked = checked; onToggle(checked) }
            })
        }
        if (entries != null) {
            builder.setItems(entries.toTypedArray()) { _, index -> back(); onSelected(index) }
        } else {
            val field = TextInputEditText(context).apply {
                setSingleLine()
                filters = arrayOf(InputFilter.LengthFilter(100))
                setText(editedName)
                doAfterTextChanged {
                    editedName = it.toString()
                    dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = editedName.isNotBlank()
                }
            }
            val layout = TextInputLayout(context).apply {
                hint = nameLabel
                val padding = (24 * resources.displayMetrics.density).toInt()
                setPadding(padding, 0, padding, 0)
                addView(field)
            }
            builder.setView(layout).setPositiveButton(android.R.string.ok) { _, _ ->
                back()
                onName(editedName.trim())
            }
        }
        dialog = builder.create().apply { window?.setType(OverlayManager.OVERLAY_WINDOW_TYPE) }
    }

    override fun onStart() {
        dialog?.show()
        if (entries == null) dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = editedName.isNotBlank()
    }
    override fun onStop() { dialog?.hide() }
    override fun onDestroy() { dialog?.dismiss(); dialog = null }
}
