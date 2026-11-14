#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::{fs, path::PathBuf, sync::{Mutex, atomic::{AtomicBool, AtomicU64, Ordering}}};
use std::collections::VecDeque;
use std::time::{Duration, Instant};
use tauri::{AppHandle, Manager, RunEvent, State};
use tauri::menu::{Menu, MenuItem};
use tauri::tray::TrayIconBuilder;
use tauri_plugin_shell::{process::{CommandChild, CommandEvent}, ShellExt};
use serde::Serialize;
use serde_json::json;
use rusqlite::{Connection, OpenFlags};
use sha2::{Digest, Sha256};
use std::time::{SystemTime, UNIX_EPOCH};
use std::io::Write;
use rand::RngCore;
use tokio::io::{AsyncReadExt, AsyncWriteExt};

#[derive(Default)]
struct CompanionState(
    Mutex<Option<CommandChild>>,
    Mutex<Option<Vec<String>>>,
    Mutex<VecDeque<Instant>>,
    Mutex<Option<String>>,
    AtomicBool,
    AtomicU64, // process generation prevents stale termination from clearing a new child
    Mutex<Option<String>>, // per-launch health secret; never persisted or logged
);

/// Rolling five-restart budget with 1/2/4/8/16-second backoff.
fn restart_delay(history: &mut VecDeque<Instant>, now: Instant) -> Option<Duration> {
    while history.front().is_some_and(|time| now.duration_since(*time) >= Duration::from_secs(60)) {
        history.pop_front();
    }
    history.push_back(now);
    if history.len() > 5 { None }
    else { Some(Duration::from_secs(1u64 << (history.len() - 1))) }
}

#[derive(Serialize)]
struct CompanionHealth {
    running: bool,
    restart_count_60s: usize,
    last_error: Option<String>,
}

/// Only 127.0.0.1:8766; no proxy, no IPC-originated arbitrary host/port.
/// Authenticate health with the secret given directly to the launched process.
async fn authenticated_health_probe(secret: &str) -> bool {
    let future = async {
        let mut socket = tokio::net::TcpStream::connect("127.0.0.1:8766").await?;
        let message = format!("GET /health HTTP/1.1\r\nHost: 127.0.0.1:8766\r\nAuthorization: Bearer {}\r\nConnection: close\r\n\r\n", secret);
        socket.write_all(message.as_bytes()).await?;
        let mut response = [0u8; 4096];
        let count = socket.read(&mut response).await?;
        Ok::<bool, std::io::Error>(count > 20 &&
            response[..count].starts_with(b"HTTP/1.1 200 "))
    };
    matches!(tokio::time::timeout(Duration::from_secs(2), future).await, Ok(Ok(true)))
}

#[tauri::command]
async fn companion_health(state: State<'_, CompanionState>) -> Result<CompanionHealth, String> {
    let spawned = state.0.lock().map_err(|_| "Process state unavailable")?.is_some();
    let secret = state.6.lock().map_err(|_| "Health secret unavailable")?.clone();
    let restarts = state.2.lock().map_err(|_| "Restart state unavailable")?
        .iter().filter(|time| time.elapsed() < Duration::from_secs(60)).count();
    let prior_error = state.3.lock().map_err(|_| "Error state unavailable")?.clone();
    let alive = match secret.as_ref() {
        Some(token) if spawned => authenticated_health_probe(token).await,
        _ => false,
    };
    let error = if spawned && !alive {
        prior_error.or_else(|| Some("Companion process exists, but the authenticated gateway health listener is unavailable.".into()))
    } else { prior_error };
    Ok(CompanionHealth { running: alive, restart_count_60s: restarts, last_error: error })
}


#[derive(Serialize)]
struct EvidenceFile {
    name: String,
    size: u64,
}

