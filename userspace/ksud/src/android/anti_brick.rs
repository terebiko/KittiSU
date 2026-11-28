use std::fs;
use std::io::{self, Read, Write};
use std::path::Path;
use std::time::{Duration, Instant};

use anyhow::{Result, bail};
use log::warn;
use regex_lite::Regex;

/// If this file exists, Anti-Brick protection is disabled by the user.
/// Absent by default => protection is ON by default.
pub const ANTI_BRICK_DISABLED_FLAG: &str = "/data/adb/ksu/.anti_brick_disabled";

/// Environment variable set ONLY by SovaSU Manager after the user explicitly
/// confirmed the 10-second Anti-Brick dialog in UI.
pub const ANTI_BRICK_CONFIRMED_ENV: &str = "SOVASU_ANTI_BRICK_CONFIRMED";

const CONFIRM_TIMEOUT_SECS: u64 = 10;

/// Critical Android / Qualcomm / MediaTek / Exynos / Tensor partitions that must
/// never be silently wiped, zeroed, or overwritten by untrusted scripts/modules.
const CRITICAL_PARTITIONS: &[&str] = &[
    "edl",
    "bootloader",
    "aboot",
    "abl",
    "xbl",
    "xbl_config",
    "xbl_ramdump",
    "sbl",
    "sbl1",
    "sbl2",
    "sbl3",
    "tz",
    "tzsdi",
    "ocdt",
    "cdt",
    "ddr",
    "rpm",
    "hyp",
    "devcfg",
    "devinfo",
    "keymaster",
    "keystore",
    "frp",
    "persist",
    "modem",
    "modemst1",
    "modemst2",
    "fsg",
    "fsc",
    "efs",
    "sec_efs",
    "nvram",
    "nvdata",
    "nvcfg",
    "protect1",
    "protect2",
    "proinfo",
    "seccfg",
    "lk",
    "lk2",
    "preloader",
    "preloader_raw",
    "tee",
    "scp",
    "sspm",
    "mcupm",
    "dpm",
    "spmfw",
    "pi_img",
    "boot_para",
    "misc",
    "para",
    "param",
    "apdp",
    "msadp",
    "cmnlib",
    "cmnlib64",
    "storsec",
    "uefisecapp",
    "qupfw",
    "shrm",
    "aop",
    "cpucp",
    "multiimgoem",
    "imagefv",
    "featenabler",
    "sec",
    "secdata",
    "vbmeta",
    "vbmeta_system",
    "vbmeta_vendor",
    "super",
    "userdata",
    "metadata",
    "dtbo",
    "dsp",
    "rawdump",
    "mmcblk0",
    "mmcblk0boot0",
    "mmcblk0boot1",
    "sda",
    "sdb",
    "sdc",
    "sdd",
    "sde",
    "sdf",
    "sdg",
    "nvme0n1",
];

#[derive(Debug, Clone)]
pub struct Finding {
    pub source: String,
    pub line_no: usize,
    pub reason: String,
    pub snippet: String,
}

pub fn is_enabled() -> bool {
    !Path::new(ANTI_BRICK_DISABLED_FLAG).exists()
}

pub fn is_preconfirmed_by_env() -> bool {
    std::env::var(ANTI_BRICK_CONFIRMED_ENV)
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false)
}

