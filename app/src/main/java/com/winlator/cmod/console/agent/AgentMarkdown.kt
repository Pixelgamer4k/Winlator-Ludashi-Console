package com.winlator.cmod.console.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.console.ConsoleColors
import com.winlator.cmod.console.ConsoleFontFamily
import com.winlator.cmod.console.ConsoleRowShape

/**
 * Lightweight markdown → Compose for Hive Agent replies.
 * Renders bold/italic/code/headers/lists; flattens LaTeX and crude tables.
 */
@Composable
fun AgentMarkdownText(
    markdown: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(sanitizeAgentMarkdown(markdown)) }
    Column(modifier) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MdBlock.Heading -> {
                    Text(
                        block.text,
                        color = color,
                        fontFamily = ConsoleFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = when (block.level) {
                            1 -> 18.sp
                            2 -> 17.sp
                            else -> 16.sp
                        },
                        lineHeight = 24.sp,
                    )
                }
                is MdBlock.Paragraph -> {
                    Text(
                        inlineMarkdown(block.text, color),
                        fontFamily = ConsoleFontFamily,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )
                }
                is MdBlock.Bullet -> {
                    Text(
                        buildAnnotatedString {
                            append("•  ")
                            append(inlineMarkdown(block.text, color))
                        },
                        fontFamily = ConsoleFontFamily,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                is MdBlock.Numbered -> {
                    Text(
                        buildAnnotatedString {
                            append("${block.n}.  ")
                            append(inlineMarkdown(block.text, color))
                        },
                        fontFamily = ConsoleFontFamily,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                is MdBlock.Code -> {
                    SelectionContainer {
                        Text(
                            block.code.trimEnd(),
                            color = color.copy(alpha = 0.92f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ConsoleRowShape)
                                .background(ConsoleColors.CardStroke)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
                is MdBlock.Quote -> {
                    Text(
                        inlineMarkdown(block.text, color.copy(alpha = 0.9f)),
                        fontFamily = ConsoleFontFamily,
                        fontSize = 14.sp,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 20.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ConsoleRowShape)
                            .background(ConsoleColors.Canvas)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
                is MdBlock.Divider -> {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ConsoleColors.CardStroke),
                    )
                }
            }
            if (index < blocks.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class Bullet(val text: String) : MdBlock()
    data class Numbered(val n: Int, val text: String) : MdBlock()
    data class Code(val code: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data object Divider : MdBlock()
}

/** Clean LLM quirks before parsing. */
internal fun sanitizeAgentMarkdown(raw: String): String {
    var s = raw.replace("\r\n", "\n").replace('\r', '\n')

    // Strip common ChatGPT artifacts
    s = s.replace(Regex("(?m)^\\s*```[a-zA-Z0-9_+-]*\\s*$"), "```")

    // Convert LaTeX display math $$...$$ → plain
    s = Regex("""\$\$(.+?)\$\$""", setOf(RegexOption.DOT_MATCHES_ALL)).replace(s) { m ->
        "\n" + latexToPlain(m.groupValues[1]) + "\n"
    }
    // Inline $...$ (avoid currency like $5)
    s = Regex("""(?<!\$)\$(?!\$)([^\$\n]{1,120}?)\$(?!\$)""").replace(s) { m ->
        val inner = m.groupValues[1]
        if (inner.any { it.isLetter() } || inner.contains('\\') || inner.contains('^') || inner.contains('_')) {
            latexToPlain(inner)
        } else {
            m.value
        }
    }
    // \( ... \) and \[ ... \]
    s = Regex("""\\\((.+?)\\\)""", setOf(RegexOption.DOT_MATCHES_ALL)).replace(s) {
        latexToPlain(it.groupValues[1])
    }
    s = Regex("""\\\[(.+?)\\\]""", setOf(RegexOption.DOT_MATCHES_ALL)).replace(s) {
        "\n" + latexToPlain(it.groupValues[1]) + "\n"
    }

    // Pipe tables → readable lines
    s = flattenMarkdownTables(s)

    // Horizontal rules
    s = Regex("""(?m)^\s*(-{3,}|\*{3,}|_{3,})\s*$""").replace(s, "\n---\n")

    return s.trim()
}

private fun latexToPlain(tex: String): String {
    var t = tex.trim()
    val map = linkedMapOf(
        "\\times" to "×", "\\cdot" to "·", "\\pm" to "±",
        "\\leq" to "≤", "\\geq" to "≥", "\\neq" to "≠",
        "\\approx" to "≈", "\\infty" to "∞",
        "\\rightarrow" to "→", "\\leftarrow" to "←", "\\Rightarrow" to "⇒",
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\pi" to "π", "\\theta" to "θ", "\\lambda" to "λ", "\\mu" to "μ",
        "\\sigma" to "σ", "\\omega" to "ω",
        "\\%" to "%", "\\_" to "_", "\\{" to "{", "\\}" to "}",
        "\\left" to "", "\\right" to "", "\\," to " ", "\\;" to " ", "\\!" to "",
        "\\quad" to " ", "\\qquad" to "  ",
    )
    for ((k, v) in map) t = t.replace(k, v)
    t = Regex("""\\frac\{([^{}]+)\}\{([^{}]+)\}""").replace(t) { "(${it.groupValues[1]})/(${it.groupValues[2]})" }
    t = Regex("""\\sqrt\{([^{}]+)\}""").replace(t) { "√(${it.groupValues[1]})" }
    t = Regex("""\^\{([^{}]+)\}""").replace(t) { "^${it.groupValues[1]}" }
    t = Regex("""_\{([^{}]+)\}""").replace(t) { "_${it.groupValues[1]}" }
    t = t.replace(Regex("""\\[a-zA-Z]+"""), "")
    t = t.replace("{", "").replace("}", "")
    return t.replace(Regex("""\s+"""), " ").trim()
}

private fun flattenMarkdownTables(src: String): String {
    val lines = src.split('\n')
    val out = mutableListOf<String>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.trim().startsWith("|") && line.indexOf('|', 1) >= 0) {
            val table = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                table += lines[i]
                i++
            }
            val rows = table
                .filterNot { it.replace("|", "").trim().matches(Regex("[:\\-\\s]+")) }
                .map { row ->
                    row.trim().trim('|').split("|").map { it.trim() }.filter { it.isNotEmpty() }
                }
                .filter { it.isNotEmpty() }
            if (rows.isNotEmpty()) {
                val header = rows.first()
                out += ""
                rows.drop(1).forEach { cells ->
                    val paired = header.zip(cells).joinToString(" · ") { (h, c) -> "$h: $c" }
                    if (paired.isNotBlank()) out += "• $paired"
                    else out += "• " + cells.joinToString(" · ")
                }
                if (rows.size == 1) out += "• " + header.joinToString(" · ")
                out += ""
            }
            continue
        }
        out += line
        i++
    }
    return out.joinToString("\n")
}

private fun parseMarkdownBlocks(src: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = src.split('\n')
    var i = 0
    val para = StringBuilder()

    fun flushPara() {
        val t = para.toString().trim()
        if (t.isNotEmpty()) blocks += MdBlock.Paragraph(t)
        para.clear()
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            flushPara()
            i++
            val code = StringBuilder()
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                code.appendLine(lines[i])
                i++
            }
            if (i < lines.size) i++ // closing fence
            blocks += MdBlock.Code(code.toString())
            continue
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            flushPara()
            blocks += MdBlock.Divider
            i++
            continue
        }

        val heading = Regex("""^(#{1,6})\s+(.+)$""").matchEntire(trimmed)
        if (heading != null) {
            flushPara()
            blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
            i++
            continue
        }

        val bullet = Regex("""^[-*+]\s+(.+)$""").matchEntire(trimmed)
        if (bullet != null) {
            flushPara()
            blocks += MdBlock.Bullet(bullet.groupValues[1])
            i++
            continue
        }

        val numbered = Regex("""^(\d+)[.)]\s+(.+)$""").matchEntire(trimmed)
        if (numbered != null) {
            flushPara()
            blocks += MdBlock.Numbered(numbered.groupValues[1].toInt(), numbered.groupValues[2])
            i++
            continue
        }

        if (trimmed.startsWith(">")) {
            flushPara()
            val quote = StringBuilder()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quote.append(lines[i].trim().removePrefix(">").trim()).append(' ')
                i++
            }
            blocks += MdBlock.Quote(quote.toString().trim())
            continue
        }

        if (trimmed.isEmpty()) {
            flushPara()
            i++
            continue
        }

        if (para.isNotEmpty()) para.append(' ')
        para.append(trimmed)
        i++
    }
    flushPara()
    return blocks.ifEmpty { listOf(MdBlock.Paragraph(src.trim())) }
}