/// Fixed, allowlisted diagnostic events, never user-controlled stdout,
/// private keys, QR codes, signed requests, phone IDs, location or media.
/// Rotation uses 3 x 256 KiB files. Logging errors do not affect controls.
fn diagnostic(app: &AppHandle, event: &'static str) {
    if !matches!(event,
        "gateway_started" | "gateway_exited" | "gateway_restart" |
        "gateway_restart_failed" | "gateway_restart_cap" |
        "gateway_stopped" | "owner_quit") { return; }
    let Ok(dir) = state_dir(app) else { return; };
    let logs = dir.join("logs");
    if fs::create_dir_all(&logs).is_err() { return; }
    let path = logs.join("companion.log");
    if fs::metadata(&path).map(|m| m.len() > 256 * 1024).unwrap_or(false) {
        let _ = fs::remove_file(logs.join("companion.log.3"));
        for i in (1..=2).rev() {
            let _ = fs::rename(
                logs.join(format!("companion.log.{i}")),
                logs.join(format!("companion.log.{}", i + 1)),
            );
        }
        let _ = fs::rename(&path, logs.join("companion.log.1"));
    }
    let mut options = fs::OpenOptions::new();
    options.create(true).append(true);
    #[cfg(unix)] {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    if let Ok(mut file) = options.open(&path) {
        let when = SystemTime::now().duration_since(UNIX_EPOCH)
            .map(|d| d.as_secs()).unwrap_or_default();
        let _ = writeln!(file, "{{\"time\":{when},\"event\":\"{event}\"}}");
    }
}

fn state_dir(app: &AppHandle) -> Result<PathBuf, String> {
    let dir = app.path().app_data_dir().map_err(|e| e.to_string())?;
    fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    Ok(dir)
}

fn command_args(app: &AppHandle, subcommand: &str, extra: &[String]) -> Result<Vec<String>, String> {
    let dir = state_dir(app)?;
    let mut args = vec!["--state-dir".into(), dir.to_string_lossy().to_string(), subcommand.into()];
    args.extend_from_slice(extra);
    Ok(args)
}

async fn run_cli(app: &AppHandle, subcommand: &str, extra: Vec<String>) -> Result<String, String> {
    let args = command_args(app, subcommand, &extra)?;
    let output = app.shell().sidecar("laptopwatch-sidecar")
        .map_err(|e| e.to_string())?
        .args(args).output().await.map_err(|e| e.to_string())?;
    if output.status.code() != Some(0) {
        // Never pass arbitrary sidecar stderr (which can contain QR/PII)
        // into the owner UI, logs or telemetry.
        return Err(format!("Companion {} returned a nonzero status", subcommand));
    }
    String::from_utf8(output.stdout).map_err(|e| e.to_string())
}

#[tauri::command]
async fn initialize_companion(app: AppHandle) -> Result<String, String> {
    run_cli(&app, "init", vec![]).await
}

#[tauri::command]
async fn suggest_lan_ip() -> Result<String, String> {
    match local_ip_address::local_ip() {
        Ok(std::net::IpAddr::V4(ip)) if ip.is_private() => Ok(ip.to_string()),
        _ => Err("Automatic LAN IPv4 discovery unavailable; enter a private LAN IPv4 address".into()),
    }
}

#[tauri::command]
async fn create_pair_invitation(app: AppHandle, host: String) -> Result<String, String> {
    // Python coordinator performs additional strict private-IP validation.
    if host.len() > 45 || host.contains(|c: char| !c.is_ascii_digit() && c != '.') {
        return Err("Only a numeric private IPv4 address can be paired".into());
    }
    run_cli(&app, "pair-code", vec!["--host".into(), host, "--port".into(), "8765".into()]).await
}

fn snapshot_rows<F>(
    path: PathBuf, query: &str, parameters: &[&dyn rusqlite::ToSql],
    row_builder: F,
) -> Result<Vec<serde_json::Value>, String>
where F: FnMut(&rusqlite::Row<'_>) -> rusqlite::Result<serde_json::Value> {
    if !path.is_file() { return Ok(Vec::new()); }
    let connection=Connection::open_with_flags(path, OpenFlags::SQLITE_OPEN_READ_ONLY)
        .map_err(|e| e.to_string())?;
    connection.busy_timeout(std::time::Duration::from_millis(1000))
        .map_err(|e| e.to_string())?;
    let mut stmt=connection.prepare(query).map_err(|e| e.to_string())?;
    let iter=stmt.query_map(parameters,row_builder).map_err(|e| e.to_string())?;
    let mut data=Vec::new();
    for value in iter { data.push(value.map_err(|e| e.to_string())?); }
    Ok(data)
}

