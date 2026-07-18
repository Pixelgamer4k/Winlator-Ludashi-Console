# Credits & acknowledgements

**Winlator Ludashi Console** stands on the work of many people. This project would not exist without them.

---

## Primary lineage

| Project | Author / maintainer | Role |
|--------|---------------------|------|
| **[Winlator](https://github.com/brunodev85/winlator)** | **BrunoSX (brunodev85)** | Original Winlator — Wine on Android, containers, XServer display, the foundation of everything here |
| **[Winlator Bionic](https://github.com/Pipetto-crypto/winlator)** | **Pipetto-crypto** | Bionic / GLIBC experimental edition — Box64, FEXCore, Arm64EC, modern packaging |
| **[Winlator-Ludashi](https://github.com/StevenMXZ/Winlator-Ludashi)** | **StevenMXZ (Steven)** | Ludashi packaging, maintenance, contents, and the tree this Console beta is based on |
| **[Ludashi-Backup](https://github.com/StevenMX-backup/Ludashi-Backup)** | StevenMX backup | Historical Ludashi build archive |

Thank you **BrunoSX**, **Pipetto-crypto**, and **Steven (StevenMXZ)** — full credit for Winlator and the Ludashi / Bionic lineage.

---

## Notable forks & community

- [coffincolors/winlator](https://github.com/coffincolors/winlator) — popular community fork
- [longjunyu2](https://github.com/longjunyu2/winlator) — contents / components ecosystem
- [StevenMXZ/Winlator-Contents](https://github.com/StevenMXZ/Winlator-Contents) — FEXCore, Box64, DXVK packs and updates
- ZeroKimchi and other creators — tutorials and driver guidance for the community

---

## Emulation & graphics stack

| Component | Project / author | Notes |
|-----------|------------------|-------|
| Wine | [winehq.org](https://www.winehq.org/) | Windows API compatibility layer |
| Box86 / Box64 | [ptitSeb](https://github.com/ptitSeb) | x86/x86_64 userspace emulation on ARM |
| FEX-Emu | [FEX-Emu](https://github.com/FEX-Emu/FEX) | Arm64EC / FEXCore path |
| PRoot | [proot-me.github.io](https://proot-me.github.io) | Userspace chroot |
| Mesa (Turnip / Zink / VirGL) | [mesa3d.org](https://www.mesa3d.org) | OpenGL / Vulkan drivers |
| Turnip / Adreno tips | Danylo (Igalia) and contributors | Qualcomm GPU work |
| AdrenoTools / drivers | [K11MCH1/AdrenoToolsDrivers](https://github.com/K11MCH1/AdrenoToolsDrivers) | Driver sideloading |
| libadrenotools / linkernsbypass | Pipetto-crypto | Native Adreno tooling used in CI |
| DXVK | [doitsujin/dxvk](https://github.com/doitsujin/dxvk) | D3D9/10/11 → Vulkan |
| VKD3D | Wine Project | D3D12 → Vulkan |
| D8VK | AlpyneDreams / community | D3D8 → Vulkan |
| CNC DDraw | [FunkyFr3sh/cnc-ddraw](https://github.com/FunkyFr3sh/cnc-ddraw) | DirectDraw wrapper |
| Ubuntu RootFs (Bionic) | Canonical | Container root filesystem base |
| Termux / Termux:X11 ideas | Termux community | Android terminal / X11 patterns |
| OpenXR SDK | Khronos | XR path (where enabled) |
| MidiSynth | Bundled under `app/libs/MidiSynth` | MIDI playback |

Additional shout-outs: **alexvorxx** (mods / tips), and everyone who filed issues, shared presets, or packaged `wcp` contents for Winlator users.

---

## This Console 0.1.1-beta layer

The **Console** layer in this tree — retro handheld / Game Boy–class library experience, System menu, container editor, gestures, and the **Hive Agent** performance AI — is additive UX on top of Steven’s Ludashi / Bionic base. It does **not** replace credit for the upstream authors above.

---

## License reminder

- App shell and much of the Java/Kotlin tree: **MIT** (see `LICENSE`) — originally Copyright BrunoSX, with additional copyright holders for forks and this beta.
- Native and third-party trees under `app/src/main/cpp/`, `audio_plugin/`, etc. may use **GPL**, **Apache-2.0**, **BSD**, Khronos, or other licenses — see `NOTICE` and each component’s own files.
- Wine, Mesa, Box64, FEX, DXVK, and related binaries/assets remain under their respective licenses.

If you redistribute this build, **keep BrunoSX, Pipetto-crypto, StevenMXZ, and the third-party notices intact**.
