package com.winlator.cmod.console.compat

/**
 * Maps wine/DXVK log snippets + exit status → user-facing tip + optional retry action.
 */
object LaunchFailureClassifier {

    enum class RetryAction {
        NONE,
        FORCE_BOX64,
        FORCE_UNITY_D3D11,
        FORCE_CRT_BUILTIN,
    }

    data class Verdict(
        val isError: Boolean,
        val title: String,
        val message: String,
        val retryAction: RetryAction = RetryAction.NONE,
    )

    fun classify(
        logLines: List<String>,
        exitStatus: Int,
        sessionMs: Long,
        shortcutName: String?,
        compatTags: String?,
    ): Verdict {
        val name = shortcutName?.takeIf { it.isNotBlank() } ?: "Game"
        val text = logLines.joinToString("\n").lowercase()
        val tags = compatTags.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

        // User quit after a real session — not an error card.
        if (sessionMs >= 30_000L && exitStatus == 0) {
            return Verdict(false, name, "Session ended.", RetryAction.NONE)
        }
        if (sessionMs >= 60_000L) {
            return Verdict(false, name, "Session ended.", RetryAction.NONE)
        }

        when {
            text.contains("msvcp100") && (text.contains("failed to initialize") || text.contains("0xc0000005")) ->
                return Verdict(
                    true,
                    "$name quit early",
                    "VC++ 2010 runtime crashed (msvcp100). Retry with Box64 + builtin CRT overrides.",
                    RetryAction.FORCE_CRT_BUILTIN,
                )

            text.contains("msvcr100") && (text.contains("failed") || text.contains("0xc0000005")) ->
                return Verdict(
                    true,
                    "$name quit early",
                    "VC++ 2010 runtime issue (msvcr100). Retry with Box64 + builtin CRT.",
                    RetryAction.FORCE_CRT_BUILTIN,
                )

            text.contains("unityplayer") && (text.contains("not found") || text.contains("cannot load") || text.contains("error loading")) ->
                return Verdict(
                    true,
                    "$name failed",
                    "UnityPlayer.dll missing — this install is incomplete. Re-add from the full game folder.",
                    RetryAction.NONE,
                )

            text.contains("dxgi_error") || text.contains("dxvk") && text.contains("error") ->
                return Verdict(
                    true,
                    "$name graphics error",
                    "DXVK/DXGI reported an error. Try Vulkan driver settings or a different DX wrapper in shortcut settings.",
                    RetryAction.NONE,
                )

            CompatTags.UNITY in tags && sessionMs < 15_000L ->
                return Verdict(
                    true,
                    "$name quit early",
                    "Unity title exited quickly. Retry with Box64 + -force-d3d11.",
                    RetryAction.FORCE_UNITY_D3D11,
                )

            CompatTags.I386 in tags && sessionMs < 15_000L ->
                return Verdict(
                    true,
                    "$name quit early",
                    "32-bit EXE exited quickly. Retry with Box64 (recommended over FEX for WoW64).",
                    RetryAction.FORCE_BOX64,
                )

            exitStatus != 0 && sessionMs < 20_000L ->
                return Verdict(
                    true,
                    "$name quit early",
                    "Guest process exited with status $exitStatus after ${sessionMs / 1000}s.",
                    if (CompatTags.UNITY in tags) RetryAction.FORCE_UNITY_D3D11 else RetryAction.FORCE_BOX64,
                )

            sessionMs < 8_000L ->
                return Verdict(
                    true,
                    "$name quit early",
                    "Session lasted under 8 seconds — often a missing DLL, wrong EXE, or emulator mismatch.",
                    RetryAction.FORCE_BOX64,
                )
        }

        return Verdict(false, name, "Session ended.", RetryAction.NONE)
    }
}