#[tauri::command]
fn location_snapshot(app: AppHandle) -> Result<String, String> {
    // Local owner-only Tauri IPC, not exposed on signed phone HTTPS.
    let directory = state_dir(&app)?;
    let rows = snapshot_rows(
        directory.join("last_seen.db"),
        "SELECT latitude,longitude,accuracy_m,observed_at_ms,provider
         FROM fixes ORDER BY observed_at_ms DESC,id DESC LIMIT 1",
        &[],
        |r| Ok(json!({
            "latitude":r.get::<_,f64>(0)?,
            "longitude":r.get::<_,f64>(1)?,
            "accuracy_meters":r.get::<_,f64>(2)?,
            "observed_at_ms":r.get::<_,i64>(3)?,
            "provider":r.get::<_,String>(4)?,
            "live":false
        })),
    )?;
    Ok(json!({"last_seen":rows.into_iter().next()}).to_string())
}

#[tauri::command]
async fn audit_history(app: AppHandle) -> Result<String, String> {
    // The Python subcommand validates the ENTIRE Ed25519 hash chain first.
    // Never display unverified/partially parsed audit entries as trusted.
    run_cli(&app, "audit-history", vec![]).await
}

#[tauri::command]
fn dashboard_snapshot(app: AppHandle) -> Result<String, String> {
    let directory=state_dir(&app)?;
    let now=SystemTime::now().duration_since(UNIX_EPOCH)
        .map_err(|e| e.to_string())?.as_secs() as i64;
    let pending=snapshot_rows(
        directory.join("pairing.db"),
        "SELECT p.request_id,p.phone_id,p.phone_label,p.phone_pubkey
         FROM pending_pairs AS p JOIN pairing_sessions AS s ON p.challenge=s.challenge
         WHERE s.expires>=?1 ORDER BY p.received DESC LIMIT 100",
        &[&now], |r| {
            let public:Vec<u8>=r.get(3)?;
            let fingerprint=format!("{:x}",Sha256::digest(&public));
            Ok(json!({
                "request_id":r.get::<_,String>(0)?,
                "device_id":r.get::<_,String>(1)?,
                "label":r.get::<_,String>(2)?,
                "public_key_sha256":fingerprint
            }))
        })?;
    let devices=snapshot_rows(
        directory.join("paired.db"),
        "SELECT id,revoked FROM paired_devices ORDER BY id LIMIT 100",
        &[], |r| Ok(json!({"id":r.get::<_,String>(0)?,
                          "revoked":r.get::<_,bool>(1)?})))?;
    let incidents=snapshot_rows(
        directory.join("incidents.db"),
        "SELECT id,observed_at_ms,kind,confidence,evidence_id,video_id
         FROM incidents ORDER BY id DESC LIMIT 100",
        &[], |r| Ok(json!({
            "id":r.get::<_,i64>(0)?,
            "observed_at_ms":r.get::<_,i64>(1)?,
            "kind":r.get::<_,String>(2)?,
            "confidence":r.get::<_,f64>(3)?,
            "evidence_id":r.get::<_,Option<String>>(4)?,
            "video_id":r.get::<_,Option<String>>(5)?
        })))?;
    Ok(json!({"pending":pending,"devices":devices,"incidents":incidents}).to_string())
}

#[tauri::command]
async fn pending_pairs(app: AppHandle) -> Result<String, String> {
    run_cli(&app, "pending", vec![]).await
}

#[tauri::command]
async fn paired_devices(app: AppHandle) -> Result<String, String> {
    run_cli(&app, "devices", vec![]).await
}

fn require_uuid(value: &str) -> Result<(), String> {
    let matches_pattern = value.len() == 36 &&
        value.chars().enumerate().all(|(i, c)| {
            if matches!(i, 8 | 13 | 18 | 23) { c == '-' } else { c.is_ascii_hexdigit() }
        });
    if !matches_pattern { return Err("Expected a device UUID".into()); }
    Ok(())
}

#[tauri::command]
async fn approve_pending(app: AppHandle, request_id: String) -> Result<String, String> {
    require_uuid(&request_id)?;
    run_cli(&app, "approve", vec!["--request-id".into(), request_id]).await
}

