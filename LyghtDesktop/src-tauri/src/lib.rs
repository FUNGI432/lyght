mod audio;
mod ble;
mod config;
mod protocol;

use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;

use audio::AudioState;
use ble::{BleState, FoundDevice};
use protocol::EffectCatalog;
use tauri::menu::{Menu, MenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Emitter, Manager, State, WindowEvent};
use tauri_plugin_autostart::MacosLauncher;

struct TrayHandles {
    power_item: MenuItem<tauri::Wry>,
    connect_item: MenuItem<tauri::Wry>,
}

/// Tracks what we last *told* the light, since it can't tell us its own state.
struct LastKnownState {
    power_on: AtomicBool,
}

#[tauri::command]
async fn scan_devices(state: State<'_, BleState>) -> Result<Vec<FoundDevice>, String> {
    state.scan().await
}

#[tauri::command]
async fn connect_device(
    app: AppHandle,
    state: State<'_, BleState>,
    address: String,
    name: String,
) -> Result<(), String> {
    state.connect(&address).await?;
    config::set_last_device(&app, &address, &name);
    refresh_tray(&app).await;
    Ok(())
}

#[tauri::command]
async fn disconnect_device(app: AppHandle, state: State<'_, BleState>) -> Result<(), String> {
    state.disconnect().await?;
    refresh_tray(&app).await;
    Ok(())
}

#[tauri::command]
async fn is_connected(state: State<'_, BleState>) -> Result<bool, String> {
    Ok(state.is_connected().await)
}

#[tauri::command]
fn get_last_device(app: AppHandle) -> Option<(String, String)> {
    let cfg = config::load(&app);
    Some((cfg.last_device_address?, cfg.last_device_name.unwrap_or_default()))
}

#[tauri::command]
fn get_effect_catalog() -> EffectCatalog {
    protocol::load_effect_catalog()
}

#[tauri::command]
fn get_mic_eq_labels() -> Vec<&'static str> {
    protocol::MIC_EQ_LABELS.to_vec()
}

#[tauri::command]
fn get_windows_accent_color() -> Option<(u8, u8, u8)> {
    let key = windows_registry::CURRENT_USER
        .open(r"Software\Microsoft\Windows\DWM")
        .ok()?;
    let value = key.get_u32("AccentColor").ok()?;
    let r = (value & 0xFF) as u8;
    let g = ((value >> 8) & 0xFF) as u8;
    let b = ((value >> 16) & 0xFF) as u8;
    Some((r, g, b))
}

#[tauri::command]
async fn set_power(
    app: AppHandle,
    state: State<'_, BleState>,
    last: State<'_, LastKnownState>,
    on: bool,
) -> Result<(), String> {
    last.power_on.store(on, Ordering::Relaxed);
    refresh_tray(&app).await;
    state.send(protocol::power(on)).await
}

// ---------- audio ----------

#[tauri::command]
fn list_audio_devices() -> Vec<String> {
    AudioState::list_devices()
}

#[tauri::command]
fn start_audio_capture(audio: State<'_, AudioState>, device: Option<String>) -> Result<(), String> {
    audio.start(device)
}

#[tauri::command]
fn stop_audio_capture(audio: State<'_, AudioState>) {
    audio.stop();
}

#[tauri::command]
fn set_audio_reactive(audio: State<'_, AudioState>, enabled: bool, r: u8, g: u8, b: u8) {
    audio.set_base_color(r, g, b);
    audio.reactive_enabled.store(enabled, Ordering::Relaxed);
}

#[tauri::command]
fn set_audio_pattern(audio: State<'_, AudioState>, pattern: u8) {
    audio.set_pattern(pattern);
}

#[tauri::command]
fn set_audio_gain(audio: State<'_, AudioState>, gain: f32) {
    audio.set_gain(gain);
}

macro_rules! send_command {
    ($name:ident ( $($arg:ident : $ty:ty),* ) ) => {
        #[tauri::command]
        async fn $name(state: State<'_, BleState>, $($arg: $ty),*) -> Result<(), String> {
            state.send(protocol::$name($($arg),*)).await
        }
    };
}

send_command!(color(r: u8, g: u8, b: u8));
send_command!(white_temperature(warm: u8, cold: u8));
send_command!(brightness(level: u8, light_mode: u8));
send_command!(effect_mode(code: u8));
send_command!(effect_speed(speed: u8));
send_command!(scene(index: u8));
send_command!(mic_on_off(on: bool));
send_command!(mic_sensitivity(level: u8));
send_command!(mic_eq_mode(mode: u8));
send_command!(pin_sequence(r: u8, g: u8, b: u8));
send_command!(system_time(hour: u8, minute: u8, second: u8, weekday: u8));
send_command!(timing_schedule(hour: u8, minute: u8, second: u8, timing_mode: u8, weekday_bitmask: u8));

// ---------- tray ----------

