package com.winlator.cmod.delta;

import android.app.Activity;
import android.content.Context;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.inputcontrols.ExternalController;
import com.winlator.cmod.inputcontrols.GamepadState;
import com.winlator.cmod.winhandler.MouseEventFlags;
import com.winlator.cmod.winhandler.WinHandler;
import com.winlator.cmod.xserver.XKeycode;
import com.winlator.cmod.xserver.XServer;

import java.lang.ref.WeakReference;

/**
 * Bridges the on-screen Delta Xbox chassis to Ludashi's FakeInputWriter / XInput path.
 * Stick axes: +X right, +Y screen-down (matches ControlElement / Linux ABS_Y).
 * Also injects Windows virtual keys for the on-screen keyboard overlays.
 */
public final class ChassisInputBridge {
    private static final ChassisInputBridge INSTANCE = new ChassisInputBridge();

    /** Win32 KEYEVENTF_KEYUP */
    private static final int KEYEVENTF_KEYUP = 0x0002;

    private final GamepadState state = new GamepadState();
    private volatile WinHandler winHandler;
    private volatile XServer xServer;
    private WeakReference<Activity> activityRef = new WeakReference<>(null);

    private ChassisInputBridge() {}

    public static ChassisInputBridge get() {
        return INSTANCE;
    }

    public void bind(WinHandler handler) {
        this.winHandler = handler;
    }

    public void bindXServer(XServer server) {
        this.xServer = server;
    }

    public void bindActivity(Activity activity) {
        this.activityRef = new WeakReference<>(activity);
    }

    /** Toggle the Android soft keyboard (IME). */
    public void showSystemKeyboard() {
        Activity a = activityRef.get();
        if (a instanceof AppCompatActivity) {
            AppUtils.showKeyboard((AppCompatActivity) a);
            return;
        }
        if (a != null) {
            InputMethodManager imm =
                    (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
            }
        }
    }

    /**
     * Inject a Windows virtual-key tap (down + up).
     * @param vk Windows VK_* code (e.g. 0x41 = 'A', 0x0D = Enter)
     */
    public void injectVkTap(int vk) {
        injectVk(vk, true);
        injectVk(vk, false);
    }

    public void injectVk(int vk, boolean down) {
        byte vkey = (byte) (vk & 0xFF);
        int flags = down ? 0 : KEYEVENTF_KEYUP;
        WinHandler wh = winHandler;
        if (wh != null) {
            wh.keyboardEvent(vkey, flags);
        }
        // Also poke X11 path when available (some Wine builds prefer it)
        XServer xs = xServer;
        if (xs != null) {
            XKeycode xk = vkToXKeycode(vk);
            if (xk != null) {
                if (down) xs.injectKeyPress(xk);
                else xs.injectKeyRelease(xk);
            }
        }
    }

    private static XKeycode vkToXKeycode(int vk) {
        if (vk >= 0x41 && vk <= 0x5A) {
            // A-Z → KEY_A … KEY_Z via enum name
            try {
                return XKeycode.valueOf("KEY_" + (char) vk);
            } catch (Exception ignored) {
                return null;
            }
        }
        if (vk >= 0x30 && vk <= 0x39) {
            try {
                return XKeycode.valueOf("KEY_" + (char) vk);
            } catch (Exception ignored) {
                return null;
            }
        }
        return switch (vk) {
            case 0x0D -> XKeycode.KEY_ENTER;
            case 0x08 -> XKeycode.KEY_BKSP;
            case 0x09 -> XKeycode.KEY_TAB;
            case 0x1B -> XKeycode.KEY_ESC;
            case 0x20 -> XKeycode.KEY_SPACE;
            case 0x10 -> XKeycode.KEY_SHIFT_L;
            default -> null;
        };
    }

    public void setStick(float lsX, float lsY, float rsX, float rsY) {
        state.thumbLX = clamp(lsX);
        state.thumbLY = clamp(lsY);
        state.thumbRX = clamp(rsX);
        state.thumbRY = clamp(rsY);
        flush();
    }

    public void setButton(byte idx, boolean pressed) {
        state.setPressed(idx, pressed);
        if (idx == ExternalController.IDX_BUTTON_L2) {
            state.triggerL = pressed ? 1f : 0f;
        } else if (idx == ExternalController.IDX_BUTTON_R2) {
            state.triggerR = pressed ? 1f : 0f;
        }
        flush();
    }

    public void setTrigger(Boolean left, Boolean right) {
        if (left != null) {
            state.triggerL = left ? 1f : 0f;
            state.setPressed(ExternalController.IDX_BUTTON_L2, left);
        }
        if (right != null) {
            state.triggerR = right ? 1f : 0f;
            state.setPressed(ExternalController.IDX_BUTTON_R2, right);
        }
        flush();
    }

    public void setDpad(boolean up, boolean right, boolean down, boolean left) {
        state.dpad[0] = up;
        state.dpad[1] = right;
        state.dpad[2] = down;
        state.dpad[3] = left;
        flush();
    }

    // ── Mouse (for Mouse Pad mode) ─────────────────────────────────────────

    /** Relative mouse move in pixels. */
    public void mouseMove(int dx, int dy) {
        WinHandler wh = winHandler;
        if (wh != null && (dx != 0 || dy != 0)) {
            wh.mouseEvent(MouseEventFlags.MOVE, dx, dy, 0);
        }
    }

    /** Left / middle / right button down or up. button: 0=L 1=M 2=R */
    public void mouseButton(int button, boolean down) {
        WinHandler wh = winHandler;
        if (wh == null) return;
        int flag;
        switch (button) {
            case 1:
                flag = down ? MouseEventFlags.MIDDLEDOWN : MouseEventFlags.MIDDLEUP;
                break;
            case 2:
                flag = down ? MouseEventFlags.RIGHTDOWN : MouseEventFlags.RIGHTUP;
                break;
            default:
                flag = down ? MouseEventFlags.LEFTDOWN : MouseEventFlags.LEFTUP;
                break;
        }
        wh.mouseEvent(flag, 0, 0, 0);
    }

    /** Vertical wheel: positive = scroll up. */
    public void mouseWheel(int delta) {
        WinHandler wh = winHandler;
        if (wh != null && delta != 0) {
            wh.mouseEvent(MouseEventFlags.WHEEL, 0, 0, delta);
        }
    }

    private void flush() {
        WinHandler wh = winHandler;
        if (wh != null) {
            wh.sendDeltaVirtualGamepadState(state);
        }
    }

    private static float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }
}
