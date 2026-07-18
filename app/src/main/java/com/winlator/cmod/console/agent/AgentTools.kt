package com.winlator.cmod.console.agent

import android.content.Context
import androidx.preference.PreferenceManager
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.winlator.cmod.console.EasySetup
import com.winlator.cmod.console.ShortcutImporter
import com.winlator.cmod.console.compat.CompatApplier
import com.winlator.cmod.console.compat.CompatRules
import com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.container.Shortcut
import org.json.JSONObject
import java.io.File

/**
 * Tool registry + executors for the hive agent (library, storage, drivers, fixes).
 */
class AgentTools(private val context: Context) {
    private val research = WebResearchTool()
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(context)

    val definitions: List<ToolDefinition> = listOf(
        tool(
            "list_games",
            "List imported games/shortcuts in the library with key settings.",
            props(),
        ),
        tool(
            "search_library",
            "Search the library by game name substring.",
            props("query" to str("Game name fragment")),
            required = listOf("query"),
        ),
        tool(
            "select_game",
            "Remember the active game for follow-up tools. Pass empty query to clear.",
            props("query" to str("Game name or .desktop path (empty to clear)")),
        ),
        tool(
            "get_selected_game",
            "Return the currently selected game (from select_game).",
            props(),
        ),
        tool(
            "get_game_settings",
            "Get detailed settings for one game by name/path, or the selected game if query omitted.",
            props("query" to str("Game name or .desktop path (optional if selected)")),
        ),
        tool(
            "apply_game_settings",
            "Apply per-game Shortcut overrides. Only when the user asked to optimize/change settings.",
            props(
                "query" to str("Game name or path (optional if selected)"),
                "emulator" to str("Box64 or FEXCore (optional)"),
                "box64Preset" to str("STABILITY|COMPATIBILITY|INTERMEDIATE|PERFORMANCE (optional)"),
                "box64Version" to str("optional"),
                "dxwrapper" to str("e.g. dxvk+vkd3d or wined3d (optional)"),
                "dxwrapperConfig" to str("optional"),
                "envVars" to str("Space-separated KEY=value overrides (optional)"),
                "execArgs" to str("Extra exe arguments (optional)"),
                "disableXinput" to str("0 or 1 (optional)"),
                "rendererNative" to str("true or false (optional)"),
                "graphicsDriver" to str("optional, usually wrapper"),
                "graphicsDriverConfig" to str("optional full config string"),
                "graphicsDriverVersion" to str("Adreno/Turnip driver id/name to set as version= (optional)"),
                "audioDriver" to str("alsa or pulseaudio (optional)"),
                "screenSize" to str("e.g. 540x360 (optional)"),
                "wincomponents" to str("e.g. direct3d=1,vcrun2010=1 (optional)"),
                "fexcorePreset" to str("optional"),
                "controlsProfile" to str("profile id (optional)"),
                "fullscreenStretched" to str("0 or 1 (optional)"),
                "simTouchScreen" to str("0 or 1 (optional)"),
                "inputType" to str("optional"),
            ),
        ),
        tool(
            "delete_game",
            "Remove a game from the library (deletes the .desktop shortcut only; game files stay on disk). Requires confirm=yes.",
            props(
                "query" to str("Game name or path"),
                "confirm" to str("Must be yes"),
            ),
            required = listOf("query", "confirm"),
        ),
        tool(
            "add_game",
            "Add a game to the library from an absolute .exe path or game folder on storage. Does not copy files.",
            props(
                "path" to str("Absolute path to .exe/.msi/.bat or game folder"),
                "container" to str("Container id or name (optional, defaults to PC Games)"),
            ),
            required = listOf("path"),
        ),
        tool(
            "search_storage",
            "Search internal/shared storage for PC game EXEs (Download, Winlator/Games, etc.). Use before add_game when user says a game is installed somewhere.",
            props(
                "query" to str("Game name to match, e.g. GTA 5 (optional — omit to list recent finds)"),
                "root" to str("Optional absolute folder to scan"),
                "max_depth" to str("1-6, default 4"),
            ),
        ),
        tool(
            "list_directory",
            "List files/folders at an absolute path on storage.",
            props("path" to str("Absolute directory or file path")),
            required = listOf("path"),
        ),
        tool(
            "list_containers",
            "List Wine containers (id, name).",
            props(),
        ),
        tool(
            "get_container_settings",
            "Get container (Wine prefix) settings by id or name. Omit for primary PC Games container.",
            props("container" to str("Container id or name (optional)")),
        ),
        tool(
            "apply_container_settings",
            "Apply container settings. Only when user asked to change container defaults.",
            props(
                "container" to str("Container id or name (optional)"),
                "screenSize" to str("e.g. 540x360 (optional)"),
                "emulator" to str("Box64 or FEXCore (optional)"),
                "box64Preset" to str("optional"),
                "graphicsDriver" to str("optional"),
                "graphicsDriverVersion" to str("Adreno/Turnip version name (optional)"),
                "dxwrapper" to str("optional"),
                "audioDriver" to str("alsa or pulseaudio (optional)"),
                "envVars" to str("optional"),
                "wincomponents" to str("optional"),
                "hudMode" to str("0 off, 1 classic, 2 modern (optional)"),
            ),
        ),
        tool(
            "apply_compat_fixes",
            "Probe the game EXE and apply known Winlator/Box64/Unity/VC++ compatibility extras.",
            props(
                "query" to str("Game name or path (optional if selected)"),
                "force" to str("true to overwrite existing extras (optional)"),
            ),
        ),
        tool(
            "list_adreno_drivers",
            "List installed Adreno/Turnip graphics drivers on device.",
            props(),
        ),
        tool(
            "list_adreno_downloads",
            "List downloadable Adreno/Turnip driver ZIPs from configured GitHub repos.",
            props("query" to str("Filter by release/asset name (optional)")),
        ),
        tool(
            "install_adreno_driver",
            "Download and install an Adreno/Turnip driver by name substring or direct ZIP URL.",
            props("query" to str("Release/asset name substring OR https ZIP url")),
            required = listOf("query"),
        ),
        tool(
            "remove_adreno_driver",
            "Uninstall an installed Adreno/Turnip driver by id.",
            props("driver_id" to str("Driver folder id from list_adreno_drivers")),
            required = listOf("driver_id"),
        ),
        tool(
            "list_content_packs",
            "List installed and downloadable content packs (DXVK, VKD3D, Box64, Wine, Proton, FEXCore).",
            props("type" to str("Optional: DXVK, VKD3D, Box64, Wine, Proton, WOWBox64, FEXCore")),
        ),
        tool(
            "install_content_pack",
            "Download and install a content pack (DXVK/VKD3D/Box64/etc.) by type and version name.",
            props(
                "type" to str("DXVK, VKD3D, Box64, Wine, Proton, WOWBox64, or FEXCore"),
                "verName" to str("Version name from list_content_packs"),
            ),
            required = listOf("type", "verName"),
        ),
        tool(
            "get_device_info",
            "Return device, GPU, RAM, and ImageFS status.",
            props(),
        ),
        tool(
            "get_app_preferences",
            "Return console-relevant SharedPreferences.",
            props(),
        ),
        tool(
            "research_web",
            "Search Reddit and the web for Winlator/Box64/DXVK settings tips for a game or issue.",
            props("query" to str("Search query, include game name")),
            required = listOf("query"),
        ),
        tool(
            "get_last_session_report",
            "Get the last finished game session: diagnosis, suggested fix, and log excerpt. Use when the user says a game crashed, quit, or asks to check logs/errors.",
            props(),
        ),
        tool(
            "read_live_logs",
            "Read in-memory Wine/Box64 logs from the current or most recent session buffer.",
            props("max_lines" to str("How many tail lines (default 200)")),
        ),
        tool(
            "analyze_logs",
            "Classify crash/error patterns from live or last-session logs and suggest a fix.",
            props(
                "source" to str("auto|live|last (default auto)"),
                "query" to str("Optional game name for context"),
            ),
        ),
        tool(
            "list_saved_log_files",
            "List saved .txt log files under Winlator/logs.",
            props(),
        ),
        tool(
            "read_saved_log_file",
            "Read a saved log file by absolute path or file name from list_saved_log_files.",
            props("path" to str("Absolute path or file name")),
            required = listOf("path"),
        ),
        tool(
            "explain_plan",
            "Record a short plan before applying changes (for the user).",
            props("summary" to str("What you will change and why")),
            required = listOf("summary"),
        ),
    )

