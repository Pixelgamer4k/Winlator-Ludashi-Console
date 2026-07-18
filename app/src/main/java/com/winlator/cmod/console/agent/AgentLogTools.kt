package com.winlator.cmod.console.agent

import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
import com.winlator.cmod.SettingsFragment
import com.winlator.cmod.console.SessionLogBuffer
import com.winlator.cmod.console.compat.LaunchFailureClassifier
import com.winlator.cmod.console.compat.LaunchLogBuffer
import com.winlator.cmod.console.compat.SessionExit
import com.winlator.cmod.core.FileUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Read / classify Wine-Box64 session logs for the hive agent.
 */
object AgentLogTools {

    fun getLastSessionReport(context: Context): String {
        val last = SessionExit.lastSession(context)
            ?: return "No saved session yet. Play a game once, then ask again."
        val whenStr = if (last.atMs > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(last.atMs))
        } else {
            "unknown time"
        }
        val live = LaunchLogBuffer.snapshot()
        val liveHint = if (live.isNotEmpty()) {
            "Live in-memory buffer also has ${live.size} lines (use read_live_logs for full text)."
        } else {
            "Live in-memory buffer is empty (normal after leaving the game)."
        }
        return buildString {
            appendLine("LAST_SESSION at=$whenStr")
            appendLine("game=${last.shortcutName ?: "(unknown)"}")
            appendLine("path=${last.shortcutPath ?: "(none)"}")
            appendLine("container_id=${last.containerId}")
            appendLine("was_error=${last.isError}")
            appendLine("title=${last.title}")
            appendLine("diagnosis=${last.message}")
            appendLine("suggested_retry=${last.retryAction}")
            appendLine(liveHint)
            appendLine("--- LOG EXCERPT (tail) ---")
            append(
                if (last.logExcerpt.isBlank()) "(no log captured)"
                else last.logExcerpt.takeLast(8_000),
            )
        }
    }

    fun readLiveLogs(maxLines: Int = 200): String {
        val launch = LaunchLogBuffer.snapshot()
        val session = SessionLogBuffer.snapshot()
        if (launch.isEmpty() && session.isEmpty()) {
            return "No live logs in memory. Use get_last_session_report after a game run, or list_saved_log_files."
        }
        return buildString {
            if (launch.isNotEmpty()) {
                appendLine("## LaunchLogBuffer (${launch.size} lines, showing last $maxLines)")
                launch.takeLast(maxLines).forEach { appendLine(it) }
            }
            if (session.isNotEmpty()) {
                appendLine("## SessionLogBuffer (${session.size} lines, showing last $maxLines)")
                session.takeLast(maxLines).forEach { appendLine(it) }
            }
        }
    }

    fun analyzeLogs(
        context: Context,
        shortcutName: String? = null,
        compatTags: String? = null,
        source: String = "auto",
    ): String {
        val lines = when (source.lowercase()) {
            "live" -> LaunchLogBuffer.snapshot().ifEmpty { SessionLogBuffer.snapshot() }
            "last" -> {
                val excerpt = SessionExit.lastSession(context)?.logExcerpt.orEmpty()
                if (excerpt.isBlank()) emptyList() else excerpt.lines()
            }
            else -> {
                val live = LaunchLogBuffer.snapshot()
                if (live.isNotEmpty()) live
                else {
                    val excerpt = SessionExit.lastSession(context)?.logExcerpt.orEmpty()
                    if (excerpt.isBlank()) SessionLogBuffer.snapshot() else excerpt.lines()
                }
            }
        }
        if (lines.isEmpty()) {
            return "No log lines available to analyze."
        }

        val last = SessionExit.lastSession(context)
        val name = shortcutName ?: last?.shortcutName
        val tags = compatTags ?: run {
            // best-effort: leave null
            null
        }
        val verdict = LaunchFailureClassifier.classify(
            logLines = lines,
            exitStatus = if (last?.isError == true) 1 else 0,
            sessionMs = 5_000L,
            shortcutName = name,
            compatTags = tags,
        )
        val patterns = scanErrorPatterns(lines)
        return buildString {
            appendLine("ANALYSIS")
            appendLine("is_error=${verdict.isError}")
            appendLine("title=${verdict.title}")
            appendLine("message=${verdict.message}")
            appendLine("suggested_retry=${verdict.retryAction}")
            appendLine("log_lines_used=${lines.size}")
            if (patterns.isNotEmpty()) {
                appendLine("notable_patterns:")
                patterns.forEach { appendLine("- $it") }
            }
            appendLine("--- TAIL ---")
            lines.takeLast(40).forEach { appendLine(it) }
        }
    }

    fun listSavedLogFiles(context: Context, limit: Int = 20): String {
        val dir = logsDir(context)
        if (!dir.isDirectory) return "Logs folder missing: ${dir.absolutePath}"
        val files = dir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".txt") || it.name.endsWith(".log")) }
            ?.sortedByDescending { it.lastModified() }
            ?.take(limit)
            .orEmpty()
        if (files.isEmpty()) return "No saved log files in ${dir.absolutePath}"
        return files.joinToString("\n") { f ->
            val whenStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(f.lastModified()))
            "- ${f.name} (${f.length()} bytes, $whenStr) path=${f.absolutePath}"
        }
    }

    fun readSavedLogFile(context: Context, pathOrName: String, maxChars: Int = 10_000): String {
        val raw = pathOrName.trim()
        if (raw.isEmpty()) return "Missing path."
        val file = when {
            raw.startsWith("/") -> File(raw)
            else -> File(logsDir(context), raw)
        }
        if (!file.isFile) return "Log file not found: $raw"
        val text = try {
            file.readText()
        } catch (e: Exception) {
            return "Cannot read ${file.absolutePath}: ${e.message}"
        }
        val body = if (text.length <= maxChars) text else text.takeLast(maxChars)
        return "FILE ${file.absolutePath} (${text.length} chars, showing last ${body.length})\n$body"
    }

    private fun logsDir(context: Context): File {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val winlatorPath = prefs.getString("winlator_path_uri", null)
        return if (!winlatorPath.isNullOrBlank()) {
            val uri = Uri.parse(winlatorPath)
            val path = FileUtils.getFilePathFromUri(context, uri)
            if (!path.isNullOrBlank()) File(path, "logs")
            else File(SettingsFragment.DEFAULT_WINLATOR_PATH, "logs")
        } else {
            File(SettingsFragment.DEFAULT_WINLATOR_PATH, "logs")
        }.also { if (!it.exists()) it.mkdirs() }
    }

    private fun scanErrorPatterns(lines: List<String>): List<String> {
        val text = lines.joinToString("\n").lowercase()
        val hits = mutableListOf<String>()
        fun hit(label: String, vararg needles: String) {
            if (needles.any { text.contains(it) }) hits += label
        }
        hit("VC++2010 / msvcp100 crash", "msvcp100", "msvcr100")
        hit("UnityPlayer missing/incomplete", "unityplayer")
        hit("DXVK / DXGI graphics error", "dxgi_error", "dxvk")
        hit("Vulkan driver issue", "vk_error", "vulkan")
        hit("Missing DLL", "err:module:import", "cannot find")
        hit("Unhandled exception / access violation", "0xc0000005", "unhandled exception")
        hit("Box64 dynarec note", "box64", "dynarec")
        hit("Wine unimplemented function", "fix_stub", "unimplemented")
        return hits
    }
}
