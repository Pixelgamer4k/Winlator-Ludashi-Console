package com.winlator.cmod.console.compat

import android.util.Log
import com.winlator.cmod.container.Shortcut
import java.io.File

/**
 * Writes probe-driven Extra Data onto shortcuts (Add Game + one-shot first Play).
 */
object CompatApplier {

    const val VERSION = "1"
    private const val TAG = "CompatApplier"

    data class ApplyResult(
        val profile: CompatProfile,
        val applied: Boolean,
        val blockedReason: String? = null,
    )

    fun probeExe(exe: File): CompatProfile = GameProbe.probe(exe)

    /**
     * Block Add Game when the chosen EXE is a known-incomplete Unity stub.
     */
    fun incompleteUnityMessage(profile: CompatProfile): String? {
        if (!profile.has(CompatTags.UNITY_INCOMPLETE)) return null
        return "Incomplete Unity game: UnityPlayer.dll / *_Data missing next to “${profile.exe.name}”. " +
            "Pick the full install folder (e.g. under Download), not a tiny stub copy."
    }

    fun applyToNewShortcut(shortcut: Shortcut, exe: File): ApplyResult {
        val profile = GameProbe.probe(exe)
        val block = incompleteUnityMessage(profile)
        if (block != null) {
            return ApplyResult(profile, applied = false, blockedReason = block)
        }
        writeExtras(shortcut, profile, force = true)
        return ApplyResult(profile, applied = true)
    }

    /**
     * One-shot migration for bare .desktop files that predate the compat layer.
     * Returns true if extras were written.
     */
    fun ensureMigrated(shortcut: Shortcut): Boolean {
        if (shortcut.getExtra("compatVersion") == VERSION) return false
        val exe = resolveExeFile(shortcut) ?: run {
            Log.w(TAG, "Cannot migrate — no local EXE for ${shortcut.name}")
            shortcut.putExtra("compatVersion", VERSION)
            shortcut.putExtra("compatTags", "unresolved")
            shortcut.saveData()
            return true
        }
        val profile = GameProbe.probe(exe)
        writeExtras(shortcut, profile, force = false)
        Log.i(TAG, "Migrated ${shortcut.name}: ${profile.summary()}")
        return true
    }

    fun resolveExeFile(shortcut: Shortcut): File? {
        val path = shortcut.path?.trim().orEmpty()
        if (path.isEmpty()) return null
        // Our console importer stores absolute Unix paths.
        val unix = File(path)
        if (unix.isFile) return unix
        // Wine-style path — not resolvable without drive map; skip.
        if (path.contains(":\\") || path.contains(":/")) return null
        return null
    }

    private fun writeExtras(shortcut: Shortcut, profile: CompatProfile, force: Boolean) {
        val suggested = CompatRules.extrasFor(profile)
        for ((key, value) in suggested) {
            val existing = shortcut.getExtra(key)
            if (force || existing.isEmpty()) {
                if (key == "envVars" && existing.isNotEmpty() && !force) {
                    shortcut.putExtra(key, mergeEnvVars(existing, value))
                } else if (key == "execArgs" && existing.isNotEmpty() && !force) {
                    if (!existing.contains(value, ignoreCase = true)) {
                        shortcut.putExtra(key, (existing.trim() + " " + value).trim())
                    }
                } else {
                    shortcut.putExtra(key, value)
                }
            } else if (key == "envVars") {
                shortcut.putExtra(key, mergeEnvVars(existing, value))
            }
        }
        shortcut.putExtra("compatVersion", VERSION)
        shortcut.putExtra("compatTags", profile.tags.joinToString(","))
        shortcut.putExtra("peMachine", profile.machine.name)
        shortcut.saveData()
    }

    private fun mergeEnvVars(existing: String, addition: String): String {
        // EnvVars format: KEY=VAL KEY2=VAL2 (space-separated)
        val map = linkedMapOf<String, String>()
        fun ingest(s: String) {
            for (part in s.trim().split(Regex("\\s+"))) {
                val i = part.indexOf('=')
                if (i > 0) map[part.substring(0, i)] = part.substring(i + 1)
            }
        }
        ingest(existing)
        ingest(addition)
        return map.entries.joinToString(" ") { "${it.key}=${it.value}" }
    }
}
