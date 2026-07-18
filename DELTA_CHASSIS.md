# PC Emu Delta chassis on Winlator Ludashi (StevenMXZ)

Portrait layout while a game is running:

- **Top** — hard **4:3** game pane (`width × 3/4`, chassis keeps ≥42% height)
- **Bottom** — concept-faithful **3D** Delta chassis (stick, brushed pad, face diamond, guide, L1–R2)

## Input path

```
DeltaChassisView touch
  → ChassisInputBridge (GamepadState)
  → WinHandler.sendDeltaVirtualGamepadState()
  → FakeInputWriter (dev/input/eventN)
  → Wine XInput
```

Same stack as Ludashi on-screen controls / physical pads. Stick axes: **+X right, +Y down** (Linux/Android stick convention).

## Code

| Path | Role |
|------|------|
| `app/.../delta/DeltaChassisView.java` | Chassis UI + gestures |
| `app/.../delta/ChassisInputBridge.java` | GamepadState bridge |
| `app/.../winhandler/WinHandler.java` | `sendDeltaVirtualGamepadState` |
| `res/layout/xserver_display_activity.xml` | Split layout |
| `XServerDisplayActivity.java` | Portrait + bind chassis |

## Build

```bash
cd Winlator-Ludashi
# First build downloads imagefs + proton assets (large)
./gradlew :app:assembleDebug
```

Package id: `com.winlator.cmod` (dev/vanilla-style id in this tree; Ludashi renames may differ in release APKs).

## Notes

- Chassis is **always on** during `XServerDisplayActivity` (in-game). Home/containers UI is unchanged.
- Built-in touch overlay remains available via the sidebar; chassis does not replace it, it coexists.
- Silksong / container tuning is still per-container in Ludashi settings.