async fn refresh_tray(app: &AppHandle) {
    let Some(handles) = app.try_state::<TrayHandles>() else { return };
    let last = app.state::<LastKnownState>();
    let ble = app.state::<BleState>();

    let power_on = last.power_on.load(Ordering::Relaxed);
    let _ = handles
        .power_item
        .set_text(if power_on { "Turn off" } else { "Turn on" });

    let connected = ble.is_connected().await;
    let label = if connected {
        "Disconnect".to_string()
    } else {
        match config::load(app).last_device_name {
            Some(name) => format!("Connect to {name}"),
            None => "Connect".to_string(),
        }
    };
    let _ = handles.connect_item.set_text(label);
}

fn create_tray(app: &AppHandle) -> tauri::Result<()> {
    let show_item = MenuItem::with_id(app, "show", "Open LYGHT", true, None::<&str>)?;
    let power_item = MenuItem::with_id(app, "power", "Turn on", true, None::<&str>)?;
    let connect_item = MenuItem::with_id(app, "connect", "Connect", true, None::<&str>)?;
    let quit_item = MenuItem::with_id(app, "quit", "Quit", true, None::<&str>)?;
    let menu = Menu::with_items(app, &[&show_item, &power_item, &connect_item, &quit_item])?;

    app.manage(TrayHandles {
        power_item: power_item.clone(),
        connect_item: connect_item.clone(),
    });

    TrayIconBuilder::with_id("lyght-tray")
        .tooltip("LYGHT")
        .icon(app.default_window_icon().unwrap().clone())
        .menu(&menu)
        .show_menu_on_left_click(false)
        .on_menu_event(move |app, event| match event.id.as_ref() {
            "show" => {
                if let Some(w) = app.get_webview_window("main") {
                    let _ = w.show();
                    let _ = w.set_focus();
                }
            }
            "quit" => app.exit(0),
            "power" => {
                let app = app.clone();
                tauri::async_runtime::spawn(async move {
                    let last = app.state::<LastKnownState>();
                    let next = !last.power_on.load(Ordering::Relaxed);
                    let ble = app.state::<BleState>();
                    let _ = ble.send(protocol::power(next)).await;
                    last.power_on.store(next, Ordering::Relaxed);
                    refresh_tray(&app).await;
                });
            }
            "connect" => {
                let app = app.clone();
                tauri::async_runtime::spawn(async move {
                    let ble = app.state::<BleState>();
                    if ble.is_connected().await {
                        let _ = ble.disconnect().await;
                    } else if let Some(address) = config::load(&app).last_device_address {
                        let _ = ble.connect(&address).await;
                    }
                    refresh_tray(&app).await;
                });
            }
            _ => {}
        })
        .on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::Click {
                button: MouseButton::Left,
                button_state: MouseButtonState::Up,
                ..
            } = event
            {
                let app = tray.app_handle();
                if let Some(w) = app.get_webview_window("main") {
                    let _ = w.show();
                    let _ = w.set_focus();
                }
            }
        })
        .build(app)?;

    Ok(())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_autostart::init(
            MacosLauncher::LaunchAgent,
            None,
        ))
        .manage(BleState::new())
        .manage(AudioState::new())
        .manage(LastKnownState {
            power_on: AtomicBool::new(true),
        })
        .setup(|app| {
            let handle = app.handle().clone();
            create_tray(&handle)?;

            // try the last saved device on startup (useful when autostarted into the tray)
            let startup_handle = handle.clone();
            tauri::async_runtime::spawn(async move {
                if let Some(address) = config::load(&startup_handle).last_device_address {
                    let ble = startup_handle.state::<BleState>();
                    let _ = ble.connect(&address).await;
                    refresh_tray(&startup_handle).await;
                }
            });

            // background: poll audio level -> emit to frontend, and drive the light if
            // PC audio-reactive mode is enabled
            let poll_handle = handle.clone();
            tauri::async_runtime::spawn(async move {
                const TICK: Duration = Duration::from_millis(60);
                loop {
                    tokio::time::sleep(TICK).await;
                    let audio = poll_handle.state::<AudioState>();
                    if !audio.is_capturing() {
                        continue;
                    }
                    let level = audio.current_level();
                    let _ = poll_handle.emit("audio-level", level);
                    if let Some((r, g, b)) = audio.tick_reactive_color(TICK.as_secs_f32()) {
                        let ble = poll_handle.state::<BleState>();
                        let _ = ble.send(protocol::music_reactive_color(r, g, b)).await;
                    }
                }
            });

            Ok(())
        })
        .on_window_event(|window, event| {
            if let WindowEvent::CloseRequested { api, .. } = event {
                // closing the window just hides it to the tray; "Quit" in the tray menu
                // (or app.exit) is the only thing that actually ends the process
                api.prevent_close();
                let _ = window.hide();
            }
        })
        .invoke_handler(tauri::generate_handler![
            scan_devices,
            connect_device,
            disconnect_device,
            is_connected,
            get_last_device,
            get_effect_catalog,
            get_mic_eq_labels,
            get_windows_accent_color,
            set_power,
            color,
            white_temperature,
            brightness,
            effect_mode,
            effect_speed,
            scene,
            mic_on_off,
            mic_sensitivity,
            mic_eq_mode,
            pin_sequence,
            system_time,
            timing_schedule,
            list_audio_devices,
            start_audio_capture,
            stop_audio_capture,
            set_audio_reactive,
            set_audio_pattern,
            set_audio_gain,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
