use std::time::{Duration, Instant};

use btleplug::api::{Central, Manager as _, Peripheral as _, ScanFilter, WriteType};
use btleplug::platform::{Adapter, Manager, Peripheral};
use serde::Serialize;
use tokio::sync::Mutex;
use tokio::time;

use crate::protocol;

const MIN_WRITE_SPACING: Duration = Duration::from_millis(25);

#[derive(Serialize, Clone)]
pub struct FoundDevice {
    pub name: String,
    pub address: String,
}

pub struct BleState {
    adapter: Mutex<Option<Adapter>>,
    connected: Mutex<Option<Peripheral>>,
    connected_address: Mutex<Option<String>>,
    last_write: Mutex<Instant>,
}

impl BleState {
    pub fn new() -> Self {
        Self {
            adapter: Mutex::new(None),
            connected: Mutex::new(None),
            connected_address: Mutex::new(None),
            last_write: Mutex::new(Instant::now() - MIN_WRITE_SPACING),
        }
    }

    async fn adapter(&self) -> Result<Adapter, String> {
        let mut guard = self.adapter.lock().await;
        if let Some(a) = guard.as_ref() {
            return Ok(a.clone());
        }
        let manager = Manager::new().await.map_err(|e| e.to_string())?;
        let adapters = manager.adapters().await.map_err(|e| e.to_string())?;
        let adapter = adapters
            .into_iter()
            .next()
            .ok_or("No Bluetooth adapter found on this PC")?;
        *guard = Some(adapter.clone());
        Ok(adapter)
    }

    pub async fn scan(&self) -> Result<Vec<FoundDevice>, String> {
        let adapter = self.adapter().await?;
        adapter
            .start_scan(ScanFilter::default())
            .await
            .map_err(|e| e.to_string())?;
        time::sleep(Duration::from_secs(5)).await;
        let peripherals = adapter.peripherals().await.map_err(|e| e.to_string())?;
        adapter.stop_scan().await.ok();

        let mut found = Vec::new();
        for p in peripherals {
            if let Ok(Some(props)) = p.properties().await {
                if let Some(name) = props.local_name {
                    if name.starts_with(protocol::DEVICE_NAME_PREFIX) {
                        found.push(FoundDevice {
                            name,
                            address: p.address().to_string(),
                        });
                    }
                }
            }
        }
        eprintln!("[ble] scan found {} device(s)", found.len());
        Ok(found)
    }

    pub async fn connect(&self, address: &str) -> Result<(), String> {
        let adapter = self.adapter().await?;
        let peripherals = adapter.peripherals().await.map_err(|e| e.to_string())?;
        let target = peripherals
            .into_iter()
            .find(|p| p.address().to_string() == address)
            .ok_or("Device not found - try scanning again")?;

        target.connect().await.map_err(|e| e.to_string())?;
        target.discover_services().await.map_err(|e| e.to_string())?;

        let characteristic_count = target
            .characteristics()
            .iter()
            .filter(|c| c.uuid == protocol::CHARACTERISTIC_UUID)
            .count();
        if characteristic_count == 0 {
            eprintln!("[ble] WARNING: connected but the expected characteristic wasn't found");
        }

        let mut guard = self.connected.lock().await;
        if let Some(previous) = guard.take() {
            let _ = previous.disconnect().await;
        }
        *guard = Some(target);
        *self.connected_address.lock().await = Some(address.to_string());
        eprintln!("[ble] connected to {address}");
        Ok(())
    }

    pub async fn disconnect(&self) -> Result<(), String> {
        let mut guard = self.connected.lock().await;
        if let Some(p) = guard.take() {
            p.disconnect().await.map_err(|e| e.to_string())?;
        }
        Ok(())
    }

    pub async fn connected_address(&self) -> Option<String> {
        self.connected_address.lock().await.clone()
    }

    pub async fn send(&self, data: Vec<u8>) -> Result<(), String> {
        // the cheap BLE module this talks to can drop back-to-back writes with
        // no gap between them; space writes out like the mobile/Android app does.
        {
            let mut last = self.last_write.lock().await;
            let elapsed = last.elapsed();
            if elapsed < MIN_WRITE_SPACING {
                time::sleep(MIN_WRITE_SPACING - elapsed).await;
            }
            *last = Instant::now();
        }

        let guard = self.connected.lock().await;
        let peripheral = guard.as_ref().ok_or("Not connected to a light")?;
        let chars = peripheral.characteristics();
        let characteristic = chars
            .iter()
            .find(|c| c.uuid == protocol::CHARACTERISTIC_UUID)
            .ok_or("Light doesn't expose the expected characteristic")?;
        let result = peripheral
            .write(characteristic, &data, WriteType::WithoutResponse)
            .await
            .map_err(|e| e.to_string());
        if let Err(e) = &result {
            eprintln!("[ble] write failed: {e} (bytes: {data:02x?})");
        }
        result
    }

    pub async fn is_connected(&self) -> bool {
        let guard = self.connected.lock().await;
        match guard.as_ref() {
            Some(p) => p.is_connected().await.unwrap_or(false),
            None => false,
        }
    }
}
