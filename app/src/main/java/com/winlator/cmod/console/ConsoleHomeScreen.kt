@file:OptIn(ExperimentalFoundationApi::class)

package com.winlator.cmod.console

import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.console.agent.AgentConfig
import com.winlator.cmod.console.agent.AgentFab
import com.winlator.cmod.console.agent.ConsoleAgentChatSheet
import com.winlator.cmod.container.Shortcut
import kotlinx.coroutines.delay
import java.util.Calendar

data class ConsoleGameItem(
    val id: String,
    val name: String,
    val icon: Bitmap?,
    val shortcut: Shortcut,
)

@Composable
fun ConsoleHomeScreen(
    games: List<ConsoleGameItem>,
    settingUp: Boolean,
    setupMessage: String,
    importing: Boolean,
    importMessage: String = "Adding game…",
    browsingFiles: Boolean = false,
    menuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onCloseMenu: () -> Unit,
    onMenuAction: (ConsoleMenuAction) -> Unit,
    onAddGame: () -> Unit,
    onCancelBrowse: () -> Unit = {},
    onPickExe: (java.io.File) -> Unit = {},
    onPlay: (ConsoleGameItem) -> Unit,
    onLongPress: (ConsoleGameItem) -> Unit,
    onBindNestedBack: ((() -> Boolean)?) -> Unit = {},
) {
    var focusedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(games.size) {
        if (games.isEmpty()) focusedIndex = 0
        else if (focusedIndex >= games.size) focusedIndex = 0
    }

    // System menu closes on back; file browser handles folder-up via nested back.
    var agentOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val agentEnabled = AgentConfig.load(context).enabled

    BackHandler(enabled = agentOpen) { agentOpen = false }
    BackHandler(enabled = menuOpen && !browsingFiles && !agentOpen) { onCloseMenu() }

    DisposableEffect(browsingFiles) {
        if (!browsingFiles) onBindNestedBack(null)
        onDispose {
            if (!browsingFiles) onBindNestedBack(null)
        }
    }

    val focusedGame = games.getOrNull(focusedIndex)
    var menuPeek by remember { mutableFloatStateOf(0f) }
    val peekAnim = remember { Animatable(0f) }
    val peekScope = rememberCoroutineScope()

    ConsoleTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(ConsoleCanvasBrush)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                TopChrome(
                    onOpenMenu = onOpenMenu,
                    onAddGame = onAddGame,
                )

                when {
                    settingUp -> {
                        Spacer(Modifier.weight(1f))
                        SetupPane(setupMessage)
                        Spacer(Modifier.weight(1f))
                    }
                    games.isEmpty() -> {
                        Spacer(Modifier.weight(1f))
                        EmptyShelf(
                            onAddGame = onAddGame,
                            importing = importing,
                            importMessage = importMessage,
                        )
                        Spacer(Modifier.weight(1f))
                        BottomHintBar(onOpenMenu = onOpenMenu)
                    }
                    else -> {
                        FocusTitleBand(
                            title = focusedGame?.name ?: "WINLATOR",
                            subtitle = if (games.size == 1) "Ready to start"
                            else "${games.size} games · Ready to start",
                        )
                        GameGrid(
                            games = games,
                            focusedIndex = focusedIndex,
                            onFocus = { focusedIndex = it },
                            onPlay = onPlay,
                            onLongPress = onLongPress,
                            modifier = Modifier.weight(1f),
                        )
                        BottomHintBar(onOpenMenu = onOpenMenu, showLibraryHints = true)
                    }
                }
            }

            AnimatedVisibility(
                visible = importing && games.isNotEmpty(),
                enter = fadeIn(animationSpec = tween(220, easing = ConsoleMotionEasing)),
                exit = fadeOut(animationSpec = tween(160, easing = ConsoleMotionEasing)),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(ConsoleColors.Scrim.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .shadow(16.dp, ConsoleRowShape)
                            .clip(ConsoleRowShape)
                            .background(ConsoleColors.SurfaceRaised)
                            .padding(horizontal = 28.dp, vertical = 24.dp),
                    ) {
                        CircularProgressIndicator(
                            color = ConsoleColors.AccentBlue,
                            strokeWidth = 3.dp,
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            importMessage,
                            color = ConsoleColors.TextSecondary,
                            textAlign = TextAlign.Center,
                            fontFamily = ConsoleFontFamily,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            ConsoleSystemMenu(
                visible = menuOpen,
                peekProgress = if (menuOpen) 0f else menuPeek,
                onDismiss = onCloseMenu,
                onAction = onMenuAction,
            )

            AnimatedVisibility(
                visible = browsingFiles,
                enter = consoleSheetEnter(),
                exit = consoleSheetExit(),
            ) {
                GameFileBrowser(
                    onCancel = onCancelBrowse,
                    onPickExe = onPickExe,
                    onBindNestedBack = onBindNestedBack,
                )
            }

            if (!browsingFiles && !menuOpen && !agentOpen && agentEnabled) {
                AgentFab(
                    onClick = { agentOpen = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(end = 20.dp, bottom = 28.dp),
                )
            }

            if (agentOpen) {
                ConsoleAgentChatSheet(onDismiss = { agentOpen = false })
            }

            // Interactive edge reveal — panel peeks with the finger, then springs open.
            ConsoleEdgeSwipeZone(
                enabled = !menuOpen && !browsingFiles && !agentOpen && !settingUp && !importing,
                edge = ConsoleEdge.Start,
                edgeWidth = 40.dp,
                revealDistance = 300.dp,
                commitFraction = 0.28f,
                onProgress = { menuPeek = it },
                onCommit = {
                    menuPeek = 0f
                    onOpenMenu()
                },
                onCancel = {
                    peekScope.launch {
                        peekAnim.snapTo(menuPeek)
                        peekAnim.animateTo(0f, consoleGestureSpring()) { menuPeek = value }
                    }
                },
            )
        }
    }
}

@Composable
private fun TopChrome(
    onOpenMenu: () -> Unit,
    onAddGame: () -> Unit,
) {
    val context = LocalContext.current
    var clockText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            clockText = DateFormat.getTimeFormat(context).format(cal.time)
            delay(30_000)
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SoftIconButton(onClick = onOpenMenu) {
            UserGlyph()
        }

        Spacer(Modifier.weight(1f))

        Text(
            clockText,
            color = ConsoleColors.TextSecondary,
            fontFamily = ConsoleFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            letterSpacing = 0.2.sp,
        )

        Spacer(Modifier.width(12.dp))

        SoftIconButton(
            onClick = onAddGame,
            filled = true,
        ) {
            Text(
                "+",
                color = Color.White,
                fontFamily = ConsoleFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            )
        }
    }
}

