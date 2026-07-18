package com.winlator.cmod.console

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConsolePill(
    label: String,
    filled: Boolean = false,
    enabled: Boolean = true,
    fontSize: TextUnit = 14.sp,
    horizontalPadding: Dp = 14.dp,
    verticalPadding: Dp = 10.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Text(
        label,
        color = when {
            !enabled -> ConsoleColors.TextSecondary
            filled -> Color.White
            else -> ConsoleColors.AccentBlue
        },
        fontFamily = ConsoleFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = fontSize,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(ConsoleChipShape)
            .background(
                when {
                    !enabled -> ConsoleColors.CanvasDeep
                    filled -> ConsoleColors.AccentBlue
                    else -> ConsoleColors.SurfaceRaised
                },
            )
            .border(
                width = if (filled) 0.dp else 0.5.dp,
                color = if (filled) Color.Transparent else ConsoleColors.CardStroke,
                shape = ConsoleChipShape,
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConsoleIconButton(
    onClick: () -> Unit,
    filled: Boolean = false,
    size: Dp = 48.dp,
    shape: Shape = CircleShape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(size)
            .clip(shape)
            .background(if (filled) ConsoleColors.AccentBlue else ConsoleColors.SurfaceRaised)
            .border(
                width = if (filled) 0.dp else 0.5.dp,
                color = if (filled) Color.Transparent else ConsoleColors.CardStroke,
                shape = shape,
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun ConsoleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    Column(modifier) {
        if (!label.isNullOrBlank()) {
            Text(
                label,
                color = ConsoleColors.TextSecondary,
                fontFamily = ConsoleFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(ConsoleRowShape)
                .background(ConsoleColors.Canvas)
                .padding(14.dp),
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    placeholder,
                    color = ConsoleColors.TextSecondary.copy(alpha = 0.55f),
                    fontFamily = ConsoleFontFamily,
                    fontSize = 15.sp,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else minLines,
                textStyle = TextStyle(
                    color = ConsoleColors.TextPrimary,
                    fontFamily = ConsoleFontFamily,
                    fontSize = 15.sp,
                ),
                cursorBrush = SolidColor(ConsoleColors.AccentBlue),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