#[tauri::command]
async fn reject_pending(app: AppHandle, request_id: String) -> Result<String, String> {
    require_uuid(&request_id)?;
    run_cli(&app, "reject", vec!["--request-id".into(), request_id]).await
}

#[tauri::command]
async fn revoke_device(app: AppHandle, device_id: String) -> Result<String, String> {
    require_uuid(&device_id)?;
    run_cli(&app, "revoke", vec!["--device-id".into(), device_id]).await
}

#[tauri::command]
fn open_evidence_directory(app: AppHandle) -> Result<(), String> {
    // Opens only this application's own evidence path; never accept an
    // arbitrary JS-provided file path or run a shell command.
    let dir=state_dir(&app)?.join("evidence");
    fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    let executable=if cfg!(target_os="windows") { "explorer.exe" }
        else if cfg!(target_os="macos") { "open" }
        else { "xdg-open" };
    std::process::Command::new(executable).arg(&dir).spawn()
        .map_err(|e| e.to_string())?;
    Ok(())
}

#[tauri::command]
fn evidence_files(app: AppHandle) -> Result<Vec<EvidenceFile>, String> {
    let dir = state_dir(&app)?.join("evidence");
    if !dir.is_dir() { return Ok(Vec::new()); }
    let mut files = Vec::new();
    let entries = fs::read_dir(&dir).map_err(|e| e.to_string())?;
    for entry in entries.flatten().take(400) {
        let Ok(file_type) = entry.file_type() else { continue; };
        if !file_type.is_file() { continue; }
        let name = entry.file_name().to_string_lossy().to_string();
        let is_evidence = (name.starts_with("photo-") && name.ends_with(".jpg")) ||
            (name.starts_with("video-") && name.ends_with(".mp4"));
        if !is_evidence { continue; }
        if let Ok(metadata) = entry.metadata() {
            files.push(EvidenceFile { name, size: metadata.len() });
        }
    }
    files.sort_by(|a, b| b.name.cmp(&a.name));
    Ok(files)
}

