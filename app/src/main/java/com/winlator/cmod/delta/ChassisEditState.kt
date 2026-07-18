package com.winlator.cmod.delta

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Shared edit session so the EDIT toolbar can live on the game display
 * while the control deck stays free for positioning.
 */
object ChassisEditState {
    var editMode by mutableStateOf(false)
        private set

    var selectedId by mutableStateOf<String?>(null)
    var showAddMenu by mutableStateOf(false)
    var lastAction by mutableStateOf("READY")

    /** Chassis deck registers these so the display overlay can drive it. */
    var onPersist: (() -> Unit)? = null
    var onReset: (() -> Unit)? = null
    var onAddKey: ((String) -> Unit)? = null
    var onRemoveSelected: (() -> Unit)? = null
    var onSetSize: ((Float) -> Unit)? = null
    var onSetW: ((Float) -> Unit)? = null
    var onSetH: ((Float) -> Unit)? = null
    var sizeValue by mutableStateOf(1f)
    var wValue by mutableStateOf(1f)
    var hValue by mutableStateOf(1f)
    var selectedIsFreeform by mutableStateOf(false)

    fun openEdit() {
        editMode = true
        showAddMenu = false
        lastAction = "EDIT — tap a control · size on display panel"
    }

    fun closeEdit(save: Boolean = true) {
        if (save) onPersist?.invoke()
        editMode = false
        showAddMenu = false
        selectedId = null
        lastAction = if (save) "SAVED" else "CANCELLED"
    }

    fun select(id: String, freeform: Boolean, size: Float, w: Float, h: Float) {
        selectedId = id
        selectedIsFreeform = freeform
        sizeValue = size
        wValue = w
        hValue = h
        lastAction = "SEL $id"
    }
}
