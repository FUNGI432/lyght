//! PC-side audio capture: a live level for the "what is my input actually
//! hearing" visualization, and an optional mode that drives the light's
//! color/brightness from that level in real time. Two sources: a microphone
//! (via `cpal`) or system audio / "what's playing" (via WASAPI loopback,
//! `wasapi` crate) - genuinely different from the light's own onboard
//! "mic reactive" toggle, which this app has no way to read back from (no
//! protocol command for it - see PROTOCOL.md and the decompiled
//! StreamingFragment.java: the device's onboard mic is armed with a one-shot
//! "on + eq mode" command and then runs entirely on its own; there is no
//! continuous data path for us to tap into for that).

use std::sync::atomic::{AtomicBool, AtomicU32, AtomicU8, Ordering};
use std::sync::{mpsc, Arc, Mutex};
use std::thread;
use std::time::Duration;

use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};

pub const SOURCE_SYSTEM_AUDIO: &str = "__system_audio__";

/// Mirrors the light's own named EQ patterns (Energic/Rhythm/Spectrum/Rolling
/// x2) in name only - these are our own software implementations driven by
/// PC audio, not the device's on-board algorithms, which can't be reached
/// externally at all.
#[derive(Clone, Copy, PartialEq)]
enum Pattern {
    Energic,
    Rhythm,
    Spectrum,
    Rolling,
    Energic2,
    Rhythm2,
    Spectrum2,
    Rolling2,
}

impl Pattern {
    fn from_index(i: u8) -> Self {
        match i {
            0 => Pattern::Energic,
            1 => Pattern::Rhythm,
            2 => Pattern::Spectrum,
            3 => Pattern::Rolling,
            4 => Pattern::Energic2,
            5 => Pattern::Rhythm2,
            6 => Pattern::Spectrum2,
            _ => Pattern::Rolling2,
        }
    }

    /// the "2" variants react the same way but to a slower, averaged signal
    fn is_slow(self) -> bool {
        matches!(self, Pattern::Energic2 | Pattern::Rhythm2 | Pattern::Spectrum2 | Pattern::Rolling2)
    }
}

fn hsv_to_rgb(h: f32, s: f32, v: f32) -> (u8, u8, u8) {
    let h = h.rem_euclid(360.0);
    let c = v * s;
    let hh = h / 60.0;
    let x = c * (1.0 - (hh.rem_euclid(2.0) - 1.0).abs());
    let (r1, g1, b1) = if hh < 1.0 {
        (c, x, 0.0)
    } else if hh < 2.0 {
        (x, c, 0.0)
    } else if hh < 3.0 {
        (0.0, c, x)
    } else if hh < 4.0 {
        (0.0, x, c)
    } else if hh < 5.0 {
        (x, 0.0, c)
    } else {
        (c, 0.0, x)
    };
    let m = v - c;
    (
        ((r1 + m) * 255.0).round() as u8,
        ((g1 + m) * 255.0).round() as u8,
        ((b1 + m) * 255.0).round() as u8,
    )
}

fn rms_f32(data: &[f32]) -> f32 {
    if data.is_empty() {
        return 0.0;
    }
    let sum_sq: f32 = data.iter().map(|s| s * s).sum();
    (sum_sq / data.len() as f32).sqrt()
}

fn rms_i16(data: &[i16]) -> f32 {
    if data.is_empty() {
        return 0.0;
    }
    let sum_sq: f64 = data.iter().map(|&s| { let v = s as f64 / i16::MAX as f64; v * v }).sum();
    (sum_sq / data.len() as f64).sqrt() as f32
}

fn rms_u16(data: &[u16]) -> f32 {
    if data.is_empty() {
        return 0.0;
    }
    let sum_sq: f64 = data.iter().map(|&s| { let v = (s as f64 - 32768.0) / 32768.0; v * v }).sum();
    (sum_sq / data.len() as f64).sqrt() as f32
}

pub struct AudioState {
    level_bits: Arc<AtomicU32>,
    capturing: Arc<AtomicBool>,
    should_stop: Arc<AtomicBool>,
    pub reactive_enabled: Arc<AtomicBool>,
    base_color: Mutex<(u8, u8, u8)>,
    gain: Mutex<f32>,
    pattern: AtomicU8,
    hue_phase: Mutex<f32>,
    smooth_level: Mutex<f32>,
}

impl AudioState {
    pub fn new() -> Self {
        Self {
            level_bits: Arc::new(AtomicU32::new(0)),
            capturing: Arc::new(AtomicBool::new(false)),
            should_stop: Arc::new(AtomicBool::new(false)),
            reactive_enabled: Arc::new(AtomicBool::new(false)),
            base_color: Mutex::new((255, 255, 255)),
            gain: Mutex::new(3.0),
            pattern: AtomicU8::new(0),
            hue_phase: Mutex::new(0.0),
            smooth_level: Mutex::new(0.0),
        }
    }

