package com.winlator.cmod.console
import kotlinx.coroutines.launch

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.box64.Box64Preset
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog
import com.winlator.cmod.contents.ContentsManager
import com.winlator.cmod.core.DefaultVersion
import com.winlator.cmod.core.GPUInformation
import com.winlator.cmod.core.WineInfo
import com.winlator.cmod.core.WineThemeManager
import com.winlator.cmod.fexcore.FEXCorePreset
import com.winlator.cmod.winhandler.WinHandler
import org.json.JSONObject

private enum class EditorTab(val label: String) {
    GENERAL("General"),
    ENVIRONMENT("Environment"),
    ADVANCED("Advanced"),
    COMPONENTS("Components"),
}

/**
 * Full-screen container create/edit — modern Console UI only (no classic detail fragment).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConsoleContainerEditorScreen(
    containerId: Int?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val manager = remember { ContainerManager(context) }
    val existing = remember(containerId) {
        if (containerId != null && containerId > 0) manager.getContainerById(containerId) else null
    }
    val isEdit = existing != null

    var saving by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { EditorTab.entries.size })
    val scope = rememberCoroutineScope()
    val tab = EditorTab.entries[pagerState.currentPage]


    var name by remember {
        mutableStateOf(existing?.name ?: "container-${manager.nextContainerId}")
    }
    var screenSize by remember { mutableStateOf(existing?.screenSize ?: Container.DEFAULT_SCREEN_SIZE) }
    var graphicsDriver by remember {
        mutableStateOf(existing?.graphicsDriver ?: Container.DEFAULT_GRAPHICS_DRIVER)
    }
    var graphicsDriverConfig by remember {
        mutableStateOf(existing?.graphicsDriverConfig ?: Container.DEFAULT_GRAPHICSDRIVERCONFIG)
    }
    var dxWrapper by remember { mutableStateOf(existing?.dxWrapper ?: Container.DEFAULT_DXWRAPPER) }
    var dxWrapperConfig by remember {
        mutableStateOf(existing?.dxWrapperConfig ?: Container.DEFAULT_DXWRAPPERCONFIG)
    }
    var audioDriver by remember { mutableStateOf(existing?.audioDriver ?: Container.DEFAULT_AUDIO_DRIVER) }
    var emulator by remember { mutableStateOf(existing?.emulator ?: Container.DEFAULT_EMULATOR) }
    var rendererNative by remember { mutableStateOf(existing?.isRendererNative ?: false) }
    var fullscreen by remember { mutableStateOf(existing?.isFullscreenStretched ?: false) }
    var hudMode by remember {
        mutableStateOf(
            existing?.getExtra("hudMode")?.ifEmpty {
                if (existing.isShowFPS) "1" else "0"
            } ?: "0",
        )
    }
    var lcAll by remember { mutableStateOf(existing?.lC_ALL ?: "") }
    var midiSf by remember { mutableStateOf(existing?.getMIDISoundFont() ?: "") }
    var drives by remember { mutableStateOf(existing?.drives ?: Container.DEFAULT_DRIVES) }
    var desktopTheme by remember {
        mutableStateOf(existing?.desktopTheme ?: WineThemeManager.DEFAULT_DESKTOP_THEME)
    }
    var envVars by remember { mutableStateOf(existing?.envVars ?: Container.DEFAULT_ENV_VARS) }

    var box64Preset by remember { mutableStateOf(existing?.box64Preset ?: Box64Preset.COMPATIBILITY) }
    var box64Version by remember { mutableStateOf(existing?.box64Version ?: DefaultVersion.BOX64) }
    var fexPreset by remember { mutableStateOf(existing?.fexCorePreset ?: FEXCorePreset.INTERMEDIATE) }
    var fexVersion by remember { mutableStateOf(existing?.fexCoreVersion ?: DefaultVersion.FEXCORE) }
    var startup by remember {
        mutableIntStateOf((existing?.startupSelection ?: Container.STARTUP_SELECTION_ESSENTIAL).toInt())
    }
    var exclusiveXInput by remember { mutableStateOf(existing?.isExclusiveXInput ?: true) }
    var enableXInput by remember {
        mutableStateOf(
            ((existing?.inputType ?: WinHandler.DEFAULT_INPUT_TYPE).toInt() and
                WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()) != 0,
        )
    }
    var enableDInput by remember {
        mutableStateOf(
            ((existing?.inputType ?: 0).toInt() and WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()) != 0,
        )
    }
    var syncCpu by remember { mutableStateOf(existing?.isSyncCpuTopology ?: false) }
    var cpuList by remember {
        mutableStateOf(existing?.getCPUList(true) ?: Container.getFallbackCPUList())
    }
    var cpuListWow by remember {
        mutableStateOf(existing?.getCPUListWoW64(true) ?: Container.getFallbackCPUListWoW64())
    }

    val winComponents = remember {
        mutableStateMapOf<String, String>().also { map ->
            parseWinComponents(existing?.winComponents ?: Container.DEFAULT_WINCOMPONENTS)
                .forEach { (k, v) -> map[k] = v }
        }
    }

    fun save() {
        if (name.isBlank()) {
            Toast.makeText(context, "Name required", Toast.LENGTH_SHORT).show()
            return
        }
        saving = true
        val inputType = (
            (if (enableXInput) WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt() else 0) or
                (if (enableDInput) WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt() else 0)
            ).toByte()
        val winStr = winComponents.entries.joinToString(",") { "${it.key}=${it.value}" }
        val showFpsFlag = hudMode != "0"

        if (isEdit && existing != null) {
            existing.name = name.trim()
            existing.screenSize = screenSize
            existing.graphicsDriver = graphicsDriver
            existing.graphicsDriverConfig = graphicsDriverConfig
            existing.dxWrapper = dxWrapper
            existing.dxWrapperConfig = dxWrapperConfig
            existing.audioDriver = audioDriver
            existing.emulator = emulator
            existing.isRendererNative = rendererNative
            existing.isFullscreenStretched = fullscreen
            existing.isShowFPS = showFpsFlag
            existing.putExtra("hudMode", hudMode)
            existing.setLC_ALL(lcAll)
            existing.setMidiSoundFont(midiSf)
            existing.drives = drives
            existing.desktopTheme = desktopTheme
            existing.envVars = envVars
            existing.box64Preset = box64Preset
            existing.box64Version = box64Version
            existing.fexCorePreset = fexPreset
            existing.fexCoreVersion = fexVersion
            existing.startupSelection = startup.toByte()
            existing.isExclusiveXInput = exclusiveXInput
            existing.inputType = inputType.toInt()
            existing.isSyncCpuTopology = syncCpu
            existing.cpuList = cpuList
            existing.cpuListWoW64 = cpuListWow
            existing.winComponents = winStr
            existing.saveData()
            saving = false
            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
            onBack()
        } else {
            val contentsManager = ContentsManager(context)
            contentsManager.syncContents()
            val gfxConfig = HashMap(GraphicsDriverConfigDialog.parseGraphicsDriverConfig(graphicsDriverConfig))
            if (gfxConfig["version"].isNullOrBlank()) {
                gfxConfig["version"] =
                    if (GPUInformation.isDriverSupported(DefaultVersion.WRAPPER_ADRENO, context)) {
                        DefaultVersion.WRAPPER_ADRENO
                    } else {
                        DefaultVersion.WRAPPER
                    }
            }
            val data = JSONObject().apply {
                put("name", name.trim())
                put("screenSize", screenSize)
                put("envVars", envVars)
                put("cpuList", cpuList)
                put("cpuListWoW64", cpuListWow)
                put("graphicsDriver", graphicsDriver)
                put("graphicsDriverConfig", GraphicsDriverConfigDialog.toGraphicsDriverConfig(gfxConfig))
                put("dxwrapper", dxWrapper)
                put("dxwrapperConfig", dxWrapperConfig)
                put("audioDriver", audioDriver)
                put("emulator", emulator)
                put("wincomponents", winStr)
                put("drives", drives)
                put("showFPS", showFpsFlag)
                put("hudMode", hudMode)
                put("fullscreenStretched", fullscreen)
                put("exclusiveXInput", exclusiveXInput)
                put("inputType", inputType.toInt())
                put("startupSelection", startup)
                put("box64Version", box64Version)
                put("box64Preset", box64Preset)
                put("fexcoreVersion", fexVersion)
                put("fexcorePreset", fexPreset)
                put("desktopTheme", desktopTheme)
                put("wineVersion", WineInfo.MAIN_WINE_VERSION.identifier())
                put("midiSoundFont", midiSf)
                put("lc_all", lcAll)
                put("primaryController", 1)
                put("rendererNative", rendererNative)
                put("rendererPresentMode", "fifo")
                if (syncCpu) put("syncCpuTopology", true)
            }
            manager.createContainerAsync(data, contentsManager) { created ->
                saving = false
                if (created != null) {
                    Toast.makeText(context, "Container created", Toast.LENGTH_SHORT).show()
                    onBack()
                } else {
                    Toast.makeText(context, "Create failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    BackHandler(onBack = onBack)

    ConsoleScaffold(
        title = if (isEdit) "Edit container" else "New container",
        onBack = onBack,
        // On General, edge swipe pops; on other tabs the pager owns horizontal swipes.
        edgeSwipeBack = pagerState.currentPage == 0,
        trailing = {
            EditorPill("Save", filled = true, enabled = !saving) { save() }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            EditorTabRow(
                selected = tab,
                onSelect = { t ->
                    scope.launch {
                        pagerState.animateScrollToPage(
                            page = t.ordinal,
                            animationSpec = tween(360, easing = ConsoleMotionEasing),
                        )
                    }
                },
            )

            if (saving) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ConsoleColors.AccentBlue)
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                    beyondViewportPageCount = 0,
                    flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapAnimationSpec = tween(360, easing = ConsoleMotionEasing),
                    ),
                ) { page ->
                ConsoleScrollColumn(Modifier.padding(bottom = 40.dp)) {
                    when (EditorTab.entries[page]) {
                        EditorTab.GENERAL -> {
                            ConsoleSectionLabel("Identity")

                            ConsoleCard {
                                EditorFieldLabel("Name")
                                EditorTextField(name, singleLine = true) { name = it }
                                EditorFieldLabel("Screen size")
                                ChipRow(
                                    listOf("540x360", "640x360", "960x544", "1280x720", "1920x1080")
                                        .let { base -> if (screenSize in base) base else base + screenSize },
                                    screenSize,
                                ) { screenSize = it }
                                EditorFieldLabel("Custom size (WxH)")
                                EditorTextField(screenSize, singleLine = true) { screenSize = it }
                            }
                            ConsoleSectionLabel("Graphics & audio")

                            ConsoleCard {
                                EditorFieldLabel("Graphics driver")
                                ChipRow(listOf("wrapper"), graphicsDriver) { graphicsDriver = it }
                                EditorFieldLabel("DX wrapper")
                                ChipRow(listOf("dxvk+vkd3d", "wined3d"), dxWrapper) { dxWrapper = it }
                                EditorFieldLabel("Renderer")
                                ChipRow(
                                    listOf("Vulkan", "Native"),
                                    if (rendererNative) "Native" else "Vulkan",
                                ) { rendererNative = it == "Native" }
                                EditorFieldLabel("Audio")
                                ChipRow(listOf("alsa", "pulseaudio"), audioDriver) { audioDriver = it }
                                EditorToggle("Fullscreen stretched", fullscreen) { fullscreen = it }
                            }
                            ConsoleSectionLabel("Emulation")

                            ConsoleCard {
                                EditorFieldLabel("Emulator (32-bit / WoW64)")
                                ChipRow(listOf("Box64", "FEXCore"), emulator) { emulator = it }
                                EditorFieldLabel("Performance HUD")
                                ChipRow(
                                    listOf("Off", "Classic", "Modern"),
                                    when (hudMode) {
                                        "1" -> "Classic"
                                        "2" -> "Modern"
                                        else -> "Off"
                                    },
                                ) {
                                    hudMode = when (it) {
                                        "Classic" -> "1"
                                        "Modern" -> "2"
                                        else -> "0"
                                    }
                                }
                            }
                            ConsoleSectionLabel("Locale & theme")

                            ConsoleCard {
                                EditorFieldLabel("LC_ALL (empty = system)")
                                EditorTextField(lcAll, singleLine = true, placeholder = "e.g. en_US.utf8") {
                                    lcAll = it
                                }
                                EditorFieldLabel("MIDI soundfont filename")
                                EditorTextField(midiSf, singleLine = true, placeholder = "(none)") {
                                    midiSf = it
                                }
                                EditorFieldLabel("Desktop theme")
                                EditorTextField(desktopTheme, singleLine = true) { desktopTheme = it }
                                EditorFieldLabel("Drives")
                                EditorTextField(drives, singleLine = false, minLines = 2) { drives = it }
                            }

                        }

                        EditorTab.ENVIRONMENT -> {
                            ConsoleSectionLabel("Environment variables")

                            ConsoleCard {
                                Text(
                                    "Space-separated KEY=value pairs",
                                    color = ConsoleColors.TextSecondary,
                                    fontFamily = ConsoleFontFamily,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                                EditorTextField(envVars, singleLine = false, minLines = 8) { envVars = it }
                            }

                        }

                        EditorTab.ADVANCED -> {
                            ConsoleSectionLabel("Box64")

                            ConsoleCard {
                                EditorFieldLabel("Preset")
                                ChipRow(
                                    listOf(
                                        Box64Preset.STABILITY,
                                        Box64Preset.COMPATIBILITY,
                                        Box64Preset.INTERMEDIATE,
                                        Box64Preset.PERFORMANCE,
                                    ),
                                    box64Preset,
                                ) { box64Preset = it }
                                EditorFieldLabel("Version")
                                EditorTextField(box64Version, singleLine = true) { box64Version = it }
                            }
                            ConsoleSectionLabel("FEXCore")

                            ConsoleCard {
                                EditorFieldLabel("Preset")
                                ChipRow(
                                    listOf(
                                        FEXCorePreset.COMPATIBILITY,
                                        FEXCorePreset.INTERMEDIATE,
                                        FEXCorePreset.PERFORMANCE,
                                    ),
                                    fexPreset,
                                ) { fexPreset = it }
                                EditorFieldLabel("Version")
                                EditorTextField(fexVersion, singleLine = true) { fexVersion = it }
                            }
                            ConsoleSectionLabel("Startup & input")

                            ConsoleCard {
                                EditorFieldLabel("Startup services")
                                ChipRow(
                                    listOf("Normal", "Essential", "Aggressive"),
                                    when (startup) {
                                        Container.STARTUP_SELECTION_NORMAL.toInt() -> "Normal"
                                        Container.STARTUP_SELECTION_AGGRESSIVE.toInt() -> "Aggressive"
                                        else -> "Essential"
                                    },
                                ) {
                                    startup = when (it) {
                                        "Normal" -> Container.STARTUP_SELECTION_NORMAL.toInt()
                                        "Aggressive" -> Container.STARTUP_SELECTION_AGGRESSIVE.toInt()
                                        else -> Container.STARTUP_SELECTION_ESSENTIAL.toInt()
                                    }
                                }
                                EditorToggle("XInput", enableXInput) { enableXInput = it }
                                EditorToggle("DInput", enableDInput) { enableDInput = it }
                                EditorToggle("Exclusive XInput", exclusiveXInput) { exclusiveXInput = it }
                                EditorToggle("Sync CPU topology", syncCpu) { syncCpu = it }
                            }
                            ConsoleSectionLabel("CPU affinity")

                            ConsoleCard {
                                EditorFieldLabel("CPU list")
                                EditorTextField(cpuList, singleLine = true) { cpuList = it }
                                EditorFieldLabel("CPU list (WoW64)")
                                EditorTextField(cpuListWow, singleLine = true) { cpuListWow = it }
                            }

                        }

                        EditorTab.COMPONENTS -> {
                            ConsoleSectionLabel("Win components")

                            ConsoleCard {
                                winComponents.keys.sorted().forEach { key ->
                                    EditorToggle(
                                        title = key,
                                        checked = winComponents[key] == "1",
                                    ) { on ->
                                        winComponents[key] = if (on) "1" else "0"
                                    }
                                }
                                if (winComponents.isEmpty()) {
                                    Text(
                                        "No components configured",
                                        color = ConsoleColors.TextSecondary,
                                        fontFamily = ConsoleFontFamily,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            }

                        }
                    }
                }
                }
            }
        }
    }
}

private fun parseWinComponents(raw: String): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    return raw.split(",")
        .mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null
            else part.substring(0, i).trim() to part.substring(i + 1).trim()
        }
        .toMap()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorTabRow(selected: EditorTab, onSelect: (EditorTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EditorTab.entries.forEach { t ->
            EditorPill(t.label, filled = selected == t) { onSelect(t) }
        }
    }
}

@Composable
private fun EditorPill(
    label: String,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ConsolePill(
        label = label,
        filled = filled,
        enabled = enabled,
        onClick = onClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    ConsoleDisableOverscroll {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { opt ->
                EditorPill(opt, filled = opt == selected) { onSelect(opt) }
            }
        }
    }
}

@Composable
private fun EditorFieldLabel(text: String) {
    Text(
        text,
        color = ConsoleColors.TextSecondary,
        fontFamily = ConsoleFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun EditorTextField(
    value: String,
    singleLine: Boolean,
    minLines: Int = 1,
    placeholder: String = "",
    onValueChange: (String) -> Unit,
) {
    ConsoleTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        singleLine = singleLine,
        minLines = minLines,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun EditorToggle(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = ConsoleColors.TextPrimary,
            fontFamily = ConsoleFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
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