    fun execute(name: String, argumentsJson: String): String {
        val args = try {
            JsonParser.parseString(argumentsJson.ifBlank { "{}" }).asJsonObject
        } catch (_: Exception) {
            JsonObject()
        }
        return try {
            when (name) {
                "list_games" -> listGames()
                "search_library" -> searchLibrary(args.str("query"))
                "select_game" -> selectGame(args.strOrNull("query"))
                "get_selected_game" -> getSelectedGame()
                "get_game_settings" -> getGameSettings(args.strOrNull("query"))
                "apply_game_settings" -> applyGameSettings(args)
                "delete_game" -> deleteGame(args.str("query"), args.str("confirm"))
                "add_game" -> addGame(args.str("path"), args.strOrNull("container"))
                "search_storage" -> searchStorage(args)
                "list_directory" -> AgentStorageScan.listDirectory(args.str("path"))
                "list_containers" -> listContainers()
                "get_container_settings" -> getContainerSettings(args.strOrNull("container"))
                "apply_container_settings" -> applyContainerSettings(args)
                "apply_compat_fixes" -> applyCompatFixes(args.strOrNull("query"), args.strOrNull("force") == "true")
                "list_adreno_drivers" -> AgentContentsOps.listInstalledAdreno(context)
                "list_adreno_downloads" -> AgentContentsOps.listAdrenoDownloads(context, args.strOrNull("query"))
                "install_adreno_driver" -> AgentContentsOps.installAdrenoByName(context, args.str("query"))
                "remove_adreno_driver" -> AgentContentsOps.removeAdreno(context, args.str("driver_id"))
                "list_content_packs" -> AgentContentsOps.syncAndListContents(context, args.strOrNull("type"))
                "install_content_pack" -> AgentContentsOps.installContentPack(
                    context,
                    args.str("type"),
                    args.str("verName"),
                )
                "get_device_info" -> DeviceContext.snapshot(context)
                "get_app_preferences" -> getAppPreferences()
                "research_web" -> research.research(args.str("query"))
                "get_last_session_report" -> AgentLogTools.getLastSessionReport(context)
                "read_live_logs" -> AgentLogTools.readLiveLogs(
                    args.strOrNull("max_lines")?.toIntOrNull() ?: 200,
                )
                "analyze_logs" -> AgentLogTools.analyzeLogs(
                    context,
                    shortcutName = args.strOrNull("query"),
                    source = args.strOrNull("source") ?: "auto",
                )
                "list_saved_log_files" -> AgentLogTools.listSavedLogFiles(context)
                "read_saved_log_file" -> AgentLogTools.readSavedLogFile(context, args.str("path"))
                "explain_plan" -> "PLAN_NOTED: ${args.str("summary")}"
                else -> "Unknown tool: $name"
            }
        } catch (e: Exception) {
            "Tool error ($name): ${e.message}"
        }
    }

