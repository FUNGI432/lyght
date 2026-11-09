# LYGHT for PC

A Windows desktop controller for the MELK/ELK-BLEDOM BLE LED strip, built as a
**Tauri** app: a Rust backend (BLE via `btleplug`) with a React/TypeScript
frontend rendered in Windows' built-in WebView2 — no bundled Chromium, so the
app stays small (~5MB exe). UI is deliberately styled to match the
[Android app](../MelkLight) (same dark theme, same gradient hue/saturation
sliders, same effect/scene catalog, same group icons) rather than looking like
a generic desktop tool.

This replaces the earlier Python/Tkinter version, which couldn't do styled
gradient sliders — Tkinter's `ttk.Scale` is a plain OS widget, not something
you can skin like CSS.

## Run it

`src-tauri/target/release/lyghtdesktop.exe` is the standalone exe (needs
Windows' WebView2 runtime, which ships with Windows 10/11 by default - nothing
else to install). `src-tauri/target/release/bundle/` also has an MSI and an
NSIS installer if you'd rather install it properly (Start Menu entry, uninstaller).

1. Open the app, click **+ Scan** (~5 seconds).
2. Click your light's chip to connect.
3. Same controls as mobile: power, color (hue/saturation), brightness, white
   balance, all 241 effects + 28 scenes (color-coded names, grouped with
   icons), mic-reactive mode, pin remap, clock/schedule — plus **Sync with
   Windows accent color**, which follows your current Windows accent color
   (Settings > Personalization > Colors) every 2 seconds.

## Rebuilding from source

```bash
npm install
npm run tauri dev      # run with hot reload
npm run tauri build    # produces the exe + MSI + NSIS installer
```

Needs the Rust toolchain (MSVC target) and Node installed. See
[tauri.app/start/prerequisites](https://tauri.app/start/prerequisites/).

## Architecture

- `src-tauri/src/protocol.rs` — the BLE command byte builders, ported 1:1 from
  the Android app's `Protocol.kt`. Also embeds `../../effects.json` (the
  effect/scene catalog) at compile time via `include_str!`.
- `src-tauri/src/ble.rs` — scan/connect/send via `btleplug`, holds the single
  active connection in shared state.
- `src-tauri/src/lib.rs` — Tauri commands exposed to the frontend (one per BLE
  command, plus scan/connect/accent-color).
- `src/App.tsx` + `src/App.css` — the UI; `src/EffectName.tsx` colorizes
  R/G/B/Y/C/P letters in effect names, `src/GroupIcon.tsx` has the same icon
  glyphs as the Android app's group chips.

## What's new since the first pass

- **System tray**: left-click shows the window; right-click gives Open /
  Turn on-off / Connect-Disconnect (to whichever device you last connected,
  saved in `%APPDATA%/com.aman.lyghtdesktop/config.json`) / Quit. Closing the
  window just hides it to the tray - only "Quit" ends the process. On launch
  the app tries to reconnect to that last device automatically.
- **Start with Windows**: checkbox in the left pane (`tauri-plugin-autostart`).
- **Wide two-column layout**: left pane is the device/hero controls, right
  pane is Effects/More - no longer a narrow phone-shaped window.
- **PC audio reactive** (More tab): captures your PC's own microphone (not
  the light's onboard one - there's no protocol command to read that back,
  see below), shows a live VU-style meter so you can actually see what level
  your input signal is sitting at, and can optionally pulse the light's
  brightness from it in real time (`music_reactive_color`, 0x20 marker) - a
  genuine "music reactive" mode distinct from the device's own onboard-mic
  toggle. True system-audio loopback (reacting to what's *playing*, not your
  mic) wasn't built - this uses whatever input device you pick.
- Every light-control call now surfaces failures as an on-screen toast
  instead of silently failing, and BLE writes are spaced ~25ms apart (the
  original app throttles writes the same way; back-to-back writes with zero
  gap can get dropped by this hardware).

## Where the mic-reactive control actually lives (checked the decompiled app)

Re-read `StreamingFragment.java` properly this time. The original app has two
completely separate audio paths, and they explain why the first pass behaved
oddly:

- **"External mic" (the light's own onboard mic)**: a one-shot "arm it" command
  (`mic_on_off(true)` + `mic_eq_mode(pattern)`), then the device runs entirely
  on its own - no further data from the app. This is the on-device toggle in
  this app, and it's genuinely unreachable from outside: there's no
  read-back path, full stop. The 8 named patterns (Energic/Rhythm/Spectrum/
  Rolling x2) only exist *inside the device's firmware* for this path.
- **"Phone mic"**: the original app captures its *own* phone's mic and
  continuously streams color+brightness frames (`music_reactive_color`,
  the same command this app already had) - this is the "PC audio reactive"
  feature, and the first pass only replicated half of it (continuous
  brightness scaling of a frozen color, no patterns, mic-only).

So: the 8 patterns can't be handed off to the device for PC/system audio -
only the on-device path has them, and it can't take external input. What we
*can* do, and did: implement our own 8 same-named patterns entirely in this
app's software, driven by real audio we captured ourselves and driving the
light directly - full customization, at the cost of them being our own
algorithms rather than the device's internal ones.

## What's new in this pass

- **System audio (loopback)**: a new source option alongside your microphones
  - reacts to whatever's actually playing (media, a game, a call), not just
  what your mic picks up. Uses WASAPI loopback capture (`wasapi` crate) on the
  default output device.
- **8 named patterns** (same names as the on-device EQ modes, own
  implementation): Energic (direct brightness pulse), Rhythm (hard on/off
  strobe on loud transients), Spectrum (hue sweeps faster as it gets louder),
  Rolling (hue auto-rotates at a steady rate, brightness pulses with volume),
  and "2" variants of each that react to a smoothed/slower signal instead of
  the raw one.
- **Meter gain slider**: the old fixed ×4 scaling made the meter pin to full
  for almost any input. Now adjustable live so quiet mics or loud rooms both
  get a usable range.
- **Fixed**: the audio-reactive base color used to freeze at whatever was set
  the moment you turned "Drive the light" on; it now follows the color picker
  live while reactive mode is running.

## Known limitations

Same protocol caveats as the Android app (`../README.md`): no live status
read-back from the device, and the schedule's `timingMode` byte is a
best-effort guess. The tray's power/connect state isn't pushed back into the
open window live (e.g. toggling power from the tray won't flip the window's
power button) - only verified one direction so far. Not tested against real
hardware by me directly (no way to click the native window from here) -
please put it through its paces.
