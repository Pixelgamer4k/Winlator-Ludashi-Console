package com.winlator.cmod.console.compat

import com.winlator.cmod.core.Callback
import java.util.ArrayDeque

/**
 * Ring buffer of recent Wine/guest stdout+stderr lines for failure classification.
 * Registered as a ProcessHelper debug callback for every game session.
 */
object LaunchLogBuffer : Callback<String> {

    private const val CAPACITY = 400
    private val lines = ArrayDeque<String>(CAPACITY)
    private val lock = Any()

    fun clear() {
        synchronized(lock) { lines.clear() }
    }

    override fun call(line: String?) {
        if (line.isNullOrEmpty()) return
        synchronized(lock) {
            if (lines.size >= CAPACITY) lines.removeFirst()
            lines.addLast(line)
        }
    }

    fun snapshot(): List<String> {
        synchronized(lock) {
            return ArrayList(lines)
        }
    }

    fun snapshotText(maxChars: Int = 12000): String {
        val all = snapshot().joinToString("\n")
        return if (all.length <= maxChars) all else all.takeLast(maxChars)
    }
}
