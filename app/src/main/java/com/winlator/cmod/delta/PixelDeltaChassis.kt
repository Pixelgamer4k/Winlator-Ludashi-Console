package com.winlator.cmod.delta

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.inputcontrols.ExternalController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

private val LocalChassisHaptics = staticCompositionLocalOf<ChassisHaptics?> { null }
private val LocalPressRouter = staticCompositionLocalOf<ChassisPressRouter?> { null }

/**
 * Unified Delta chassis:
 * - Deck fills the entire chassis (rounded pad top fully visible)
 * - EDIT chip sits on the pad top-right (edit tools still on the display)
 * - LT/RT/LB/RB + RS pad: freeform W×H reshape via sliders + corner drag
 * - Other controls: size only (uniform)
 * - ADD KEYS from edit overlay; mouse pad removed
 */

private data class ControlDef(
    val id: String,
    val kind: ControlKind,
    val defaultFrac: Offset,
    val label: String = "",
    val color: Color = Color.White,
    val vk: Int = 0,
)

private enum class ControlKind {
    L1, L2, R1, R2,
    GUIDE, BACK, SHARE,
    FACE_A, FACE_B, FACE_X, FACE_Y,
    FN1, FN2, FN3, SLIDER,
    STICK, DPAD, TRACKPAD_RS,
    KEY,
}

/** L/R + RS pad support freeform width/height reshape */
private fun freeformIds() = setOf("l1", "l2", "r1", "r2", "trackpad_rs")

/** Control widget outline (play mode) */
private val ControlBorderYellow = Color(0xFFFFB300)
/** Edit-mode selection halo (selected = brighter blue) */
private val EditHaloBlue = Color(0xFF3B82F6)
private val EditHaloBlueSel = Color(0xFF60A5FA)

/**
 * Default control panel — shoulders inset so rounded pad corners stay fully
 * visible; layout matches the clean Xbox-style portrait chassis.
 */
private fun defaultControls(): List<ControlDef> = listOf(
    // Shoulders — bumpers (LB/RB) on top, triggers (LT/RT) below
    ControlDef("l1", ControlKind.L1, Offset(0.04f, 0.055f), "LB"),
    ControlDef("l2", ControlKind.L2, Offset(0.20f, 0.195f), "LT"),
    ControlDef("r2", ControlKind.R2, Offset(0.58f, 0.105f), "RT"),
    ControlDef("r1", ControlKind.R1, Offset(0.76f, 0.055f), "RB"),
    // Back arrow + Start (hamburger) — top center
    ControlDef("back", ControlKind.BACK, Offset(0.36f, 0.070f), "BACK"),
    ControlDef("xbox", ControlKind.GUIDE, Offset(0.48f, 0.065f), "START"),
    // Face buttons — separate controls (still slide-linked via ChassisPressRouter)
    ControlDef("y", ControlKind.FACE_Y, Offset(0.70f, 0.250f), "Y", Color(0xFFD9C84A)),
    ControlDef("x", ControlKind.FACE_X, Offset(0.58f, 0.360f), "X", Color(0xFF6FA8DC)),
    ControlDef("b", ControlKind.FACE_B, Offset(0.82f, 0.360f), "B", Color(0xFFD9534F)),
    ControlDef("a", ControlKind.FACE_A, Offset(0.70f, 0.470f), "A", Color(0xFF7BC96F)),
    // Left D-pad — spaced above the stick to avoid cross-taps
    ControlDef("dpad", ControlKind.DPAD, Offset(0.055f, 0.250f), "D-PAD"),
    // Stick lower-left — clear vertical gap from D-pad
    ControlDef("joystick", ControlKind.STICK, Offset(0.07f, 0.700f)),
    ControlDef("ls", ControlKind.FN1, Offset(0.36f, 0.520f), "LS"),
    ControlDef("rs", ControlKind.FN2, Offset(0.44f, 0.720f), "RS"),
    // RS pad
    ControlDef("trackpad_rs", ControlKind.TRACKPAD_RS, Offset(0.52f, 0.600f)),
)

private fun keyCatalog(): List<ControlDef> {
    val keys = mutableListOf<ControlDef>()
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ".forEachIndexed { i, c ->
        keys += ControlDef(
            id = "key_$c",
            kind = ControlKind.KEY,
            defaultFrac = Offset(0.08f + (i % 8) * 0.11f, 0.55f + (i / 8) * 0.08f),
            label = c.toString(),
            vk = c.code
        )
    }
    "1234567890".forEachIndexed { i, c ->
        keys += ControlDef(
            id = "key_$c",
            kind = ControlKind.KEY,
            defaultFrac = Offset(0.08f + i * 0.09f, 0.82f),
            label = c.toString(),
            vk = c.code
        )
    }
    keys += ControlDef("key_esc", ControlKind.KEY, Offset(0.05f, 0.90f), "ESC", vk = Vk.ESCAPE)
    keys += ControlDef("key_tab", ControlKind.KEY, Offset(0.18f, 0.90f), "TAB", vk = Vk.TAB)
    keys += ControlDef("key_spc", ControlKind.KEY, Offset(0.35f, 0.90f), "SPC", vk = Vk.SPACE)
    keys += ControlDef("key_ent", ControlKind.KEY, Offset(0.55f, 0.90f), "ENT", vk = Vk.RETURN)
    keys += ControlDef("key_bksp", ControlKind.KEY, Offset(0.72f, 0.90f), "⌫", vk = Vk.BACK)
    "QWERTYUIOP".forEachIndexed { i, c ->
        keys += ControlDef("key_$c", ControlKind.KEY, Offset(0.08f + i * 0.09f, 0.48f), c.toString(), vk = c.code)
    }
    return keys
}

