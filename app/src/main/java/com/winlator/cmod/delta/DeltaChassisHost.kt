package com.winlator.cmod.delta

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.winlator.cmod.winhandler.WinHandler
import com.winlator.cmod.xserver.XServer

/**
 * Installs chassis, display edit tools overlay, and absolute top-right EDIT button.
 */
object DeltaChassisHost {
    @JvmStatic
    @JvmOverloads
    fun install(
        view: ComposeView,
        winHandler: WinHandler?,
        activity: Activity? = null,
        xServer: XServer? = null,
        editOverlay: ComposeView? = null,
        editButton: ComposeView? = null,
    ) {
        val bridge = ChassisInputBridge.get()
        bridge.bind(winHandler)
        if (activity != null) bridge.bindActivity(activity)
        else {
            val ctx = view.context
            if (ctx is Activity) bridge.bindActivity(ctx)
        }
        if (xServer != null) bridge.bindXServer(xServer)

        view.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        view.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                PixelDeltaChassis()
            }
        }

        editOverlay?.let { ov ->
            ov.setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            ov.setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    DeltaEditOverlay(Modifier.fillMaxWidth())
                }
            }
        }

        editButton?.let { btn ->
            btn.setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            btn.setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    DeltaEditButton()
                }
            }
        }
    }
}