#[tauri::command]
fn start_companion(
    app: AppHandle,
    allow_lan: bool,
    enable_gpsd: bool,
    relay_url: Option<String>,
    person_model_path: Option<String>,
    person_model_sha: Option<String>,
    incident_relay_url: Option<String>,
    state: State<'_, CompanionState>,
) -> Result<(), String> {
    let mut child = state.0.lock().map_err(|_| "Internal process lock failed")?;
    if child.is_some() {
        return Err("Gateway already running; stop it before applying new tracking options".into());
    }
    let mut args = command_args(&app, "serve", &["--port".into(), "8765".into()])?;
    if allow_lan { args.push("--listen-lan".into()); }
    if enable_gpsd { args.push("--enable-gpsd".into()); }
    if let Some(raw) = relay_url {
        let url = url::Url::parse(raw.trim()).map_err(|_| "Invalid relay URL")?;
        if !enable_gpsd || url.scheme() != "https" || url.domain().is_none() ||
           url.port_or_known_default() != Some(443) ||
           !url.username().is_empty() || url.password().is_some() ||
           url.path() != "/" || url.query().is_some() ||
           url.fragment().is_some() || raw.len() > 256 {
            return Err("Only a consenting GNSS user may configure a normal HTTPS relay hostname".into());
        }
        args.extend(["--relay-url".into(), raw.into()]);
    }
    if let Some(model) = person_model_path {
        let path=PathBuf::from(model.trim()).canonicalize()
            .map_err(|_| "Person model is missing or not accessible")?;
        if !path.is_file() || path.extension().and_then(|s| s.to_str()) != Some("onnx") {
            return Err("Choose a local ONNX model file".into());
        }
        let size=fs::metadata(&path).map_err(|e| e.to_string())?.len();
        if !(1024..=200_000_000).contains(&size) {
            return Err("Model must be 1 KB–200 MB".into());
        }
        let expected=person_model_sha.ok_or("A SHA256 model checksum is required")?;
        if expected.len()!=64 || !expected.bytes().all(|c| c.is_ascii_hexdigit()) {
            return Err("Invalid model SHA256 checksum".into());
        }
        args.extend([
            "--person-model".into(),path.to_string_lossy().to_string(),
            "--person-model-sha256".into(),expected.to_ascii_lowercase(),
        ]);
    } else if person_model_sha.is_some() {
        return Err("A model checksum without a model is not supported".into());
    }
    if let Some(relay) = incident_relay_url {
        if !args.iter().any(|p|p=="--person-model") {
            return Err("Incident push requires a verified person model".into());
        }
        let value=url::Url::parse(relay.trim()).map_err(|_| "Invalid incident relay URL")?;
        if value.scheme()!="https" || value.domain().is_none() ||
           value.port_or_known_default()!=Some(443) || !value.username().is_empty() ||
           value.password().is_some() || value.path()!="/" ||
           value.query().is_some() || value.fragment().is_some() || relay.len()>256 {
            return Err("Incident relay must be a normal HTTPS domain on port 443".into());
        }
        args.extend(["--incident-relay-url".into(),relay.into()]);
    }
    // The secret is independent of all phone keys and passed only via the
    // child environment. Never print, persist or expose it through IPC.
    let mut raw_secret = [0u8; 32];
    rand::rngs::OsRng.fill_bytes(&mut raw_secret);
    let secret: String = raw_secret.iter().map(|byte| format!("{byte:02x}")).collect();
    {
        let mut stored = state.6.lock().map_err(|_| "Health secret unavailable")?;
        *stored = Some(secret.clone());
    }
    let (mut rx, process) = app.shell().sidecar("laptopwatch-sidecar")
        .map_err(|e| e.to_string())?
        .args(args.clone())
        .env("LAPTOPWATCH_OWNER_BEARER", secret)
        .spawn().map_err(|e| e.to_string())?;
    {
        let mut config = state.1.lock().map_err(|_| "Process configuration unavailable")?;
        *config = Some(args);
    }
    if let Ok(mut history) = state.2.lock() { history.clear(); }
    if let Ok(mut last_error) = state.3.lock() { *last_error = None; }
    let generation = state.5.fetch_add(1, Ordering::SeqCst) + 1;
    state.4.store(false, Ordering::SeqCst);
    *child = Some(process);
    diagnostic(&app, "gateway_started");
    let handle = app.clone();
    tauri::async_runtime::spawn(async move {
        loop {
            // The receiver closes when its process ends. Do not parse or log
            // stdout/stderr: backend logs may contain owner private data.
            while let Some(event) = rx.recv().await {
                if let CommandEvent::Terminated(_) = event { break; }
            }
            let state = handle.state::<CompanionState>();
            if state.5.load(Ordering::SeqCst) != generation { break; }
            diagnostic(&handle, "gateway_exited");
            if let Ok(mut running) = state.0.lock() { running.take(); }
            if state.4.load(Ordering::SeqCst) { break; }
            let wait = match state.2.lock() {
                Ok(mut history) => restart_delay(&mut history, Instant::now()),
                Err(_) => { break; }
            };
            let Some(wait) = wait else {
                diagnostic(&handle, "gateway_restart_cap");
                if let Ok(mut error) = state.3.lock() {
                    *error = Some("Gateway restarted more than five times in 60 seconds. Manual restart required.".into());
                }
                break;
            }
            tokio::time::sleep(wait).await;
            if state.4.load(Ordering::SeqCst) ||
                state.5.load(Ordering::SeqCst) != generation { break; }
            let configuration = match state.1.lock() {
                Ok(config) => config.clone(),
                Err(_) => None,
            };
            let Some(configuration) = configuration else { break; };
            let secret = match state.6.lock() {
                Ok(stored) => stored.clone(),
                Err(_) => None,
            };
            let Some(secret) = secret else { break; };
            match handle.shell().sidecar("laptopwatch-sidecar")
                .and_then(|cmd| cmd.args(configuration)
                    .env("LAPTOPWATCH_OWNER_BEARER", secret).spawn()) {
                Ok((next_receiver, next_child)) => {
                    diagnostic(&handle, "gateway_restart");
                    if let Ok(mut running) = state.0.lock() {
                        *running = Some(next_child);
                    } else { break; }
                    rx = next_receiver;
                }
                Err(_) => {
                    diagnostic(&handle, "gateway_restart_failed");
                    if let Ok(mut error) = state.3.lock() {
                        *error = Some("Gateway restart failed. Check installed sidecar and logs.".into());
                    }
                    break;
                }
            }
        }
    });
    Ok(())
}