/// Scan arbitrary shell text (command or script) for brick/destructive operations.
pub fn scan_text(source: &str, content: &str) -> Vec<Finding> {
    let mut findings = Vec::new();

    let re_dd_of_block = Regex::new(r"(?i)\bdd\b[^\n]*\bof\s*=\s*['\x22]?/dev/(block|mtd|disk|mmc|sd|nvme)").unwrap();
    let re_dd_zero = Regex::new(r"(?i)\bdd\b[^\n]*\bif\s*=\s*['\x22]?/dev/(zero|urandom|random)\b").unwrap();
    let re_dd_safe_of = Regex::new(r"(?i)\bof\s*=\s*['\x22]?/(sdcard|storage|data/local/tmp|tmp|cache)/").unwrap();
    let re_redir_block = Regex::new(r"(?i)(>>?|tee\s+(-a\s+)?|cp\s+[^\n]+\s+|cat\s+[^\n]*>\s*|truncate\s+[^\n]*)/dev/(block|mtd|disk)").unwrap();
    let re_rm_root = Regex::new(r"(?i)\brm\b[^\n]*\s+-[a-zA-Z]*[rf][a-zA-Z]*[^\n]*\s+(/(\*|\s|$|;|&|\|)|/(data|system|vendor|product|odm|dev|persist|efs|metadata|apex|mnt/vendor/persist)(/(\*|\s|$|;|&|\|)|\s|$|;|&|\|))").unwrap();
    let re_rm_block = Regex::new(r"(?i)\brm\b[^\n]*/dev/(block|mtd)").unwrap();
    let re_wipe_tools = Regex::new(r"(?i)\b(wipefs|blkdiscard|mkfs(\.[a-z0-9]+)?|mke2fs|sgdisk|gdisk|fdisk|sfdisk|parted|flash_erase|nandwrite|ubiformat|shred)\b").unwrap();
    let re_fastboot_erase = Regex::new(r"(?i)\bfastboot\b[^\n]*\b(erase|format|flash)\b").unwrap();
    let re_edl_trigger = Regex::new(r"(?i)(\breboot\b[^\n]*\bedl\b|sys\.powerctl[^\n]*\bedl\b|\bsvc\s+power\s+reboot\s+edl\b|/proc/sysrq-trigger)").unwrap();
    let re_obfuscated_exec = Regex::new(r"(?i)(base64\s+(-d|--decode)|xxd\s+-r|openssl\s+enc\s+-d)[^\n]*\|\s*(sh|bash|ash|su|eval)\b").unwrap();
    let re_self_extract = Regex::new(r"(?i)(tail\s+[^\n]*\|\s*(gzip|gunzip|zcat|xz|bzip2|base64)|__ARCHIVE_BELOW__|\bgztmp(dir)?\b)").unwrap();
    let re_sensitive_dump = Regex::new(r"(?i)(\bDUMP_PARTITION\s*=\s*['\x22]?(ocdt|efs|nvram|modemst\d*|persist|frp|fsg)\b|\bdd\b[^\n]*\bif\s*=[^\n]*(ocdt|efs|nvram|modemst\d*|persist|frp|fsg))").unwrap();
    let re_module_hijack = Regex::new(r"(?i)(/data/adb/service\.d/[^\n]*\.sh|for\s+\w+\s+in\s+/data/adb/modules/\*|touch\s+['\x22]?\$[^\s'\x22]+/disable)").unwrap();
    let re_destructive_verb = Regex::new(r"(?i)(\bdd\b|\bof\s*=|\brm\b|\bwipe\b|\berase\b|\bformat\b|\bmkfs|\bzero\b|/dev/zero|/dev/urandom|>>?|\bcat\b|\becho\b|\btruncate\b|\bshred\b|\bblkdiscard\b|\bwipefs\b|\bsgdisk\b|\bfdisk\b|\bparted\b|\bflash_erase\b|\bnandwrite\b|\bcp\b|\btee\b)").unwrap();

    for (idx, raw_line) in content.lines().enumerate() {
        let line = raw_line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let line_no = idx + 1;
        let snippet: String = line.chars().take(140).collect();

        if re_dd_of_block.is_match(line) {
            findings.push(Finding {
                source: source.to_string(),
                line_no,
                reason: "dd write to raw block device (/dev/block)".to_string(),
                snippet: snippet.clone(),
            });
            continue;
        }

        if re_dd_zero.is_match(line) && !re_dd_safe_of.is_match(line) {
            findings.push(Finding {
                source: source.to_string(),
                line_no,
                reason: "dd zero/random overwrite (if=/dev/zero or urandom)".to_string(),
                snippet: snippet.clone(),
            });
            continue;
        }

        if re_redir_block.is_match(line) {
            findings.push(Finding {
                source: source.to_string(),
                line_no,
                reason: "Direct write/redirect to block device (/dev/block)".to_string(),
                snippet: snippet.clone(),
            });
            continue;
        }

        if re_rm_root.is_match(line) || re_rm_block.is_match(line) {
            findings.push(Finding {
                source: source.to_string(),
                line_no,
                reason: "Destructive rm targeting root/system/data/block partition".to_string(),
                snippet: snippet.clone(),
            });
            continue;
        }

        if re_wipe_tools.is_match(line) || re_fastboot_erase.is_match(line) {
            findings.push(Finding {
                source: source.to_string(),
                line_no,
                reason: "Disk/partition wipe or format command".to_string(),
                snippet: snippet.clone(),
            });
            continue;
        }

        if re_edl_trigger.is_match(line) {
            findings.push(Finding {
                source: source.to_string(),
                line_no,
                reason: "Forced EDL / sysrq-trigger command".to_string(),
                snippet: snippet.clone(),
            });
            continue;
        }

        if re_obfuscated_exec.is_match(line) {
            findings.push(Finding {
                source: source.to_string(),
                line_no,
                reason: "Obfuscated payload piped directly to shell (base64/xxd | sh)".to_string(),
                snippet: snippet.clone(),
            });
            continue;
        }

        if re_self_extract.is_match(line) {
            findings.push(Finding {
                source: source.to_string(),
                line_no,
                reason: "Self-extracting dropper / hidden compressed payload (gzexe)".to_string(),
                snippet: snippet.clone(),
            });
            continue;
        }

        if re_sensitive_dump.is_match(line) {
            findings.push(Finding {
                source: source.to_string(),
                line_no,
                reason: "Sensitive partition dump/exfiltration (ocdt/efs/nvram/persist)".to_string(),
                snippet: snippet.clone(),
            });
            continue;
        }

        if re_module_hijack.is_match(line) {
            findings.push(Finding {
                source: source.to_string(),
                line_no,
                reason: "Suspicious boot persistence or mass module disable (/data/adb/service.d)".to_string(),
                snippet: snippet.clone(),
            });
            continue;
        }

        // Check for critical partition names in a destructive context
        if re_destructive_verb.is_match(line) {
            // Skip obvious read-only dd backup: `dd if=/dev/block/by-name/boot of=/sdcard/...`
            let is_readonly_backup = line.to_ascii_lowercase().contains("dd ")
                && re_dd_safe_of.is_match(line)
                && !re_dd_zero.is_match(line);
            if !is_readonly_backup
                && let Some(part) = find_critical_partition(line)
            {
                findings.push(Finding {
                    source: source.to_string(),
                    line_no,
                    reason: format!("Destructive operation targeting critical partition '{part}'"),
                    snippet,
                });
            }
        }
    }

    findings
}

