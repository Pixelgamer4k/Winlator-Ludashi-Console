package com.winlator.cmod.console.agent

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.console.ConsoleChipShape
import com.winlator.cmod.console.ConsoleLazyColumn
import com.winlator.cmod.console.ConsoleColors
import com.winlator.cmod.console.ConsoleFontFamily
import com.winlator.cmod.console.ConsolePill
import com.winlator.cmod.console.ConsoleRowShape
import com.winlator.cmod.console.ConsoleSheetShape
import com.winlator.cmod.console.ConsoleSquircleShape
import com.winlator.cmod.console.ConsoleTextField
import com.winlator.cmod.console.ConsoleTheme
import com.winlator.cmod.console.consoleListFlingBehavior
import com.winlator.cmod.console.consoleSheetEnter
import com.winlator.cmod.console.consoleSheetExit
import com.winlator.cmod.console.consoleInteractiveSwipeDown
import com.winlator.cmod.console.rememberPressScale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed class ChatBubble {
    data class User(val text: String) : ChatBubble()
    data class Assistant(val text: String) : ChatBubble()
    data class Applied(val text: String) : ChatBubble()
    data class Err(val text: String) : ChatBubble()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AgentFab(
    visible: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(12.dp, ConsoleSquircleShape, clip = false)
            .size(58.dp)
            .clip(ConsoleSquircleShape)
            .background(ConsoleColors.AccentBlue)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "AI",
            color = Color.White,
            fontFamily = ConsoleFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            letterSpacing = 0.5.sp,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConsoleAgentChatSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val agent = remember { HiveAgent(context.applicationContext) }
    val bubbles = remember { mutableStateListOf<ChatBubble>() }
    val history = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val listState = rememberLazyListState()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var sheetVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { sheetVisible = true }

    BackHandler(onBack = onDismiss)

    LaunchedEffect(bubbles.size) {
        if (bubbles.isNotEmpty()) listState.animateScrollToItem(bubbles.lastIndex)
    }

    fun sendMessage(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || busy) return
        if (!AgentConfig.load(context).isReady) {
            bubbles += ChatBubble.Err("Set API URL, key, and model in Settings → AI assistant.")
            return
        }
        input = ""
        bubbles += ChatBubble.User(text)
        busy = true
        status = "Starting…"
        val histSnapshot = history.toList()
        job?.cancel()
        job = scope.launch {
            val assistantParts = mutableListOf<String>()
            var userHistoryAdded = false
            agent.run(text, histSnapshot) { event ->
                mainHandler.post {
                    when (event) {
                        is AgentEvent.Status -> status = event.text
                        is AgentEvent.AssistantDelta -> {
                            bubbles += ChatBubble.Assistant(event.text)
                            assistantParts += event.text
                            if (!userHistoryAdded) {
                                history += ChatMessage("user", text)
                                userHistoryAdded = true
                            }
                        }
                        is AgentEvent.ToolStart -> status = friendlyToolStatus(event.name)
                        is AgentEvent.ToolResult -> status = "Finished ${event.name.replace('_', ' ')}"
                        is AgentEvent.Applied -> bubbles += ChatBubble.Applied(event.summary)
                        is AgentEvent.Error -> bubbles += ChatBubble.Err(event.message)
                        AgentEvent.Done -> {
                            if (userHistoryAdded && assistantParts.isNotEmpty()) {
                                history += ChatMessage("assistant", assistantParts.joinToString("\n\n"))
                            }
                            busy = false
                            status = null
                        }
                    }
                }
            }
        }
    }

    ConsoleTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(ConsoleColors.Scrim)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = sheetVisible,
                enter = consoleSheetEnter(),
                exit = consoleSheetExit(),
            ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .shadow(14.dp, ConsoleSheetShape, clip = true)
                    .clip(ConsoleSheetShape)
                    .background(ConsoleColors.SurfaceRaised)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding(),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .consoleInteractiveSwipeDown(onDismiss = onDismiss)
                        .padding(top = 10.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .width(36.dp)
                            .height(5.dp)
                            .clip(ConsoleChipShape)
                            .background(ConsoleColors.CardStroke),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .consoleInteractiveSwipeDown(onDismiss = onDismiss)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Hive Agent",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = ConsoleColors.TextPrimary,
                            ),
                        )
                        Text(
                            status ?: "Optimize games · research · apply settings",
                            color = ConsoleColors.TextSecondary,
                            fontFamily = ConsoleFontFamily,
                            fontSize = 13.sp,
                        )
                    }
                    ConsolePill("Close", onClick = onDismiss)
                }

                if (bubbles.isEmpty()) {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "Ask me to optimize a game, fix crashes, or look up Winlator settings.",
                            color = ConsoleColors.TextSecondary,
                            fontFamily = ConsoleFontFamily,
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        ConsolePill("Optimize settings for my games") {
                            sendMessage("Optimize settings for my games")
                        }
                        Spacer(Modifier.height(8.dp))
                        ConsolePill("Research best Box64 preset for Unity") {
                            sendMessage("Research best Box64 preset for Unity games on Winlator")
                        }
                        Spacer(Modifier.height(8.dp))
                        ConsolePill("Check why the last game crashed") {
                            sendMessage("Check why the last game crashed and suggest a fix")
                        }
                    }
                } else {
                    ConsoleLazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(bubbles.size) { i ->
                            when (val bubble = bubbles[i]) {
                                is ChatBubble.User -> Bubble(bubble.text, mine = true)
                                is ChatBubble.Assistant -> Bubble(bubble.text, mine = false)
                                is ChatBubble.Applied ->
                                    Bubble(bubble.text, mine = false, accent = true)
                                is ChatBubble.Err ->
                                    Bubble(bubble.text, mine = false, danger = true)
                            }
                        }
                    }
                }

                if (busy) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            Modifier.size(16.dp),
                            color = ConsoleColors.AccentBlue,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            status ?: "Working…",
                            color = ConsoleColors.TextSecondary,
                            fontFamily = ConsoleFontFamily,
                            fontSize = 12.sp,
                        )
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ConsoleTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = "Message Hive Agent…",
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    ConsolePill("Send", filled = true, enabled = !busy) {
                        sendMessage(input)
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun Bubble(
    text: String,
    mine: Boolean,
    accent: Boolean = false,
    danger: Boolean = false,
) {
    val bg = when {
        danger -> ConsoleColors.Danger.copy(alpha = 0.12f)
        accent -> ConsoleColors.AccentBlue.copy(alpha = 0.12f)
        mine -> ConsoleColors.AccentBlue
        else -> ConsoleColors.Canvas
    }
    val fg = when {
        danger -> ConsoleColors.Danger
        accent -> ConsoleColors.AccentBlue
        mine -> Color.White
        else -> ConsoleColors.TextPrimary
    }
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(ConsoleRowShape)
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (mine || danger) {
                Text(
                    text,
                    color = fg,
                    fontFamily = ConsoleFontFamily,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
            } else {
                AgentMarkdownText(markdown = text, color = fg)
            }
        }
    }
}

private fun friendlyToolStatus(name: String): String = when (name) {
    "apply_game_settings", "apply_container_settings" -> "Updating settings…"
    "apply_compat_fixes" -> "Applying compatibility fixes…"
    "install_adreno_driver" -> "Downloading graphics driver…"
    "install_content_pack" -> "Downloading content pack…"
    "add_game" -> "Adding game to library…"
    "delete_game" -> "Removing from library…"
    "search_storage", "list_directory" -> "Searching storage…"
    "get_last_session_report", "read_live_logs", "analyze_logs",
    "list_saved_log_files", "read_saved_log_file",
    -> "Reading logs…"
    "research_web" -> "Researching…"
    "list_games", "search_library", "get_game_settings" -> "Checking library…"
    else -> "Working: ${name.replace('_', ' ')}…"
}