@Composable
fun PixelDeltaChassis(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = rememberChassisHaptics()
    val prefs = remember { context.getSharedPreferences("delta_pc_layout_v10", Context.MODE_PRIVATE) }

    val dragOffsets = remember { mutableStateMapOf<String, Offset>() }
    val sizeScales = remember { mutableStateMapOf<String, Float>() } // uniform scale for non-freeform
    val wScales = remember { mutableStateMapOf<String, Float>() }    // freeform width
    val hScales = remember { mutableStateMapOf<String, Float>() }    // freeform height
    val enabledIds = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(Unit) {
        loadLayout(prefs, dragOffsets, sizeScales, wScales, hScales, enabledIds)
        val coreIds = defaultControls().map { it.id }.toSet()
        // v12: restore separate A/B/X/Y (drop clustered "face" control)
        val isFreshSeparate = !prefs.getBoolean("face_separate_v12", false)
        if (isFreshSeparate) {
            enabledIds["face"] = false
            dragOffsets.remove("face")
            listOf("a", "b", "x", "y").forEach { dragOffsets.remove(it) }
            prefs.edit().putBoolean("face_separate_v12", true).apply()
        }
        // v13: ensure left D-pad is present/enabled for existing layouts
        val isFreshDpad = !prefs.getBoolean("dpad_added_v13", false)
        if (isFreshDpad) {
            enabledIds["dpad"] = true
            dragOffsets.remove("dpad")
            sizeScales["dpad"] = 1f
            prefs.edit().putBoolean("dpad_added_v13", true).apply()
        }
        // v14: modern D-pad + clearer left-side separation (reset related offsets)
        val isFreshDpadLayout = !prefs.getBoolean("dpad_layout_v14", false)
        if (isFreshDpadLayout) {
            listOf("dpad", "joystick", "ls", "rs").forEach {
                dragOffsets.remove(it)
                sizeScales[it] = 1f
            }
            enabledIds["dpad"] = true
            prefs.edit().putBoolean("dpad_layout_v14", true).apply()
        }
        val isFreshDefault = !prefs.getBoolean("defaults_applied_v10", false)
        if (isFreshDefault) {
            dragOffsets.clear()
            sizeScales.clear()
            wScales.clear()
            hScales.clear()
            enabledIds.clear()
            prefs.edit()
                .putBoolean("defaults_applied_v10", true)
                .putBoolean("face_separate_v12", true)
                .putBoolean("dpad_added_v13", true)
                .putBoolean("dpad_layout_v14", true)
                .apply()
        }
        defaultControls().forEach { def ->
            enabledIds[def.id] = true
            if (!sizeScales.containsKey(def.id)) sizeScales[def.id] = 1f
            if (def.id in freeformIds()) {
                if (!wScales.containsKey(def.id)) wScales[def.id] = 1f
                if (!hScales.containsKey(def.id)) hScales[def.id] = 1f
            }
            if (isFreshDefault || isFreshSeparate || isFreshDpad || isFreshDpadLayout || !prefs.contains("off_${def.id}")) {
                dragOffsets.remove(def.id)
            }
        }
        // Never show removed controls
        listOf("fn1", "fn2", "fn3", "share", "slider", "mouse_pad", "face").forEach {
            enabledIds[it] = false
            dragOffsets.remove(it)
        }
        // Keys stay as user enabled them (after first v10 apply)
        if (!isFreshDefault) {
            enabledIds.keys.filter { it.startsWith("key_") && it !in coreIds }.forEach { /* keep */ }
        }
    }

    var padVx by remember { mutableFloatStateOf(0f) }
    var padVy by remember { mutableFloatStateOf(0f) }
    var padHeld by remember { mutableStateOf(false) }
    var stickX by remember { mutableFloatStateOf(0f) }
    var stickY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            if (!padHeld) {
                padVx *= 0.90f
                padVy *= 0.90f
                if (hypot(padVx.toDouble(), padVy.toDouble()) < 0.02) {
                    padVx = 0f; padVy = 0f
                }
                ChassisInputBridge.get().setStick(stickX, stickY, padVx, padVy)
            }
        }
    }

    fun persist() = saveLayout(prefs, dragOffsets, sizeScales, wScales, hScales, enabledIds)

    DisposableEffect(Unit) {
        onDispose {
            persist()
            ChassisInputBridge.get().setStick(0f, 0f, 0f, 0f)
            ChassisInputBridge.get().setDpad(false, false, false, false)
        }
    }

    val allDefs = remember { (defaultControls() + keyCatalog()).associateBy { it.id } }

    // Register edit callbacks for the *display* overlay
    LaunchedEffect(Unit) {
        ChassisEditState.onPersist = { persist() }
        ChassisEditState.onReset = {
            dragOffsets.clear()
            sizeScales.clear()
            wScales.clear()
            hScales.clear()
            enabledIds.clear()
            defaultControls().forEach {
                enabledIds[it.id] = true
                sizeScales[it.id] = 1f
                if (it.id in freeformIds()) {
                    wScales[it.id] = 1f
                    hScales[it.id] = 1f
                }
            }
            listOf("fn1", "fn2", "fn3", "share", "slider", "mouse_pad", "face").forEach {
                enabledIds[it] = false
            }
            ChassisEditState.selectedId = null
            prefs.edit().clear()
                .putBoolean("defaults_applied_v10", true)
                .putBoolean("face_separate_v12", true)
                .apply()
            ChassisEditState.lastAction = "RESET"
        }
        ChassisEditState.onAddKey = { id ->
            enabledIds[id] = true
            if (!sizeScales.containsKey(id)) sizeScales[id] = 1f
            ChassisEditState.selectedId = id
            ChassisEditState.lastAction = "ADDED $id"
        }
        ChassisEditState.onRemoveSelected = {
            val id = ChassisEditState.selectedId
            if (id != null && id.startsWith("key_")) {
                enabledIds[id] = false
                ChassisEditState.selectedId = null
                ChassisEditState.lastAction = "REMOVED $id"
            } else {
                ChassisEditState.lastAction = "Select a key to remove"
            }
        }
        ChassisEditState.onSetSize = { v ->
            ChassisEditState.selectedId?.let { sizeScales[it] = v }
        }
        ChassisEditState.onSetW = { v ->
            ChassisEditState.selectedId?.let { wScales[it] = v }
        }
        ChassisEditState.onSetH = { v ->
            ChassisEditState.selectedId?.let { hScales[it] = v }
        }
        listOf("fn1", "fn2", "fn3", "share", "slider", "mouse_pad", "face").forEach {
            enabledIds[it] = false
        }
    }

    CompositionLocalProvider(LocalChassisHaptics provides haptics) {
        // Full-width pad (view width already matches display bezel)
        Box(
            modifier
                .fillMaxSize()
                .background(Color(0xFF5A5A64))
                .padding(horizontal = 0.dp, vertical = 2.dp)
        ) {
            UnifiedDeck(
                modifier = Modifier.fillMaxSize(),
                editMode = ChassisEditState.editMode,
                selectedId = ChassisEditState.selectedId,
                onSelect = { id ->
                    val free = id in freeformIds()
                    ChassisEditState.select(
                        id,
                        free,
                        sizeScales[id] ?: 1f,
                        wScales[id] ?: 1f,
                        hScales[id] ?: 1f
                    )
                },
                dragOffsets = dragOffsets,
                sizeScales = sizeScales,
                wScales = wScales,
                hScales = hScales,
                enabledIds = enabledIds,
                allDefs = allDefs,
                onAction = { ChassisEditState.lastAction = it },
                onToggleEdit = { },
                onStick = { x, y ->
                    stickX = x; stickY = y
                    ChassisInputBridge.get().setStick(x, y, padVx, padVy)
                },
                onPadVelocity = { vx, vy ->
                    padVx = vx; padVy = vy
                    ChassisInputBridge.get().setStick(stickX, stickY, vx, vy)
                },
                onPadHeld = { padHeld = it },
                onFreeformResize = { id, dw, dh ->
                    wScales[id] = ((wScales[id] ?: 1f) + dw).coerceIn(0.4f, 2.5f)
                    hScales[id] = ((hScales[id] ?: 1f) + dh).coerceIn(0.4f, 2.5f)
                    if (ChassisEditState.selectedId == id) {
                        ChassisEditState.wValue = wScales[id] ?: 1f
                        ChassisEditState.hValue = hScales[id] ?: 1f
                    }
                }
            )
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFFFFB300) else Color(0xFF2A2A32))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