fn find_critical_partition(line: &str) -> Option<&'static str> {
    let lower = line.to_ascii_lowercase();
    for &part in CRITICAL_PARTITIONS {
        // Match partition token bounded by path separators, whitespace, quotes, or A/B suffix (_a / _b)
        let mut start = 0;
        while let Some(pos) = lower[start..].find(part) {
            let abs_pos = start + pos;
            let before_ok = abs_pos == 0 || {
                let b = lower.as_bytes()[abs_pos - 1];
                !b.is_ascii_alphanumeric() && b != b'_'
            };
            let end_pos = abs_pos + part.len();
            let after_ok = end_pos == lower.len() || {
                let rest = &lower[end_pos..];
                let first = rest.as_bytes()[0];
                if !first.is_ascii_alphanumeric() && first != b'_' {
                    true
                } else {
                    // Allow _a / _b A/B slot suffixes (e.g., abl_a, xbl_b, tz_a)
                    (rest.starts_with("_a") || rest.starts_with("_b"))
                        && (rest.len() == 2
                            || (!rest.as_bytes()[2].is_ascii_alphanumeric()
                                && rest.as_bytes()[2] != b'_'))
                }
            };
            if before_ok && after_ok {
                // Require path-like context (/dev/block, by-name, /persist, /efs, of=, partition)
                // or explicit partition-named target to avoid false positives on words like "sec" or "para"
                let short_word = part.len() <= 4;
                let has_device_context = lower.contains("/dev/")
                    || lower.contains("by-name")
                    || lower.contains("by-partlabel")
                    || lower.contains("of=")
                    || lower.contains("block")
                    || lower.contains("/persist")
                    || lower.contains("/efs")
                    || lower.contains("partition");
                if !short_word || has_device_context {
                    return Some(part);
                }
            }
            start = abs_pos + 1;
        }
    }
    None
}

const HEAD_SCAN_BYTES: usize = 256 * 1024;
const LARGE_SCRIPT_THRESHOLD: u64 = 2 * 1024 * 1024;

fn find_subslice(haystack: &[u8], needle: &[u8]) -> Option<usize> {
    if needle.is_empty() || haystack.len() < needle.len() {
        return None;
    }
    haystack.windows(needle.len()).position(|w| w == needle)
}

