//! ARM64 kernel-image LKM capsule handling.
//!
//! The boot image patcher intentionally keeps image parsing separate from the
//! ramdisk patcher.  A capsule is appended only after validating the ARM64
//! Image header and the module ELF header; malformed inputs are rejected
//! without modifying the destination file.

use std::{fs, path::PathBuf};

use anyhow::{Context, Result, ensure};
use clap::Args;

use crate::lkm_image_btf::find_kernel_btf;

const ARM64_MAGIC: &[u8; 4] = b"ARM\x64";
const IMAGE_MAGIC_OFFSET: usize = 0x38;
const IMAGE_SIZE_OFFSET: usize = 0x10;
const PAGE: usize = 4096;
const CAPSULE_MAGIC: &[u8; 8] = b"KSULKM1\0";
const CAPSULE_VERSION: u32 = 1;

#[derive(Args, Debug, Clone)]
pub struct BootPatchV2Args {
    /// ARM64 Linux Image file
    pub boot: PathBuf,
    /// Relocatable KernelSU module (.ko)
    #[arg(long)]
    pub module: PathBuf,
    /// Output Image file
    #[arg(short, long)]
    pub output: PathBuf,
    /// Permit replacing an image that already contains a KSU capsule
    #[arg(long)]
    pub force: bool,
}

#[derive(Debug, Clone, Copy, Eq, PartialEq)]
pub struct ImageHeader {
    pub image_size: usize,
    pub text_offset: u64,
    pub page_size: usize,
}

#[derive(Debug, Clone, Copy, Eq, PartialEq)]
pub struct InjectionReport {
    pub original_size: usize,
    pub capsule_offset: usize,
    pub output_size: usize,
    pub btf_offset: Option<usize>,
}

fn u32_at(data: &[u8], offset: usize) -> Result<u32> {
    Ok(u32::from_le_bytes(
        data.get(offset..offset + 4)
            .context("truncated ARM64 Image header")?
            .try_into()
            .unwrap(),
    ))
}

fn u64_at(data: &[u8], offset: usize) -> Result<u64> {
    Ok(u64::from_le_bytes(
        data.get(offset..offset + 8)
            .context("truncated ARM64 Image header")?
            .try_into()
            .unwrap(),
    ))
}

pub fn parse_image_header(image: &[u8]) -> Result<ImageHeader> {
    ensure!(
        image.len() >= IMAGE_MAGIC_OFFSET + 4,
        "kernel image is too small"
    );
    ensure!(
        &image[IMAGE_MAGIC_OFFSET..IMAGE_MAGIC_OFFSET + 4] == ARM64_MAGIC,
        "not an ARM64 Linux Image"
    );
    let image_size =
        usize::try_from(u64_at(image, IMAGE_SIZE_OFFSET)?).context("image size overflows host")?;
    let text_offset = u64::from(u32_at(image, 8)?);
    let page_size = usize::try_from(u32_at(image, 12)?).context("invalid page size")?;
    ensure!(
        page_size.is_power_of_two() && page_size >= 4096,
        "invalid ARM64 Image page size"
    );
    ensure!(
        image_size == 0 || image_size <= image.len(),
        "declared image size exceeds file"
    );
    Ok(ImageHeader {
        image_size: if image_size == 0 {
            image.len()
        } else {
            image_size
        },
        text_offset,
        page_size,
    })
}

fn validate_module(module: &[u8]) -> Result<()> {
    ensure!(
        module.len() >= 64 && &module[..4] == b"\x7fELF",
        "module is not an ELF file"
    );
    ensure!(
        module[4] == 2 && module[5] == 1,
        "module must be a little-endian ELF64"
    );
    ensure!(
        u16::from_le_bytes([module[16], module[17]]) == 1,
        "module must be relocatable ELF"
    );
    ensure!(
        u16::from_le_bytes([module[18], module[19]]) == 0xb7,
        "module architecture is not AArch64"
    );
    Ok(())
}

fn align(value: usize, alignment: usize) -> Result<usize> {
    value
        .checked_add(alignment - 1)
        .map(|v| v / alignment * alignment)
        .context("image size overflow")
}

