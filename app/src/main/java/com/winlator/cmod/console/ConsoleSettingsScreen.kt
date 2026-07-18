package com.winlator.cmod.console

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.winlator.cmod.console.agent.AgentConfig
import com.winlator.cmod.console.agent.HiveAgent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION")
@Composable
fun ConsoleSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var cursorSpeed by remember {
        mutableFloatStateOf(prefs.getFloat("cursor_speed", 1f).coerceIn(0.1f, 2f))
    }
    var useDri3 by remember { mutableStateOf(prefs.getBoolean("use_dri3", true)) }
    var cursorLock by remember { mutableStateOf(prefs.getBoolean("cursor_lock", false)) }
    var xinputToggle by remember { mutableStateOf(prefs.getBoolean("xinput_toggle", false)) }
    var wineDebug by remember { mutableStateOf(prefs.getBoolean("enable_wine_debug", false)) }
    var box64Logs by remember { mutableStateOf(prefs.getBoolean("enable_box64_logs", false)) }
    var fileProvider by remember { mutableStateOf(prefs.getBoolean("enable_file_provider", false)) }
    var openBrowser by remember { mutableStateOf(prefs.getBoolean("open_with_android_browser", false)) }
    var shareClipboard by remember { mutableStateOf(prefs.getBoolean("share_android_clipboard", false)) }
    var pauseResume by remember { mutableStateOf(prefs.getBoolean("pause_resume_wine", false)) }
    var highRefresh by remember { mutableStateOf(prefs.getBoolean("high_refresh_rate_mode", false)) }
    var removeLoading by remember {
        mutableStateOf(prefs.getBoolean("remove_loading_bar_when_booting_games", false))
    }
    var consoleHome by remember { mutableStateOf(prefs.getBoolean("console_home_enabled", true)) }

    val initialAi = remember { AgentConfig.load(context) }
    var aiEnabled by remember { mutableStateOf(initialAi.enabled) }
    var aiBaseUrl by remember { mutableStateOf(initialAi.baseUrl) }
    var aiApiKey by remember { mutableStateOf(initialAi.apiKey) }
    var aiModel by remember { mutableStateOf(initialAi.model) }
    var aiTestStatus by remember { mutableStateOf<String?>(null) }
    var aiTesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun saveBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun saveAi() {
        AgentConfig.save(
            context,
            AgentConfig(
                enabled = aiEnabled,
                baseUrl = aiBaseUrl,
                apiKey = aiApiKey,
                model = aiModel,
            ),
        )
    }

    LaunchedEffect(aiBaseUrl, aiApiKey, aiModel, aiEnabled) {
        delay(500)
        saveAi()
    }
    DisposableEffect(Unit) {
        onDispose { saveAi() }
    }

    BackHandler(onBack = onBack)

    ConsoleScaffold(title = "Settings", onBack = onBack) {
        ConsoleScrollColumn(Modifier.padding(bottom = 40.dp)) {
                ConsoleSectionLabel("Console")
                ConsoleCard {
                    PrefSwitch("Console home", "Switch-style library as start screen", consoleHome) {
                        consoleHome = it
                        saveBool("console_home_enabled", it)
                    }
                }

                ConsoleSectionLabel("AI assistant")
                ConsoleCard {
                    PrefSwitch(
                        "Show Hive Agent",
                        "Floating AI button on the library",
                        aiEnabled,
                    ) {
                        aiEnabled = it
                    }
                    PrefField("API base URL", aiBaseUrl, "https://openrouter.ai/api/v1") {
                        aiBaseUrl = it
                    }
                    PrefField("API key", aiApiKey, "sk-… or OpenRouter key") {
                        aiApiKey = it
                    }
                    PrefField("Model", aiModel, "openai/gpt-4.1-mini") {
                        aiModel = it
                    }
                    Text(
                        "Works with OpenRouter, OpenAI, Gemini proxies, Grok, or any OpenAI-compatible URL.",
                        color = ConsoleColors.TextSecondary,
                        fontFamily = ConsoleFontFamily,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    val testInteraction = remember { MutableInteractionSource() }
                    Text(
                        if (aiTesting) "Testing…" else (aiTestStatus ?: "Test connection"),
                        color = ConsoleColors.AccentBlue,
                        fontFamily = ConsoleFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .combinedClickable(
                                interactionSource = testInteraction,
                                indication = null,
                                enabled = !aiTesting,
                                onClick = {
                                    saveAi()
                                    aiTesting = true
                                    aiTestStatus = null
                                    scope.launch {
                                        val result = HiveAgent(context).testConnection()
                                        aiTesting = false
                                        aiTestStatus = result.fold(
                                            onSuccess = { "OK: $it" },
                                            onFailure = { "Failed: ${it.message}" },
                                        )
                                    }
                                },
                            ),
                    )
                }

                ConsoleSectionLabel("Display & input")
                ConsoleCard {
                    Column(Modifier.padding(bottom = 4.dp)) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(
                                "Cursor speed  ${(cursorSpeed * 100).toInt()}%",
                                color = ConsoleColors.TextPrimary,
                                fontFamily = ConsoleFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            )
                            Slider(
                                value = cursorSpeed,
                                onValueChange = { cursorSpeed = it },
                                onValueChangeFinished = {
                                    prefs.edit().putFloat("cursor_speed", cursorSpeed).apply()
                                },
                                valueRange = 0.1f..2f,
                                colors = SliderDefaults.colors(
                                    thumbColor = ConsoleColors.AccentBlue,
                                    activeTrackColor = ConsoleColors.AccentBlue,
                                ),
                            )
                        }
                        PrefSwitch("Use DRI3", "Faster GPU buffer path", useDri3) {
                            useDri3 = it; saveBool("use_dri3", it)
                        }
                        PrefSwitch("Capture external pointer", "Cursor lock", cursorLock) {
                            cursorLock = it; saveBool("cursor_lock", it)
                        }
                        PrefSwitch("XInput toggle", "Prefer XInput gamepads", xinputToggle) {
                            xinputToggle = it; saveBool("xinput_toggle", it)
                        }
                    }
                }

                ConsoleSectionLabel("Logs")
                ConsoleCard {
                    PrefSwitch("Wine debug logs", "Capture wine debug output", wineDebug) {
                        wineDebug = it; saveBool("enable_wine_debug", it)
                    }
                    PrefSwitch("Box64 logs", "Emulator logging", box64Logs) {
                        box64Logs = it; saveBool("enable_box64_logs", it)
                    }
                }

                ConsoleSectionLabel("Experimental")
                ConsoleCard {
                    PrefSwitch("File provider", "Share container files", fileProvider) {
                        fileProvider = it; saveBool("enable_file_provider", it)
                    }
                    PrefSwitch("Open with Android browser", null, openBrowser) {
                        openBrowser = it; saveBool("open_with_android_browser", it)
                    }
                    PrefSwitch("Share Android clipboard", null, shareClipboard) {
                        shareClipboard = it; saveBool("share_android_clipboard", it)
                    }
                    PrefSwitch("Pause / resume Wine", null, pauseResume) {
                        pauseResume = it; saveBool("pause_resume_wine", it)
                    }
                    PrefSwitch("High refresh rate", null, highRefresh) {
                        highRefresh = it; saveBool("high_refresh_rate_mode", it)
                    }
                    PrefSwitch("Hide loading bar", "When booting games", removeLoading) {
                        removeLoading = it; saveBool("remove_loading_bar_when_booting_games", it)
                    }
                }

                Spacer(Modifier.height(8.dp))
                ConsoleCard {
                    val ctx = LocalContext.current
                    val advInteraction = remember { MutableInteractionSource() }
                    Text(
                        "Open advanced tools",
                        color = ConsoleColors.AccentBlue,
                        fontFamily = ConsoleFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .combinedClickable(
                                interactionSource = advInteraction,
                                indication = null,
                                onClick = {
                                    val act = ctx as? com.winlator.cmod.MainActivity
                                        ?: return@combinedClickable
                                    act.navigateTo(com.winlator.cmod.SettingsFragment(), false)
                                },
                            ),
                    )
                }
                Text(
                    "Presets, MIDI fonts, paths, and ImageFS reinstall.",
                    color = ConsoleColors.TextSecondary,
                    fontFamily = ConsoleFontFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                )
        }
    }
}

@Composable
private fun PrefField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    ConsoleTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        singleLine = true,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun PrefSwitch(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = ConsoleColors.TextSecondary,
                    fontFamily = ConsoleFontFamily,
                    fontSize = 13.sp,
                )
            }
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