    private fun listGames(): String {
        val shortcuts = ContainerManager(context).loadShortcuts()
        if (shortcuts.isEmpty()) return "No games in library."
        val selected = prefs.getString(PREF_SELECTED, null)
        return shortcuts.joinToString("\n") { sc ->
            val mark = if (selected != null && sc.file.absolutePath == selected) " [SELECTED]" else ""
            buildString {
                append("- name=\"${sc.name}\"$mark")
                append(" path=${sc.file.absolutePath}")
                append(" container=${sc.container.name}(#${sc.container.id})")
                append(" emulator=${sc.getExtra("emulator", sc.container.emulator)}")
                append(" box64Preset=${sc.getExtra("box64Preset", sc.container.box64Preset)}")
                append(" dxwrapper=${sc.getExtra("dxwrapper", sc.container.getDXWrapper())}")
                append(" exec=${sc.executable}")
            }
        }
    }

    private fun searchLibrary(query: String): String {
        val q = query.trim()
        if (q.isEmpty()) return "Empty query."
        val hits = ContainerManager(context).loadShortcuts()
            .filter { it.name.contains(q, true) || it.file.name.contains(q, true) ||
                (it.executable?.contains(q, true) == true) }
        if (hits.isEmpty()) return "No library games matching \"$q\"."
        return hits.joinToString("\n") {
            "- name=\"${it.name}\" path=${it.file.absolutePath} exec=${it.executable}"
        }
    }

    private fun selectGame(query: String?): String {
        if (query.isNullOrBlank()) {
            prefs.edit().remove(PREF_SELECTED).apply()
            return "Cleared selected game."
        }
        val sc = findShortcut(query) ?: return "Game not found: $query"
        prefs.edit().putString(PREF_SELECTED, sc.file.absolutePath).apply()
        return "Selected: ${sc.name} (${sc.file.absolutePath})"
    }

