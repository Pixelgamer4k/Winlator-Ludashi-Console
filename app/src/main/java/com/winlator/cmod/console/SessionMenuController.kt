package com.winlator.cmod.console

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.winlator.cmod.core.CPUStatus
import com.winlator.cmod.core.ProcessHelper
import com.winlator.cmod.core.StringUtils
import com.winlator.cmod.winhandler.OnGetProcessInfoListener
import com.winlator.cmod.winhandler.ProcessInfo
import com.winlator.cmod.winhandler.WinHandler
import java.util.Timer
import java.util.TimerTask

enum class SessionPanel {
    HOME,
    TASKS,
    VIBRATION,
    LOGS,
}

data class SessionProcessRow(
    val pid: Int,
    val name: String,
    val memory: String,
    val wow64: Boolean,
    val affinityMask: Int,
)

/**
 * Hosts [ConsoleSessionMenu] and nested Console panels (tasks / vibration / logs).
 */
class SessionMenuController(
    composeView: ComposeView,
    private val context: Context,
    private val actions: SessionMenuActions,
) : OnGetProcessInfoListener {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var winHandler: WinHandler? = null
    private var pollTimer: Timer? = null
    private val pendingProcesses = ArrayList<ProcessInfo>()

    private var model by mutableStateOf(defaultModel())

    private val logListener: () -> Unit = {
        mainHandler.post {
            if (model.panel == SessionPanel.LOGS) {
                model = model.copy(
                    logLines = SessionLogBuffer.snapshot(),
                    logsPaused = SessionLogBuffer.paused,
                )
            }
        }
    }

    init {
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            ConsoleSessionMenu(model = model, actions = wrapActions(actions))
        }
        SessionLogBuffer.addListener(logListener)
    }

    fun update(next: SessionMenuModel) {
        model = next.copy(
            panel = model.panel,
            cpuPercent = model.cpuPercent,
            memoryText = model.memoryText,
            processes = model.processes,
            vibrationSlots = model.vibrationSlots,
            logLines = model.logLines,
            logsPaused = model.logsPaused,
            newTaskCommand = model.newTaskCommand,
            selectedProcessPid = model.selectedProcessPid,
            affinityMask = model.affinityMask,
        )
    }

    fun onDrawerClosed() {
        stopTaskPolling()
        if (model.panel != SessionPanel.HOME) {
            model = model.copy(panel = SessionPanel.HOME)
        }
    }

    fun dispose() {
        stopTaskPolling()
        SessionLogBuffer.removeListener(logListener)
    }

    private fun wrapActions(base: SessionMenuActions): SessionMenuActions =
        object : SessionMenuActions by base {
            override fun openTaskManager() {
                openTasks()
            }

            override fun showVibration() {
                openVibration()
            }

            override fun openLogs() {
                openLogsPanel()
            }

            override fun backToSessionHome() {
                stopTaskPolling()
                model = model.copy(panel = SessionPanel.HOME, selectedProcessPid = -1)
            }

            override fun setVibrationSlot(slot: Int, enabled: Boolean) {
                winHandler?.setVibrationEnabledForSlot(slot, enabled)
                val slots = model.vibrationSlots.toMutableList()
                if (slot in slots.indices) {
                    slots[slot] = enabled
                    model = model.copy(vibrationSlots = slots)
                }
            }

            override fun clearLogs() {
                SessionLogBuffer.clear()
                model = model.copy(logLines = emptyList())
            }

            override fun toggleLogsPaused() {
                SessionLogBuffer.setPaused(!SessionLogBuffer.paused)
                model = model.copy(logsPaused = SessionLogBuffer.paused)
            }

            override fun setNewTaskCommand(command: String) {
                model = model.copy(newTaskCommand = command)
            }

            override fun runNewTask() {
                val cmd = model.newTaskCommand.trim().ifEmpty { "taskmgr.exe" }
                winHandler?.exec(cmd)
            }

            override fun selectProcess(pid: Int) {
                val proc = model.processes.firstOrNull { it.pid == pid }
                val mask = proc?.affinityMask ?: 0
                model = model.copy(selectedProcessPid = pid, affinityMask = mask)
            }

            override fun killSelectedProcess() {
                val proc = model.processes.firstOrNull { it.pid == model.selectedProcessPid } ?: return
                winHandler?.killProcess(proc.name)
                model = model.copy(selectedProcessPid = -1)
                winHandler?.listProcesses()
            }

            override fun bringSelectedToFront() {
                val proc = model.processes.firstOrNull { it.pid == model.selectedProcessPid } ?: return
                winHandler?.bringToFront(proc.name)
            }

            override fun toggleAffinityCpu(cpuIndex: Int) {
                val bit = 1 shl cpuIndex
                val next = if ((model.affinityMask and bit) != 0) {
                    model.affinityMask and bit.inv()
                } else {
                    model.affinityMask or bit
                }
                model = model.copy(affinityMask = next)
            }

            override fun applyAffinity() {
                val pid = model.selectedProcessPid
                if (pid <= 0) return
                winHandler?.setProcessAffinity(pid, model.affinityMask)
                winHandler?.listProcesses()
            }
        }

    private fun openTasks() {
        model = model.copy(panel = SessionPanel.TASKS, selectedProcessPid = -1)
        startTaskPolling()
    }

    private fun openVibration() {
        val handler = winHandler
        val max = handler?.maxControllers ?: 4
        val slots = (0 until max).map { handler?.isVibrationEnabledForSlot(it) == true }
        model = model.copy(panel = SessionPanel.VIBRATION, vibrationSlots = slots)
    }

    private fun openLogsPanel() {
        model = model.copy(
            panel = SessionPanel.LOGS,
            logLines = SessionLogBuffer.snapshot(),
            logsPaused = SessionLogBuffer.paused,
        )
    }

    fun attachWinHandler(handler: WinHandler?) {
        winHandler = handler
    }

    private fun startTaskPolling() {
        stopTaskPolling()
        val handler = winHandler ?: return
        handler.setOnGetProcessInfoListener(this)
        pollTimer = Timer()
        pollTimer?.schedule(object : TimerTask() {
            override fun run() {
                handler.listProcesses()
                mainHandler.post {
                    updateCpuMem()
                }
            }
        }, 0, 1000)
    }

    private fun stopTaskPolling() {
        pollTimer?.cancel()
        pollTimer = null
        winHandler?.setOnGetProcessInfoListener(null)
        pendingProcesses.clear()
    }

    private fun updateCpuMem() {
        val clocks = CPUStatus.getCurrentClockSpeeds()
        var cpuPercent = 0
        if (clocks.isNotEmpty()) {
            var total = 0
            var max = 0
            for (i in clocks.indices) {
                total += clocks[i].toInt()
                max = maxOf(max, CPUStatus.getMaxClockSpeed(i).toInt())
            }
            val avg = total / clocks.size
            cpuPercent = if (max == 0) 0 else ((avg.toFloat() / max) * 100f).toInt()
        }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val used = mem.totalMem - mem.availMem
        val memText = StringUtils.formatBytes(used, false) + " / " + StringUtils.formatBytes(mem.totalMem)
        if (model.panel == SessionPanel.TASKS) {
            model = model.copy(cpuPercent = cpuPercent, memoryText = memText)
        }
    }

    override fun onGetProcessInfo(index: Int, count: Int, processInfo: ProcessInfo?) {
        mainHandler.post {
            if (model.panel != SessionPanel.TASKS) return@post
            if (count == 0 || processInfo == null) {
                pendingProcesses.clear()
                model = model.copy(processes = emptyList())
                return@post
            }
            if (index == 0) pendingProcesses.clear()
            pendingProcesses.add(processInfo)
            if (index == count - 1) {
                val rows = pendingProcesses.map {
                    SessionProcessRow(
                        pid = it.pid,
                        name = it.name,
                        memory = it.formattedMemoryUsage,
                        wow64 = it.wow64Process,
                        affinityMask = run {
                            val linux = ProcessHelper.getProcessAffinityMask(it.pid)
                            if (linux != 0) linux else it.affinityMask
                        },
                    )
                }
                model = model.copy(processes = rows)
            }
        }
    }

    companion object {
        fun defaultModel() = SessionMenuModel(
            title = "Session",
            paused = false,
            relativeMouse = false,
            mouseDisabled = false,
            softStretch = false,
            showTouchControls = false,
            touchTimeout = false,
            touchHaptics = false,
            controlsOpacity = 0.6f,
            hudMode = 0,
            fpsLimitIndex = 0,
            fsrEnabled = false,
            postFxIndex = 0,
            sharpness = 50f,
            logsEnabled = false,
            xrMode = false,
            profileNames = listOf("Disabled"),
            profileIndex = 0,
        )
    }
}
