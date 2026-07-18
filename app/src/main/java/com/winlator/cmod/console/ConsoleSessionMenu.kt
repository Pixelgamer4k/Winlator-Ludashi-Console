package com.winlator.cmod.console

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * In-session options opened via system back / four-finger tap during play.
 * Nested panels (tasks / vibration / logs) stay in the same Console UI.
 */
data class SessionMenuModel(
    val title: String,
    val paused: Boolean,
    val relativeMouse: Boolean,
    val mouseDisabled: Boolean,
    val softStretch: Boolean,
    val showTouchControls: Boolean,
    val touchTimeout: Boolean,
    val touchHaptics: Boolean,
    val controlsOpacity: Float,
    val hudMode: Int,
    val fpsLimitIndex: Int,
    val fsrEnabled: Boolean,
    val postFxIndex: Int,
    val sharpness: Float,
    val logsEnabled: Boolean,
    val xrMode: Boolean,
    val profileNames: List<String>,
    val profileIndex: Int,
    val panel: SessionPanel = SessionPanel.HOME,
    val cpuPercent: Int = 0,
    val memoryText: String = "-- / --",
    val processes: List<SessionProcessRow> = emptyList(),
    val vibrationSlots: List<Boolean> = emptyList(),
    val logLines: List<String> = emptyList(),
    val logsPaused: Boolean = false,
    val newTaskCommand: String = "taskmgr.exe",
    val selectedProcessPid: Int = -1,
    val affinityMask: Int = 0,
) {
    companion object {
        @JvmStatic
        fun base(
            title: String,
            paused: Boolean,
            relativeMouse: Boolean,
            mouseDisabled: Boolean,
            softStretch: Boolean,
            showTouchControls: Boolean,
            touchTimeout: Boolean,
            touchHaptics: Boolean,
            controlsOpacity: Float,
            hudMode: Int,
            fpsLimitIndex: Int,
            fsrEnabled: Boolean,
            postFxIndex: Int,
            sharpness: Float,
            logsEnabled: Boolean,
            xrMode: Boolean,
            profileNames: List<String>,
            profileIndex: Int,
        ) = SessionMenuModel(
            title = title,
            paused = paused,
            relativeMouse = relativeMouse,
            mouseDisabled = mouseDisabled,
            softStretch = softStretch,
            showTouchControls = showTouchControls,
            touchTimeout = touchTimeout,
            touchHaptics = touchHaptics,
            controlsOpacity = controlsOpacity,
            hudMode = hudMode,
            fpsLimitIndex = fpsLimitIndex,
            fsrEnabled = fsrEnabled,
            postFxIndex = postFxIndex,
            sharpness = sharpness,
            logsEnabled = logsEnabled,
            xrMode = xrMode,
            profileNames = profileNames,
            profileIndex = profileIndex,
        )
    }
}

interface SessionMenuActions {
    fun closeMenu()
    fun togglePause()
    fun showKeyboard()
    fun enterPip()
    fun toggleFullscreen()
    fun openMagnifier()
    fun toggleSoftStretch()
    fun setRelativeMouse(enabled: Boolean)
    fun setMouseDisabled(disabled: Boolean)
    fun showVibration()
    fun openControlPanelEdit()
    fun setShowTouchControls(enabled: Boolean)
    fun setTouchTimeout(enabled: Boolean)
    fun setTouchHaptics(enabled: Boolean)
    fun setControlsOpacity(opacity: Float)
    fun setInputProfileIndex(index: Int)
    fun editInputProfiles()
    fun setHudMode(mode: Int)
    fun setFpsLimitIndex(index: Int)
    fun setFsrEnabled(enabled: Boolean)
    fun setPostFxIndex(index: Int)
    fun setSharpness(value: Float)
    fun saveGraphicsPreset()
    fun openLogs()
    fun openTaskManager()
    fun exitSession()

    fun backToSessionHome()
    fun setVibrationSlot(slot: Int, enabled: Boolean)
    fun clearLogs()
    fun toggleLogsPaused()
    fun setNewTaskCommand(command: String)
    fun runNewTask()
    fun selectProcess(pid: Int)
    fun killSelectedProcess()
    fun bringSelectedToFront()
    fun toggleAffinityCpu(cpuIndex: Int)
    fun applyAffinity()
}

/** Java-friendly stubs for nested panel actions (Compose controller overrides them). */
abstract class SessionMenuActionsAdapter : SessionMenuActions {
    override fun backToSessionHome() {}
    override fun setVibrationSlot(slot: Int, enabled: Boolean) {}
    override fun clearLogs() {}
    override fun toggleLogsPaused() {}
    override fun setNewTaskCommand(command: String) {}
    override fun runNewTask() {}
    override fun selectProcess(pid: Int) {}
    override fun killSelectedProcess() {}
    override fun bringSelectedToFront() {}
    override fun toggleAffinityCpu(cpuIndex: Int) {}
    override fun applyAffinity() {}
}