    pub fn list_devices() -> Vec<String> {
        let host = cpal::default_host();
        match host.input_devices() {
            Ok(it) => it.filter_map(|d| d.name().ok()).collect(),
            Err(_) => Vec::new(),
        }
    }

    pub fn current_level(&self) -> f32 {
        let raw = f32::from_bits(self.level_bits.load(Ordering::Relaxed));
        (raw * *self.gain.lock().unwrap()).min(1.0)
    }

    pub fn is_capturing(&self) -> bool {
        self.capturing.load(Ordering::Relaxed)
    }

    pub fn set_base_color(&self, r: u8, g: u8, b: u8) {
        *self.base_color.lock().unwrap() = (r, g, b);
    }

    pub fn set_gain(&self, gain: f32) {
        *self.gain.lock().unwrap() = gain.max(0.1);
    }

    pub fn set_pattern(&self, pattern: u8) {
        self.pattern.store(pattern.min(7), Ordering::Relaxed);
    }

    pub fn stop(&self) {
        self.should_stop.store(true, Ordering::Relaxed);
    }

    /// Called every tick from the background poll loop. `dt` is the tick
    /// interval in seconds, used to pace the hue rotation. Returns the next
    /// RGB frame to send, or None if reactive mode is off.
    pub fn tick_reactive_color(&self, dt: f32) -> Option<(u8, u8, u8)> {
        if !self.reactive_enabled.load(Ordering::Relaxed) {
            return None;
        }
        let raw_level = self.current_level();
        let pattern = Pattern::from_index(self.pattern.load(Ordering::Relaxed));

        let mut smooth = self.smooth_level.lock().unwrap();
        let alpha = 0.15; // slower = calmer response for the "2" patterns
        *smooth += (raw_level - *smooth) * alpha;
        let level = if pattern.is_slow() { *smooth } else { raw_level };
        drop(smooth);

        let (br, bg, bb) = self.base_color();
        let (h0, s0, _v0) = {
            let max = br.max(bg).max(bb) as f32 / 255.0;
            let min = br.min(bg).min(bb) as f32 / 255.0;
            let delta = max - min;
            let s = if max == 0.0 { 0.0 } else { delta / max };
            let h = if delta == 0.0 {
                0.0
            } else if max == br as f32 / 255.0 {
                60.0 * (((bg as f32 - bb as f32) / 255.0 / delta).rem_euclid(6.0))
            } else if max == bg as f32 / 255.0 {
                60.0 * (((bb as f32 - br as f32) / 255.0 / delta) + 2.0)
            } else {
                60.0 * (((br as f32 - bg as f32) / 255.0 / delta) + 4.0)
            };
            (h, s, max)
        };

        Some(match pattern {
            Pattern::Energic | Pattern::Energic2 => {
                let scale = |c: u8| (c as f32 * level) as u8;
                (scale(br), scale(bg), scale(bb))
            }
            Pattern::Rhythm | Pattern::Rhythm2 => {
                if level > 0.5 {
                    (br, bg, bb)
                } else {
                    (0, 0, 0)
                }
            }
            Pattern::Spectrum | Pattern::Spectrum2 => {
                let mut phase = self.hue_phase.lock().unwrap();
                *phase = (*phase + level * 180.0 * dt).rem_euclid(360.0);
                hsv_to_rgb(h0 + *phase, s0.max(0.6), level.max(0.15))
            }
            Pattern::Rolling | Pattern::Rolling2 => {
                let mut phase = self.hue_phase.lock().unwrap();
                *phase = (*phase + 60.0 * dt).rem_euclid(360.0);
                hsv_to_rgb(*phase, s0.max(0.6), level.max(0.15))
            }
        })
    }

    fn base_color(&self) -> (u8, u8, u8) {
        *self.base_color.lock().unwrap()
    }

    pub fn start(&self, device: Option<String>) -> Result<(), String> {
        self.stop();
        // give the previous capture thread a moment to notice should_stop and exit
        thread::sleep(Duration::from_millis(60));

        self.should_stop.store(false, Ordering::Relaxed);
        self.capturing.store(true, Ordering::Relaxed);

        if device.as_deref() == Some(SOURCE_SYSTEM_AUDIO) {
            self.start_system_audio()
        } else {
            self.start_microphone(device)
        }
    }