@Composable
private fun AddKeysPanel(
    enabledIds: SnapshotStateMap<String, Boolean>,
    sizeScales: SnapshotStateMap<String, Float>,
    catalog: List<ControlDef>,
    onAdded: (String) -> Unit,
) {
    Column(Modifier.padding(top = 6.dp)) {
        Text("Add keys onto the deck:", color = Color(0xFFB0B0BA), fontSize = 9.sp)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("ESC" to "key_esc", "TAB" to "key_tab", "SPC" to "key_spc", "ENT" to "key_ent", "⌫" to "key_bksp").forEach { (lab, id) ->
                Chip(lab, enabledIds[id] == true) {
                    enabledIds[id] = true
                    if (!sizeScales.containsKey(id)) sizeScales[id] = 1f
                    onAdded(id)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            "QWERTYUIOPASDFGHJKLZXCVBNM".forEach { c ->
                val id = "key_$c"
                val on = enabledIds[id] == true
                Text(
                    c.toString(),
                    color = if (on) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (on) Color(0xFFB24BFF) else Color(0xFF2A2A32))
                        .clickable {
                            enabledIds[id] = true
                            if (!sizeScales.containsKey(id)) sizeScales[id] = 1f
                            onAdded(id)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            "1234567890".forEach { c ->
                val id = "key_$c"
                val on = enabledIds[id] == true
                Text(
                    c.toString(),
                    color = if (on) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (on) Color(0xFFB24BFF) else Color(0xFF2A2A32))
                        .clickable {
                            enabledIds[id] = true
                            if (!sizeScales.containsKey(id)) sizeScales[id] = 1f
                            onAdded(id)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        LaunchedEffect(Unit) {
            catalog.forEach { if (!enabledIds.containsKey(it.id)) enabledIds[it.id] = false }
        }
    }
}

// ── Deck ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnifiedDeck(
    modifier: Modifier,
    editMode: Boolean,
    selectedId: String?,
    onSelect: (String) -> Unit,
    dragOffsets: SnapshotStateMap<String, Offset>,
    sizeScales: SnapshotStateMap<String, Float>,
    wScales: SnapshotStateMap<String, Float>,
    hScales: SnapshotStateMap<String, Float>,
    enabledIds: SnapshotStateMap<String, Boolean>,
    allDefs: Map<String, ControlDef>,
    onAction: (String) -> Unit,
    onToggleEdit: () -> Unit,
    onStick: (Float, Float) -> Unit,
    onPadVelocity: (Float, Float) -> Unit,
    onPadHeld: (Boolean) -> Unit,
    onFreeformResize: (id: String, dW: Float, dH: Float) -> Unit,
) {
    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val haptics = LocalChassisHaptics.current
    val router = remember(haptics) { ChassisPressRouter(haptics) }
    val deckOrigin = remember { mutableStateOf(Offset.Zero) }

    // Clear digital holds when entering edit (pointerInput tears down too)
    LaunchedEffect(editMode) {
        if (editMode) router.releaseAll()
    }

    CompositionLocalProvider(
        LocalPressRouter provides router,
    ) {
        Box(
            modifier
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF1C1C22))
                .border(2.dp, Color.White, RoundedCornerShape(22.dp))
                .onSizeChanged { layoutSize = it }
                .onGloballyPositioned { coords ->
                    if (coords.isAttached) deckOrigin.value = coords.positionInRoot()
                }
                // Deck-level digital multi-touch: slide LT↔LB, Back↔Start, face, LS↔RS, keys
                .then(
                    if (!editMode) {
                        Modifier.pointerInput(router) {
                            try {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val origin = deckOrigin.value
                                        event.changes.forEach { change ->
                                            val id = change.id
                                            val posRoot = Offset(
                                                change.position.x + origin.x,
                                                change.position.y + origin.y
                                            )
                                            when {
                                                change.changedToDown() -> {
                                                    if (router.onDown(id, posRoot)) change.consume()
                                                }
                                                // Treat any not-pressed change as up/cancel.
                                                // changedToUp() alone misses ACTION_CANCEL and
                                                // drops ownership → stuck buttons with 3–4 fingers.
                                                !change.pressed -> {
                                                    if (router.releaseIfUp(id)) change.consume()
                                                }
                                                change.pressed -> {
                                                    if (router.onMove(id, posRoot)) {
                                                        if (change.positionChange() != Offset.Zero) {
                                                            change.consume()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } finally {
                                // Gesture scope cancelled (rotation, overlay, etc.) — never leave stuck
                                router.releaseAll()
                            }
                        }
                    } else Modifier
                )
        ) {
            if (layoutSize.width <= 0) return@Box
            val padW = with(density) { layoutSize.width.toDp() }
            val padH = with(density) { layoutSize.height.toDp() }

            allDefs.values.forEach { def ->
                if (enabledIds[def.id] != true) return@forEach
                val sc = (sizeScales[def.id] ?: 1f).coerceIn(0.55f, 1.80f)
                val free = def.id in freeformIds()
                val wSc = if (free) (wScales[def.id] ?: 1f).coerceIn(0.4f, 2.5f) else sc
                val hSc = if (free) (hScales[def.id] ?: 1f).coerceIn(0.4f, 2.5f) else sc
                val selected = selectedId == def.id

                Movable(
                    id = def.id,
                    frac = def.defaultFrac,
                    layoutSize = layoutSize,
                    editMode = editMode,
                    selected = selected,
                    freeform = free,
                    dragOffsets = dragOffsets,
                    onSelect = { onSelect(def.id) },
                    onCornerDrag = if (free && editMode && selected) {
                        { dx, dy ->
                            val dw = dx / (layoutSize.width * 0.25f)
                            val dh = dy / (layoutSize.height * 0.18f)
                            onFreeformResize(def.id, dw, dh)
                        }
                    } else null
                ) {
                    when (def.kind) {
                        ControlKind.L1 -> Shoulder("l1", "LB", padW * 0.168f * wSc, padH * 0.072f * hSc) {
                            ChassisInputBridge.get().setButton(ExternalController.IDX_BUTTON_L1, it)
                            if (it) onAction("LB")
                        }
                        ControlKind.L2 -> Shoulder("l2", "LT", padW * 0.168f * wSc, padH * 0.072f * hSc) {
                            ChassisInputBridge.get().setTrigger(it, null)
                            if (it) onAction("LT")
                        }
                        ControlKind.R1 -> Shoulder("r1", "RB", padW * 0.168f * wSc, padH * 0.072f * hSc) {
                            ChassisInputBridge.get().setButton(ExternalController.IDX_BUTTON_R1, it)
                            if (it) onAction("RB")
                        }
                        ControlKind.R2 -> Shoulder("r2", "RT", padW * 0.168f * wSc, padH * 0.072f * hSc) {
                            ChassisInputBridge.get().setTrigger(null, it)
                            if (it) onAction("RT")
                        }
                        ControlKind.GUIDE -> HoldCircle(
                            id = "xbox",
                            size = (padW * 0.095f * sc).coerceIn(30.dp, 48.dp),
                            onPressed = {
                                ChassisInputBridge.get().setButton(ExternalController.IDX_BUTTON_START, it)
                                if (it) onAction("START")
                            },
                            haptic = PressHaptic.GUIDE,
                        ) { HamburgerIcon() }
                        ControlKind.BACK -> HoldCircle(
                            id = "back",
                            size = (padW * 0.08f * sc).coerceIn(28.dp, 40.dp),
                            onPressed = {
                                ChassisInputBridge.get().setButton(ExternalController.IDX_BUTTON_SELECT, it)
                                if (it) onAction("BACK")
                            },
                        ) { BackArrowIcon() }
                        ControlKind.SHARE -> { /* removed */ }
                        ControlKind.FN1 -> StickClickButton(
                            id = "ls",
                            label = "LS",
                            size = (padW * 0.10f * sc).coerceIn(32.dp, 46.dp),
                        ) {
                            ChassisInputBridge.get().setButton(ExternalController.IDX_BUTTON_L3, it)
                            if (it) onAction("LS")
                        }
                        ControlKind.FN2 -> StickClickButton(
                            id = "rs",
                            label = "RS",
                            size = (padW * 0.10f * sc).coerceIn(32.dp, 46.dp),
                        ) {
                            ChassisInputBridge.get().setButton(ExternalController.IDX_BUTTON_R3, it)
                            if (it) onAction("RS")
                        }
                        ControlKind.FN3 -> { /* removed */ }
                        ControlKind.SLIDER -> { /* removed */ }
                        ControlKind.FACE_A, ControlKind.FACE_B, ControlKind.FACE_X, ControlKind.FACE_Y -> {
                            val idx = when (def.kind) {
                                ControlKind.FACE_A -> ExternalController.IDX_BUTTON_A
                                ControlKind.FACE_B -> ExternalController.IDX_BUTTON_B
                                ControlKind.FACE_X -> ExternalController.IDX_BUTTON_X
                                else -> ExternalController.IDX_BUTTON_Y
                            }
                            FaceButton(
                                id = def.id,
                                label = def.label,
                                color = def.color,
                                size = (padW * 0.122f * sc).coerceIn(34.dp, 58.dp),
                            ) {
                                ChassisInputBridge.get().setButton(idx, it)
                                if (it) onAction(def.label)
                            }
                        }
                        ControlKind.STICK -> Joystick(
                            size = (padW * 0.30f * sc).coerceIn(92.dp, 152.dp),
                            onStick = onStick,
                            onAction = onAction
                        )
                        ControlKind.DPAD -> DPadControl(
                            size = (padW * 0.236f * sc).coerceIn(80.dp, 118.dp),
                            onAction = onAction,
                        )
                        ControlKind.TRACKPAD_RS -> MiniTrackpad(
                            widthDp = padW * 0.48f * wSc,
                            heightDp = padH * 0.22f * hSc,
                            label = "RS PAD",
                            onVelocity = onPadVelocity,
                            onHeld = onPadHeld,
                            onAction = onAction
                        )
                        ControlKind.KEY -> KeyButton(
                            id = def.id,
                            label = def.label,
                            size = (padW * 0.09f * sc).coerceIn(28.dp, 48.dp),
                        ) {
                            ChassisInputBridge.get().injectVkTap(def.vk)
                            onAction("KEY ${def.label}")
                        }
                    }
                }
            }
        }
    }
}

// ── Movable + corner handle ────────────────────────────────────────────────

@Composable
private fun BoxScope.Movable(
    id: String,
    frac: Offset,
    layoutSize: IntSize,
    editMode: Boolean,
    selected: Boolean,
    freeform: Boolean,
    dragOffsets: SnapshotStateMap<String, Offset>,
    onSelect: () -> Unit,
    onCornerDrag: ((Float, Float) -> Unit)?,
    content: @Composable () -> Unit
) {
    val extra = dragOffsets[id] ?: Offset.Zero
    Box(
        Modifier.offset {
            IntOffset(
                (layoutSize.width * frac.x + extra.x).roundToInt(),
                (layoutSize.height * frac.y + extra.y).roundToInt()
            )
        }
    ) {
        content()
        if (editMode) {
            // Selection / move overlay — blue halo in edit mode
            Box(
                Modifier
                    .matchParentSize()
                    .border(
                        if (selected) 2.dp else 1.5.dp,
                        if (selected) EditHaloBlueSel else EditHaloBlue,
                        RoundedCornerShape(6.dp)
                    )
                    .pointerInput(id) {
                        detectTapGestures(onTap = { onSelect() })
                    }
                    .pointerInput(id) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            onSelect()
                            val cur = dragOffsets[id] ?: Offset.Zero
                            dragOffsets[id] = cur + drag
                        }
                    }
            )
            // Freeform corner handle (bottom-right) — drag to reshape W×H
            if (selected && freeform && onCornerDrag != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 6.dp, y = 6.dp)
                        .size(18.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(EditHaloBlue)
                        .border(1.dp, Color.White, RoundedCornerShape(3.dp))
                        .pointerInput(id) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                onCornerDrag(drag.x, drag.y)
                            }
                        }
                )
            }
        }
    }
}

// ── Widgets ────────────────────────────────────────────────────────────────

/** Registers a digital press zone with the deck router and keeps bounds fresh. */
@Composable
private fun Modifier.pressZone(
    id: String,
    haptic: PressHaptic,
    circular: Boolean,
    fireOnDownOnly: Boolean = false,
    analog: Boolean = false,
    onPressed: (Boolean) -> Unit,
): Modifier {
    val router = LocalPressRouter.current
    val onPressedState by rememberUpdatedState(onPressed)
    DisposableEffect(id, router) {
        router?.register(id, haptic, circular, fireOnDownOnly, analog) { down ->
            onPressedState(down)
        }
        onDispose { router?.unregister(id) }
    }
    return this.onGloballyPositioned { coords ->
        if (router != null && coords.isAttached) {
            router.updateBoundsRoot(id, coords.boundsInRoot())
        }
    }
}

@Composable
private fun HoldCircle(
    id: String,
    size: Dp,
    onPressed: (Boolean) -> Unit,
    haptic: PressHaptic = PressHaptic.BUTTON,
    content: @Composable BoxScope.() -> Unit,
) {
    val router = LocalPressRouter.current
    val pressed = router?.visuallyPressed?.get(id) == true
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "p")
    val hit = size + 12.dp
    Box(
        Modifier
            .size(hit)
            .pressZone(id, haptic, circular = true, onPressed = onPressed),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF3E3E46), Color(0xFF232328))))
                .border(1.dp, ControlBorderYellow, CircleShape),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

