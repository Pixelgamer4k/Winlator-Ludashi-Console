package com.winlator.cmod.console.agent

import android.os.Environment
import com.winlator.cmod.console.ShortcutImporter
import java.io.File

/**
 * Scans phone storage for PC game EXEs the hive agent can import.
 */
object AgentStorageScan {

    data class Hit(
        val exe: File,
        val folder: File,
        val score: Int,
    )

    fun defaultRoots(): List<File> {
        val root = Environment.getExternalStorageDirectory()
        return listOf(
            File(root, "Download"),
            File(root, "Downloads"),
            ShortcutImporter.gamesDir(),
            File(root, "Winlator"),
            root,
        ).distinctBy { it.absolutePath }
            .filter { it.isDirectory }
    }

    /**
     * Find game EXEs under [root] (or default roots). When [query] is set, prefer
     * folder/exe names that match (e.g. "GTA 5", "gta5").
     */
    fun search(
        query: String? = null,
        rootPath: String? = null,
        maxDepth: Int = 4,
        limit: Int = 40,
    ): List<Hit> {
        val roots = if (!rootPath.isNullOrBlank()) {
            val f = File(rootPath.trim())
            if (f.isDirectory) listOf(f) else emptyList()
        } else {
            defaultRoots()
        }
        if (roots.isEmpty()) return emptyList()

        val tokens = normalizeTokens(query)
        val hits = mutableListOf<Hit>()
        val seen = HashSet<String>()

        for (root in roots) {
            walk(root, depth = 0, maxDepth = maxDepth.coerceIn(1, 6)) { exe ->
                val folder = exe.parentFile ?: return@walk
                val key = exe.absolutePath
                if (!seen.add(key)) return@walk
                val score = scoreHit(exe, folder, tokens)
                if (tokens.isNotEmpty() && score < 10) return@walk
                hits += Hit(exe, folder, score)
            }
        }

        return hits
            .sortedWith(compareByDescending<Hit> { it.score }.thenBy { it.exe.name.lowercase() })
            .take(limit)
    }

    fun listDirectory(path: String, limit: Int = 80): String {
        val dir = File(path.trim())
        if (!dir.exists()) return "Path not found: $path"
        if (!dir.isDirectory) {
            return if (dir.isFile) {
                "FILE ${dir.name} size=${dir.length()} path=${dir.absolutePath}"
            } else {
                "Not a directory: $path"
            }
        }
        val children = dir.listFiles()?.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() },
        ) ?: return "Cannot list: $path (permission?)"

        val lines = mutableListOf<String>()
        lines += "DIR ${dir.absolutePath} (${children.size} entries)"
        for (c in children.take(limit)) {
            when {
                c.isDirectory -> lines += "  [dir]  ${c.name}/"
                ShortcutImporter.isSupportedName(c.name) ->
                    lines += "  [exe]  ${c.name} (${c.length()} bytes)"
                else -> lines += "  [file] ${c.name}"
            }
        }
        if (children.size > limit) lines += "  … +${children.size - limit} more"
        return lines.joinToString("\n")
    }

    fun formatHits(hits: List<Hit>): String {
        if (hits.isEmpty()) return "No matching game EXEs found."
        return hits.joinToString("\n") { h ->
            "- name=\"${h.exe.nameWithoutExtension}\" exe=${h.exe.absolutePath} folder=${h.folder.absolutePath} score=${h.score}"
        }
    }

    private fun walk(dir: File, depth: Int, maxDepth: Int, onExe: (File) -> Unit) {
        if (depth > maxDepth) return
        if (shouldSkipDir(dir.name)) return
        val children = try {
            dir.listFiles()
        } catch (_: SecurityException) {
            null
        } ?: return

        for (f in children) {
            try {
                when {
                    f.isFile && f.name.lowercase().endsWith(".exe") -> {
                        if (!isJunkExe(f.name.lowercase())) onExe(f)
                    }
                    f.isDirectory -> walk(f, depth + 1, maxDepth, onExe)
                }
            } catch (_: SecurityException) {
            }
        }
    }

    private fun normalizeTokens(query: String?): List<String> {
        if (query.isNullOrBlank()) return emptyList()
        val raw = query.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it !in STOP_WORDS }
        val extras = mutableListOf<String>()
        // "gta 5" / "gta v" → also match gta5
        if (raw.size >= 2) extras += raw.joinToString("")
        return (raw + extras).distinct()
    }

    private fun scoreHit(exe: File, folder: File, tokens: List<String>): Int {
        var score = 5
        val hay = (
            exe.nameWithoutExtension + " " + folder.name + " " +
                (folder.parentFile?.name ?: "")
            ).lowercase().replace(Regex("[^a-z0-9]+"), "")

        if (tokens.isEmpty()) {
            // Prefer deeper game-looking folders under Download/Games
            val path = exe.absolutePath.lowercase()
            if (path.contains("/download")) score += 5
            if (path.contains("winlator/games")) score += 8
            return score + (
                ShortcutImporter.findBestExeFile(folder)?.let {
                    if (it.absolutePath == exe.absolutePath) 15 else 0
                } ?: 0
            )
        }

        for (t in tokens) {
            val compact = t.replace(Regex("[^a-z0-9]+"), "")
            if (compact.isEmpty()) continue
            if (hay.contains(compact)) score += 40
            else if (compact.length >= 3 && hay.contains(compact.take(3))) score += 8
        }
        // Prefer the "best" exe in the folder
        val best = ShortcutImporter.findBestExeFile(folder)
        if (best != null && best.absolutePath == exe.absolutePath) score += 20
        return score
    }

    private fun shouldSkipDir(name: String): Boolean {
        val lower = name.lowercase()
        return lower in SKIP_DIRS || lower.startsWith(".")
    }

    private fun isJunkExe(lower: String): Boolean {
        return lower.contains("unitycrashhandler") ||
            lower.contains("crashhandler") ||
            lower.startsWith("unins") ||
            lower.contains("vcredist") ||
            lower.contains("dxsetup") ||
            lower.contains("redist") ||
            lower == "unitycrashhandler64.exe"
    }

    private val STOP_WORDS = setOf(
        "the", "a", "an", "game", "games", "pc", "for", "of", "and", "in", "on",
    )

    private val SKIP_DIRS = setOf(
        "android", "dcim", "pictures", "movies", "music", "alarms", "notifications",
        "ringtones", "podcasts", "audiobooks", "documents", ".thumbnails", ".trash",
        "lost+found", "obb", "cache", "code_cache", "no_backup", "_ost", "__macosx",
        "node_modules", ".git", "system", "data",
    )
}
