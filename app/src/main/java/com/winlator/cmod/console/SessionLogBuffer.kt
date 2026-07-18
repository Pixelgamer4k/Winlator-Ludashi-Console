package com.winlator.cmod.console

import com.winlator.cmod.core.Callback
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Shared debug log buffer for the in-session Compose logs panel.
 * Also registered as a ProcessHelper debug callback.
 */
object SessionLogBuffer : Callback<String> {
    private const val MAX_LINES = 400
    private val lines = CopyOnWriteArrayList<String>()
    @Volatile
    var paused: Boolean = false
        private set

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun snapshot(): List<String> = lines.toList()

    fun clear() {
        lines.clear()
        notifyListeners()
    }

    fun setPaused(value: Boolean) {
        paused = value
        notifyListeners()
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    override fun call(line: String) {
        if (paused) return
        lines.add(line)
        while (lines.size > MAX_LINES) {
            lines.removeAt(0)
        }
        notifyListeners()
    }

    private fun notifyListeners() {
        listeners.forEach { it.invoke() }
    }
}