@Composable
private fun SoftIconButton(
    onClick: () -> Unit,
    filled: Boolean = false,
    content: @Composable () -> Unit,
) {
    ConsoleIconButton(
        onClick = onClick,
        filled = filled,
        shape = ConsoleSquircleShape,
        content = content,
    )
}

@Composable
private fun UserGlyph() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(ConsoleColors.AccentBlue),
        )
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .size(width = 14.dp, height = 7.dp)
                .clip(ConsoleChipShape)
                .background(ConsoleColors.AccentBlue),
        )
    }
}

@Composable
private fun FocusTitleBand(title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = title,
            transitionSpec = {
                fadeIn(tween(200, easing = ConsoleMotionEasing)) togetherWith
                    fadeOut(tween(140, easing = ConsoleMotionEasing))
            },
            label = "focusTitle",
        ) { name ->
            Text(
                name,
                color = ConsoleColors.TextPrimary,
                fontFamily = ConsoleFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            color = ConsoleColors.TextSecondary,
            fontFamily = ConsoleFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun BottomHintBar(
    onOpenMenu: () -> Unit,
    showLibraryHints: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLibraryHints) {
            Text(
                "Tap · Hold · Swipe › for System  ·  ",
                color = ConsoleColors.HintLabel,
                fontFamily = ConsoleFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            )
        }
        Text(
            "System",
            color = ConsoleColors.AccentBlue,
            fontFamily = ConsoleFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.combinedClickable(onClick = onOpenMenu),
        )
    }
}

