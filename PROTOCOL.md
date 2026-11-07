# MagicLantern / MELK BLE LED Protocol

Reverse-engineered from `MagicLantern-V6.11.10.apk` (package `com.easylink.colorful`,
decompiled with jadx). Source: `BluetoothLEService.java`, `BluetoothUtil.java`.

Device line: "MELK" (ELK-BLEDOM family). Vendor domains: elkble.com / elkled.com.

## Transport

- **Bluetooth LE only** — no WiFi/cloud control. Phone must be in BLE range.
- Scan filter: device advertised name starts with `MELK-` (constant `Global.NAME_FILTER`).
- Service UUID: `0000fff0-0000-1000-8000-00805f9b34fb`
- Write characteristic UUID: `0000fff3-0000-1000-8000-00805f9b34fb` (write, no response needed observed)
- Commands are **plain 9-byte packets**, NOT encrypted. (The app ships an
  `EncryptionDecryptionKt` AES/XOR-style cipher class but it is dead code —
  nothing in the app calls it. Ignore it.)
- Two outgoing buffers (`sendDatas1`, `sendDatas2`) are flushed to the characteristic
  by a background loop every 100ms whenever a "dirty" flag is set, so there's no need
  to replicate that — just write each 9-byte packet directly when you build it in order,
  with a few ms (~5ms) delay if sending back-to-back packets.

All commands are `byte[9]`, framed `0x7E ... 0xEF` (`~`...`ï`).

## Commands

| Action | Bytes `[0..8]` | Notes |
|---|---|---|
| Power on/off | `7E 04 04 {on:1/0} 00 {on:1/0} FF 00 EF` | |
| RGB color | `7E 07 05 03 {R} {G} {B} 10 EF` | |
| White / color temp | `7E 06 05 02 {warm 0-255} {cold 0-255} FF 08 EF` | |
| Brightness | `7E 04 01 {brightness} {lightMode|0xFF} FF FF 00 EF` | lightMode: 0=ALL,1=RGB,2=W,3=CT |
| Effect mode | `7E 05 03 {modeIndex} 06 FF FF 00 EF` | |
| Effect speed | `7E 04 02 {speed} FF FF FF 00 EF` | |
| Scene | `7E 05 31 {sceneIndex} 07 FF FF 01 EF` | |
| Single color preset | `7E 05 05 01 {colorIndex} FF FF 08 EF` | |
| RGBW channel on/off + mode | `7E 04 04 {flags} {mode} {extra} FF 00 EF` | flags: bit for RGB (0xE0) / W (0x10) combined; same opcode family as power |
| RGB pin sequence remap | `7E 06 81 {R_pin} {G_pin} {B_pin} FF 00 EF` | for miswired strips |
| External mic on/off | `7E 04 07 {on:1/0} FF FF FF 00 EF` | |
| External mic sensitivity | `7E 04 06 {sensitivity} FF FF FF 00 EF` | |
| External mic EQ mode | `7E 07 03 {eqMode+128} 04 FF FF 00 EF` | eqMode 0-7, see ExternalMicEqMode |
| Symphony sync point | `7E 07 21 {point low} {point high} 00 FF 00 EF` | |
| Set system time | `7E 07 83 {hour} {minute} {second} {weekday} FF EF` | |
| Set timer/schedule | `7E 08 82 {hour} {minute} {second} {timingMode} {weekdayBitmask} EF` | |
| Music-reactive color+brightness | `7E 07 05 03 {R} {G} {B} 20 EF` | same framing as RGB color but marker `0x20` instead of `0x10`; R/G/B pre-blended with brightness via HSV (hue/sat from color, value = brightness) |

Byte values above `0x7F` are Java signed bytes; e.g. `-1` = `0xFF`, `-17` = `0xEF`, `-127` = `0x81`, `-126` = `0x82`, `-125` = `0x83`.

## MVP priority (for the replacement app)

Core (covers ~everything a daily user touches): power, RGB color, brightness, white/color-temp, effect mode + speed.
Secondary: scenes, mic-reactive mode (on/off, sensitivity, EQ), timers.
Niche/skip unless asked: pin-sequence remap, symphony sync (only useful alongside their own music-sync feature, not reverse-engineerable further without the audio pipeline).
