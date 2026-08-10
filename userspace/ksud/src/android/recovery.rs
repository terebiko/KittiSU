use std::path::{Path, PathBuf};

use anyhow::Result;
use serde::{Deserialize, Serialize};

use crate::defs;

const FAILURE_LIMIT: u8 = 2;

#[derive(Default, Deserialize, Serialize)]
struct BootState {
    failures: u8,
    modules: Vec<String>,
}

fn state_path() -> PathBuf {
    Path::new(defs::WORKING_DIR).join("boot_recovery.json")
}

fn pending_path() -> PathBuf {
    Path::new(defs::WORKING_DIR).join("boot_pending.json")
}

fn read(path: &Path) -> BootState {
    std::fs::read(path)
        .ok()
        .and_then(|data| serde_json::from_slice(&data).ok())
        .unwrap_or_default()
}

fn write(path: &Path, state: &BootState) -> Result<()> {
    let temporary = path.with_extension("tmp");
    std::fs::write(&temporary, serde_json::to_vec_pretty(state)?)?;
    std::fs::rename(temporary, path)?;
    Ok(())
}

fn updated_modules() -> Vec<String> {
    std::fs::read_dir(defs::MODULE_UPDATE_DIR)
        .into_iter()
        .flatten()
        .flatten()
        .filter_map(|entry| entry.file_name().into_string().ok())
        .filter(|id| crate::android::module::validate_module_id(id).is_ok())
        .collect()
}

pub fn begin_boot() -> Result<()> {
    std::fs::create_dir_all(defs::WORKING_DIR)?;
    let previous = read(&pending_path());
    let mut state = read(&state_path());
    if !previous.modules.is_empty() {
        state.failures = state.failures.saturating_add(1);
        state.modules = previous.modules;
        if state.failures >= FAILURE_LIMIT {
            for id in &state.modules {
                let module = Path::new(defs::MODULE_DIR).join(id);
                if module.is_dir() {
                    std::fs::write(module.join(defs::DISABLE_FILE_NAME), [])?;
                    log::warn!("disabled {id} after {} incomplete boots", state.failures);
                }
            }
        }
        write(&state_path(), &state)?;
    }

    write(
        &pending_path(),
        &BootState {
            failures: state.failures,
            modules: updated_modules(),
        },
    )
}

pub fn boot_completed() {
    let _ = std::fs::remove_file(pending_path());
    let _ = std::fs::remove_file(state_path());
}

pub fn show() -> Result<()> {
    println!("{}", serde_json::to_string_pretty(&read(&state_path()))?);
    Ok(())
}

pub fn reset() -> Result<()> {
    for path in [pending_path(), state_path()] {
        if path.exists() {
            std::fs::remove_file(path)?;
        }
    }
    Ok(())
}
