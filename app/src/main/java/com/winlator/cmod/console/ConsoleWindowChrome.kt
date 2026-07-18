package com.winlator.cmod.console

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.winlator.cmod.R

/**
 * Console-only edge-to-edge / immersive chrome (does not raise targetSdk).
 */
object ConsoleWindowChrome {

    @JvmStatic
    fun enter(activity: Activity) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        activity.findViewById<View>(R.id.Toolbar)?.visibility = View.GONE
        // Let Compose paint edge-to-edge behind the former black fragment host.
        activity.findViewById<View>(R.id.FLFragmentContainer)?.setBackgroundColor(Color.TRANSPARENT)
    }

    @JvmStatic
    fun exit(activity: Activity) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.BLACK
            window.navigationBarColor = Color.BLACK
        }

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        activity.findViewById<View>(R.id.Toolbar)?.visibility = View.VISIBLE
        activity.findViewById<View>(R.id.FLFragmentContainer)?.setBackgroundColor(Color.BLACK)
    }
}