@Composable
private fun FaceButton(
    id: String,
    label: String,
    color: Color,
    size: Dp,
    onPressed: (Boolean) -> Unit,
) {
    HoldCircle(id, size, onPressed) {
        Text(label, color = color, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.42f).sp)
    }
}

@Composable
private fun HamburgerIcon() {
    Canvas(Modifier.size(18.dp)) {
        val c = Color(0xFFE8E8EE)
        val sw = 2.2.dp.toPx()
        val left = size.width * 0.18f
        val right = size.width * 0.82f
        val y1 = size.height * 0.30f
        val y2 = size.height * 0.50f
        val y3 = size.height * 0.70f
        drawLine(c, Offset(left, y1), Offset(right, y1), sw)
        drawLine(c, Offset(left, y2), Offset(right, y2), sw)
        drawLine(c, Offset(left, y3), Offset(right, y3), sw)
    }
}

@Composable
private fun BackArrowIcon() {
    Canvas(Modifier.size(18.dp)) {
        val c = Color(0xFFE8E8EE)
        val sw = 2.4.dp.toPx()
        val cy = size.height * 0.5f
        val arm = size.minDimension * 0.28f
        drawLine(c, Offset(size.width * 0.22f, cy), Offset(size.width * 0.82f, cy), sw)
        drawLine(c, Offset(size.width * 0.22f, cy), Offset(size.width * 0.22f + arm, cy - arm), sw)
        drawLine(c, Offset(size.width * 0.22f, cy), Offset(size.width * 0.22f + arm, cy + arm), sw)
    }
}

