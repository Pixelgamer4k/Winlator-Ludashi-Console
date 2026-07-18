package com.winlator.cmod.console

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.delta.ChassisEditState
import com.winlator.cmod.delta.DeltaEditButton
import com.winlator.cmod.delta.DeltaEditOverlay
import com.winlator.cmod.delta.PixelDeltaChassis

/**
 * Standalone chassis / control-panel editor — same pad + edit tools as in-game.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConsoleChassisScreen(onBack: () -> Unit) {
    fun leave() {
        if (ChassisEditState.editMode) {
            ChassisEditState.closeEdit(save = true)
        }
        onBack()
    }

    BackHandler { leave() }

    LaunchedEffect(Unit) {
        ChassisEditState.openEdit()
    }
    DisposableEffect(Unit) {
        onDispose {
            if (ChassisEditState.editMode) {
                ChassisEditState.closeEdit(save = true)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0D))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val interaction = remember { MutableInteractionSource() }
                val scale = rememberPressScale(interaction)
                Box(
                    Modifier
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .shadow(4.dp, CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(Color(0xFF1C1C1E))
                        .combinedClickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = { leave() },
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        "‹ Back",
                        color = ConsoleColors.AccentBlue,
                        fontFamily = ConsoleFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Control panel",
                        color = Color.White,
                        fontFamily = ConsoleFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    )
                    Text(
                        "Same editor as while playing",
                        color = Color(0xFF9AA0AE),
                        fontFamily = ConsoleFontFamily,
                        fontSize = 13.sp,
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(0.38f)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                DeltaEditOverlay(Modifier.fillMaxWidth())
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(0.62f)
            ) {
                PixelDeltaChassis()
            }
        }

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 6.dp)
        ) {
            DeltaEditButton()
        }
    }
}