/// Inspect raw bytes of a shell script (handles large files, embedded gzexe/ELF payloads,
/// and safely peeks into a gzip-compressed layer in memory).
pub fn scan_script_bytes(source: &str, bytes: &[u8], total_len: u64) -> Vec<Finding> {
    let mut findings = Vec::new();
    let gzip_pos = find_subslice(bytes, &[0x1f, 0x8b, 0x08]);
    let elf_pos = find_subslice(bytes, b"\x7fELF");
    let null_count = bytes.iter().filter(|&&b| b == 0).count();

    if total_len > LARGE_SCRIPT_THRESHOLD || gzip_pos.is_some() || elf_pos.is_some() || null_count > 16 {
        let kind = match (gzip_pos.is_some(), elf_pos.is_some()) {
            (true, _) => "embedded gzip archive (gzexe)",
            (false, true) => "embedded ELF binary",
            _ if total_len > LARGE_SCRIPT_THRESHOLD => "oversized shell script (>2 MB)",
            _ => "embedded binary data",
        };
        findings.push(Finding {
            source: source.to_string(),
            line_no: 1,
            reason: format!("Suspicious hidden payload in script: {kind} ({} KB)", total_len / 1024),
            snippet: format!("size={} bytes, gzip={}, elf={}, nulls={}", total_len, gzip_pos.is_some(), elf_pos.is_some(), null_count),
        });
    }

    // Scan the readable text prefix (stop before embedded binary if present)
    let text_end = gzip_pos.or(elf_pos).unwrap_or(bytes.len()).min(bytes.len());
    let text = String::from_utf8_lossy(&bytes[..text_end]);
    findings.extend(scan_text(source, &text));

    findings
}

/// Scan a shell script file on disk (never skips oversized scripts — reads up to 256 KB head).
pub fn scan_script_file(path: &Path) -> Vec<Finding> {
    let Ok(meta) = fs::metadata(path) else {
        return Vec::new();
    };
    if !meta.is_file() {
        return Vec::new();
    }
    let total_len = meta.len();
    let Ok(file) = fs::File::open(path) else {
        return Vec::new();
    };
    let mut buf = Vec::new();
    if file.take(HEAD_SCAN_BYTES as u64).read_to_end(&mut buf).is_err() {
        return Vec::new();
    }
    scan_script_bytes(&path.display().to_string(), &buf, total_len)
}

/// Scan a command line passed to `su -c` or `su <exec> <args>`.
/// Also follows direct script paths like `sh /sdcard/test.sh` or `/data/local/tmp/run.sh`.
pub fn scan_command_line(cmd: &str) -> Vec<Finding> {
    let mut findings = scan_text("command", cmd);
    for token in cmd.split_whitespace() {
        let cleaned = token.trim_matches(|c| c == '\'' || c == '"' || c == ';');
        if cleaned.ends_with(".sh") && cleaned.starts_with('/') {
            findings.extend(scan_script_file(Path::new(cleaned)));
        }
    }
    findings
}

/// Scan all `.sh` files and `update-binary` inside a module ZIP archive.
pub fn scan_module_zip(zip_path: &str) -> Vec<Finding> {
    let mut findings = Vec::new();
    let Ok(file) = fs::File::open(zip_path) else {
        return findings;
    };
    let Ok(mut archive) = zip::ZipArchive::new(file) else {
        return findings;
    };
    for i in 0..archive.len() {
        let Ok(entry) = archive.by_index(i) else {
            continue;
        };
        if entry.is_dir() {
            continue;
        }
        let name = entry.name().to_string();
        let lower = name.to_ascii_lowercase();
        let should_scan = lower.ends_with(".sh")
            || lower.ends_with("/update-binary")
            || lower == "update-binary";
        if !should_scan {
            continue;
        }
        let total_len = entry.size();
        let mut buf = Vec::new();
        if entry.take(HEAD_SCAN_BYTES as u64).read_to_end(&mut buf).is_ok() {
            findings.extend(scan_script_bytes(&name, &buf, total_len));
        }
    }
    findings
}