@Composable
private fun StickClickButton(id: String, label: String, size: Dp, onPressed: (Boolean) -> Unit) {
    HoldCircle(id, size, onPressed) {
        Text(label, color = Color(0xFFC8C8D0), fontWeight = FontWeight.Bold, fontSize = (size.value * 0.32f).sp)
    }
}

@Composable
private fun Shoulder(id: String, label: String, width: Dp, height: Dp, onPressed: (Boolean) -> Unit) {
    val router = LocalPressRouter.current
    val pressed = router?.visuallyPressed?.get(id) == true
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "s")
    val corner = (minOf(width, height) * 0.35f).coerceAtLeast(4.dp)
    val w = width.coerceAtLeast(24.dp)
    val h = height.coerceAtLeast(18.dp)
    Box(
        Modifier
            .size(w + 10.dp, h + 10.dp)
            .pressZone(id, PressHaptic.SHOULDER, circular = false, onPressed = onPressed),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(w, h)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(corner))
                .background(if (pressed) Color(0xFF33333B) else Color(0xFF1B1B20))
                .border(1.dp, ControlBorderYellow, RoundedCornerShape(corner)),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun KeyButton(id: String, label: String, size: Dp, onTap: () -> Unit) {
    val onTapState by rememberUpdatedState(onTap)
    val router = LocalPressRouter.current
    val pressed = router?.visuallyPressed?.get(id) == true
    Box(
        Modifier
            .size(size.coerceAtLeast(28.dp), (size * 0.85f).coerceAtLeast(26.dp))
            .pressZone(
                id = id,
                haptic = PressHaptic.KEY,
                circular = false,
                fireOnDownOnly = true,
                onPressed = { down -> if (down) onTapState() },
            )
            .clip(RoundedCornerShape(8.dp))
            .background(if (pressed) Color(0xFF3A3A44) else Color(0xFF2A2A34))
            .border(1.dp, ControlBorderYellow, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (label.length > 1) 9.sp else 13.sp)
    }
}