    private fun getSelectedGame(): String {
        val path = prefs.getString(PREF_SELECTED, null)
            ?: return "No game selected. Use select_game first."
        val sc = findShortcut(path) ?: run {
            prefs.edit().remove(PREF_SELECTED).apply()
            return "Selected game missing from library (cleared)."
        }
        return "selected=${sc.name} path=${sc.file.absolutePath} exec=${sc.executable}"
    }

    private fun resolveGameQuery(query: String?): Shortcut? {
        if (!query.isNullOrBlank()) return findShortcut(query)
        val path = prefs.getString(PREF_SELECTED, null) ?: return null
        return findShortcut(path)
    }

    private fun findShortcut(query: String): Shortcut? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val all = ContainerManager(context).loadShortcuts()
        all.firstOrNull { it.file.absolutePath.equals(q, true) }?.let { return it }
        all.firstOrNull { it.name.equals(q, true) }?.let { return it }
        all.firstOrNull { it.name.contains(q, true) }?.let { return it }
        all.firstOrNull { it.executable?.contains(q, true) == true }?.let { return it }
        return null
    }

    private fun getGameSettings(query: String?): String {
        val sc = resolveGameQuery(query)
            ?: return if (query.isNullOrBlank()) "No game selected and no query given."
            else "Game not found: $query"
        val extras = JSONObject()
        val keys = listOf(
            "emulator", "box64Preset", "box64Version", "dxwrapper", "dxwrapperConfig",
            "envVars", "execArgs", "disableXinput", "compatVersion", "compatTags", "uuid",
            "rendererNative", "rendererPresentMode", "rendererDriverId",
            "graphicsDriver", "graphicsDriverConfig", "audioDriver", "screenSize",
            "wincomponents", "fexcorePreset", "fexcoreVersion", "controlsProfile",
            "fullscreenStretched", "simTouchScreen", "inputType", "peMachine",
        )
        for (k in keys) {
            val v = sc.getExtra(k)
            if (v.isNotEmpty()) extras.put(k, v)
        }
        return buildString {
            appendLine("name=${sc.name}")
            appendLine("path=${sc.file.absolutePath}")
            appendLine("container=${sc.container.name} id=${sc.container.id}")
            appendLine("executable=${sc.executable}")
            appendLine("container_emulator=${sc.container.emulator}")
            appendLine("container_screen=${sc.container.screenSize}")
            appendLine("container_dxwrapper=${sc.container.getDXWrapper()}")
            appendLine("container_graphicsDriverConfig=${sc.container.graphicsDriverConfig}")
            appendLine("extras=$extras")
        }
    }

    private fun applyGameSettings(args: JsonObject): String {
        val sc = resolveGameQuery(args.strOrNull("query"))
            ?: return "Game not found / not selected."
        val applied = mutableListOf<Pair<String, String>>()
        fun put(key: String, prefKey: String = key) {
            val v = args.strOrNull(prefKey) ?: return
            sc.putExtra(key, v)
            applied += key to v
        }
        put("emulator")
        put("box64Preset")
        put("box64Version")
        put("dxwrapper")
        put("dxwrapperConfig")
        put("envVars")
        put("execArgs")
        put("disableXinput")
        put("graphicsDriver")
        put("audioDriver")
        put("screenSize")
        args.strOrNull("wincomponents")?.let { raw ->
            val cleaned = sanitizeWinComponents(raw)
            sc.putExtra("wincomponents", cleaned)
            applied += "wincomponents" to cleaned
        }
        put("fexcorePreset")
        put("controlsProfile")
        put("fullscreenStretched")
        put("simTouchScreen")
        put("inputType")
        args.strOrNull("rendererNative")?.let {
            sc.putExtra("rendererNative", it)
            applied += "rendererNative" to it
        }
        args.strOrNull("graphicsDriverConfig")?.let {
            sc.putExtra("graphicsDriverConfig", it)
            applied += "graphicsDriverConfig" to "(updated)"
        }
        args.strOrNull("graphicsDriverVersion")?.let { ver ->
            val base = sc.getExtra("graphicsDriverConfig", sc.container.graphicsDriverConfig)
            val map = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(base.ifBlank { "version=System" })
            map["version"] = ver
            val cfg = GraphicsDriverConfigDialog.toGraphicsDriverConfig(map)
            sc.putExtra("graphicsDriverConfig", cfg)
            if (sc.getExtra("graphicsDriver").isEmpty()) {
                sc.putExtra("graphicsDriver", "wrapper")
            }
            applied += "graphicsDriverVersion" to ver
        }
        sc.saveData()
        return AgentChangeLabels.formatApplyResult(sc.name, applied)
    }

    private fun deleteGame(query: String, confirm: String): String {
        if (!confirm.equals("yes", true)) {
            return "Refused: set confirm=yes to delete from library (game files on disk are kept)."
        }
        val sc = findShortcut(query) ?: return "Game not found: $query"
        val name = sc.name
        val desktop = sc.file
        val parent = desktop.parentFile
        val base = desktop.nameWithoutExtension
        val deleted = mutableListOf<String>()
        if (desktop.delete()) deleted += desktop.name
        parent?.listFiles()?.forEach { f ->
            val n = f.name.lowercase()
            if (n == "$base.lnk" || n == "$base.bat") {
                if (f.delete()) deleted += f.name
            }
        }
        val selected = prefs.getString(PREF_SELECTED, null)
        if (selected == desktop.absolutePath) {
            prefs.edit().remove(PREF_SELECTED).apply()
        }
        return if (deleted.isEmpty()) "Could not delete shortcut for $name."
        else "Removed from library: $name (deleted ${deleted.joinToString(", ")}). Game files on disk were not deleted."
    }

    private fun addGame(path: String, containerRef: String?): String {
        val file = File(path.trim())
        if (!file.exists()) return "Path not found: $path"
        val container = resolveContainer(containerRef)
            ?: return "Container not found."
        val result = when {
            file.isFile -> ShortcutImporter.importFromFile(context, file, container)
            file.isDirectory -> ShortcutImporter.importGameFolder(context, file, container)
            else -> ShortcutImporter.Result(false, message = "Not a file or folder")
        }
        return if (result.success && result.shortcut != null) {
            prefs.edit().putString(PREF_SELECTED, result.shortcut.file.absolutePath).apply()
            "ADDED: ${result.shortcut.name} path=${result.shortcut.file.absolutePath} — ${result.message}"
        } else {
            "Add failed: ${result.message}"
        }
    }

    private fun searchStorage(args: JsonObject): String {
        val depth = args.strOrNull("max_depth")?.toIntOrNull() ?: 4
        val hits = AgentStorageScan.search(
            query = args.strOrNull("query"),
            rootPath = args.strOrNull("root"),
            maxDepth = depth,
        )
        return AgentStorageScan.formatHits(hits)
    }

    private fun listContainers(): String {
        val list = ContainerManager(context).containers
        if (list.isEmpty()) return "No containers."
        return list.joinToString("\n") { "- id=${it.id} name=\"${it.name}\"" }
    }

    private fun resolveContainer(ref: String?): Container? {
        val manager = ContainerManager(context)
        if (ref.isNullOrBlank()) return EasySetup.primaryContainer(manager)
        val id = ref.toIntOrNull()
        if (id != null) return manager.getContainerById(id)
        return manager.containers.firstOrNull { it.name.equals(ref, true) }
            ?: manager.containers.firstOrNull { it.name.contains(ref, true) }
    }

    private fun getContainerSettings(ref: String?): String {
        val c = resolveContainer(ref) ?: return "Container not found"
        return buildString {
            appendLine("id=${c.id}")
            appendLine("name=${c.name}")
            appendLine("screenSize=${c.screenSize}")
            appendLine("graphicsDriver=${c.graphicsDriver}")
            appendLine("graphicsDriverConfig=${c.graphicsDriverConfig}")
            appendLine("dxwrapper=${c.getDXWrapper()}")
            appendLine("audioDriver=${c.audioDriver}")
            appendLine("emulator=${c.emulator}")
            appendLine("box64Preset=${c.box64Preset}")
            appendLine("box64Version=${c.box64Version}")
            appendLine("fexcorePreset=${c.fexCorePreset}")
            appendLine("envVars=${c.envVars}")
            appendLine("wincomponents=${c.winComponents}")
            appendLine("hudMode=${c.getExtra("hudMode")}")
            appendLine("showFPS=${c.isShowFPS}")
            appendLine("wineVersion=${c.wineVersion}")
        }
    }

    private fun applyContainerSettings(args: JsonObject): String {
        val c = resolveContainer(args.strOrNull("container")) ?: return "Container not found"
        val applied = mutableListOf<Pair<String, String>>()
        args.strOrNull("screenSize")?.let { c.screenSize = it; applied += "screenSize" to it }
        args.strOrNull("emulator")?.let { c.emulator = it; applied += "emulator" to it }
        args.strOrNull("box64Preset")?.let { c.box64Preset = it; applied += "box64Preset" to it }
        args.strOrNull("graphicsDriver")?.let { c.graphicsDriver = it; applied += "graphicsDriver" to it }
        args.strOrNull("dxwrapper")?.let { c.setDXWrapper(it); applied += "dxwrapper" to it }
        args.strOrNull("audioDriver")?.let { c.audioDriver = it; applied += "audioDriver" to it }
        args.strOrNull("envVars")?.let { c.envVars = it; applied += "envVars" to "(updated)" }
        args.strOrNull("wincomponents")?.let {
            c.winComponents = sanitizeWinComponents(it)
            applied += "wincomponents" to "(updated)"
        }
        args.strOrNull("hudMode")?.let {
            c.putExtra("hudMode", it)
            c.isShowFPS = it != "0"
            applied += "hudMode" to it
        }
        args.strOrNull("graphicsDriverVersion")?.let { ver ->
            val map = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(
                c.graphicsDriverConfig.ifBlank { "version=System" },
            )
            map["version"] = ver
            c.graphicsDriverConfig = GraphicsDriverConfigDialog.toGraphicsDriverConfig(map)
            if (c.graphicsDriver.isNullOrBlank()) c.graphicsDriver = "wrapper"
            applied += "graphicsDriverVersion" to ver
        }
        c.saveData()
        return AgentChangeLabels.formatApplyResult("container ${c.name}", applied)
    }

    private fun applyCompatFixes(query: String?, force: Boolean): String {
        val sc = resolveGameQuery(query)
            ?: return "Game not found / not selected."
        val exe = CompatApplier.resolveExeFile(sc)
            ?: return "Cannot probe — no local EXE path on shortcut ${sc.name}."
        return if (force) {
            val result = CompatApplier.applyToNewShortcut(sc, exe)
            if (!result.applied) {
                "Blocked: ${result.blockedReason}"
            } else {
                val tip = CompatRules.tipForTags(result.profile.tags)
                "Compat fixes applied to ${sc.name}: ${result.profile.summary()}" +
                    (tip?.let { " — $it" } ?: "")
            }
        } else {
            val migrated = CompatApplier.ensureMigrated(sc)
            val tags = sc.getExtra("compatTags")
            "Compat migration on ${sc.name}: updated=$migrated tags=$tags"
        }
    }

    private fun getAppPreferences(): String {
        val keys = listOf(
            "console_home_enabled", "cursor_speed", "use_dri3", "cursor_lock",
            "xinput_toggle", "enable_wine_debug", "enable_box64_logs",
            "high_refresh_rate_mode", "ai_enabled", "ai_model", "ai_api_base_url",
            "downloadable_contents_url",
        )
        return keys.joinToString("\n") { k ->
            val v = when (k) {
                "cursor_speed" -> prefs.getFloat(k, 1f).toString()
                else -> prefs.all[k]?.toString() ?: "(unset)"
            }
            "$k=$v"
        }
    }

    /** Normalize AI-supplied wincomponents into key=value,key=value form. */
    private fun sanitizeWinComponents(raw: String): String {
        val parts = raw
            .replace(';', ',')
            .split(Regex("[,\\s]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains('=') }
        return parts.joinToString(",")
    }

    private fun JsonObject.str(key: String): String =
        get(key)?.asString?.trim().orEmpty()

    private fun JsonObject.strOrNull(key: String): String? =
        get(key)?.asString?.trim()?.takeIf { it.isNotEmpty() }

    private fun tool(
        name: String,
        description: String,
        properties: JsonObject,
        required: List<String> = emptyList(),
    ): ToolDefinition {
        val params = JsonObject().apply {
            addProperty("type", "object")
            add("properties", properties)
            if (required.isNotEmpty()) {
                val arr = com.google.gson.JsonArray()
                required.forEach { arr.add(it) }
                add("required", arr)
            }
        }
        return ToolDefinition(name, description, params)
    }

    private fun props(vararg entries: Pair<String, JsonObject>): JsonObject {
        val o = JsonObject()
        for ((k, v) in entries) o.add(k, v)
        return o
    }

    private fun str(description: String): JsonObject =
        JsonObject().apply {
            addProperty("type", "string")
            addProperty("description", description)
        }

    companion object {
        private const val PREF_SELECTED = "ai_selected_game_path"

        val MUTATING_TOOLS = setOf(
            "apply_game_settings",
            "apply_container_settings",
            "apply_compat_fixes",
            "delete_game",
            "add_game",
            "install_adreno_driver",
            "remove_adreno_driver",
            "install_content_pack",
            "select_game",
        )
    }
}