fn capsule(module: &[u8], bootstrap: &[u8], btf_data: Option<&[u8]>) -> Result<Vec<u8>> {
    // Header: magic, version, header size, total size, module offset/size,
    // bootstrap offset/size, BTF offset/size. All values are little-endian.
    let header_size = 8 + 9 * 8;
    let module_offset = align(header_size, 8)?;
    let bootstrap_offset = align(
        module_offset
            .checked_add(module.len())
            .context("capsule overflow")?,
        8,
    )?;
    let btf_offset = align(
        bootstrap_offset
            .checked_add(bootstrap.len())
            .context("capsule overflow")?,
        8,
    )?;
    let total = btf_offset
        .checked_add(btf_data.map_or(0, |data| data.len()))
        .context("capsule overflow")?;
    let mut out = vec![0; total];
    out[..8].copy_from_slice(CAPSULE_MAGIC);
    fn put(out: &mut [u8], slot: usize, value: u64) {
        out[slot..slot + 8].copy_from_slice(&value.to_le_bytes());
    }
    out[8..12].copy_from_slice(&CAPSULE_VERSION.to_le_bytes());
    out[12..16].copy_from_slice(&(u32::try_from(header_size)?).to_le_bytes());
    put(&mut out, 16, total as u64);
    put(&mut out, 24, module_offset as u64);
    put(&mut out, 32, module.len() as u64);
    put(&mut out, 40, bootstrap_offset as u64);
    put(&mut out, 48, bootstrap.len() as u64);
    put(&mut out, 56, btf_offset as u64);
    put(&mut out, 64, btf_data.map_or(0, |data| data.len()) as u64);
    out[module_offset..module_offset + module.len()].copy_from_slice(module);
    out[bootstrap_offset..bootstrap_offset + bootstrap.len()].copy_from_slice(bootstrap);
    if let Some(data) = btf_data {
        out[btf_offset..btf_offset + data.len()].copy_from_slice(data);
    }
    Ok(out)
}

/// Append a validated capsule to an ARM64 Image.  The capsule is consumed by
/// the bootstrap hook built for ARM64; its offsets are relative and therefore
/// remain valid after boot-image repacking.
pub fn inject_image(
    image: &[u8],
    module: &[u8],
    force: bool,
) -> Result<(Vec<u8>, InjectionReport)> {
    let header = parse_image_header(image)?;
    validate_module(module)?;
    let original_size = header.image_size;
    let mut output = image[..original_size].to_vec();
    if let Some(existing) = output
        .windows(CAPSULE_MAGIC.len())
        .position(|w| w == CAPSULE_MAGIC)
    {
        ensure!(
            force,
            "image already contains a KSU LKM capsule at 0x{existing:x}; use --force to replace it"
        );
        output.truncate(existing);
    }
    let capsule_offset = align(output.len(), header.page_size.max(PAGE))?;
    output.resize(capsule_offset, 0);
    let bootstrap = bootstrap_bytes();
    let btf_info = find_kernel_btf(&output)
        .ok()
        .map(|b| (b.file_offset, b.size));
    let btf_data = btf_info.and_then(|(offset, size)| output.get(offset..offset + size));
    let capsule = capsule(module, bootstrap, btf_data)?;
    output.extend_from_slice(&capsule);
    Ok((
        output.clone(),
        InjectionReport {
            original_size,
            capsule_offset,
            output_size: output.len(),
            btf_offset: btf_info.map(|(o, _)| o),
        },
    ))
}

#[cfg(target_arch = "aarch64")]
fn bootstrap_bytes() -> &'static [u8] {
    include_bytes!(concat!(env!("OUT_DIR"), "/lkm_image_bootstrap.o"))
}
#[cfg(not(target_arch = "aarch64"))]
fn bootstrap_bytes() -> &'static [u8] {
    b"KSU bootstrap is built for the Android ARM64 target"
}

pub fn patch_boot(args: &BootPatchV2Args) -> Result<()> {
    let image = fs::read(&args.boot).with_context(|| format!("read {}", args.boot.display()))?;
    let module =
        fs::read(&args.module).with_context(|| format!("read {}", args.module.display()))?;
    let (patched, report) = inject_image(&image, &module, args.force)?;
    fs::write(&args.output, patched).with_context(|| format!("write {}", args.output.display()))?;
    println!(
        "LKM capsule: 0x{:x}..0x{:x} ({} bytes)",
        report.capsule_offset,
        report.output_size,
        report.output_size - report.capsule_offset
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn rejects_non_image() {
        assert!(parse_image_header(b"not an image").is_err());
    }
    #[test]
    fn aligns() {
        assert_eq!(align(1, 4096).unwrap(), 4096);
    }
}