@Composable
private fun DPadControl(size: Dp, onAction: (String) -> Unit) {
    val bridge = ChassisInputBridge.get()
    val haptics = LocalChassisHaptics.current
    val onActionState by rememberUpdatedState(onAction)
    var upOn by remember { mutableStateOf(false) }
    var downOn by remember { mutableStateOf(false) }
    var leftOn by remember { mutableStateOf(false) }
    var rightOn by remember { mutableStateOf(false) }

    val fillIdle = Color(0xFF23232A)
    val fillPress = Color(0xFF3A3A44)
    val stroke = Color(0x66FFB300)
    val chevron = Color(0xFFE8E8ED)
    val hub = Color(0xFF18181E)

    Box(
        Modifier
            .size(size)
            // Reserve this rect as analog so the deck router cannot steal taps
            // for nearby digital buttons (LS / face) — fixes cross-tapping.
            .pressZone(
                id = "dpad",
                haptic = PressHaptic.BUTTON,
                circular = false,
                analog = true,
                onPressed = {},
            )
            .pointerInput(size) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    fun apply(p: Offset) {
                        val w = this.size.width.toFloat()
                        val h = this.size.height.toFloat()
                        val cx = w * 0.5f
                        val cy = h * 0.5f
                        val dx = p.x - cx
                        val dy = p.y - cy
                        // Segment geometry: 3×3 cell grid with center dead + gaps
                        val cell = min(w, h) / 3.15f
                        val gap = cell * 0.12f
                        val half = cell * 0.5f
                        val armReach = cell + gap + half

                        // Dominant-axis first (prevents accidental diagonals / double taps)
                        val absX = kotlin.math.abs(dx)
                        val absY = kotlin.math.abs(dy)
                        val inRing = hypot(dx.toDouble(), dy.toDouble()) >= cell * 0.42

                        var u = false
                        var d = false
                        var l = false
                        var r = false
                        if (inRing) {
                            val primaryVertical = absY >= absX * 1.12f
                            val primaryHorizontal = absX >= absY * 1.12f
                            if (primaryVertical) {
                                u = dy < -gap && absY <= armReach + half
                                d = dy > gap && absY <= armReach + half
                            } else if (primaryHorizontal) {
                                l = dx < -gap && absX <= armReach + half
                                r = dx > gap && absX <= armReach + half
                            } else {
                                // Clear diagonal only when both axes are strong
                                u = dy < -gap * 1.4f
                                d = dy > gap * 1.4f
                                l = dx < -gap * 1.4f
                                r = dx > gap * 1.4f
                            }
                        }

                        upOn = u
                        downOn = d
                        leftOn = l
                        rightOn = r
                        bridge.setDpad(u, r, d, l)
                    }

                    apply(down.position)
                    haptics?.buttonDown()
                    onActionState("D-PAD")
                    try {
                        drag(down.id) { change ->
                            apply(change.position)
                            change.consume()
                        }
                    } finally {
                        bridge.setDpad(false, false, false, false)
                        upOn = false
                        downOn = false
                        leftOn = false
                        rightOn = false
                        haptics?.buttonUp()
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val cx = w * 0.5f
            val cy = h * 0.5f
            val cell = min(w, h) / 3.15f
            val gap = cell * 0.14f
            val arm = cell
            val cr = CornerRadius(cell * 0.34f)

            fun drawArm(
                left: Float,
                top: Float,
                width: Float,
                height: Float,
                pressed: Boolean,
            ) {
                val brush = Brush.linearGradient(
                    colors = if (pressed) {
                        listOf(Color(0xFF4A4A55), fillPress)
                    } else {
                        listOf(Color(0xFF2C2C34), fillIdle)
                    }
                )
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = cr,
                )
                drawRoundRect(
                    color = if (pressed) ControlBorderYellow else stroke,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = cr,
                    style = Stroke(width = 1.25.dp.toPx()),
                )
            }

            // Soft plate behind segments
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x2218181E), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = min(w, h) * 0.55f,
                ),
                radius = min(w, h) * 0.52f,
                center = Offset(cx, cy),
            )

            // Up / Down / Left / Right capsules with gaps
            drawArm(cx - arm / 2, cy - gap - arm - arm / 2, arm, arm, upOn)
            drawArm(cx - arm / 2, cy + gap + arm / 2, arm, arm, downOn)
            drawArm(cx - gap - arm - arm / 2, cy - arm / 2, arm, arm, leftOn)
            drawArm(cx + gap + arm / 2, cy - arm / 2, arm, arm, rightOn)

            // Center hub
            drawCircle(hub, radius = cell * 0.36f, center = Offset(cx, cy))
            drawCircle(
                Color(0x33FFFFFF),
                radius = cell * 0.36f,
                center = Offset(cx, cy),
                style = Stroke(1.dp.toPx()),
            )

            // Direction chevrons
            val tip = cell * 0.18f
            fun chevronUp(ox: Float, oy: Float, on: Boolean) {
                val c = if (on) ControlBorderYellow else chevron
                drawLine(c, Offset(ox - tip, oy + tip * 0.35f), Offset(ox, oy - tip * 0.45f), strokeWidth = 2.4.dp.toPx())
                drawLine(c, Offset(ox + tip, oy + tip * 0.35f), Offset(ox, oy - tip * 0.45f), strokeWidth = 2.4.dp.toPx())
            }
            fun chevronDown(ox: Float, oy: Float, on: Boolean) {
                val c = if (on) ControlBorderYellow else chevron
                drawLine(c, Offset(ox - tip, oy - tip * 0.35f), Offset(ox, oy + tip * 0.45f), strokeWidth = 2.4.dp.toPx())
                drawLine(c, Offset(ox + tip, oy - tip * 0.35f), Offset(ox, oy + tip * 0.45f), strokeWidth = 2.4.dp.toPx())
            }
            fun chevronLeft(ox: Float, oy: Float, on: Boolean) {
                val c = if (on) ControlBorderYellow else chevron
                drawLine(c, Offset(ox + tip * 0.35f, oy - tip), Offset(ox - tip * 0.45f, oy), strokeWidth = 2.4.dp.toPx())
                drawLine(c, Offset(ox + tip * 0.35f, oy + tip), Offset(ox - tip * 0.45f, oy), strokeWidth = 2.4.dp.toPx())
            }
            fun chevronRight(ox: Float, oy: Float, on: Boolean) {
                val c = if (on) ControlBorderYellow else chevron
                drawLine(c, Offset(ox - tip * 0.35f, oy - tip), Offset(ox + tip * 0.45f, oy), strokeWidth = 2.4.dp.toPx())
                drawLine(c, Offset(ox - tip * 0.35f, oy + tip), Offset(ox + tip * 0.45f, oy), strokeWidth = 2.4.dp.toPx())
            }

            chevronUp(cx, cy - gap - arm, upOn)
            chevronDown(cx, cy + gap + arm, downOn)
            chevronLeft(cx - gap - arm, cy, leftOn)
            chevronRight(cx + gap + arm, cy, rightOn)
        }
    }
}

