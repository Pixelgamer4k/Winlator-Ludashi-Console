package com.winlator.cmod.console

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ConsoleMenuAction {
    CONTAINERS,
    FILE_MANAGER,
    INPUT_CONTROLS,
    SETTINGS,
    SHORTCUTS,
    ABOUT,
}

/**
 * System drawer — finger-follows on open (peek) and close, springs to settle.
 */
@Composable
fun ConsoleSystemMenu(
    visible: Boolean,
    onDismiss: () -> Unit,
    onAction: (ConsoleMenuAction) -> Unit,
    peekProgress: Float = 0f,
) {
    val density = LocalDensity.current
    val panelTravel = with(density) { 330.dp.toPx() }

    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(1f) }

    val settled by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = consoleGestureSpring(),
        label = "systemMenuOpen",
    )

    val fraction = when {
        dragging -> dragFraction
        !visible && peekProgress > 0.001f -> peekProgress.coerceIn(0f, 1f)
        else -> settled
    }.coerceIn(0f, 1f)
    val fractionRef = rememberUpdatedState(fraction)

    if (fraction <= 0.001f) return

    val translationX = (1f - fraction) * -panelTravel

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.44f * fraction }
                .background(Color.Black)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
        )

        Column(
            Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    this.translationX = translationX
                    val s = 0.97f + 0.03f * fraction
                    scaleX = s
                    scaleY = s
                }
                .fillMaxHeight()
                .width(312.dp)
                .padding(start = 10.dp, top = 10.dp, bottom = 10.dp)
                .shadow((10f + 10f * fraction).dp, RoundedCornerShape(ConsoleRadii.sheet), clip = true)
                .clip(RoundedCornerShape(ConsoleRadii.sheet))
                .background(ConsoleColors.Glass)
                .pointerInput(panelTravel) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            dragFraction = fractionRef.value
                            tracker.resetTracking()
                        },
                        onDragCancel = {
                            dragging = false
                        },
                        onDragEnd = {
                            val vx = tracker.calculateVelocity().x
                            val shouldClose = dragFraction <= 0.72f || vx <= -650f
                            dragging = false
                            if (shouldClose) onDismiss()
                        },
                        onHorizontalDrag = { change, amount ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            dragFraction = (dragFraction + amount / panelTravel).coerceIn(0f, 1f)
                            change.consume()
                        },
                    )
                }
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 12.dp, bottom = 20.dp),
        ) {
            Text(
                "System",
                color = ConsoleColors.TextPrimary,
                fontFamily = ConsoleFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                letterSpacing = (-0.4).sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Text(
                "Advanced tools",
                color = ConsoleColors.TextSecondary,
                fontFamily = ConsoleFontFamily,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(
                color = ConsoleColors.CardStroke,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            MenuRow("Library", "Classic shortcuts view", ConsoleColors.AccentBlue) {
                onAction(ConsoleMenuAction.SHORTCUTS)
            }
            MenuRow("Containers", "Wine environments", ConsoleColors.AccentBlue) {
                onAction(ConsoleMenuAction.CONTAINERS)
            }
            MenuRow("Files", "Browse storage & EXEs", ConsoleColors.AccentBlue) {
                onAction(ConsoleMenuAction.FILE_MANAGER)
            }
            MenuRow("Control panel", "On-screen chassis editor", ConsoleColors.AccentBlue) {
                onAction(ConsoleMenuAction.INPUT_CONTROLS)
            }
            MenuRow("Settings", "App preferences", ConsoleColors.AccentBlue) {
                onAction(ConsoleMenuAction.SETTINGS)
            }
            Spacer(Modifier.weight(1f))
            HorizontalDivider(
                color = ConsoleColors.CardStroke,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            MenuRow("About", "Version & credits", ConsoleColors.TextSecondary) {
                onAction(ConsoleMenuAction.ABOUT)
            }
        }
    }
}

@Composable
private fun MenuRow(
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, pressedScale = 0.97f)

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(ConsoleRowShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f))
                .border(0.5.dp, accent.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title.take(1),
                color = accent,
                fontFamily = ConsoleFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = ConsoleColors.TextPrimary,
                fontFamily = ConsoleFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            )
            Text(
                subtitle,
                color = ConsoleColors.TextSecondary,
                fontFamily = ConsoleFontFamily,
                fontSize = 13.sp,
            )
        }
    }
}
