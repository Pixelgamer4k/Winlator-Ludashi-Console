package com.winlator.cmod.console.compat

/**
 * Maps probe tags → Shortcut [Extra Data] keys that XServerDisplayActivity already reads.
 */
object CompatRules {

    /**
     * Suggested extras. Caller merges with existing shortcut values
     * (empty fields / first-time only unless [force]).
     */
    fun extrasFor(profile: CompatProfile): Map<String, String> {
        val out = linkedMapOf<String, String>()
        out["compatTags"] = profile.tags.joinToString(",")
        out["peMachine"] = profile.machine.name

        val needBox64 =
            profile.has(CompatTags.I386) ||
                profile.has(CompatTags.VCRUN2010) ||
                profile.has(CompatTags.UNITY)

        if (needBox64) {
            out["emulator"] = "Box64"
        }

        if (profile.has(CompatTags.I386) || profile.has(CompatTags.VCRUN2010)) {
            // Builtin CRT avoids native msvcp100 init crashes under WoW64/FEX.
            out["envVars"] = "WINEDLLOVERRIDES=msvcp100,msvcr100=builtin"
            out["box64Preset"] = "COMPATIBILITY"
        }

        if (profile.has(CompatTags.UNITY)) {
            out["emulator"] = "Box64"
            out["box64Preset"] = "COMPATIBILITY"
            out["execArgs"] = "-force-d3d11"
            // Prefer DXVK path for Unity D3D11 (keep vkd3d available for D3D12 titles)
            if (!profile.has(CompatTags.D3D12)) {
                out["dxwrapper"] = "dxvk+vkd3d"
            }
        }

        if (profile.has(CompatTags.D3D9) && !profile.has(CompatTags.UNITY)) {
            out["dxwrapper"] = "dxvk+vkd3d"
        }

        return out
    }

    /** Human tip shown after a failed / short session, keyed by tags. */
    fun tipForTags(tags: Set<String>): String? {
        return when {
            CompatTags.UNITY_INCOMPLETE in tags ->
                "This looks like an incomplete Unity install (missing UnityPlayer.dll / *_Data). Pick the full game folder."
            CompatTags.VCRUN2010 in tags || CompatTags.I386 in tags ->
                "32-bit / VC++2010 title — using Box64 + builtin msvcp100/msvcr100."
            CompatTags.UNITY in tags ->
                "Unity title — Box64 + -force-d3d11 applied."
            else -> null
        }
    }
}