    fn start_microphone(&self, device_name: Option<String>) -> Result<(), String> {
        let host = cpal::default_host();
        let device = device_name
            .as_ref()
            .and_then(|name| {
                host.input_devices()
                    .ok()
                    .and_then(|mut it| it.find(|d| d.name().map(|n| &n == name).unwrap_or(false)))
            })
            .or_else(|| host.default_input_device())
            .ok_or("No microphone found on this PC")?;

        let config = device.default_input_config().map_err(|e| e.to_string())?;
        let sample_format = config.sample_format();
        let stream_config: cpal::StreamConfig = config.into();

        let level_bits = self.level_bits.clone();
        let capturing = self.capturing.clone();
        let should_stop = self.should_stop.clone();

        thread::spawn(move || {
            let err_fn = |err| eprintln!("[audio] mic stream error: {err}");
            let stream = match sample_format {
                cpal::SampleFormat::F32 => {
                    let level_bits = level_bits.clone();
                    device.build_input_stream(
                        &stream_config,
                        move |data: &[f32], _| level_bits.store(rms_f32(data).to_bits(), Ordering::Relaxed),
                        err_fn,
                        None,
                    )
                }
                cpal::SampleFormat::I16 => {
                    let level_bits = level_bits.clone();
                    device.build_input_stream(
                        &stream_config,
                        move |data: &[i16], _| level_bits.store(rms_i16(data).to_bits(), Ordering::Relaxed),
                        err_fn,
                        None,
                    )
                }
                cpal::SampleFormat::U16 => {
                    let level_bits = level_bits.clone();
                    device.build_input_stream(
                        &stream_config,
                        move |data: &[u16], _| level_bits.store(rms_u16(data).to_bits(), Ordering::Relaxed),
                        err_fn,
                        None,
                    )
                }
                other => {
                    eprintln!("[audio] unsupported sample format: {other:?}");
                    capturing.store(false, Ordering::Relaxed);
                    return;
                }
            };
            let stream = match stream {
                Ok(s) => s,
                Err(e) => {
                    eprintln!("[audio] failed to build mic input stream: {e}");
                    capturing.store(false, Ordering::Relaxed);
                    return;
                }
            };
            if let Err(e) = stream.play() {
                eprintln!("[audio] failed to start mic stream: {e}");
                capturing.store(false, Ordering::Relaxed);
                return;
            }
            while !should_stop.load(Ordering::Relaxed) {
                thread::sleep(Duration::from_millis(50));
            }
            capturing.store(false, Ordering::Relaxed);
        });

        Ok(())
    }

    fn start_system_audio(&self) -> Result<(), String> {
        let level_bits = self.level_bits.clone();
        let capturing = self.capturing.clone();
        let should_stop = self.should_stop.clone();
        let (ready_tx, ready_rx) = mpsc::channel::<Result<(), String>>();

        thread::spawn(move || {
            let result = run_system_audio_loop(level_bits, &should_stop);
            if let Err(e) = &result {
                eprintln!("[audio] system audio capture error: {e}");
            }
            let _ = ready_tx.send(result.map(|_| ()).or(Ok(())));
            capturing.store(false, Ordering::Relaxed);
        });

        // best-effort: don't block the UI thread waiting; errors surface via the log above
        let _ = ready_rx.try_recv();
        Ok(())
    }
}

fn run_system_audio_loop(level_bits: Arc<AtomicU32>, should_stop: &AtomicBool) -> Result<(), String> {
    use wasapi::{DeviceEnumerator, Direction, SampleType, StreamMode, WaveFormat};

    wasapi::initialize_mta().ok().map_err(|e| format!("{e:?}"))?;

    let enumerator = DeviceEnumerator::new().map_err(|e| e.to_string())?;
    let device = enumerator
        .get_default_device(Direction::Render)
        .map_err(|e| e.to_string())?;
    let mut audio_client = device.get_iaudioclient().map_err(|e| e.to_string())?;

    let desired_format = WaveFormat::new(32, 32, SampleType::Float, 48000, 2, None);
    let (_def_time, min_time) = audio_client.get_device_period().map_err(|e| e.to_string())?;
    let mode = StreamMode::EventsShared {
        autoconvert: true,
        buffer_duration_hns: min_time,
    };
    // the Render-direction client + Capture-direction init is what puts WASAPI into loopback mode
    audio_client
        .initialize_client(&desired_format, Direction::Capture, mode, None)
        .map_err(|e| e.to_string())?;

    let h_event = audio_client.set_get_eventhandle().map_err(|e| e.to_string())?;
    let capture_client = audio_client.get_audiocaptureclient().map_err(|e| e.to_string())?;
    let mut sample_queue: std::collections::VecDeque<u8> = std::collections::VecDeque::with_capacity(1 << 16);

    audio_client.start_stream().map_err(|e| e.to_string())?;

    while !should_stop.load(Ordering::Relaxed) {
        capture_client
            .read_from_device_to_deque(&mut sample_queue)
            .map_err(|e| e.to_string())?;

        if !sample_queue.is_empty() {
            let bytes: Vec<u8> = sample_queue.drain(..).collect();
            let samples: Vec<f32> = bytes
                .chunks_exact(4)
                .map(|c| f32::from_le_bytes([c[0], c[1], c[2], c[3]]))
                .collect();
            if !samples.is_empty() {
                level_bits.store(rms_f32(&samples).to_bits(), Ordering::Relaxed);
            }
        }

        let _ = h_event.wait_for_event(100);
    }

    let _ = audio_client.stop_stream();
    Ok(())
}
