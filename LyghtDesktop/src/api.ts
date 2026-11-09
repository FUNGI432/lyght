import { invoke } from "@tauri-apps/api/core";
import type { EffectCatalog, FoundDevice } from "./types";

export const scanDevices = () => invoke<FoundDevice[]>("scan_devices");
export const connectDevice = (address: string, name: string) =>
  invoke<void>("connect_device", { address, name });
export const disconnectDevice = () => invoke<void>("disconnect_device");
export const isConnected = () => invoke<boolean>("is_connected");
export const getLastDevice = () => invoke<[string, string] | null>("get_last_device");
export const getEffectCatalog = () => invoke<EffectCatalog>("get_effect_catalog");
export const getMicEqLabels = () => invoke<string[]>("get_mic_eq_labels");
export const getWindowsAccentColor = () => invoke<[number, number, number] | null>("get_windows_accent_color");

export const setPower = (on: boolean) => invoke<void>("set_power", { on });
export const color = (r: number, g: number, b: number) => invoke<void>("color", { r, g, b });
export const whiteTemperature = (warm: number, cold: number) => invoke<void>("white_temperature", { warm, cold });
export const brightness = (level: number, lightMode: number) => invoke<void>("brightness", { level, lightMode });
export const effectMode = (code: number) => invoke<void>("effect_mode", { code });
export const effectSpeed = (speed: number) => invoke<void>("effect_speed", { speed });
export const scene = (index: number) => invoke<void>("scene", { index });
export const micOnOff = (on: boolean) => invoke<void>("mic_on_off", { on });
export const micSensitivity = (level: number) => invoke<void>("mic_sensitivity", { level });
export const micEqMode = (mode: number) => invoke<void>("mic_eq_mode", { mode });
export const pinSequence = (r: number, g: number, b: number) => invoke<void>("pin_sequence", { r, g, b });
export const systemTime = (hour: number, minute: number, second: number, weekday: number) =>
  invoke<void>("system_time", { hour, minute, second, weekday });
export const timingSchedule = (
  hour: number,
  minute: number,
  second: number,
  timingMode: number,
  weekdayBitmask: number
) => invoke<void>("timing_schedule", { hour, minute, second, timingMode, weekdayBitmask });

export const SYSTEM_AUDIO_SOURCE = "__system_audio__";

export const listAudioDevices = () => invoke<string[]>("list_audio_devices");
export const startAudioCapture = (device: string | null) => invoke<void>("start_audio_capture", { device });
export const stopAudioCapture = () => invoke<void>("stop_audio_capture");
export const setAudioReactive = (enabled: boolean, r: number, g: number, b: number) =>
  invoke<void>("set_audio_reactive", { enabled, r, g, b });
export const setAudioPattern = (pattern: number) => invoke<void>("set_audio_pattern", { pattern });
export const setAudioGain = (gain: number) => invoke<void>("set_audio_gain", { gain });
