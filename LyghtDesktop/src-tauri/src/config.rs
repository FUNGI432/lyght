use serde::{Deserialize, Serialize};
use std::fs;
use tauri::{AppHandle, Manager};

#[derive(Serialize, Deserialize, Clone, Default)]
pub struct AppConfig {
    pub last_device_address: Option<String>,
    pub last_device_name: Option<String>,
    pub audio_input_device: Option<String>,
}

fn config_path(app: &AppHandle) -> Option<std::path::PathBuf> {
    let dir = app.path().app_config_dir().ok()?;
    fs::create_dir_all(&dir).ok()?;
    Some(dir.join("config.json"))
}

pub fn load(app: &AppHandle) -> AppConfig {
    config_path(app)
        .and_then(|p| fs::read_to_string(p).ok())
        .and_then(|s| serde_json::from_str(&s).ok())
        .unwrap_or_default()
}

pub fn save(app: &AppHandle, config: &AppConfig) {
    if let Some(path) = config_path(app) {
        if let Ok(json) = serde_json::to_string_pretty(config) {
            let _ = fs::write(path, json);
        }
    }
}

pub fn set_last_device(app: &AppHandle, address: &str, name: &str) {
    let mut config = load(app);
    config.last_device_address = Some(address.to_string());
    config.last_device_name = Some(name.to_string());
    save(app, &config);
}
