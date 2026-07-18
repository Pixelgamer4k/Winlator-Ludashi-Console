package com.winlator.cmod.delta

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Edit toolbar on the **game display** when edit mode is active.
 * EDIT/DONE entry chip is absolute screen top-right ([DeltaEditButton]).
 */
@Composable
fun DeltaEditOverlay(modifier: Modifier = Modifier) {
    if (!ChassisEditState.editMode) return

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xF01A1410))
            .border(1.5.dp, Color(0xFFFFB300), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text(
            "EDIT CONTROLS",
            color = Color(0xFFFFB300),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            ChassisEditState.lastAction,
            color = Color(0xFF7CFC9A),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
        )

        val sel = ChassisEditState.selectedId
        if (sel != null && ChassisEditState.selectedIsFreeform) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("W · $sel", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                Slider(
                    value = ChassisEditState.wValue,
                    onValueChange = {
                        ChassisEditState.wValue = it
                        ChassisEditState.onSetW?.invoke(it)
                    },
                    valueRange = 0.4f..2.5f,
                    modifier = Modifier.weight(1f).height(28.dp),
                    colors = editSliderColors()
                )
                Text("${(ChassisEditState.wValue * 100).roundToInt()}%", color = Color(0xFFFFB300), fontSize = 10.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("H · $sel", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                Slider(
                    value = ChassisEditState.hValue,
                    onValueChange = {
                        ChassisEditState.hValue = it
                        ChassisEditState.onSetH?.invoke(it)
                    },
                    valueRange = 0.4f..2.5f,
                    modifier = Modifier.weight(1f).height(28.dp),
                    colors = editSliderColors()
                )
                Text("${(ChassisEditState.hValue * 100).roundToInt()}%", color = Color(0xFFFFB300), fontSize = 10.sp)
            }
            Text("Or drag yellow corner on the control", color = Color(0xFFB0B0BA), fontSize = 9.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (sel != null) "SIZE · $sel" else "SIZE · (tap a control below)",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(140.dp)
                )
                Slider(
                    value = ChassisEditState.sizeValue,
                    onValueChange = {
                        ChassisEditState.sizeValue = it
                        ChassisEditState.onSetSize?.invoke(it)
                    },
                    enabled = sel != null,
                    valueRange = 0.55f..1.80f,
                    modifier = Modifier.weight(1f).height(28.dp),
                    colors = editSliderColors()
                )
                Text(
                    if (sel != null) "${(ChassisEditState.sizeValue * 100).roundToInt()}%" else "—",
                    color = Color(0xFFFFB300),
                    fontSize = 10.sp
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OverlayChip("＋ KEYS", ChassisEditState.showAddMenu) {
                ChassisEditState.showAddMenu = !ChassisEditState.showAddMenu
            }
            OverlayChip("REMOVE KEY", false) { ChassisEditState.onRemoveSelected?.invoke() }
            OverlayChip("RESET", false) { ChassisEditState.onReset?.invoke() }
            OverlayChip("DONE", false) { ChassisEditState.closeEdit(save = true) }
        }

        if (ChassisEditState.showAddMenu) {
            Spacer(Modifier.height(6.dp))
            Text("Tap to add a key on the pad:", color = Color(0xFFB0B0BA), fontSize = 9.sp)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("ESC", "TAB", "SPC", "ENT", "⌫").forEach { lab ->
                    val id = when (lab) {
                        "ESC" -> "key_esc"
                        "TAB" -> "key_tab"
                        "SPC" -> "key_spc"
                        "ENT" -> "key_ent"
                        else -> "key_bksp"
                    }
                    OverlayChip(lab, false) { ChassisEditState.onAddKey?.invoke(id) }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                "QWERTYUIOPASDFGHJKLZXCVBNM".forEach { c ->
                    OverlayChip(c.toString(), false) { ChassisEditState.onAddKey?.invoke("key_$c") }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                "1234567890".forEach { c ->
                    OverlayChip(c.toString(), false) { ChassisEditState.onAddKey?.invoke("key_$c") }
                }
            }
        }
    }
}

@Composable
private fun editSliderColors() = SliderDefaults.colors(
    thumbColor = Color(0xFFFFB300),
    activeTrackColor = Color(0xFFFFB300),
    inactiveTrackColor = Color(0xFF443820),
    disabledThumbColor = Color(0xFF555555),
    disabledActiveTrackColor = Color(0xFF333333)
)

@Composable
private fun OverlayChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