private fun inlineMarkdown(text: String, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        // Process **bold**, *italic*, `code`, ~~strike~~, [label](url)
        var i = 0
        val s = text
        while (i < s.length) {
            when {
                s.startsWith("**", i) || s.startsWith("__", i) -> {
                    val delim = if (s.startsWith("**", i)) "**" else "__"
                    val end = s.indexOf(delim, i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                            append(stripNestedMarks(s.substring(i + 2, end)))
                        }
                        i = end + 2
                    } else {
                        append(s[i]); i++
                    }
                }
                s.startsWith("~~", i) -> {
                    val end = s.indexOf("~~", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = baseColor)) {
                            append(s.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(s[i]); i++
                    }
                }
                s.startsWith("`", i) && !s.startsWith("```", i) -> {
                    val end = s.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                background = Color(0x14000000),
                                color = baseColor,
                            ),
                        ) {
                            append(s.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(s[i]); i++
                    }
                }
                s.startsWith("*", i) || (s.startsWith("_", i) && (i == 0 || !s[i - 1].isLetterOrDigit())) -> {
                    val delim = s[i].toString()
                    val end = s.indexOf(delim, i + 1)
                    if (end > i + 1 && (delim != "_" || end == s.length - 1 || !s[end + 1].isLetterOrDigit())) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                            append(s.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(s[i]); i++
                    }
                }
                s.startsWith("[", i) -> {
                    val mid = s.indexOf("](", i)
                    val end = if (mid > i) s.indexOf(')', mid + 2) else -1
                    if (mid > i && end > mid) {
                        val label = s.substring(i + 1, mid)
                        withStyle(
                            SpanStyle(
                                color = ConsoleColors.AccentBlue,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium,
                            ),
                        ) {
                            append(label)
                        }
                        i = end + 1
                    } else {
                        append(s[i]); i++
                    }
                }
                else -> {
                    withStyle(SpanStyle(color = baseColor)) { append(s[i]) }
                    i++
                }
            }
        }
    }
}

private fun stripNestedMarks(s: String): String =
    s.replace("**", "").replace("__", "")
