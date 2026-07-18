package com.winlator.cmod.console.agent

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.winlator.cmod.core.GPUInformation
import com.winlator.cmod.xenvironment.ImageFs

object DeviceContext {
    fun snapshot(context: Context): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val totalMb = mem.totalMem / (1024 * 1024)
        val availMb = mem.availMem / (1024 * 1024)
        val renderer = try {
            GPUInformation.getRenderer(null, context)
        } catch (_: Throwable) {
            "unknown"
        }
        val imageFs = ImageFs.find(context)
        return buildString {
            appendLine("manufacturer=${Build.MANUFACTURER}")
            appendLine("model=${Build.MODEL}")
            appendLine("device=${Build.DEVICE}")
            appendLine("android_sdk=${Build.VERSION.SDK_INT}")
            appendLine("android_release=${Build.VERSION.RELEASE}")
            appendLine("cpus=${Runtime.getRuntime().availableProcessors()}")
            appendLine("ram_total_mb=$totalMb")
            appendLine("ram_avail_mb=$availMb")
            appendLine("gpu_renderer=$renderer")
            appendLine("imagefs_valid=${imageFs.isValid}")
            appendLine("app=Winlator Ludashi Console")
        }.trim()
    }
}
