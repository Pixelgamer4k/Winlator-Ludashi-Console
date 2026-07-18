package com.winlator.cmod.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ConsoleDestination {
    HOME,
    CONTAINERS,
    CONTAINER_EDITOR,
    FILES,
    CONTROLS,
    SETTINGS,
    ABOUT,
    SHORTCUTS,
}

@Composable
fun ConsoleScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    /** Interactive edge-back: content follows the finger, then springs or pops. */
    edgeSwipeBack: Boolean = onBack != null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    ConsoleTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(ConsoleCanvasBrush)
                .then(
                    if (onBack != null && edgeSwipeBack) {
                        Modifier.consoleInteractiveEdgeBack(onBack = onBack)
                    } else {
                        Modifier
                    },
                ),
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
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        ConsoleIconButton(onClick = onBack, size = 44.dp) {
                            Text(
                                "‹",
                                color = ConsoleColors.AccentBlue,
                                fontFamily = ConsoleFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            letterSpacing = (-0.4).sp,
                            color = ConsoleColors.TextPrimary,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    trailing?.invoke()
                }
                content()
            }
        }
    }
}

@Composable
fun ConsoleSectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = ConsoleColors.TextSecondary,
        fontFamily = ConsoleFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
    )
}

/**
 * Raised preference card for lists.
 * Uses a light stroke instead of a heavy soft-shadow so LazyColumn scrolls stay fluid.
 */
@Composable
fun ConsoleCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(ConsoleRowShape)
            .background(ConsoleColors.SurfaceRaised)
            .border(0.5.dp, ConsoleColors.CardStroke, ConsoleRowShape)
            .padding(vertical = 2.dp),
    ) {
        content()
    }
}
