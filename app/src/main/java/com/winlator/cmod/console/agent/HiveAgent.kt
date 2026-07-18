package com.winlator.cmod.console.agent

import android.content.Context
import com.winlator.cmod.console.EasySetup
import com.winlator.cmod.console.compat.SessionExit
import com.winlator.cmod.container.ContainerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class AgentEvent {
    data class Status(val text: String) : AgentEvent()
    data class AssistantDelta(val text: String) : AgentEvent()
    data class ToolStart(val name: String, val argsPreview: String) : AgentEvent()
    data class ToolResult(val name: String, val resultPreview: String) : AgentEvent()
    data class Applied(val summary: String) : AgentEvent()
    data class Error(val message: String) : AgentEvent()
    data object Done : AgentEvent()
}

/**
 * Hive-mind agent: system context + OpenAI-compatible tool loop.
 */
class HiveAgent(private val context: Context) {
    private val tools = AgentTools(context)

    suspend fun run(
        userMessage: String,
        history: List<ChatMessage> = emptyList(),
        onEvent: (AgentEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val config = AgentConfig.load(context)
        if (!config.isReady) {
            onEvent(AgentEvent.Error("Configure AI in Settings: API URL, key, and model."))
            onEvent(AgentEvent.Done)
            return@withContext
        }

        val client = OpenAICompatClient(config)
        val messages = mutableListOf<ChatMessage>()
        messages += ChatMessage(role = "system", content = buildSystemPrompt())
        messages += history.filter { it.role == "user" || it.role == "assistant" }
        messages += ChatMessage(role = "user", content = userMessage)

        try {
            var rounds = 0
            var mutated = false
            while (rounds < MAX_ROUNDS) {
                rounds++
                onEvent(AgentEvent.Status("Thinking… (round $rounds)"))
                val result = client.chat(messages, tools.definitions)
                val completion = result.getOrElse {
                    onEvent(AgentEvent.Error(it.message ?: "API error"))
                    onEvent(AgentEvent.Done)
                    return@withContext
                }
                val msg = completion.message
                messages += msg

                val calls = msg.toolCalls
                val content = msg.content?.trim().orEmpty()

                // Show any prose even when the model also called tools.
                if (content.isNotEmpty() && !calls.isNullOrEmpty()) {
                    onEvent(AgentEvent.AssistantDelta(content))
                }

                if (calls.isNullOrEmpty()) {
                    if (content.isNotEmpty()) {
                        onEvent(AgentEvent.AssistantDelta(content))
                    } else if (mutated) {
                        // Force a clear user-facing summary if the model stopped after tools.
                        onEvent(AgentEvent.Status("Writing summary…"))
                        messages += ChatMessage(
                            role = "user",
                            content = SUMMARY_NUDGE,
                        )
                        mutated = false
                        continue
                    }
                    onEvent(AgentEvent.Done)
                    return@withContext
                }

                for (call in calls) {
                    onEvent(
                        AgentEvent.ToolStart(
                            call.name,
                            call.argumentsJson.take(160),
                        ),
                    )
                    val output = tools.execute(call.name, call.argumentsJson)
                    onEvent(
                        AgentEvent.ToolResult(
                            call.name,
                            output.take(240),
                        ),
                    )
                    if (call.name in AgentTools.MUTATING_TOOLS) {
                        mutated = true
                        // Short friendly chip — full explanation comes in the final assistant reply.
                        onEvent(AgentEvent.Applied(friendlyAppliedLabel(call.name, output)))
                    }
                    messages += ChatMessage(
                        role = "tool",
                        content = output,
                        toolCallId = call.id,
                        name = call.name,
                    )
                }
            }
            onEvent(AgentEvent.Error("Stopped after $MAX_ROUNDS tool rounds."))
            onEvent(AgentEvent.Done)
        } catch (e: Exception) {
            onEvent(AgentEvent.Error(e.message ?: "Agent failed"))
            onEvent(AgentEvent.Done)
        }
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val config = AgentConfig.load(context)
        if (!config.isReady) {
            return@withContext Result.failure(IllegalStateException("Missing API key or model"))
        }
        OpenAICompatClient(config).testConnection()
    }

    private fun friendlyAppliedLabel(tool: String, output: String): String {
        val first = output.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        return when {
            first.startsWith("SUCCESS:") -> first.removePrefix("SUCCESS:").trim()
            tool == "delete_game" -> "Removed from library"
            tool == "add_game" -> first.ifBlank { "Game added" }
            tool == "select_game" -> first.ifBlank { "Game selected" }
            tool.startsWith("install_") -> first.ifBlank { "Install finished" }
            tool.startsWith("apply_") -> first.ifBlank { "Settings updated" }
            else -> first.take(120).ifBlank { "Done" }
        }
    }

    private fun buildSystemPrompt(): String {
        val manager = ContainerManager(context)
        val primary = EasySetup.primaryContainer(manager)
        val games = try {
            manager.loadShortcuts().size
        } catch (_: Exception) {
            0
        }
        val device = DeviceContext.snapshot(context)
        val last = try {
            SessionExit.lastSession(context)
        } catch (_: Exception) {
            null
        }
        val lastSessionLine = if (last != null) {
            "last_session=\"${last.shortcutName}\" error=${last.isError} tip=\"${last.message}\""
        } else {
            "last_session=(none)"
        }
        return """
You are the Winlator Console Hive Agent — a full on-device hive mind for PC games on Android (Wine/Box64/FEX/DXVK/Turnip).

Capabilities:
- Library: list/search/select/delete/add games
- Storage: search and list directories for EXEs
- Settings: per-game and container (emulator, DXVK, drivers, wincomponents, env, input)
- Fixes: apply_compat_fixes
- Drivers/contents: Adreno-Turnip + DXVK/VKD3D/Box64 packs
- Logs/errors: get_last_session_report, read_live_logs, analyze_logs, list/read saved log files
- Research + device prefs

Reply style (mandatory after any change or diagnosis):
1. Short opening sentence of what you did or found.
2. Bulleted list of each change in plain language (what it is + why it helps). Never show only raw IDs like key=value or “driver number” without explaining the driver name and purpose.
3. What the user should try next (launch game, check FPS, etc.).
4. If diagnosing a crash: say the likely cause, evidence from logs, and the fix you applied or recommend.
5. Simple Markdown only: **bold**, bullets, short headings. No LaTeX, HTML tables, or ASCII charts.

Rules:
1. Prefer tools over guessing. Use log tools when the user mentions crash, error, black screen, quit, or “check logs”.
2. If a game is installed somewhere on storage, search_storage / list_directory then add_game.
3. delete_game needs confirm=yes. Never invent a wipe-folder tool.
4. Before mutating, call explain_plan briefly.
5. Only mutate when asked to add/remove/optimize/fix/download/change — or when logs clearly warrant a safe known fix and the user asked you to fix it.
6. After tools finish you MUST send a final user-facing explanation (do not end on tool calls alone).
7. Use select_game when focusing on one title.

Context:
primary_container=${primary?.name ?: "(none)"} (#${primary?.id ?: "-"})
library_games=$games
$lastSessionLine

Device:
$device
        """.trimIndent()
    }

    companion object {
        private const val MAX_ROUNDS = 16
        private const val SUMMARY_NUDGE =
            "Write the final user-facing reply now. Summarize every change you made in plain language " +
                "(what each setting/driver does and why). Include next steps. Do not call more tools unless something failed."
    }
}