private enum class SessionTab(val label: String) {
    QUICK("Quick"),
    DISPLAY("Display"),
    INPUT("Input"),
    GRAPHICS("Graphics"),
    HUD("HUD"),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConsoleSessionMenu(
    model: SessionMenuModel,
    actions: SessionMenuActions,
) {
    ConsoleTheme {
        Box(
            Modifier
                .fillMaxHeight()
                .width(340.dp)
                .background(ConsoleCanvasBrush)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            when (model.panel) {
                SessionPanel.HOME -> SessionHomePanel(model, actions)
                SessionPanel.TASKS -> SessionTasksPanel(model, actions)
                SessionPanel.VIBRATION -> SessionVibrationPanel(model, actions)
                SessionPanel.LOGS -> SessionLogsPanel(model, actions)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionHomePanel(model: SessionMenuModel, actions: SessionMenuActions) {
    var tab by remember { mutableStateOf(SessionTab.QUICK) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Session",
                    color = ConsoleColors.TextPrimary,
                    fontFamily = ConsoleFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    letterSpacing = (-0.3).sp,
                )
                Text(
                    model.title,
                    color = ConsoleColors.TextSecondary,
                    fontFamily = ConsoleFontFamily,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            }
            SessionPill("Done") { actions.closeMenu() }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SessionTab.entries.forEach { t ->
                SessionPill(t.label, filled = tab == t) { tab = t }
            }
        }

        Spacer(Modifier.height(8.dp))

        ConsoleLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            when (tab) {
                SessionTab.QUICK -> {
                    item { ConsoleSectionLabel("Playback") }
                    item {
                        ConsoleCard {
                            SessionActionRow(
                                if (model.paused) "Resume" else "Pause",
                                if (model.paused) "Continue the session" else "Freeze Wine processes",
                            ) { actions.togglePause() }
                            SessionActionRow("Keyboard", "Show on-screen keyboard") {
                                actions.showKeyboard()
                            }
                            SessionActionRow("Control panel", "Edit chassis layout") {
                                actions.openControlPanelEdit()
                            }
                            SessionActionRow("Logs", "Wine / Box64 debug output") {
                                actions.openLogs()
                            }
                            SessionActionRow("Task manager", "CPU, memory, processes") {
                                actions.openTaskManager()
                            }
                        }
                    }
                    item { ConsoleSectionLabel("Leave") }
                    item {
                        ConsoleCard {
                            SessionActionRow(
                                "Exit session",
                                "Return to library",
                                danger = true,
                            ) { actions.exitSession() }
                        }
                    }
                }

                SessionTab.DISPLAY -> {
                    item { ConsoleSectionLabel("Screen") }
                    item {
                        ConsoleCard {
                            if (!model.xrMode) {
                                SessionActionRow("Magnifier", "Zoom into the display") {
                                    actions.openMagnifier()
                                }
                            }
                            SessionActionRow("Picture in picture", "Shrink to a floating window") {
                                actions.enterPip()
                            }
                            SessionActionRow("Toggle fullscreen", "Fit game to the screen") {
                                actions.toggleFullscreen()
                            }
                            SessionToggle(
                                "Soft stretch",
                                "Fill the panel with stretch",
                                model.softStretch,
                            ) { actions.toggleSoftStretch() }
                        }
                    }
                }

                SessionTab.INPUT -> {
                    item { ConsoleSectionLabel("Pointer") }
                    item {
                        ConsoleCard {
                            SessionToggle(
                                "Relative mouse",
                                "Pointer moves like a PC mouse",
                                model.relativeMouse,
                            ) { actions.setRelativeMouse(it) }
                            SessionToggle(
                                "Disable mouse",
                                "Ignore touchpad mouse input",
                                model.mouseDisabled,
                            ) { actions.setMouseDisabled(it) }
                            SessionActionRow("Vibration slots", "Per-controller rumble") {
                                actions.showVibration()
                            }
                        }
                    }
                    item { ConsoleSectionLabel("Touch overlay") }
                    item {
                        ConsoleCard {
                            SessionToggle(
                                "Show touch controls",
                                "Legacy on-screen buttons",
                                model.showTouchControls,
                            ) { actions.setShowTouchControls(it) }
                            SessionToggle(
                                "Auto-hide timeout",
                                "Hide controls after idle",
                                model.touchTimeout,
                            ) { actions.setTouchTimeout(it) }
                            SessionToggle(
                                "Haptics",
                                "Vibrate on control press",
                                model.touchHaptics,
                            ) { actions.setTouchHaptics(it) }
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Text(
                                    "Opacity  ${(model.controlsOpacity * 100).toInt()}%",
                                    color = ConsoleColors.TextPrimary,
                                    fontFamily = ConsoleFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                )
                                Slider(
                                    value = model.controlsOpacity,
                                    onValueChange = { actions.setControlsOpacity(it) },
                                    valueRange = 0.2f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = ConsoleColors.AccentBlue,
                                        activeTrackColor = ConsoleColors.AccentBlue,
                                    ),
                                )
                            }
                        }
                    }
                    item { ConsoleSectionLabel("Profile") }
                    item {
                        ConsoleCard {
                            ChipPicker(
                                options = model.profileNames.ifEmpty { listOf("Disabled") },
                                selectedIndex = model.profileIndex.coerceIn(
                                    0,
                                    (model.profileNames.size - 1).coerceAtLeast(0),
                                ),
                            ) { actions.setInputProfileIndex(it) }
                            SessionActionRow("Edit profiles", "Open controls editor") {
                                actions.editInputProfiles()
                            }
                        }
                    }
                }

                SessionTab.GRAPHICS -> {
                    item { ConsoleSectionLabel("Frame rate") }
                    item {
                        ConsoleCard {
                            ChipPicker(
                                options = listOf("Off", "30", "60", "90", "120"),
                                selectedIndex = model.fpsLimitIndex.coerceIn(0, 4),
                            ) { actions.setFpsLimitIndex(it) }
                        }
                    }
                    item { ConsoleSectionLabel("Upscale & effects") }
                    item {
                        ConsoleCard {
                            SessionToggle(
                                "FSR / SGSR",
                                "Spatial upscaling",
                                model.fsrEnabled,
                            ) { actions.setFsrEnabled(it) }
                            Text(
                                "Post FX",
                                color = ConsoleColors.TextSecondary,
                                fontFamily = ConsoleFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 16.dp, top = 10.dp),
                            )
                            ChipPicker(
                                options = listOf("None", "DLS", "CRT", "HDR", "Natural"),
                                selectedIndex = model.postFxIndex.coerceIn(0, 4),
                            ) { actions.setPostFxIndex(it) }
                            if (model.fsrEnabled || model.postFxIndex == 1) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text(
                                        "Sharpness  ${model.sharpness.toInt()}%",
                                        color = ConsoleColors.TextPrimary,
                                        fontFamily = ConsoleFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                    )
                                    Slider(
                                        value = model.sharpness,
                                        onValueChange = { actions.setSharpness(it) },
                                        valueRange = 0f..100f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = ConsoleColors.AccentBlue,
                                            activeTrackColor = ConsoleColors.AccentBlue,
                                        ),
                                    )
                                }
                            }
                            SessionActionRow("Save graphics preset", "Remember for this container") {
                                actions.saveGraphicsPreset()
                            }
                        }
                    }
                }

                SessionTab.HUD -> {
                    item { ConsoleSectionLabel("Performance overlay") }
                    item {
                        ConsoleCard {
                            ChipPicker(
                                options = listOf("Off", "Classic", "Modern"),
                                selectedIndex = when (model.hudMode) {
                                    1 -> 1
                                    2 -> 2
                                    else -> 0
                                },
                            ) {
                                actions.setHudMode(
                                    when (it) {
                                        1 -> 1
                                        2 -> 2
                                        else -> 0
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionPanelHeader(title: String, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SessionPill("‹ Back") { onBack() }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            color = ConsoleColors.TextPrimary,
            fontFamily = ConsoleFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionTasksPanel(model: SessionMenuModel, actions: SessionMenuActions) {
    val selected = model.processes.firstOrNull { it.pid == model.selectedProcessPid }
    val cpuCount = remember { Runtime.getRuntime().availableProcessors().coerceAtLeast(1) }

    Column(Modifier.fillMaxSize()) {
        SessionPanelHeader("Task manager", onBack = { actions.backToSessionHome() })

        ConsoleLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item { ConsoleSectionLabel("System") }
            item {
                ConsoleCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatBlock("CPU", "${model.cpuPercent}%", Modifier.weight(1f))
                        StatBlock("Memory", model.memoryText, Modifier.weight(1f))
                    }
                }
            }

            item { ConsoleSectionLabel("New task") }
            item {
                ConsoleCard {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clip(ConsoleRowShape)
                            .background(ConsoleColors.Canvas)
                            .padding(12.dp),
                    ) {
                        BasicTextField(
                            value = model.newTaskCommand,
                            onValueChange = { actions.setNewTaskCommand(it) },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = ConsoleColors.TextPrimary,
                                fontFamily = ConsoleFontFamily,
                                fontSize = 15.sp,
                            ),
                            cursorBrush = SolidColor(ConsoleColors.AccentBlue),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    SessionActionRow("Run", "Launch command in the container") {
                        actions.runNewTask()
                    }
                }
            }

            item { ConsoleSectionLabel("Processes (${model.processes.size})") }
            item {
                ConsoleCard {
                    if (model.processes.isEmpty()) {
                        Text(
                            "No processes yet",
                            color = ConsoleColors.TextSecondary,
                            fontFamily = ConsoleFontFamily,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        model.processes.forEach { proc ->
                            val isSel = proc.pid == model.selectedProcessPid
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSel) ConsoleColors.AccentBlue.copy(alpha = 0.08f)
                                        else Color.Transparent,
                                    )
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { actions.selectProcess(proc.pid) },
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    proc.name + if (proc.wow64) " · 32-bit" else "",
                                    color = ConsoleColors.TextPrimary,
                                    fontFamily = ConsoleFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                )
                                Text(
                                    "PID ${proc.pid}  ·  ${proc.memory}",
                                    color = ConsoleColors.TextSecondary,
                                    fontFamily = ConsoleFontFamily,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }

            if (selected != null) {
                item { ConsoleSectionLabel(selected.name) }
                item {
                    ConsoleCard {
                        SessionActionRow("Bring to front", "Focus this window") {
                            actions.bringSelectedToFront()
                        }
                        SessionActionRow("End process", "Force quit", danger = true) {
                            actions.killSelectedProcess()
                        }
                        Text(
                            "CPU affinity",
                            color = ConsoleColors.TextSecondary,
                            fontFamily = ConsoleFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            repeat(cpuCount) { i ->
                                val on = (model.affinityMask and (1 shl i)) != 0
                                SessionPill("CPU$i", filled = on) {
                                    actions.toggleAffinityCpu(i)
                                }
                            }
                        }
                        SessionActionRow("Apply affinity", "Set selected CPU cores") {
                            actions.applyAffinity()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(ConsoleRowShape)
            .background(ConsoleColors.Canvas)
            .padding(12.dp),
    ) {
        Text(
            label,
            color = ConsoleColors.TextSecondary,
            fontFamily = ConsoleFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            value,
            color = ConsoleColors.AccentBlue,
            fontFamily = ConsoleFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun SessionVibrationPanel(model: SessionMenuModel, actions: SessionMenuActions) {
    Column(Modifier.fillMaxSize()) {
        SessionPanelHeader("Vibration", onBack = { actions.backToSessionHome() })
        ConsoleLazyColumn(
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item { ConsoleSectionLabel("Controller slots") }
            item {
                ConsoleCard {
                    if (model.vibrationSlots.isEmpty()) {
                        Text(
                            "No controllers",
                            color = ConsoleColors.TextSecondary,
                            fontFamily = ConsoleFontFamily,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        model.vibrationSlots.forEachIndexed { i, enabled ->
                            SessionToggle(
                                "Controller ${i + 1}",
                                "Rumble for this slot",
                                enabled,
                            ) { actions.setVibrationSlot(i, it) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionLogsPanel(model: SessionMenuModel, actions: SessionMenuActions) {
    Column(Modifier.fillMaxSize()) {
        SessionPanelHeader(
            title = "Logs",
            onBack = { actions.backToSessionHome() },
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SessionPill(if (model.logsPaused) "Resume" else "Pause") {
                        actions.toggleLogsPaused()
                    }
                    SessionPill("Clear") { actions.clearLogs() }
                }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(ConsoleRowShape)
                .background(Color(0xFF1C1C1E))
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (model.logLines.isEmpty()) {
                Text(
                    "No log lines yet",
                    color = Color(0xFF8E8E93),
                    fontFamily = ConsoleFontFamily,
                    fontSize = 13.sp,
                )
            } else {
                model.logLines.forEach { line ->
                    Text(
                        line,
                        color = Color(0xFFE5E5EA),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionPill(label: String, filled: Boolean = false, onClick: () -> Unit) {
    ConsolePill(
        label = label,
        filled = filled,
        fontSize = 13.sp,
        horizontalPadding = 12.dp,
        verticalPadding = 8.dp,
        onClick = onClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChipPicker(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { i, label ->
            SessionPill(label, filled = i == selectedIndex) { onSelect(i) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionActionRow(
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, 0.98f)
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            title,
            color = if (danger) ConsoleColors.Danger else ConsoleColors.TextPrimary,
            fontFamily = ConsoleFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        Text(
            subtitle,
            color = ConsoleColors.TextSecondary,
            fontFamily = ConsoleFontFamily,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun SessionToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = ConsoleColors.TextPrimary,
                fontFamily = ConsoleFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            Text(
                subtitle,
                color = ConsoleColors.TextSecondary,
                fontFamily = ConsoleFontFamily,
                fontSize = 13.sp,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ConsoleColors.AccentBlue,
            ),
        )
    }
}
