package com.winlator.cmod.console

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Finger-following gestures with spring settle — no threshold snaps.
 * Drag updates run synchronously; only settle uses a coroutine.
 */

enum class ConsoleEdge { Start, End }

fun consoleGestureSpring() = spring<Float>(
    dampingRatio = 0.88f,
    stiffness = 420f,
)

fun consoleGestureSnapSpring() = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 560f,
)

/**
 * Left-edge interactive swipe.
 * Reports continuous [onProgress] 0→1 while dragging.
 */
@Composable
fun ConsoleEdgeSwipeZone(
    enabled: Boolean,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
    edge: ConsoleEdge = ConsoleEdge.Start,
    edgeWidth: Dp = 40.dp,
    revealDistance: Dp = 300.dp,
    commitFraction: Float = 0.28f,
    commitVelocity: Float = 900f,
    onProgress: (Float) -> Unit = {},
    onCancel: () -> Unit = {},
    alignment: Alignment = if (edge == ConsoleEdge.Start) Alignment.CenterStart else Alignment.CenterEnd,
) {
    if (!enabled) return
    val density = LocalDensity.current
    val revealPx = with(density) { revealDistance.toPx() }
    var progress by remember { mutableFloatStateOf(0f) }

    fun publish(p: Float) {
        progress = p.coerceIn(0f, 1f)
        onProgress(progress)
    }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(alignment)
                .fillMaxHeight()
                .width(edgeWidth)
                .pointerInput(enabled, edge, revealPx) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            publish(0f)
                        },
                        onDragCancel = {
                            publish(0f)
                            onCancel()
                        },
                        onDragEnd = {
                            val vx = tracker.calculateVelocity().x
                            val commit = when (edge) {
                                ConsoleEdge.Start ->
                                    progress >= commitFraction || vx >= commitVelocity
                                ConsoleEdge.End ->
                                    progress >= commitFraction || vx <= -commitVelocity
                            }
                            if (commit) onCommit() else onCancel()
                            publish(0f)
                        },
                        onHorizontalDrag = { change, amount ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val delta = when (edge) {
                                ConsoleEdge.Start -> amount
                                ConsoleEdge.End -> -amount
                            }
                            if (delta != 0f) {
                                publish(progress + delta / revealPx)
                                change.consume()
                            }
                        },
                    )
                },
        )
    }
}

/** Compatibility overload used by older call sites. */
@Composable
fun ConsoleEdgeSwipeZone(
    enabled: Boolean,
    onSwipe: () -> Unit,
    modifier: Modifier = Modifier,
    edge: ConsoleEdge = ConsoleEdge.Start,
    edgeWidth: Dp = 40.dp,
    minDistance: Dp = 48.dp,
    alignment: Alignment = if (edge == ConsoleEdge.Start) Alignment.CenterStart else Alignment.CenterEnd,
) {
    ConsoleEdgeSwipeZone(
        enabled = enabled,
        onCommit = onSwipe,
        modifier = modifier,
        edge = edge,
        edgeWidth = edgeWidth,
        revealDistance = 280.dp,
        alignment = alignment,
    )
}

/**
 * Drawer panel: follows finger left, springs back or dismisses.
 */
@Composable
fun Modifier.consoleInteractiveDismissLeft(
    enabled: Boolean = true,
    widthPx: Float,
    onDismiss: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    var offset by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }

    return this
        .graphicsLayer { translationX = offset }
        .then(
            if (!enabled) Modifier
            else Modifier.pointerInput(widthPx) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        settling = false
                        tracker.resetTracking()
                    },
                    onDragCancel = {
                        scope.launch {
                            settling = true
                            val anim = Animatable(offset)
                            anim.animateTo(0f, consoleGestureSpring()) { offset = value }
                            settling = false
                        }
                    },
                    onDragEnd = {
                        val vx = tracker.calculateVelocity().x
                        val shouldDismiss = offset <= -widthPx * 0.25f || vx <= -800f
                        scope.launch {
                            settling = true
                            val anim = Animatable(offset)
                            if (shouldDismiss) {
                                anim.animateTo(-widthPx, consoleGestureSnapSpring()) { offset = value }
                                onDismiss()
                                offset = 0f
                            } else {
                                anim.animateTo(0f, consoleGestureSpring()) { offset = value }
                            }
                            settling = false
                        }
                    },
                    onHorizontalDrag = { change, amount ->
                        if (settling) return@detectHorizontalDragGestures
                        tracker.addPosition(change.uptimeMillis, change.position)
                        offset = (offset + amount).coerceIn(-widthPx, 0f)
                        change.consume()
                    },
                )
            },
        )
}