@Composable
private fun Joystick(size: Dp, onStick: (Float, Float) -> Unit, onAction: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var knob by remember { mutableStateOf(Offset.Zero) }
    val haptics = LocalChassisHaptics.current
    val maxR = with(LocalDensity.current) { (size * 0.36f).toPx() }
    val knobSz = size * 0.54f
    val onStickState by rememberUpdatedState(onStick)

    fun applyKnob(raw: Offset) {
        val dist = raw.getDistance()
        knob = if (dist > maxR && dist > 0f) Offset(raw.x * maxR / dist, raw.y * maxR / dist) else raw
        val nx = (knob.x / maxR).coerceIn(-1f, 1f)
        val ny = (knob.y / maxR).coerceIn(-1f, 1f)
        haptics?.stickTravel(nx, ny)
        onStickState(nx, ny)
    }

    Box(
        Modifier
            .size(size)
            .pressZone(
                id = "joystick",
                haptic = PressHaptic.BUTTON,
                circular = true,
                analog = true,
                onPressed = {},
            )
            .clip(CircleShape)
            .background(Color(0xFF2C2C32))
            .border(2.dp, ControlBorderYellow, CircleShape)
            .pointerInput(size) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    haptics?.stickEngage()
                    val cx = this.size.width / 2f
                    val cy = this.size.height / 2f
                    applyKnob(Offset(down.position.x - cx, down.position.y - cy))
                    onAction("JOY")
                    val pointerId = down.id
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.find { it.id == pointerId } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            applyKnob(Offset(change.position.x - cx, change.position.y - cy))
                            change.consume()
                        }
                    } finally {
                        haptics?.stickRelease()
                        val start = knob
                        scope.launch {
                            animate(0f, 1f, animationSpec = spring(stiffness = Spring.StiffnessMedium)) { t, _ ->
                                knob = Offset(start.x * (1f - t), start.y * (1f - t))
                            }
                            knob = Offset.Zero
                            onStickState(0f, 0f)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }
                .size(knobSz)
                .clip(CircleShape)
                .background(Color(0xFF6E6E78))
                .border(1.dp, ControlBorderYellow, CircleShape)
        )
    }
}

