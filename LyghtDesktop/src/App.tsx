import { useEffect, useRef, useState } from "react";
import { listen } from "@tauri-apps/api/event";
import { isEnabled as autostartIsEnabled, enable as autostartEnable, disable as autostartDisable } from "@tauri-apps/plugin-autostart";
import "./App.css";
import * as api from "./api";
import type { ConnectionStatus, EffectCatalog, FoundDevice } from "./types";
import { hsvToRgb, rgbToHex, rgbToHsv, BASIC_SWATCHES } from "./colorUtils";
import { EffectName } from "./EffectName";
import { GroupIcon } from "./GroupIcon";
import { Toast } from "./Toast";
import { AudioMeter } from "./AudioMeter";

const WEEKDAY_LABELS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

export default function App() {
  const [devices, setDevices] = useState<FoundDevice[]>([]);
  const [activeAddress, setActiveAddress] = useState<string | null>(null);
  const [activeName, setActiveName] = useState<string | null>(null);
  const [status, setStatus] = useState<ConnectionStatus>("disconnected");
  const [scanning, setScanning] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const toastTimer = useRef<number | null>(null);

  function showError(message: string) {
    setToast(message);
    if (toastTimer.current) window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setToast(null), 4000);
  }
  function guard<T>(p: Promise<T>) {
    return p.catch((e) => {
      showError(typeof e === "string" ? e : "Something went wrong talking to the light.");
      throw e;
    });
  }

  const [powerOn, setPowerOnState] = useState(true);
  const [inWhiteMode, setInWhiteMode] = useState(false);
  const [hue, setHue] = useState(30);
  const [saturation, setSaturation] = useState(1);
  const [brightnessVal, setBrightnessVal] = useState(100);
  const [syncWindows, setSyncWindows] = useState(false);
  const [autostart, setAutostart] = useState(false);
  const lastAccentRef = useRef<string | null>(null);

  const hueRef = useRef<HTMLInputElement>(null);
  const satRef = useRef<HTMLInputElement>(null);
  const brightnessRef = useRef<HTMLInputElement>(null);
  const warmRef = useRef<HTMLInputElement>(null);
  const coldRef = useRef<HTMLInputElement>(null);
  const speedRef = useRef<HTMLInputElement>(null);
  const micSensRef = useRef<HTMLInputElement>(null);

  const [tab, setTab] = useState<"effects" | "more">("effects");
  const [catalog, setCatalog] = useState<EffectCatalog | null>(null);
  const [groupIndex, setGroupIndex] = useState(0);
  const [selectedEffect, setSelectedEffect] = useState<{ group: number; index: number } | null>(null);

  const [micEqLabels, setMicEqLabels] = useState<string[]>([]);
  const [micEnabled, setMicEnabled] = useState(false);
  const [micEqIndex, setMicEqIndex] = useState(0);
  const [pinIndex, setPinIndex] = useState(0);
  const [weekdays, setWeekdays] = useState<boolean[]>(new Array(7).fill(false));
  const [scheduleHour, setScheduleHour] = useState(12);
  const [scheduleMinute, setScheduleMinute] = useState(0);

  const [audioDevices, setAudioDevices] = useState<string[]>([]);
  const [audioDevice, setAudioDevice] = useState<string>("");
  const [listening, setListening] = useState(false);
  const [audioLevel, setAudioLevel] = useState(0);
  const [audioReactive, setAudioReactive] = useState(false);
  const [audioPattern, setAudioPattern] = useState(0);
  const [audioGain, setAudioGain] = useState(3);

  useEffect(() => {
    api.getEffectCatalog().then(setCatalog).catch(console.error);
    api.getMicEqLabels().then(setMicEqLabels).catch(console.error);
    api.listAudioDevices().then(setAudioDevices).catch(console.error);
    autostartIsEnabled().then(setAutostart).catch(() => {});

    api.getLastDevice().then((last) => {
      if (last) {
        const [address, name] = last;
        setActiveAddress(address);
        setActiveName(name);
      }
    });

    const poll = async () => {
      const connected = await api.isConnected().catch(() => false);
      setStatus((prev) => {
        if (connected) return "connected";
        return prev === "connecting" ? prev : "disconnected";
      });
    };
    poll();
    const id = setInterval(poll, 3000);

    const unlisten = listen<number>("audio-level", (e) => setAudioLevel(e.payload));
    return () => {
      clearInterval(id);
      unlisten.then((f) => f());
    };
  }, []);

  useEffect(() => {
    if (!syncWindows) return;
    const poll = async () => {
      const rgb = await api.getWindowsAccentColor().catch(() => null);
      if (!rgb) return;
      const key = rgb.join(",");
      if (key === lastAccentRef.current) return;
      lastAccentRef.current = key;
      const [h, s] = rgbToHsv(rgb[0], rgb[1], rgb[2]);
      setHue(h);
      setSaturation(s);
      await guard(api.color(rgb[0], rgb[1], rgb[2])).catch(() => {});
    };
    poll();
    const id = setInterval(poll, 2000);
    return () => clearInterval(id);
  }, [syncWindows]);

  const rgb = hsvToRgb(hue, saturation, 1);
  const previewColor = rgbToHex(...rgb);

  async function handleScan() {
    setScanning(true);
    try {
      const found = await guard(api.scanDevices());
      setDevices(found);
      if (found.length === 0) showError("No MELK- lights found. Make sure it's powered and Bluetooth is on.");
    } catch {
      /* already toasted */
    } finally {
      setScanning(false);
    }
  }

  async function handleConnect(device: FoundDevice) {
    setActiveAddress(device.address);
    setActiveName(device.name);
    setStatus("connecting");
    try {
      await guard(api.connectDevice(device.address, device.name));
      setStatus("connected");
    } catch {
      setStatus("disconnected");
    }
  }

  function togglePower() {
    const next = !powerOn;
    setPowerOnState(next);
    guard(api.setPower(next)).catch(() => {});
  }

  function setMode(white: boolean) {
    setInWhiteMode(white);
    const level = brightnessRef.current ? Number(brightnessRef.current.value) : brightnessVal;
    guard(api.brightness(level, white ? 3 : 1)).catch(() => {});
  }

  function sendColorNow() {
    const h = hueRef.current ? Number(hueRef.current.value) : hue;
    const s = satRef.current ? Number(satRef.current.value) / 100 : saturation;
    const [r, g, b] = hsvToRgb(h, s, 1);
    guard(api.color(r, g, b)).catch(() => {});
  }

  function sendBrightnessNow() {
    const level = brightnessRef.current ? Number(brightnessRef.current.value) : brightnessVal;
    guard(api.brightness(level, inWhiteMode ? 3 : 1)).catch(() => {});
  }

  function sendWhiteNow() {
    const warm = warmRef.current ? Number(warmRef.current.value) : 0;
    const cold = coldRef.current ? Number(coldRef.current.value) : 0;
    guard(api.whiteTemperature(warm, cold)).catch(() => {});
  }

  function applySwatch(r: number, g: number, b: number) {
    const [h, s] = rgbToHsv(r, g, b);
    setHue(h);
    setSaturation(s);
    guard(api.color(r, g, b)).catch(() => {});
  }

  function selectGroup(index: number) {
    setGroupIndex(index);
    setSelectedEffect(null);
  }

  function pickEffect(index: number) {
    if (!catalog) return;
    const group = catalog.groups[groupIndex];
    const effect = group.items[index];
    setSelectedEffect({ group: groupIndex, index });
    const send = group.label === "Scenes" ? api.scene(effect.code) : api.effectMode(effect.code);
    guard(send).catch(() => {});
  }

  function toggleWeekday(i: number) {
    setWeekdays((w) => w.map((v, idx) => (idx === i ? !v : v)));
  }

  async function toggleAutostart(checked: boolean) {
    setAutostart(checked);
    try {
      if (checked) await autostartEnable();
      else await autostartDisable();
    } catch (e) {
      showError("Couldn't change the Windows startup setting.");
      setAutostart(!checked);
    }
  }

  async function toggleListening(checked: boolean) {
    setListening(checked);
    if (checked) {
      try {
        await guard(api.startAudioCapture(audioDevice || null));
      } catch {
        setListening(false);
      }
    } else {
      await api.stopAudioCapture().catch(() => {});
      setAudioReactive(false);
      await api.setAudioReactive(false, 0, 0, 0).catch(() => {});
    }
  }

  async function toggleAudioReactive(checked: boolean) {
    setAudioReactive(checked);
    const [r, g, b] = hsvToRgb(hue, saturation, 1);
    await guard(api.setAudioReactive(checked, r, g, b)).catch(() => {});
  }

  // keep the audio-reactive base color following the color picker instead of
  // freezing at whatever it was when "Drive the light" was first switched on
  useEffect(() => {
    if (!audioReactive) return;
    const [r, g, b] = hsvToRgb(hue, saturation, 1);
    api.setAudioReactive(true, r, g, b).catch(() => {});
  }, [hue, saturation, audioReactive]);

  function changePattern(index: number) {
    setAudioPattern(index);
    api.setAudioPattern(index).catch(() => {});
  }

  function changeGain(value: number) {
    setAudioGain(value);
    api.setAudioGain(value).catch(() => {});
  }

  function statusText() {
    if (!activeName) return "No device paired";
    if (status === "connected") return `Connected: ${activeName}`;
    if (status === "connecting") return `Connecting to ${activeName}...`;
    return `${activeName} · idle`;
  }

  return (
    <div className="app">
      <div className="left-pane">
        <div className="device-row">
          {devices.map((d) => (
            <button
              key={d.address}
              className={"chip" + (d.address === activeAddress ? " selected" : "")}
              onClick={() => handleConnect(d)}
            >
              {d.name}
            </button>
          ))}
          <button className="chip ghost" onClick={handleScan} disabled={scanning}>
            {scanning ? "Scanning..." : "+ Scan"}
          </button>
        </div>
        <div className="status-text">{statusText()}</div>

        <div className="hero">
          <div className="color-preview" style={{ background: previewColor }}>
            <button className={"power-button" + (powerOn ? "" : " off")} onClick={togglePower} title="Power">
              <svg width="22" height="22" viewBox="0 0 24 24">
                <path
                  fill="currentColor"
                  d="M13,3h-2v10h2V3zM17.83,5.17l-1.42,1.42C17.99,7.86 19,9.81 19,12c0,3.87 -3.13,7 -7,7s-7,-3.13 -7,-7c0,-2.19 1.01,-4.14 2.58,-5.42L6.17,5.17C4.23,6.82 3,9.26 3,12c0,4.97 4.03,9 9,9s9,-4.03 9,-9c0,-2.74 -1.23,-5.18 -3.17,-6.83z"
                />
              </svg>
            </button>
          </div>
          <div className="hero-body">
            <div className="mode-toggle">
              <button className={inWhiteMode ? "" : "active"} onClick={() => setMode(false)}>
                Color
              </button>
              <button className={inWhiteMode ? "active" : ""} onClick={() => setMode(true)}>
                White
              </button>
            </div>

            {!inWhiteMode && (
              <>
                <span className="slider-label">Hue</span>
                <input
                  ref={hueRef}
                  type="range"
                  className="hue"
                  min={0}
                  max={360}
                  value={hue}
                  disabled={syncWindows}
                  onChange={(e) => setHue(Number(e.target.value))}
                  onMouseUp={sendColorNow}
                  onTouchEnd={sendColorNow}
                />
                <span className="slider-label">Saturation</span>
                <input
                  ref={satRef}
                  type="range"
                  className="saturation"
                  style={{ ["--hue-color" as any]: rgbToHex(...hsvToRgb(hue, 1, 1)) }}
                  min={0}
                  max={100}
                  value={Math.round(saturation * 100)}
                  disabled={syncWindows}
                  onChange={(e) => setSaturation(Number(e.target.value) / 100)}
                  onMouseUp={sendColorNow}
                  onTouchEnd={sendColorNow}
                />
              </>
            )}

            {inWhiteMode && (
              <>
                <span className="slider-label">Warm white</span>
                <input ref={warmRef} type="range" className="plain" min={0} max={100} defaultValue={0}
                       onMouseUp={sendWhiteNow} onTouchEnd={sendWhiteNow} />
                <span className="slider-label">Cold white</span>
                <input ref={coldRef} type="range" className="plain" min={0} max={100} defaultValue={0}
                       onMouseUp={sendWhiteNow} onTouchEnd={sendWhiteNow} />
              </>
            )}

            <div className="slider-row">
              <span className="slider-label">Brightness</span>
              <span className="slider-value">{brightnessVal}%</span>
            </div>
            <input
              ref={brightnessRef}
              type="range"
              className="plain"
              min={0}
              max={100}
              value={brightnessVal}
              onChange={(e) => setBrightnessVal(Number(e.target.value))}
              onMouseUp={sendBrightnessNow}
              onTouchEnd={sendBrightnessNow}
            />

            <div className="swatch-row">
              {BASIC_SWATCHES.map(([r, g, b], i) => (
                <button
                  key={i}
                  className="swatch"
                  style={{ background: rgbToHex(r, g, b) }}
                  onClick={() => applySwatch(r, g, b)}
                />
              ))}
            </div>
          </div>
        </div>

        <div className="settings-list">
          <label className="sync-row">
            <input type="checkbox" checked={syncWindows} onChange={(e) => setSyncWindows(e.target.checked)} />
            Sync with Windows accent color
          </label>
          <label className="sync-row">
            <input type="checkbox" checked={autostart} onChange={(e) => toggleAutostart(e.target.checked)} />
            Start with Windows (stays in the tray)
          </label>
        </div>
      </div>

      <div className="right-pane">
        <div className="tabs">
          <button className={"tab" + (tab === "effects" ? " active" : "")} onClick={() => setTab("effects")}>
            Effects
          </button>
          <button className={"tab" + (tab === "more" ? " active" : "")} onClick={() => setTab("more")}>
            More
          </button>
        </div>

        {tab === "effects" && catalog && (
          <>
            <div className="group-row">
              {catalog.groups.map((g, i) => (
                <button
                  key={g.label}
                  className={"chip" + (i === groupIndex ? " selected" : "")}
                  onClick={() => selectGroup(i)}
                >
                  <GroupIcon label={g.label} />
                  {g.label}
                </button>
              ))}
            </div>
            <div className="tab-content">
              <div className="effect-list">
                {catalog.groups[groupIndex].items.map((effect, i) => (
                  <div className="effect-item" key={effect.name + i} onClick={() => pickEffect(i)}>
                    {selectedEffect?.group === groupIndex && selectedEffect.index === i ? (
                      <svg className="effect-check" viewBox="0 0 24 24">
                        <path fill="currentColor" d="M9,16.2 L4.8,12 L3.4,13.4 L9,19 L21,7 L19.6,5.6 Z" />
                      </svg>
                    ) : (
                      <span className="effect-check" />
                    )}
                    <span>
                      <EffectName name={effect.name} />
                    </span>
                  </div>
                ))}
              </div>
            </div>
            <div className="speed-box">
              <span className="slider-label">Speed</span>
              <input
                ref={speedRef}
                type="range"
                className="plain"
                min={0}
                max={100}
                defaultValue={100}
                onMouseUp={() => guard(api.effectSpeed(Number(speedRef.current!.value))).catch(() => {})}
                onTouchEnd={() => guard(api.effectSpeed(Number(speedRef.current!.value))).catch(() => {})}
              />
            </div>
          </>
        )}

        {tab === "more" && (
          <div className="tab-content">
            <div className="more-tab">
              <div className="section-title">On-device mic reactive</div>
              <p className="hint-text">
                The light's own built-in mic. We can't read back what it hears - there's no protocol command for
                that - we can only set these.
              </p>
              <label className="switch-row">
                <input
                  type="checkbox"
                  checked={micEnabled}
                  onChange={(e) => {
                    setMicEnabled(e.target.checked);
                    guard(api.micOnOff(e.target.checked)).catch(() => {});
                  }}
                />
                Enabled
              </label>
              <span className="slider-label" style={{ marginTop: 10, display: "block" }}>
                Sensitivity
              </span>
              <input
                ref={micSensRef}
                type="range"
                className="plain"
                min={0}
                max={100}
                defaultValue={50}
                onMouseUp={() => guard(api.micSensitivity(Number(micSensRef.current!.value))).catch(() => {})}
                onTouchEnd={() => guard(api.micSensitivity(Number(micSensRef.current!.value))).catch(() => {})}
              />
              <div className="chip-group">
                {micEqLabels.map((label, i) => (
                  <button
                    key={label}
                    className={"chip" + (i === micEqIndex ? " selected" : "")}
                    onClick={() => {
                      setMicEqIndex(i);
                      guard(api.micEqMode(i)).catch(() => {});
                    }}
                  >
                    {label}
                  </button>
                ))}
              </div>

              <div className="divider" />

              <div className="section-title">PC audio reactive</div>
              <p className="hint-text">
                Reacts to your PC's own audio - your microphone, or system audio (whatever's playing: music, a
                video, a game). A genuine software-driven reactive mode, separate from the light's onboard one above.
              </p>
              <div className="audio-row">
                <select
                  className="select"
                  value={audioDevice}
                  onChange={(e) => setAudioDevice(e.target.value)}
                  disabled={listening}
                >
                  <option value="">Default microphone</option>
                  {audioDevices.map((d) => (
                    <option key={d} value={d}>
                      {d}
                    </option>
                  ))}
                  <option value={api.SYSTEM_AUDIO_SOURCE}>System audio (what's playing)</option>
                </select>
                <label className="switch-row">
                  <input type="checkbox" checked={listening} onChange={(e) => toggleListening(e.target.checked)} />
                  Listen
                </label>
              </div>

              <div className="slider-row">
                <span className="slider-label">Meter gain (turn up if the meter barely moves)</span>
                <span className="slider-value">{audioGain.toFixed(1)}x</span>
              </div>
              <input
                type="range"
                className="plain"
                min={0.5}
                max={10}
                step={0.1}
                value={audioGain}
                onChange={(e) => changeGain(Number(e.target.value))}
              />

              <AudioMeter level={listening ? audioLevel : 0} />

              <label className="switch-row" style={{ marginTop: 10 }}>
                <input
                  type="checkbox"
                  checked={audioReactive}
                  disabled={!listening}
                  onChange={(e) => toggleAudioReactive(e.target.checked)}
                />
                Drive the light with this
              </label>

              {audioReactive && (
                <div className="chip-group">
                  {micEqLabels.map((label, i) => (
                    <button
                      key={label}
                      className={"chip" + (i === audioPattern ? " selected" : "")}
                      onClick={() => changePattern(i)}
                    >
                      {label}
                    </button>
                  ))}
                </div>
              )}

              <div className="divider" />

              <div className="section-title">RGB pin remap</div>
              <div className="chip-group">
                {catalog?.pin_sequences.map((opt, i) => (
                  <button
                    key={opt.label}
                    className={"chip" + (i === pinIndex ? " selected" : "")}
                    onClick={() => {
                      setPinIndex(i);
                      guard(api.pinSequence(opt.pins[0], opt.pins[1], opt.pins[2])).catch(() => {});
                    }}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>

              <div className="divider" />

              <div className="section-title">Timer</div>
              <div className="timer-controls">
                <button
                  className="btn"
                  onClick={() => {
                    const now = new Date();
                    const weekday = now.getDay();
                    guard(api.systemTime(now.getHours(), now.getMinutes(), now.getSeconds(), weekday)).catch(
                      () => {}
                    );
                  }}
                >
                  Sync device clock
                </button>
                <div className="time-inputs">
                  <input
                    type="number"
                    min={0}
                    max={23}
                    value={scheduleHour}
                    onChange={(e) => setScheduleHour(Number(e.target.value))}
                  />
                  <span>:</span>
                  <input
                    type="number"
                    min={0}
                    max={59}
                    value={scheduleMinute}
                    onChange={(e) => setScheduleMinute(Number(e.target.value))}
                  />
                </div>
                <div className="chip-group" style={{ justifyContent: "center" }}>
                  {WEEKDAY_LABELS.map((label, i) => (
                    <button
                      key={label}
                      className={"chip" + (weekdays[i] ? " selected" : "")}
                      onClick={() => toggleWeekday(i)}
                    >
                      {label}
                    </button>
                  ))}
                </div>
                <button
                  className="btn"
                  onClick={() => {
                    let bitmask = 0;
                    weekdays.forEach((on, i) => {
                      if (on) bitmask |= 1 << i;
                    });
                    guard(api.timingSchedule(scheduleHour, scheduleMinute, 0, 1, bitmask)).catch(() => {});
                  }}
                >
                  Set schedule
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      <Toast message={toast} />
    </div>
  );
}
