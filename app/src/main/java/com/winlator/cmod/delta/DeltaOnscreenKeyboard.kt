package com.winlator.cmod.delta

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class KeyboardMode {
    HIDDEN, QWERTY, ABC, NUM
}

/** Windows virtual-key codes used by WinHandler.keyboardEvent */
object Vk {
    const val BACK = 0x08
    const val TAB = 0x09
    const val RETURN = 0x0D
    const val SHIFT = 0x10
    const val ESCAPE = 0x1B
    const val SPACE = 0x20
    fun letter(c: Char): Int = c.uppercaseChar().code
    fun digit(c: Char): Int = c.code
}

@Composable
fun OnscreenKeyboardPanel(
    mode: KeyboardMode,
    onMode: (KeyboardMode) -> Unit,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (mode == KeyboardMode.HIDDEN) return

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1A20))
            .border(1.dp, Color(0xFF3A3A44), RoundedCornerShape(14.dp))
            .padding(6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when (mode) {
                    KeyboardMode.QWERTY -> "QWERTY"
                    KeyboardMode.ABC -> "ABC"
                    KeyboardMode.NUM -> "123"
                    else -> ""
                },
                color = Color(0xFFB24BFF),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ModeChip("QWERTY", mode == KeyboardMode.QWERTY) { onMode(KeyboardMode.QWERTY) }
                ModeChip("ABC", mode == KeyboardMode.ABC) { onMode(KeyboardMode.ABC) }
                ModeChip("123", mode == KeyboardMode.NUM) { onMode(KeyboardMode.NUM) }
                ModeChip("✕", false) {
                    onMode(KeyboardMode.HIDDEN)
                    onAction("KB CLOSE")
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        when (mode) {
            KeyboardMode.QWERTY -> QwertyRows(onAction)
            KeyboardMode.ABC -> AbcGrid(onAction)
            KeyboardMode.NUM -> NumPad(onAction)
            KeyboardMode.HIDDEN -> Unit
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color(0xFFB24BFF) else Color(0xFF2A2A32))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun QwertyRows(onAction: (String) -> Unit) {
    val rows = listOf(
        "QWERTYUIOP",
        "ASDFGHJKL",
        "ZXCVBNM"
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally)
            ) {
                row.forEach { ch ->
                    KeyBtn(ch.toString(), Modifier.weight(1f)) {
                        ChassisInputBridge.get().injectVkTap(Vk.letter(ch))
                        onAction("KEY $ch")
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyBtn("ESC", Modifier.weight(1f)) {
                ChassisInputBridge.get().injectVkTap(Vk.ESCAPE); onAction("ESC")
            }
            KeyBtn("TAB", Modifier.weight(1f)) {
                ChassisInputBridge.get().injectVkTap(Vk.TAB); onAction("TAB")
            }
            KeyBtn("SPACE", Modifier.weight(2.5f)) {
                ChassisInputBridge.get().injectVkTap(Vk.SPACE); onAction("SPACE")
            }
            KeyBtn("⌫", Modifier.weight(1.2f)) {
                ChassisInputBridge.get().injectVkTap(Vk.BACK); onAction("BKSP")
            }
            KeyBtn("↵", Modifier.weight(1.2f)) {
                ChassisInputBridge.get().injectVkTap(Vk.RETURN); onAction("ENTER")
            }
        }
    }
}

@Composable
private fun AbcGrid(onAction: (String) -> Unit) {
    val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        letters.chunked(7).forEach { chunk ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                chunk.forEach { ch ->
                    KeyBtn(ch.toString(), Modifier.weight(1f)) {
                        ChassisInputBridge.get().injectVkTap(Vk.letter(ch))
                        onAction("KEY $ch")
                    }
                }
                // pad last row
                repeat(7 - chunk.length) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KeyBtn("SPACE", Modifier.weight(2f)) {
                ChassisInputBridge.get().injectVkTap(Vk.SPACE); onAction("SPACE")
            }
            KeyBtn("⌫", Modifier.weight(1f)) {
                ChassisInputBridge.get().injectVkTap(Vk.BACK); onAction("BKSP")
            }
            KeyBtn("↵", Modifier.weight(1f)) {
                ChassisInputBridge.get().injectVkTap(Vk.RETURN); onAction("ENTER")
            }
        }
    }
}

@Composable
private fun NumPad(onAction: (String) -> Unit) {
    val rows = listOf("123", "456", "789", "0.-")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
            ) {
                row.forEach { ch ->
                    KeyBtn(ch.toString(), Modifier.weight(1f)) {
                        when (ch) {
                            in '0'..'9' -> ChassisInputBridge.get().injectVkTap(Vk.digit(ch))
                            '.' -> ChassisInputBridge.get().injectVkTap(0xBE) // VK_OEM_PERIOD
                            '-' -> ChassisInputBridge.get().injectVkTap(0xBD) // VK_OEM_MINUS
                        }
                        onAction("KEY $ch")
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KeyBtn("⌫", Modifier.weight(1f)) {
                ChassisInputBridge.get().injectVkTap(Vk.BACK); onAction("BKSP")
            }
            KeyBtn("↵", Modifier.weight(1f)) {
                ChassisInputBridge.get().injectVkTap(Vk.RETURN); onAction("ENTER")
            }
        }
    }
}

@Composable
private fun KeyBtn(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (pressed) Color(0xFF3A3A48) else Color(0xFF2A2A34))
            .border(1.dp, Color(0xFF444450), RoundedCornerShape(8.dp))
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (label.length > 1) 10.sp else 13.sp
        )
    }
}
