use std::{
    path::{Component, Path, PathBuf},
    process::Command,
};

use anyhow::{Context, Result, bail, ensure};

use crate::{assets, defs};

fn compatibility_value<'a>(module_prop: &'a str, key: &str) -> Option<&'a str> {
    module_prop.lines().find_map(|line| {
        let (name, value) = line.split_once('=')?;
        (name.trim() == key).then(|| value.trim())
    })
}

fn validate_compatibility(module: &Path, id: &str) -> Result<()> {
    let module_prop = std::fs::read_to_string(module.join("module.prop"))?;
    let sdk = crate::android::utils::getprop("ro.build.version.sdk")
        .unwrap_or_default()
        .parse::<u32>()
        .unwrap_or_default();
    let ksu_version = defs::VERSION_CODE.trim().parse::<u32>().unwrap_or_default();

    for (key, actual, minimum) in [
        ("minApi", sdk, true),
        ("maxApi", sdk, false),
        ("minKsuVersion", ksu_version, true),
    ] {
        let Some(required) = compatibility_value(&module_prop, key) else {
            continue;
        };
        let required = required
            .parse::<u32>()
            .with_context(|| format!("invalid {key} in module {id}"))?;
        let compatible = if minimum {
            actual >= required
        } else {
            actual <= required
        };
        ensure!(
            compatible,
            "module {id} is incompatible: {key}={required}, current={actual}"
        );
    }
    Ok(())
}

fn run_tar(args: &[&str]) -> Result<std::process::Output> {
    Command::new(assets::BUSYBOX_PATH)
        .arg("tar")
        .args(args)
        .output()
        .context("run busybox tar")
}

fn validate_archive(archive: &Path) -> Result<()> {
    let output = run_tar(&["-tf", archive.to_string_lossy().as_ref()])?;
    ensure!(output.status.success(), "invalid module backup archive");
    for entry in String::from_utf8(output.stdout)?.lines() {
        let path = Path::new(entry);
        ensure!(
            !path.is_absolute()
                && path
                    .components()
                    .all(|part| matches!(part, Component::Normal(_))),
            "unsafe archive entry: {entry}"
        );
        ensure!(
            entry.starts_with("modules/")
                || entry == "modules"
                || entry.starts_with("module_configs/")
                || entry == "module_configs",
            "unexpected archive entry: {entry}"
        );
    }
    Ok(())
}

pub fn backup(archive: &Path) -> Result<()> {
    let archive = archive
        .canonicalize()
        .or_else(|_| {
            let parent = archive.parent().unwrap_or_else(|| Path::new("."));
            Ok::<_, std::io::Error>(
                parent.canonicalize()?.join(
                    archive
                        .file_name()
                        .ok_or_else(|| std::io::Error::from(std::io::ErrorKind::InvalidInput))?,
                ),
            )
        })?
        .to_string_lossy()
        .into_owned();
    let mut args = vec!["-cpf", archive.as_str(), "-C", defs::ADB_DIR, "modules"];
    if Path::new(defs::MODULE_CONFIG_DIR).exists() {
        args.extend(["-C", defs::WORKING_DIR, "module_configs"]);
    }
    let output = run_tar(&args)?;
    ensure!(
        output.status.success(),
        "module backup failed: {}",
        String::from_utf8_lossy(&output.stderr)
    );
    println!("{archive}");
    Ok(())
}

pub fn restore(archive: &Path, selected: &[String]) -> Result<()> {
    validate_archive(archive)?;
    for id in selected {
        super::validate_module_id(id)?;
    }

    let staging = tempfile::Builder::new()
        .prefix("module_restore_")
        .tempdir_in(defs::WORKING_DIR)?;
    let output = run_tar(&[
        "-xpf",
        archive.to_string_lossy().as_ref(),
        "-C",
        staging.path().to_string_lossy().as_ref(),
    ])?;
    ensure!(output.status.success(), "extract module backup failed");

    let modules = staging.path().join("modules");
    ensure!(modules.is_dir(), "backup contains no modules");
    std::fs::create_dir_all(defs::MODULE_UPDATE_DIR)?;
    for entry in std::fs::read_dir(modules)? {
        let entry = entry?;
        let id = entry.file_name().to_string_lossy().into_owned();
        super::validate_module_id(&id)?;
        if !selected.is_empty() && !selected.contains(&id) {
            continue;
        }
        ensure!(
            entry.path().join("module.prop").is_file(),
            "invalid module: {id}"
        );
        validate_compatibility(&entry.path(), &id)?;
        let target = PathBuf::from(defs::MODULE_UPDATE_DIR).join(&id);
        if target.exists() {
            std::fs::remove_dir_all(&target)?;
        }
        let status = Command::new(assets::BUSYBOX_PATH)
            .args(["cp", "-a"])
            .arg(entry.path())
            .arg(&target)
            .status()?;
        ensure!(status.success(), "restore module {id} failed");
        std::fs::write(target.join(defs::UPDATE_FILE_NAME), [])?;
        println!("{id}");
    }

    let configs = staging.path().join("module_configs");
    if configs.is_dir() {
        std::fs::create_dir_all(defs::MODULE_CONFIG_DIR)?;
        for entry in std::fs::read_dir(configs)? {
            let entry = entry?;
            let id = entry.file_name().to_string_lossy().into_owned();
            if selected.is_empty() || selected.contains(&id) {
                super::validate_module_id(&id)?;
                let target = PathBuf::from(defs::MODULE_CONFIG_DIR).join(id);
                if target.exists() {
                    std::fs::remove_dir_all(&target)?;
                }
                std::fs::rename(entry.path(), target)?;
            }
        }
    }
    Ok(())
}

pub fn inspect(archive: &Path) -> Result<()> {
    validate_archive(archive)?;
    let output = run_tar(&["-tf", archive.to_string_lossy().as_ref()])?;
    let mut ids = Vec::new();
    for entry in String::from_utf8(output.stdout)?.lines() {
        if let Some(rest) = entry.strip_prefix("modules/")
            && let Some(id) = rest.strip_suffix("/module.prop")
        {
            super::validate_module_id(id)?;
            ids.push(id.to_owned());
        }
    }
    if ids.is_empty() {
        bail!("backup contains no modules");
    }
    println!("{}", serde_json::to_string(&ids)?);
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::compatibility_value;

    #[test]
    fn reads_exact_compatibility_keys() {
        let properties = "id=test\nminApi=29\nminApiExtra=99\nmaxApi=35\n";
        assert_eq!(compatibility_value(properties, "minApi"), Some("29"));
        assert_eq!(compatibility_value(properties, "maxApi"), Some("35"));
        assert_eq!(compatibility_value(properties, "missing"), None);
    }
}