@Composable
private fun MiniTrackpad(
    widthDp: Dp,
    heightDp: Dp,
    label: String,
    onVelocity: (Float, Float) -> Unit,
    onHeld: (Boolean) -> Unit,
    onAction: (String) -> Unit,
) {
    var last by remember { mutableStateOf(Offset.Zero) }
    var lastT by remember { mutableStateOf(0L) }
    val haptics = LocalChassisHaptics.current
    val corner = (minOf(widthDp, heightDp) * 0.18f).coerceAtLeast(6.dp)
    Box(
        Modifier
            .size(widthDp.coerceAtLeast(40.dp), heightDp.coerceAtLeast(28.dp))
            .pressZone(
                id = "trackpad_rs",
                haptic = PressHaptic.BUTTON,
                circular = false,
                analog = true,
                onPressed = {},
            )
            .clip(RoundedCornerShape(corner))
            .background(Color(0xFF141419))
            .border(1.dp, ControlBorderYellow, RoundedCornerShape(corner))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    last = down.position
                    lastT = System.nanoTime()
                    onHeld(true)
                    haptics?.padDown()
                    onAction(label)
                    val pointerId = down.id
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val c = event.changes.find { it.id == pointerId } ?: break
                            if (!c.pressed) {
                                c.consume()
                                break
                            }
                            val now = System.nanoTime()
                            val dt = ((now - lastT).coerceAtLeast(1_000_000L)) / 1e9f
                            val dx = c.position.x - last.x
                            val dy = c.position.y - last.y
                            last = c.position
                            lastT = now
                            val inv = 1f / dt.coerceAtLeast(0.001f)
                            val vx = ((dx * inv) / 900f * 1.35f).coerceIn(-1f, 1f)
                            val vy = ((dy * inv) / 900f * 1.35f * 0.85f).coerceIn(-1f, 1f)
                            onVelocity(vx, vy)
                            val speed = hypot(vx.toDouble(), vy.toDouble()).toFloat()
                            if (speed > 0.08f) haptics?.padTick(speed)
                            c.consume()
                        }
                    } finally {
                        onHeld(false)
                        onVelocity(0f, 0f)
                        haptics?.padUp()
                    }
                }
            }
    ) {
        Text(label, color = Color(0xFF9A9AA4), fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
        Text("PC EMU", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
    }
}

// ── Persistence ────────────────────────────────────────────────────────────

private fun loadLayout(
    prefs: SharedPreferences,
    offsets: MutableMap<String, Offset>,
    scales: MutableMap<String, Float>,
    wScales: MutableMap<String, Float>,
    hScales: MutableMap<String, Float>,
    enabled: MutableMap<String, Boolean>,
) {
    prefs.all.forEach { (k, v) ->
        when {
            k.startsWith("off_") && v is String -> {
                val p = v.split(",")
                if (p.size == 2) offsets[k.removePrefix("off_")] =
                    Offset(p[0].toFloatOrNull() ?: 0f, p[1].toFloatOrNull() ?: 0f)
            }
            k.startsWith("sz_") && v is Float -> scales[k.removePrefix("sz_")] = v
            k.startsWith("sz_") && v is String -> scales[k.removePrefix("sz_")] = v.toFloatOrNull() ?: 1f
            k.startsWith("ws_") && v is Float -> wScales[k.removePrefix("ws_")] = v
            k.startsWith("ws_") && v is String -> wScales[k.removePrefix("ws_")] = v.toFloatOrNull() ?: 1f
            k.startsWith("hs_") && v is Float -> hScales[k.removePrefix("hs_")] = v
            k.startsWith("hs_") && v is String -> hScales[k.removePrefix("hs_")] = v.toFloatOrNull() ?: 1f
            k.startsWith("on_") && v is Boolean -> enabled[k.removePrefix("on_")] = v
            k.startsWith("on_") && v is String -> enabled[k.removePrefix("on_")] = v == "true"
        }
    }
    enabled["mouse_pad"] = false
}

private fun saveLayout(
    prefs: SharedPreferences,
    offsets: Map<String, Offset>,
    scales: Map<String, Float>,
    wScales: Map<String, Float>,
    hScales: Map<String, Float>,
    enabled: Map<String, Boolean>,
) {
    val defaultsApplied = prefs.getBoolean("defaults_applied_v10", false)
    val faceSeparate = prefs.getBoolean("face_separate_v12", false)
    val dpadAdded = prefs.getBoolean("dpad_added_v13", false)
    val dpadLayout = prefs.getBoolean("dpad_layout_v14", false)
    prefs.edit().apply {
        clear()
        if (defaultsApplied) putBoolean("defaults_applied_v10", true)
        if (faceSeparate) putBoolean("face_separate_v12", true)
        if (dpadAdded) putBoolean("dpad_added_v13", true)
        if (dpadLayout) putBoolean("dpad_layout_v14", true)
        offsets.forEach { (id, o) -> putString("off_$id", "${o.x},${o.y}") }
        scales.forEach { (id, s) -> putFloat("sz_$id", s) }
        wScales.forEach { (id, s) -> putFloat("ws_$id", s) }
        hScales.forEach { (id, s) -> putFloat("hs_$id", s) }
        enabled.forEach { (id, on) -> if (id != "mouse_pad") putBoolean("on_$id", on) }
        apply()
    }
}

private operator fun Dp.times(f: Float): Dp = Dp(value * f)
