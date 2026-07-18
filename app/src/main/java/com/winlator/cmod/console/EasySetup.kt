package com.winlator.cmod.console

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import com.winlator.cmod.box64.Box64Preset
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog
import com.winlator.cmod.contents.ContentsManager
import com.winlator.cmod.core.DefaultVersion
import com.winlator.cmod.core.GPUInformation
import com.winlator.cmod.core.WineInfo
import com.winlator.cmod.core.WineThemeManager
import com.winlator.cmod.fexcore.FEXCorePreset
import com.winlator.cmod.winhandler.WinHandler
import org.json.JSONObject

/**
 * First-run / easy-path setup: ensure a performance-oriented "PC Games" container exists
 * so normal users never touch Wine/container jargon.
 */
object EasySetup {
    const val CONTAINER_NAME = "PC Games"
    private const val PREF_READY = "console_easy_container_ready"

    fun isMarkedReady(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_READY, false)

    fun markReady(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(PREF_READY, true)
            .apply()
    }

    /** Preferred play container: named PC Games, else first container, else null. */
    @JvmStatic
    fun primaryContainer(manager: ContainerManager): Container? {
        val list = manager.containers
        if (list.isEmpty()) return null
        return list.firstOrNull { it.name.equals(CONTAINER_NAME, ignoreCase = true) } ?: list[0]
    }

    /**
     * Ensures a playable container exists. Creates one with performance defaults when empty.
     * Invokes [onDone] on the main thread (container may be null if create failed).
     */
    @JvmStatic
    fun ensureContainer(
        context: Context,
        manager: ContainerManager,
        onProgress: ((String) -> Unit)? = null,
        onDone: (Container?) -> Unit,
    ) {
        val existing = primaryContainer(manager)
        if (existing != null) {
            markReady(context)
            Handler(Looper.getMainLooper()).post { onDone(existing) }
            return
        }

        onProgress?.invoke("Setting up your console…")
        val contentsManager = ContentsManager(context)
        contentsManager.syncContents()

        val data = buildPerformanceContainerJson(context)
        manager.createContainerAsync(data, contentsManager) { created ->
            if (created != null) markReady(context)
            onDone(created)
        }
    }

    private fun buildPerformanceContainerJson(context: Context): JSONObject {
        val driverVersion =
            if (GPUInformation.isDriverSupported(DefaultVersion.WRAPPER_ADRENO, context)) {
                DefaultVersion.WRAPPER_ADRENO
            } else {
                DefaultVersion.WRAPPER
            }

        val gfxConfig = HashMap(
            GraphicsDriverConfigDialog.parseGraphicsDriverConfig(Container.DEFAULT_GRAPHICSDRIVERCONFIG)
        )
        gfxConfig["version"] = driverVersion
        val graphicsDriverConfig = GraphicsDriverConfigDialog.toGraphicsDriverConfig(gfxConfig)

        return JSONObject().apply {
            put("name", CONTAINER_NAME)
            put("screenSize", Container.DEFAULT_SCREEN_SIZE)
            put("envVars", Container.DEFAULT_ENV_VARS)
            put("cpuList", Container.getFallbackCPUList())
            put("cpuListWoW64", Container.getFallbackCPUListWoW64())
            put("graphicsDriver", Container.DEFAULT_GRAPHICS_DRIVER)
            put("graphicsDriverConfig", graphicsDriverConfig)
            put("dxwrapper", Container.DEFAULT_DXWRAPPER)
            put("dxwrapperConfig", Container.DEFAULT_DXWRAPPERCONFIG)
            put("audioDriver", Container.DEFAULT_AUDIO_DRIVER)
            put("emulator", Container.DEFAULT_EMULATOR)
            put("wincomponents", Container.DEFAULT_WINCOMPONENTS)
            put("drives", Container.DEFAULT_DRIVES)
            put("showFPS", false)
            put("fullscreenStretched", false)
            put("exclusiveXInput", true)
            put("inputType", WinHandler.DEFAULT_INPUT_TYPE)
            put("startupSelection", Container.STARTUP_SELECTION_ESSENTIAL.toInt())
            put("box64Version", DefaultVersion.BOX64)
            put("box64Preset", Box64Preset.PERFORMANCE)
            put("fexcoreVersion", DefaultVersion.FEXCORE)
            put("fexcorePreset", FEXCorePreset.PERFORMANCE)
            put("desktopTheme", WineThemeManager.DEFAULT_DESKTOP_THEME)
            put("wineVersion", WineInfo.MAIN_WINE_VERSION.identifier())
            put("midiSoundFont", "")
            put("lc_all", "")
            put("primaryController", 1)
            put("rendererNative", false)
            put("rendererPresentMode", "fifo")
        }
    }
}
