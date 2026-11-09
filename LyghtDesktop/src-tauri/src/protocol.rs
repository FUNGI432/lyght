//! MELK / ELK-BLEDOM BLE protocol - ported from the Android app's Protocol.kt.
//! Plain 9-byte packets, no encryption. See ../../PROTOCOL.md for the full writeup.

use serde::Serialize;
use uuid::{uuid, Uuid};

pub const SERVICE_UUID: Uuid = uuid!("0000fff0-0000-1000-8000-00805f9b34fb");
pub const CHARACTERISTIC_UUID: Uuid = uuid!("0000fff3-0000-1000-8000-00805f9b34fb");
pub const DEVICE_NAME_PREFIX: &str = "MELK-";

const HEAD: u8 = 0x7E;
const TAIL: u8 = 0xEF;

pub fn power(on: bool) -> Vec<u8> {
    let v = if on { 1 } else { 0 };
    vec![HEAD, 0x04, 0x04, v, 0x00, v, 0xFF, 0x00, TAIL]
}

pub fn color(r: u8, g: u8, b: u8) -> Vec<u8> {
    vec![HEAD, 0x07, 0x05, 0x03, r, g, b, 0x10, TAIL]
}

/// Same framing as `color` but with the device's "music/amplitude" marker
/// (0x20 instead of 0x10) - used by our PC audio-reactive mode.
pub fn music_reactive_color(r: u8, g: u8, b: u8) -> Vec<u8> {
    vec![HEAD, 0x07, 0x05, 0x03, r, g, b, 0x20, TAIL]
}

pub fn white_temperature(warm: u8, cold: u8) -> Vec<u8> {
    vec![HEAD, 0x06, 0x05, 0x02, warm, cold, 0xFF, 0x08, TAIL]
}

pub fn brightness(level: u8, light_mode: u8) -> Vec<u8> {
    vec![HEAD, 0x04, 0x01, level, light_mode, 0xFF, 0xFF, 0x00, TAIL]
}

pub fn effect_mode(code: u8) -> Vec<u8> {
    vec![HEAD, 0x05, 0x03, code, 0x06, 0xFF, 0xFF, 0x00, TAIL]
}

pub fn effect_speed(speed: u8) -> Vec<u8> {
    vec![HEAD, 0x04, 0x02, speed, 0xFF, 0xFF, 0xFF, 0x00, TAIL]
}

pub fn scene(index: u8) -> Vec<u8> {
    vec![HEAD, 0x05, 0x31, index, 0x07, 0xFF, 0xFF, 0x01, TAIL]
}

pub fn mic_on_off(on: bool) -> Vec<u8> {
    vec![HEAD, 0x04, 0x07, if on { 1 } else { 0 }, 0xFF, 0xFF, 0xFF, 0x00, TAIL]
}

pub fn mic_sensitivity(level: u8) -> Vec<u8> {
    vec![HEAD, 0x04, 0x06, level, 0xFF, 0xFF, 0xFF, 0x00, TAIL]
}

pub fn mic_eq_mode(mode: u8) -> Vec<u8> {
    vec![HEAD, 0x07, 0x03, mode.wrapping_add(128), 0x04, 0xFF, 0xFF, 0x00, TAIL]
}

pub fn pin_sequence(r: u8, g: u8, b: u8) -> Vec<u8> {
    vec![HEAD, 0x06, 0x81, r, g, b, 0xFF, 0x00, TAIL]
}

pub fn system_time(hour: u8, minute: u8, second: u8, weekday: u8) -> Vec<u8> {
    vec![HEAD, 0x07, 0x83, hour, minute, second, weekday, 0xFF, TAIL]
}

pub fn timing_schedule(hour: u8, minute: u8, second: u8, timing_mode: u8, weekday_bitmask: u8) -> Vec<u8> {
    vec![HEAD, 0x08, 0x82, hour, minute, second, timing_mode, weekday_bitmask, TAIL]
}

pub const MIC_EQ_LABELS: [&str; 8] = [
    "Energic", "Rhythm", "Spectrum", "Rolling", "Energic 2", "Rhythm 2", "Spectrum 2", "Rolling 2",
];

#[derive(Serialize, Clone)]
pub struct Effect {
    pub name: String,
    pub code: u32,
}

#[derive(Serialize, Clone)]
pub struct EffectGroup {
    pub label: String,
    pub items: Vec<Effect>,
}

#[derive(Serialize, Clone)]
pub struct PinOption {
    pub label: String,
    pub pins: (u8, u8, u8),
}

#[derive(Serialize, Clone)]
pub struct EffectCatalog {
    pub groups: Vec<EffectGroup>,
    pub pin_sequences: Vec<PinOption>,
}

const EFFECTS_JSON: &str = include_str!("../../../effects.json");

pub fn load_effect_catalog() -> EffectCatalog {
    let raw: serde_json::Value = serde_json::from_str(EFFECTS_JSON).expect("bundled effects.json is valid");

    let groups = raw["groups"]
        .as_array()
        .unwrap()
        .iter()
        .map(|g| EffectGroup {
            label: g["label"].as_str().unwrap().to_string(),
            items: g["items"]
                .as_array()
                .unwrap()
                .iter()
                .map(|pair| Effect {
                    name: pair[0].as_str().unwrap().to_string(),
                    code: pair[1].as_u64().unwrap() as u32,
                })
                .collect(),
        })
        .collect();

    let pin_sequences = raw["pin_sequence"]
        .as_array()
        .unwrap()
        .iter()
        .map(|pair| {
            let label = pair[0].as_str().unwrap().to_string();
            let code = pair[1].as_str().unwrap().as_bytes();
            let digit = |c: u8| c - b'0';
            PinOption {
                label,
                pins: (digit(code[0]), digit(code[1]), digit(code[2])),
            }
        })
        .collect();

    EffectCatalog { groups, pin_sequences }
}
