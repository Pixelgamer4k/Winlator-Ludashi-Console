package com.winlator.cmod.delta

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Full mouse-mode surface: large trackpad + L/R/M buttons + scroll strip.
 */
@Composable
fun MousePadSurface(
    modifier: Modifier = Modifier,
    sensitivity: Float = 1.15f,
    onAction: (String) -> Unit = {},
) {
    var touch by remember { mutableStateOf<Offset?>(null) }
    var leftDown by remember { mutableStateOf(false) }
    var rightDown by remember { mutableStateOf(false) }
    var midDown by remember { mutableStateOf(false) }
    var sens by remember { mutableFloatStateOf(sensitivity.coerceIn(0.5f, 2.5f)) }
    val haptics = LocalHapticFeedback.current

    Column(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF1A1A20))
            .border(1.dp, Color(0xFF38383F), RoundedCornerShape(22.dp))
            .padding(10.dp)
    ) {
        // Title + sensitivity
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "MOUSE PAD",
                color = Color(0xFFB24BFF),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SENS", color = Color(0xFF9A9AA4), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(
                    "−",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2A2A32))
                        .clickable {
                            sens = (sens - 0.1f).coerceAtLeast(0.5f)
                            onAction("SENS ${String.format("%.1f", sens)}")
                        }
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                )
                Text(
                    String.format("%.1f×", sens),
                    color = Color(0xFFB24BFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Text(
                    "＋",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2A2A32))
                        .clickable {
                            sens = (sens + 0.1f).coerceAtMost(2.5f)
                            onAction("SENS ${String.format("%.1f", sens)}")
                        }
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Main trackpad + scroll strip
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Touch surface
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF121218))
                    .border(1.dp, Color(0xFF2E2E36), RoundedCornerShape(16.dp))
                    .pointerInput(sens) {
                        detectDragGestures(
                            onDragStart = { p ->
                                touch = p
                                onAction("MOVE")
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                touch = change.position
                                val dx = (drag.x * sens).roundToInt()
                                val dy = (drag.y * sens).roundToInt()
                                ChassisInputBridge.get().mouseMove(dx, dy)
                            },
                            onDragEnd = {
                                touch = null
                                onAction("IDLE")
                            },
                            onDragCancel = { touch = null }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                ChassisInputBridge.get().mouseButton(0, true)
                                ChassisInputBridge.get().mouseButton(0, false)
                                onAction("L-CLICK")
                            },
                            onDoubleTap = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                ChassisInputBridge.get().mouseButton(0, true)
                                ChassisInputBridge.get().mouseButton(0, false)
                                ChassisInputBridge.get().mouseButton(0, true)
                                ChassisInputBridge.get().mouseButton(0, false)
                                onAction("DBL-CLICK")
                            },
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                ChassisInputBridge.get().mouseButton(2, true)
                                ChassisInputBridge.get().mouseButton(2, false)
                                onAction("R-CLICK")
                            }
                        )
                    }
            ) {
                // Brushed grid
                Canvas(Modifier.fillMaxSize()) {
                    val step = size.height * 0.05f
                    var y = step
                    while (y < size.height) {
                        drawLine(
                            Color.White.copy(alpha = 0.04f),
                            Offset(12f, y),
                            Offset(size.width - 12f, y),
                            strokeWidth = 1f
                        )
                        y += step
                    }
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)
                        ),
                        size = Size(size.width, size.height * 0.35f),
                        cornerRadius = CornerRadius(16.dp.toPx())
                    )
                }
                touch?.let { p ->
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(
                            Color(0x66B24BFF),
                            radius = 22.dp.toPx(),
                            center = p
                        )
                        drawCircle(
                            Color(0xFFB24BFF),
                            radius = 8.dp.toPx(),
                            center = p
                        )
                    }
                }
                Column(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text("PC EMU", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("MOUSE", color = Color(0xFF9A9AA4), fontSize = 8.sp, letterSpacing = 2.sp)
                }
                Text(
                    "drag · tap L · long R · double click",
                    color = Color(0xFF6A6A74),
                    fontSize = 9.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            // Vertical scroll strip
            Column(
                Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1C1C24))
                    .border(1.dp, Color(0xFF33333C), RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val dy = drag.y
                            if (abs(dy) > 2f) {
                                val ticks = (-dy / 8f).roundToInt().coerceIn(-3, 3)
                                if (ticks != 0) {
                                    ChassisInputBridge.get().mouseWheel(ticks * 120)
                                    onAction(if (ticks > 0) "WHEEL↑" else "WHEEL↓")
                                }
                            }
                        }
                    }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("▲", color = Color(0xFF9A9AA4), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .width(4.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF3A3A44))
                )
                Spacer(Modifier.height(6.dp))
                Text("▼", color = Color(0xFF9A9AA4), fontSize = 12.sp)
                Text("SCRL", color = Color(0xFF6A6A74), fontSize = 8.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        // Mouse buttons L / M / R
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MouseButton(
                label = "L",
                pressed = leftDown,
                modifier = Modifier.weight(1.4f),
                onPressed = { down ->
                    leftDown = down
                    ChassisInputBridge.get().mouseButton(0, down)
                    if (down) onAction("L-DOWN") else onAction("L-UP")
                }
            )
            MouseButton(
                label = "M",
                pressed = midDown,
                modifier = Modifier.weight(0.8f),
                onPressed = { down ->
                    midDown = down
                    ChassisInputBridge.get().mouseButton(1, down)
                    if (down) onAction("M-DOWN") else onAction("M-UP")
                }
            )
            MouseButton(
                label = "R",
                pressed = rightDown,
                modifier = Modifier.weight(1.4f),
                onPressed = { down ->
                    rightDown = down
                    ChassisInputBridge.get().mouseButton(2, down)
                    if (down) onAction("R-DOWN") else onAction("R-UP")
                }
            )
        }
    }
}

@Composable
private fun MouseButton(
    label: String,
    pressed: Boolean,
    modifier: Modifier = Modifier,
    onPressed: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(if (pressed) Color(0xFF4A2A6A) else Color(0xFF24242E))
            .border(
                1.dp,
                if (pressed) Color(0xFFB24BFF) else Color(0xFF3A3A44),
                RoundedCornerShape(12.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPressed(true)
                        try {
                            tryAwaitRelease()
                        } finally {
                            onPressed(false)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (pressed) Color(0xFFD0A0FF) else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}