/** Sheet: follows finger down, springs back or dismisses. */
@Composable
fun Modifier.consoleInteractiveSwipeDown(
    enabled: Boolean = true,
    dismissDistance: Dp = 140.dp,
    onDismiss: () -> Unit,
): Modifier {
    val density = LocalDensity.current
    val dismissPx = with(density) { dismissDistance.toPx() }
    val scope = rememberCoroutineScope()
    var offset by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }

    return this
        .graphicsLayer {
            translationY = offset
            alpha = 1f - (offset / (dismissPx * 2.2f)).coerceIn(0f, 0.4f)
        }
        .then(
            if (!enabled) Modifier
            else Modifier.pointerInput(dismissPx) {
                val tracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = {
                        settling = false
                        tracker.resetTracking()
                    },
                    onDragCancel = {
                        scope.launch {
                            settling = true
                            val anim = Animatable(offset)
                            anim.animateTo(0f, consoleGestureSpring()) { offset = value }
                            settling = false
                        }
                    },
                    onDragEnd = {
                        val vy = tracker.calculateVelocity().y
                        val shouldDismiss = offset >= dismissPx * 0.32f || vy >= 1100f
                        scope.launch {
                            settling = true
                            val anim = Animatable(offset)
                            if (shouldDismiss) {
                                anim.animateTo(dismissPx * 1.5f, consoleGestureSnapSpring()) { offset = value }
                                onDismiss()
                                offset = 0f
                            } else {
                                anim.animateTo(0f, consoleGestureSpring()) { offset = value }
                            }
                            settling = false
                        }
                    },
                    onVerticalDrag = { change, amount ->
                        if (settling) return@detectVerticalDragGestures
                        tracker.addPosition(change.uptimeMillis, change.position)
                        offset = (offset + amount).coerceAtLeast(0f)
                        if (amount > 0f || offset > 0f) change.consume()
                    },
                )
            },
        )
}

/**
 * Screen content follows edge-back drag, then springs or pops.
 */
@Composable
fun Modifier.consoleInteractiveEdgeBack(
    enabled: Boolean = true,
    edgeWidth: Dp = 40.dp,
    maxReveal: Dp = 120.dp,
    onBack: () -> Unit,
): Modifier {
    val density = LocalDensity.current
    val edgePx = with(density) { edgeWidth.toPx() }
    val maxPx = with(density) { maxReveal.toPx() }
    val scope = rememberCoroutineScope()
    var offset by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }

    return this
        .graphicsLayer {
            val p = (offset / maxPx).coerceIn(0f, 1f)
            translationX = offset
            scaleX = 1f - 0.025f * p
            scaleY = 1f - 0.025f * p
            // Soft shadow of depth while dragging.
            shadowElevation = 8f * p
        }
        .then(
            if (!enabled) Modifier
            else Modifier.pointerInput(edgePx, maxPx) {
                val tracker = VelocityTracker()
                var tracking = false
                detectHorizontalDragGestures(
                    onDragStart = { pos ->
                        tracking = pos.x <= edgePx
                        settling = false
                        tracker.resetTracking()
                    },
                    onDragCancel = {
                        tracking = false
                        scope.launch {
                            settling = true
                            val anim = Animatable(offset)
                            anim.animateTo(0f, consoleGestureSpring()) { offset = value }
                            settling = false
                        }
                    },
                    onDragEnd = {
                        if (!tracking) return@detectHorizontalDragGestures
                        tracking = false
                        val vx = tracker.calculateVelocity().x
                        val commit = offset >= maxPx * 0.35f || vx >= 900f
                        scope.launch {
                            settling = true
                            val anim = Animatable(offset)
                            if (commit) {
                                anim.animateTo(maxPx * 1.35f, consoleGestureSnapSpring()) { offset = value }
                                onBack()
                                offset = 0f
                            } else {
                                anim.animateTo(0f, consoleGestureSpring()) { offset = value }
                            }
                            settling = false
                        }
                    },
                    onHorizontalDrag = { change, amount ->
                        if (!tracking || settling) return@detectHorizontalDragGestures
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (amount > 0f || offset > 0f) {
                            offset = (offset + amount).coerceIn(0f, maxPx)
                            change.consume()
                        }
                    },
                )
            },
        )
}

/** Alias kept for sheet call sites during migration. */
@Composable
fun Modifier.consoleSwipeDownToDismiss(
    enabled: Boolean = true,
    minDistance: Dp = 72.dp,
    onDismiss: () -> Unit,
): Modifier = consoleInteractiveSwipeDown(
    enabled = enabled,
    dismissDistance = minDistance,
    onDismiss = onDismiss,
)

@Composable
fun Modifier.consoleSwipeToDismissLeft(
    enabled: Boolean = true,
    minDistance: Dp = 64.dp,
    onDismiss: () -> Unit,
): Modifier {
    val density = LocalDensity.current
    val widthPx = with(density) { 320.dp.toPx() }
    return consoleInteractiveDismissLeft(enabled, widthPx, onDismiss)
}

fun consoleIsHorizontalIntent(dx: Float, dy: Float, ratio: Float = 1.6f): Boolean =
    abs(dx) > abs(dy) * ratio && abs(dx) > 8f