#[tauri::command]
fn stop_companion(app: AppHandle, state: State<'_, CompanionState>) -> Result<(), String> {
    diagnostic(&app, "gateway_stopped");
    state.4.store(true, Ordering::SeqCst);
    state.5.fetch_add(1, Ordering::SeqCst);
    if let Ok(mut config) = state.1.lock() { *config = None; }
    if let Ok(mut secret) = state.6.lock() { *secret = None; }
    let mut child = state.0.lock().map_err(|_| "Internal process lock failed")?;
    if let Some(process) = child.take() {
        process.kill().map_err(|e| e.to_string())?;
    }
    Ok(())
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.show();
                let _ = window.set_focus();
            }
        }))
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_autostart::init(tauri_plugin_autostart::MacosLauncher::LaunchAgent, None))
        .manage(CompanionState::default())
        .setup(|app| {
            let open = MenuItem::with_id(app, "show", "Open LaptopWatch", true, None::<&str>)?;
            let quit = MenuItem::with_id(app, "quit", "Quit LaptopWatch", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&open, &quit])?;
            let icon = app.default_window_icon().ok_or_else(|| std::io::Error::other("Missing application icon"))?.clone();
            TrayIconBuilder::new().icon(icon).menu(&menu)
                .on_menu_event(|app, event| match event.id.as_ref() {
                    "show" => {
                        if let Some(window) = app.get_webview_window("main") {
                            let _ = window.show();
                            let _ = window.set_focus();
                        }
                    }
                    "quit" => {
                        diagnostic(app, "owner_quit");
                        app.state::<CompanionState>().4.store(true, Ordering::SeqCst);
                        app.state::<CompanionState>().5.fetch_add(1, Ordering::SeqCst);
                        if let Ok(mut secret) = app.state::<CompanionState>().6.lock() { *secret = None; }
                        if let Ok(mut holder) = app.state::<CompanionState>().0.lock() {
                            if let Some(child) = holder.take() { let _ = child.kill(); }
                        }
                        app.exit(0);
                    }
                    _ => {}
                }).build(app)?;
            Ok(())
        })
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                let _ = window.hide();
            }
        })
        .invoke_handler(tauri::generate_handler![
            initialize_companion, suggest_lan_ip, create_pair_invitation,
            pending_pairs, paired_devices, dashboard_snapshot, companion_health,
            location_snapshot, audit_history,
            approve_pending, reject_pending, revoke_device,
            evidence_files, open_evidence_directory, start_companion, stop_companion
        ])
        .build(tauri::generate_context!())
        .expect("LaptopWatch desktop initialization failed")
        .run(|app, event| {
            if let RunEvent::Exit = event {
                app.state::<CompanionState>().4.store(true, Ordering::SeqCst);
                app.state::<CompanionState>().5.fetch_add(1, Ordering::SeqCst);
                if let Ok(mut secret) = app.state::<CompanionState>().6.lock() { *secret = None; }
                if let Ok(mut holder) = app.state::<CompanionState>().0.lock() {
                    if let Some(child) = holder.take() { let _ = child.kill(); }
                }
            }
        });
}

#[cfg(test)]
mod supervisor_tests {
    use super::*;

    #[test]
    fn crash_budget_allows_only_five_restarts_per_minute() {
        let t = Instant::now();
        let mut history = VecDeque::new();
        for i in 0u32..5 {
            assert_eq!(
                restart_delay(&mut history, t + Duration::from_secs(i as u64)),
                Some(Duration::from_secs(1 << i))
            );
        }
        assert_eq!(restart_delay(&mut history, t + Duration::from_secs(5)), None);
        // Fresh time window after 60 seconds permits an explicit later retry.
        assert_eq!(
            restart_delay(&mut history, t + Duration::from_secs(66)),
            Some(Duration::from_secs(1))
        );
    }
}
