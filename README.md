# MelkLight

A lightweight replacement for the "MagicLantern" app, built from reverse-engineering
`MagicLantern-V6.11.10.apk`. See [PROTOCOL.md](PROTOCOL.md) for the full reverse-engineered
BLE protocol. Source lives in [MelkLight/](MelkLight) (standard Gradle Android project).

Debug APK: `MelkLight-debug.apk` (5.8MB, vs. the original app's 34.9MB).

## Install

1. On your phone: Settings → allow installing unknown apps for whichever app you use to
   open the APK (Files app, etc).
2. Copy `MelkLight-debug.apk` to your phone and open it, or install over USB:
   ```bash
   adb install -r "MelkLight-debug.apk"
   ```
3. Open the app, grant Bluetooth permission, tap **Scan**, pick your light
   (advertises as `MELK-...`), and it's ready to use.
4. For the home-screen widget: long-press your home screen → Widgets → MelkLight.
5. For the Quick Settings power toggle: open the notification shade → edit tiles →
   drag in "Light power".

## What's covered

- Multiple saved lights: a chip row at the top switches which one is "active" (what
  the controls, widget, and tile act on). Tap **+ Add light** to scan and pair another;
  long-press a chip to remove it.
- Power, color via Hue + Saturation sliders (live preview, no raw RGB numbers),
  brightness, warm/cold white
- All 241 built-in effect modes and 28 scenes from the original app (exact names +
  command codes, pulled straight from its resources)
- Music/mic-reactive mode (on/off, sensitivity, EQ mode) - this is the light's own
  onboard mic, armed with a one-shot command; it then runs fully on its own with no
  further data from the phone (confirmed from the decompiled app - see PROTOCOL.md)
- **Phone audio reactive** (More tab): captures the phone's own microphone and drives
  the light's color/brightness from it in real time, independent of the onboard mic
  above. Same 8 named patterns as [LYGHT for PC](LyghtDesktop) (Energic/Rhythm/
  Spectrum/Rolling x2, our own software implementations - the device's internal EQ
  algorithms for those names aren't reachable from outside at all), a gain slider, and
  a live level meter. No system/app-audio option here (unlike the PC app's WASAPI
  loopback) - Android has no equivalent without a per-session MediaProjection consent
  dialog and a dedicated foreground-service type, which wasn't worth the complexity
  against this already covering the "the onboard mic isn't trustworthy" complaint.
- RGB pin remap (for miswired strips)
- Device clock sync + on/off schedule

## Known limitations (not yet verified against real hardware)

- I don't have the physical light to test against — **please test each section on
  your actual strip** and report anything that doesn't do what you expect.
- The app doesn't read live status back from the device (power/brightness shown in
  the UI/widget is just "last command sent", not a live readout) — the original app's
  decompiled code didn't show a general status-read path either, only a one-off timer read.
- `timingMode` (schedule enable byte) is sent as a guessed constant (`1`) — the
  decompiled code never exercises enough of that path to pin down its exact meaning.
- Symphony sync points and the RGBW/CCT channel-split command exist in the protocol
  layer but have no UI yet (niche, used by few device variants) — pulling them into
  the advanced screen is a quick follow-up if your device supports them.

## Rebuilding

```bash
cd MelkLight
./gradlew assembleDebug   # needs Android SDK + JDK 17/21 (JDK 26 fails to run this Gradle version)
```
