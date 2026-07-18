package com.winlator.cmod.console

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Apple UIKit navigation curve (ease-out, slightly snappy).
 * Frame-rate independent — stays fluid on 60–120 Hz.
 */
val ConsoleMotionEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
private val ConsolePressEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/** Full UINavigationController-style push (~380 ms). */
private const val SCREEN_MS = 380
private const val MENU_MS = 420
private const val SHEET_MS = 360
private const val PRESS_MS = 140

/** Soft Apple-like press scale while a control is pressed. */
@Composable
fun rememberPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(PRESS_MS, easing = ConsolePressEasing),
        label = "pressScale",
    )
    return scale
}

fun consoleSheetEnter(): EnterTransition =
    fadeIn(animationSpec = tween(SHEET_MS - 60, easing = ConsoleMotionEasing)) +
        slideInVertically(
            animationSpec = tween(SHEET_MS, easing = ConsoleMotionEasing),
        ) { it } +
        scaleIn(
            animationSpec = tween(SHEET_MS, easing = ConsoleMotionEasing),
            initialScale = 0.96f,
        )

fun consoleSheetExit(): ExitTransition =
    fadeOut(animationSpec = tween(220, easing = ConsoleMotionEasing)) +
        slideOutVertically(animationSpec = tween(280, easing = ConsoleMotionEasing)) { it / 2 } +
        scaleOut(
            animationSpec = tween(280, easing = ConsoleMotionEasing),
            targetScale = 0.97f,
        )

/**
 * Push forward — new screen slides in from the right (full width),
 * previous eases left with parallax (iOS stack).
 */
fun consoleScreenPushTransform(): ContentTransform =
    (
        slideInHorizontally(tween(SCREEN_MS, easing = ConsoleMotionEasing)) { it } +
            fadeIn(tween(SCREEN_MS / 2, easing = ConsoleMotionEasing))
        ) togetherWith (
        slideOutHorizontally(tween(SCREEN_MS, easing = ConsoleMotionEasing)) { -it / 3 } +
            fadeOut(tween(SCREEN_MS, easing = ConsoleMotionEasing))
        )

/**
 * Pop back — current slides off to the right, previous reveals from the left.
 */
fun consoleScreenPopTransform(): ContentTransform =
    (
        slideInHorizontally(tween(SCREEN_MS, easing = ConsoleMotionEasing)) { -it / 3 } +
            fadeIn(tween(SCREEN_MS, easing = ConsoleMotionEasing))
        ) togetherWith (
        slideOutHorizontally(tween(SCREEN_MS, easing = ConsoleMotionEasing)) { it } +
            fadeOut(tween(220, easing = ConsoleMotionEasing))
        )

fun consoleMenuScrimEnter(): EnterTransition =
    fadeIn(animationSpec = tween(MENU_MS - 100, easing = ConsoleMotionEasing))

fun consoleMenuScrimExit(): ExitTransition =
    fadeOut(animationSpec = tween(280, easing = ConsoleMotionEasing))

fun consoleMenuPanelEnter(): EnterTransition =
    slideInHorizontally(animationSpec = tween(MENU_MS, easing = ConsoleMotionEasing)) { -it } +
        fadeIn(animationSpec = tween(200, easing = ConsoleMotionEasing))

fun consoleMenuPanelExit(): ExitTransition =
    slideOutHorizontally(animationSpec = tween(MENU_MS - 40, easing = ConsoleMotionEasing)) { -it } +
        fadeOut(animationSpec = tween(220, easing = ConsoleMotionEasing))

fun consoleTabEnter(): EnterTransition =
    fadeIn(tween(220, easing = ConsoleMotionEasing)) +
        slideInHorizontally(tween(280, easing = ConsoleMotionEasing)) { it / 8 }

fun consoleTabExit(): ExitTransition =
    fadeOut(tween(160, easing = ConsoleMotionEasing))

/** Shared fling for LazyColumns — avoids custom overscroll fighting the system. */
@Composable
fun consoleListFlingBehavior(): FlingBehavior = ScrollableDefaults.flingBehavior()

@Composable
fun rememberConsoleListState(): LazyListState = rememberLazyListState()

/** Cheap fade for list rows without per-frame spring work. */
@Composable
fun Modifier.consoleListItemFade(visibleFraction: Float = 1f): Modifier {
    return this.graphicsLayer { alpha = visibleFraction.coerceIn(0.92f, 1f) }
}
