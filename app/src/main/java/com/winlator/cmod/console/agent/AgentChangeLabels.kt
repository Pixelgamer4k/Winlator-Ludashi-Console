package com.winlator.cmod.console.agent

/**
 * Human-readable labels for settings the hive agent changes.
 */
object AgentChangeLabels {

    fun describe(key: String, value: String): String {
        val pretty = when (key) {
            "emulator" -> "CPU emulator set to **$value** (runs the Windows EXE on ARM)"
            "box64Preset" -> "Box64 preset set to **$value** (stability vs performance trade-off)"
            "box64Version" -> "Box64 version set to **$value**"
            "dxwrapper" -> "DirectX wrapper set to **$value** (how D3D talks to Vulkan)"
            "dxwrapperConfig" -> "DirectX wrapper config updated"
            "envVars" -> "Environment / DLL overrides updated (`$value`)"
            "execArgs" -> "Launch arguments set to `$value`"
            "disableXinput" -> "Disable XInput set to **$value**"
            "rendererNative" -> "Native renderer flag set to **$value**"
            "graphicsDriver" -> "Graphics driver backend set to **$value**"
            "graphicsDriverConfig" -> "Graphics driver config string updated"
            "graphicsDriverVersion" -> "Vulkan / Adreno (Turnip) driver set to **$value**"
            "audioDriver" -> "Audio driver set to **$value**"
            "screenSize" -> "Resolution set to **$value**"
            "wincomponents" -> "Windows components updated (`$value`)"
            "fexcorePreset" -> "FEXCore preset set to **$value**"
            "controlsProfile" -> "Controls profile set to **$value**"
            "fullscreenStretched" -> "Fullscreen stretch set to **$value**"
            "simTouchScreen" -> "Simulated touch screen set to **$value**"
            "inputType" -> "Input type set to **$value**"
            "hudMode" -> "HUD / FPS overlay mode set to **$value**"
            else -> "**$key** set to `$value`"
        }
        return pretty
    }

    fun formatApplyResult(target: String, changes: List<Pair<String, String>>): String {
        if (changes.isEmpty()) return "No changes (no fields provided)."
        return buildString {
            appendLine("SUCCESS: Updated \"$target\"")
            appendLine("CHANGES:")
            changes.forEach { (k, v) ->
                appendLine("- ${describe(k, v)}")
            }
            appendLine("INSTRUCTION: Explain these changes to the user in plain language. Say what each setting does and what to try next. Do not dump raw key=value codes or driver IDs alone.")
        }
    }
}
