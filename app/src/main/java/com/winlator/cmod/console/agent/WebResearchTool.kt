package com.winlator.cmod.console.agent

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Lightweight “headless” research via HTTP fetch + text extraction
 * (DuckDuckGo HTML + Reddit public JSON). No Chromium.
 */
class WebResearchTool(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) {
    fun research(query: String, maxResults: Int = 5): String {
        val q = query.trim()
        if (q.isEmpty()) return "Empty query"

        val findings = mutableListOf<String>()
        findings += "=== Research: $q ==="

        try {
            findings += searchDuckDuckGo(q, maxResults)
        } catch (e: Exception) {
            findings += "DuckDuckGo error: ${e.message}"
        }

        try {
            findings += searchReddit(q, maxResults.coerceAtMost(4))
        } catch (e: Exception) {
            findings += "Reddit error: ${e.message}"
        }

        return findings.joinToString("\n\n").take(12_000)
    }

    private fun searchDuckDuckGo(query: String, maxResults: Int): String {
        val url = "https://html.duckduckgo.com/html/?q=" +
            URLEncoder.encode("$query winlator android OR box64 OR dxvk", "UTF-8")
        val html = fetch(url) ?: return "DuckDuckGo: no response"
        val results = mutableListOf<String>()
        val linkPattern = Pattern.compile(
            """class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
            Pattern.DOTALL or Pattern.CASE_INSENSITIVE,
        )
        val snippetPattern = Pattern.compile(
            """class="result__snippet"[^>]*>(.*?)</(?:a|td|div)""",
            Pattern.DOTALL or Pattern.CASE_INSENSITIVE,
        )
        val links = mutableListOf<Pair<String, String>>()
        val lm = linkPattern.matcher(html)
        while (lm.find() && links.size < maxResults) {
            val href = decodeHtml(lm.group(1) ?: continue)
            val title = stripTags(lm.group(2) ?: "")
            if (href.startsWith("http")) links += href to title
        }
        val snippets = mutableListOf<String>()
        val sm = snippetPattern.matcher(html)
        while (sm.find() && snippets.size < maxResults) {
            snippets += stripTags(sm.group(1) ?: "")
        }
        results += "--- DuckDuckGo ---"
        links.forEachIndexed { i, (href, title) ->
            val snip = snippets.getOrNull(i)?.take(280).orEmpty()
            results += "[${i + 1}] $title\n$url: $href\n$snip"
            // Fetch first 2 pages for deeper context
            if (i < 2) {
                try {
                    val page = fetch(href)?.let { stripTags(it).take(1500) }
                    if (!page.isNullOrBlank()) results += "Page extract:\n$page"
                } catch (_: Exception) {
                }
            }
        }
        if (links.isEmpty()) results += "(no results parsed)"
        return results.joinToString("\n")
    }

    private fun searchReddit(query: String, maxResults: Int): String {
        val url = "https://www.reddit.com/search.json?q=" +
            URLEncoder.encode("$query winlator OR box64", "UTF-8") +
            "&sort=relevance&t=year&limit=$maxResults"
        val raw = fetch(url, acceptJson = true) ?: return "Reddit: no response"
        val root = JSONObject(raw)
        val children = root.optJSONObject("data")?.optJSONArray("children") ?: JSONArray()
        val out = mutableListOf("--- Reddit ---")
        for (i in 0 until children.length()) {
            val data = children.getJSONObject(i).optJSONObject("data") ?: continue
            val title = data.optString("title")
            val permalink = data.optString("permalink")
            val selftext = data.optString("selftext").take(500)
            val sub = data.optString("subreddit")
            out += "[r/$sub] $title\nhttps://reddit.com$permalink\n$selftext"
        }
        if (out.size == 1) out += "(no posts)"
        return out.joinToString("\n\n")
    }

    private fun fetch(url: String, acceptJson: Boolean = false): String? {
        val builder = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "WinlatorConsoleHiveAgent/1.0 (Android; research; +https://winlator.console.local)",
            )
        if (acceptJson) builder.header("Accept", "application/json")
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun stripTags(html: String): String {
        return html
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
            .replace(Regex("(?is)<[^>]+>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("&#39;"), "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun decodeHtml(s: String): String {
        // DuckDuckGo sometimes wraps redirects: //duckduckgo.com/l/?uddg=...
        if (s.contains("uddg=")) {
            val m = Pattern.compile("uddg=([^&]+)").matcher(s)
            if (m.find()) {
                return try {
                    java.net.URLDecoder.decode(m.group(1), "UTF-8")
                } catch (_: Exception) {
                    s
                }
            }
        }
        return if (s.startsWith("//")) "https:$s" else s
    }
}
