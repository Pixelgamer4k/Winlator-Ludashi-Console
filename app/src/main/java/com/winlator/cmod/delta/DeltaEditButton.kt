package com.winlator.cmod.delta

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.console.rememberPressScale

/**
 * Compact EDIT / DONE chip at the absolute top-right of the phone screen
 * (outside the game display and outside the control pad).
 */
@Composable
fun DeltaEditButton(modifier: Modifier = Modifier) {
    val editing = ChassisEditState.editMode
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, pressedScale = 0.92f)
    Text(
        if (editing) "DONE" else "EDIT",
        color = if (editing) Color.Black else Color.White,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.3.sp,
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 1.dp, end = 1.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(5.dp))
            .background(if (editing) Color(0xFFFFB300) else Color(0xE02A2A32))
            .border(0.5.dp, Color(0xFF55555E), RoundedCornerShape(5.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) {
                if (editing) ChassisEditState.closeEdit(save = true)
                else ChassisEditState.openEdit()
            }
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