/// Enforce Anti-Brick policy for the given findings.
/// - If protection is disabled or `SOVASU_ANTI_BRICK_CONFIRMED=1` is set: allow.
/// - If interactive TTY: ask user with a strict 10-second timeout; no confirm => STOP!
/// - If non-interactive (background app / boot script): STOP immediately!
pub fn enforce(context_label: &str, findings: &[Finding]) -> Result<()> {
    if findings.is_empty() || !is_enabled() || is_preconfirmed_by_env() {
        return Ok(());
    }

    let mut stderr = io::stderr();
    let _ = writeln!(
        stderr,
        "\n\x1b[1;31m============================================================\x1b[0m"
    );
    let _ = writeln!(
        stderr,
        "\x1b[1;31m[SovaSU Anti-Brick] DANGEROUS OPERATION DETECTED ({context_label})\x1b[0m"
    );
    for (idx, f) in findings.iter().take(8).enumerate() {
        let _ = writeln!(
            stderr,
            "  {}. [{}:L{}] {}",
            idx + 1,
            f.source,
            f.line_no,
            f.reason
        );
        let _ = writeln!(stderr, "     > {}", f.snippet);
    }
    if findings.len() > 8 {
        let _ = writeln!(stderr, "  ... and {} more finding(s)", findings.len() - 8);
    }
    let _ = writeln!(
        stderr,
        "\x1b[1;31m============================================================\x1b[0m"
    );

    let is_tty = unsafe { libc::isatty(libc::STDIN_FILENO) == 1 };
    if !is_tty {
        let _ = writeln!(
            stderr,
            "\x1b[1;31m[SovaSU Anti-Brick] Non-interactive shell — BLOCKED automatically to prevent device brick!\x1b[0m"
        );
        warn!(
            "Anti-Brick blocked non-interactive execution ({context_label}): {} finding(s)",
            findings.len()
        );
        bail!(
            "Blocked by SovaSU Anti-Brick Guard ({} dangerous pattern(s) detected)",
            findings.len()
        );
    }

    let _ = write!(
        stderr,
        "\x1b[1;33mDo you REALLY need to run this? Type 'yes' and press Enter within {CONFIRM_TIMEOUT_SECS}s (auto-STOP in {CONFIRM_TIMEOUT_SECS}s): \x1b[0m"
    );
    let _ = stderr.flush();

    match read_line_with_timeout(Duration::from_secs(CONFIRM_TIMEOUT_SECS)) {
        Some(answer) => {
            let trimmed = answer.trim();
            if trimmed.eq_ignore_ascii_case("yes") || trimmed.eq_ignore_ascii_case("y") {
                let _ = writeln!(
                    stderr,
                    "\x1b[1;32m[SovaSU Anti-Brick] Confirmed by user — proceeding.\x1b[0m"
                );
                Ok(())
            } else {
                let _ = writeln!(
                    stderr,
                    "\x1b[1;31m[SovaSU Anti-Brick] Not confirmed — STOPPED!\x1b[0m"
                );
                bail!("Cancelled by SovaSU Anti-Brick Guard")
            }
        }
        None => {
            let _ = writeln!(
                stderr,
                "\n\x1b[1;31m[SovaSU Anti-Brick] Timed out ({CONFIRM_TIMEOUT_SECS}s) without confirmation — STOPPED!\x1b[0m"
            );
            bail!(
                "Timed out ({CONFIRM_TIMEOUT_SECS}s) — blocked by SovaSU Anti-Brick Guard"
            )
        }
    }
}

fn read_line_with_timeout(timeout: Duration) -> Option<String> {
    let deadline = Instant::now() + timeout;
    let mut buf = Vec::new();
    let mut byte = [0u8; 1];

    loop {
        let now = Instant::now();
        if now >= deadline {
            return None;
        }
        let remaining_ms = (deadline - now).as_millis().min(i32::MAX as u128) as i32;
        let mut pfd = libc::pollfd {
            fd: libc::STDIN_FILENO,
            events: libc::POLLIN,
            revents: 0,
        };
        let rc = unsafe { libc::poll(&mut pfd, 1, remaining_ms) };
        if rc <= 0 {
            return None;
        }
        let n = unsafe {
            libc::read(
                libc::STDIN_FILENO,
                byte.as_mut_ptr().cast::<libc::c_void>(),
                1,
            )
        };
        if n <= 0 {
            return None;
        }
        if byte[0] == b'\n' {
            break;
        }
        if byte[0] != b'\r' {
            buf.push(byte[0]);
        }
        if buf.len() >= 64 {
            break;
        }
    }
    Some(String::from_utf8_lossy(&buf).into_owned())
}
