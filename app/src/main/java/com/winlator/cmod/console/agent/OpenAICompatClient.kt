package com.winlator.cmod.console.agent

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String,
    val content: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val name: String? = null,
)

data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

data class ChatCompletionResult(
    val message: ChatMessage,
    val finishReason: String?,
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersJson: JsonObject,
)

/**
 * OpenAI-compatible chat completions client (OpenRouter, OpenAI, Gemini proxies, Grok, etc.).
 */
class OpenAICompatClient(
    private val config: AgentConfig,
    private val gson: Gson = Gson(),
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun testConnection(): Result<String> {
        return try {
            val body = JsonObject().apply {
                addProperty("model", config.model)
                add("messages", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        addProperty("content", "Reply with exactly: ok")
                    })
                })
                addProperty("max_tokens", 16)
            }
            val result = postChat(body)
            result.map { it.message.content?.trim().orEmpty().ifEmpty { "ok" } }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition> = emptyList(),
    ): Result<ChatCompletionResult> {
        return try {
            val body = JsonObject().apply {
                addProperty("model", config.model)
                add("messages", messagesToJson(messages))
                if (tools.isNotEmpty()) {
                    add("tools", toolsToJson(tools))
                    addProperty("tool_choice", "auto")
                }
            }
            postChat(body)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun postChat(body: JsonObject): Result<ChatCompletionResult> {
        val url = "${config.normalizedBaseUrl}/chat/completions"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://winlator.console.local")
            .addHeader("X-Title", "Winlator Console Hive Agent")
            .post(gson.toJson(body).toRequestBody(jsonMedia))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return Result.failure(
                    IllegalStateException("HTTP ${response.code}: ${raw.take(400)}"),
                )
            }
            val root = JsonParser.parseString(raw).asJsonObject
            val choice = root.getAsJsonArray("choices")?.get(0)?.asJsonObject
                ?: return Result.failure(IllegalStateException("No choices in response"))
            val finish = choice.get("finish_reason")?.asString
            val msg = choice.getAsJsonObject("message")
                ?: return Result.failure(IllegalStateException("No message in choice"))
            return Result.success(
                ChatCompletionResult(
                    message = parseMessage(msg),
                    finishReason = finish,
                ),
            )
        }
    }

    private fun parseMessage(msg: JsonObject): ChatMessage {
        val role = msg.get("role")?.asString ?: "assistant"
        val content = msg.get("content")?.let {
            if (it.isJsonNull) null else it.asString
        }
        val toolCalls = msg.getAsJsonArray("tool_calls")?.mapNotNull { el ->
            val obj = el.asJsonObject
            val id = obj.get("id")?.asString ?: return@mapNotNull null
            val fn = obj.getAsJsonObject("function") ?: return@mapNotNull null
            val name = fn.get("name")?.asString ?: return@mapNotNull null
            val args = fn.get("arguments")?.asString ?: "{}"
            ToolCall(id = id, name = name, argumentsJson = args)
        }
        return ChatMessage(
            role = role,
            content = content,
            toolCalls = toolCalls?.takeIf { it.isNotEmpty() },
        )
    }

    private fun messagesToJson(messages: List<ChatMessage>): JsonArray {
        val arr = JsonArray()
        for (m in messages) {
            val obj = JsonObject()
            obj.addProperty("role", m.role)
            when {
                m.role == "tool" -> {
                    obj.addProperty("tool_call_id", m.toolCallId)
                    if (m.name != null) obj.addProperty("name", m.name)
                    obj.addProperty("content", m.content ?: "")
                }
                m.toolCalls != null -> {
                    if (m.content != null) obj.addProperty("content", m.content)
                    else obj.add("content", null)
                    val calls = JsonArray()
                    for (tc in m.toolCalls) {
                        calls.add(JsonObject().apply {
                            addProperty("id", tc.id)
                            addProperty("type", "function")
                            add("function", JsonObject().apply {
                                addProperty("name", tc.name)
                                addProperty("arguments", tc.argumentsJson)
                            })
                        })
                    }
                    obj.add("tool_calls", calls)
                }
                else -> obj.addProperty("content", m.content ?: "")
            }
            arr.add(obj)
        }
        return arr
    }

    private fun toolsToJson(tools: List<ToolDefinition>): JsonArray {
        val arr = JsonArray()
        for (t in tools) {
            arr.add(JsonObject().apply {
                addProperty("type", "function")
                add("function", JsonObject().apply {
                    addProperty("name", t.name)
                    addProperty("description", t.description)
                    add("parameters", t.parametersJson)
                })
            })
        }
        return arr
    }
}