@Composable
private fun SetupPane(message: String) {
    Column(
        Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = ConsoleColors.AccentBlue, strokeWidth = 3.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            message.ifBlank { "Setting up your console…" },
            color = ConsoleColors.TextPrimary,
            fontFamily = ConsoleFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Building a ready-to-play environment.\nThis only happens once.",
            color = ConsoleColors.TextSecondary,
            fontFamily = ConsoleFontFamily,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun EmptyShelf(
    onAddGame: () -> Unit,
    importing: Boolean,
    importMessage: String,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "WINLATOR",
            color = ConsoleColors.TextPrimary,
            fontFamily = ConsoleFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 40.sp,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Browse folders, then tap the .exe you want.\nNothing is copied — play from where it is.",
            color = ConsoleColors.TextSecondary,
            fontFamily = ConsoleFontFamily,
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(28.dp))
        if (importing) {
            CircularProgressIndicator(color = ConsoleColors.AccentBlue)
            Spacer(Modifier.height(14.dp))
            Text(
                importMessage,
                color = ConsoleColors.TextSecondary,
                fontFamily = ConsoleFontFamily,
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
            )
        } else {
            Text(
                "Add Game",
                color = Color.White,
                fontFamily = ConsoleFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .shadow(10.dp, ConsoleButtonShape, clip = false)
                    .clip(ConsoleButtonShape)
                    .background(ConsoleColors.AccentBlue)
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onAddGame,
                    )
                    .padding(horizontal = 40.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun GameGrid(
    games: List<ConsoleGameItem>,
    focusedIndex: Int,
    onFocus: (Int) -> Unit,
    onPlay: (ConsoleGameItem) -> Unit,
    onLongPress: (ConsoleGameItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(),
        modifier = modifier,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cols = when {
                maxWidth >= 900.dp -> 5
                maxWidth >= 600.dp -> 4
                else -> 3
            }
            ConsoleLazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(games, key = { _, g -> g.id }) { index, game ->
                    GameTile(
                        game = game,
                        focused = index == focusedIndex,
                        onFocus = { onFocus(index) },
                        onPlay = { onPlay(game) },
                        onLongPress = { onLongPress(game) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GameTile(
    game: ConsoleGameItem,
    focused: Boolean,
    onFocus: () -> Unit,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interaction, pressedScale = 0.97f)
    val focusScale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "tileFocus",
    )
    val combined = focusScale * pressScale

    Column(
        Modifier
            .graphicsLayer {
                scaleX = combined
                scaleY = combined
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    if (focused) onPlay()
                    else onFocus()
                },
                onLongClick = {
                    onFocus()
                    onLongPress()
                },
                onClickLabel = "Play",
                onLongClickLabel = "Options",
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(ConsoleTileShape)
                .background(ConsoleColors.SurfaceRaised)
                .border(
                    width = if (focused) 2.dp else 0.5.dp,
                    color = if (focused) ConsoleColors.FocusRing else ConsoleColors.CardStroke,
                    shape = ConsoleTileShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = game.icon
            if (bmp != null && !bmp.isRecycled) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = game.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    ConsoleColors.AccentBlue.copy(alpha = 0.22f),
                                    ConsoleColors.AccentRed.copy(alpha = 0.16f),
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        game.name.take(1).uppercase(),
                        color = ConsoleColors.TextPrimary,
                        fontFamily = ConsoleFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 34.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            game.name,
            color = if (focused) ConsoleColors.TextPrimary else ConsoleColors.TextSecondary,
            fontFamily = ConsoleFontFamily,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = if (focused) 13.sp else 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
        )
    }
}
