//! BTF discovery used by the boot-image LKM injector.
//!
//! This parser deliberately works on an arbitrary byte slice.  Kernel images
//! contain padding and sometimes more than one BTF blob, so callers must not
//! assume that the first `0x9feb` marker is valid.

use std::collections::BTreeSet;

use anyhow::{Result, ensure};

const MAGIC: u16 = 0xeb9f;
const VERSION: u8 = 1;
const HEADER_SIZE: usize = 24;
const PTR_SIZE: u64 = 8;

const INT: u8 = 1;
const PTR: u8 = 2;
const ARRAY: u8 = 3;
const STRUCT: u8 = 4;
const UNION: u8 = 5;
const ENUM: u8 = 6;
const FWD: u8 = 7;
const TYPEDEF: u8 = 8;
const VOLATILE: u8 = 9;
const CONST: u8 = 10;
const RESTRICT: u8 = 11;
const FUNC: u8 = 12;
const FUNC_PROTO: u8 = 13;
const VAR: u8 = 14;
const DATASEC: u8 = 15;
const FLOAT: u8 = 16;
const DECL_TAG: u8 = 17;
const TYPE_TAG: u8 = 18;
const ENUM64: u8 = 19;

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub struct LoadInfoLayout {
    pub structure_size: u64,
    pub hdr_offset: u64,
    pub len_offset: u64,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct KernelBtf {
    pub file_offset: usize,
    pub size: usize,
    pub type_count: usize,
    pub load_info: Option<LoadInfoLayout>,
}

#[derive(Clone, Copy)]
struct Type {
    kind: u8,
    vlen: u16,
    flag: bool,
    name: u32,
    value: u32,
    payload: usize,
}

pub struct BtfCandidate<'a> {
    data: &'a [u8],
    offset: usize,
    end: usize,
    strings: (usize, usize),
    types: Vec<Type>,
}

fn u16_at(data: &[u8], p: usize) -> Option<u16> {
    data.get(p..p + 2)?.try_into().ok().map(u16::from_le_bytes)
}
fn u32_at(data: &[u8], p: usize) -> Option<u32> {
    data.get(p..p + 4)?.try_into().ok().map(u32::from_le_bytes)
}

fn payload(kind: u8, vlen: u16) -> Option<usize> {
    let n = usize::from(vlen);
    Some(match kind {
        INT | VAR | DECL_TAG => 4,
        PTR | FWD | TYPEDEF | VOLATILE | CONST | RESTRICT | FUNC | FLOAT | TYPE_TAG => 0,
        ARRAY => 12,
        STRUCT | UNION | DATASEC | ENUM64 => n.checked_mul(12)?,
        ENUM | FUNC_PROTO => n.checked_mul(8)?,
        _ => return None,
    })
}

impl<'a> BtfCandidate<'a> {
    fn parse(data: &'a [u8], offset: usize) -> Option<Self> {
        let magic = u16_at(data, offset)?;
        let version = *data.get(offset + 2)?;
        let flags = *data.get(offset + 3)?;
        let header_len = usize::try_from(u32_at(data, offset + 4)?).ok()?;
        if magic != MAGIC || version != VERSION || flags != 0 || header_len < HEADER_SIZE {
            return None;
        }
        let header_end = offset.checked_add(header_len)?;
        let type_off = usize::try_from(u32_at(data, offset + 8)?).ok()?;
        let type_len = usize::try_from(u32_at(data, offset + 12)?).ok()?;
        let str_off = usize::try_from(u32_at(data, offset + 16)?).ok()?;
        let str_len = usize::try_from(u32_at(data, offset + 20)?).ok()?;
        let types_start = header_end.checked_add(type_off)?;
        let types_end = types_start.checked_add(type_len)?;
        let strings_start = header_end.checked_add(str_off)?;
        let strings_end = strings_start.checked_add(str_len)?;
        if header_end > data.len()
            || types_end > data.len()
            || strings_end > data.len()
            || str_len == 0
            || types_start.max(strings_start) < types_end.min(strings_end)
            || data.get(strings_start) != Some(&0)
            || data.get(strings_end.checked_sub(1)?) != Some(&0)
        {
            return None;
        }
        let mut types = Vec::new();
        let mut p = types_start;
        while p < types_end {
            let name = u32_at(data, p)?;
            let info = u32_at(data, p + 4)?;
            let value = u32_at(data, p + 8)?;
            let kind = u8::try_from(info >> 24 & 0x1f).ok()?;
            let vlen = u16::try_from(info & 0xffff).ok()?;
            let next = p.checked_add(12)?.checked_add(payload(kind, vlen)?)?;
            if next > types_end {
                return None;
            }
            types.push(Type {
                kind,
                vlen,
                flag: info >> 31 != 0,
                name,
                value,
                payload: p + 12,
            });
            p = next;
        }
        if p != types_end {
            return None;
        }
        let candidate = Self {
            data,
            offset,
            end: types_end.max(strings_end),
            strings: (strings_start, strings_end),
            types,
        };
        candidate.validate().then_some(candidate)
    }

    fn string(&self, offset: u32) -> Option<&'a str> {
        let p = self.strings.0.checked_add(usize::try_from(offset).ok()?)?;
        let bytes = self.data.get(p..self.strings.1)?;
        let end = bytes.iter().position(|b| *b == 0)?;
        std::str::from_utf8(&bytes[..end]).ok()
    }
    fn ty(&self, id: u32) -> Option<&Type> {
        if id == 0 {
            None
        } else {
            self.types.get(usize::try_from(id).ok()?.checked_sub(1)?)
        }
    }
    fn valid_id(&self, id: u32) -> bool {
        id == 0 || usize::try_from(id).is_ok_and(|n| n <= self.types.len())
    }
    fn validate(&self) -> bool {
        self.types.iter().all(|t| {
            self.string(t.name).is_some()
                && match t.kind {
                    PTR | TYPEDEF | VOLATILE | CONST | RESTRICT | VAR | DECL_TAG | TYPE_TAG => {
                        self.valid_id(t.value)
                    }
                    ARRAY => {
                        u32_at(self.data, t.payload).is_some_and(|e| self.valid_id(e))
                            && u32_at(self.data, t.payload + 4).is_some_and(|i| self.valid_id(i))
                    }
                    STRUCT | UNION => (0..usize::from(t.vlen)).all(|i| {
                        let p = t.payload + i * 12;
                        u32_at(self.data, p).is_some_and(|n| self.string(n).is_some())
                            && u32_at(self.data, p + 4).is_some_and(|id| self.valid_id(id))
                    }),
                    ENUM => (0..usize::from(t.vlen)).all(|i| {
                        u32_at(self.data, t.payload + i * 8)
                            .is_some_and(|n| self.string(n).is_some())
                    }),
                    FUNC => self.ty(t.value).is_some_and(|p| p.kind == FUNC_PROTO),
                    FUNC_PROTO => (0..usize::from(t.vlen)).all(|i| {
                        let p = t.payload + i * 8;
                        u32_at(self.data, p).is_some_and(|n| self.string(n).is_some())
                            && u32_at(self.data, p + 4).is_some_and(|id| self.valid_id(id))
                    }),
                    _ => true,
                }
        })
    }
    fn strip(&self, mut id: u32) -> Option<u32> {
        for _ in 0..self.types.len() {
            let t = self.ty(id)?;
            if matches!(t.kind, TYPEDEF | VOLATILE | CONST | RESTRICT | TYPE_TAG) {
                id = t.value;
            } else {
                return Some(id);
            }
        }
        None
    }
    fn type_size(&self, id: u32) -> Option<u64> {
        self.size_inner(id, 0)
    }
    fn size_inner(&self, id: u32, depth: usize) -> Option<u64> {
        if depth > 64 {
            return None;
        }
        let t = self.ty(self.strip(id)?)?;
        Some(match t.kind {
            INT | STRUCT | UNION | ENUM | FLOAT | ENUM64 => u64::from(t.value),
            PTR => PTR_SIZE,
            ARRAY => self
                .size_inner(u32_at(self.data, t.payload)?, depth + 1)?
                .checked_mul(u64::from(u32_at(self.data, t.payload + 8)?))?,
            _ => return None,
        })
    }
    fn is_load_info_ptr(&self, id: u32) -> bool {
        let Some(ptr) = self.ty(self.strip(id).unwrap_or(0)) else {
            return false;
        };
        if ptr.kind != PTR {
            return false;
        };
        let Some(st) = self.ty(self.strip(ptr.value).unwrap_or(0)) else {
            return false;
        };
        st.kind == STRUCT && self.string(st.name) == Some("load_info")
    }
    fn load_info(&self) -> Result<Option<LoadInfoLayout>> {
        let mut found = BTreeSet::new();
        let mut named = false;
        for st in self
            .types
            .iter()
            .filter(|t| t.kind == STRUCT && self.string(t.name) == Some("load_info"))
        {
            named = true;
            let mut hdr = None;
            let mut len = None;
            for i in 0..usize::from(st.vlen) {
                let p = st.payload + i * 12;
                let n = self
                    .string(
                        u32_at(self.data, p).ok_or_else(|| anyhow::anyhow!("truncated member"))?,
                    )
                    .ok_or_else(|| anyhow::anyhow!("invalid member name"))?;
                if n != "hdr" && n != "len" {
                    continue;
                }
                ensure!(
                    self.type_size(
                        u32_at(self.data, p + 4)
                            .ok_or_else(|| anyhow::anyhow!("truncated member type"))?
                    ) == Some(PTR_SIZE),
                    "load_info.{n} is not pointer-sized"
                );
                let raw = u32_at(self.data, p + 8)
                    .ok_or_else(|| anyhow::anyhow!("truncated member offset"))?;
                ensure!(!st.flag || raw >> 24 == 0, "bitfield load_info member");
                let bits = if st.flag { raw & 0x00ff_ffff } else { raw };
                ensure!(bits % 8 == 0, "unaligned load_info member");
                if n == "hdr" {
                    hdr = Some(u64::from(bits / 8));
                } else {
                    len = Some(u64::from(bits / 8));
                }
            }
            if let Some((hdr_offset, len_offset)) = hdr.zip(len) {
                let layout = LoadInfoLayout {
                    structure_size: u64::from(st.value),
                    hdr_offset,
                    len_offset,
                };
                ensure!(
                    hdr_offset + PTR_SIZE <= layout.structure_size
                        && len_offset + PTR_SIZE <= layout.structure_size,
                    "load_info member outside structure"
                );
                found.insert(layout);
            }
        }
        if !named {
            return Ok(None);
        }
        ensure!(found.len() <= 1, "conflicting load_info layouts");
        Ok(found.into_iter().next())
    }
    fn validate_abi(&self) -> Result<()> {
        for t in self
            .types
            .iter()
            .filter(|t| t.kind == FUNC && self.string(t.name) == Some("load_module"))
        {
            let p = self
                .ty(t.value)
                .ok_or_else(|| anyhow::anyhow!("load_module prototype missing"))?;
            ensure!(
                p.kind == FUNC_PROTO && p.vlen == 3,
                "load_module ABI has {} parameters",
                p.vlen
            );
            ensure!(
                self.is_load_info_ptr(
                    u32_at(self.data, p.payload + 4)
                        .ok_or_else(|| anyhow::anyhow!("truncated load_module prototype"))?
                ),
                "load_module first parameter is not load_info *"
            );
        }
        Ok(())
    }
    pub fn file_offset(&self) -> usize {
        self.offset
    }
    pub fn size(&self) -> usize {
        self.end - self.offset
    }
    pub fn to_kernel_btf(&self) -> Result<KernelBtf> {
        self.validate_abi()?;
        Ok(KernelBtf {
            file_offset: self.offset,
            size: self.size(),
            type_count: self.types.len(),
            load_info: self.load_info()?,
        })
    }
}

pub fn find_btf_candidates(image: &[u8]) -> Vec<BtfCandidate<'_>> {
    let magic = [0x9f, 0xeb, 1, 0];
    let mut out = Vec::new();
    let mut at = 0;
    while let Some(rel) = image
        .get(at..)
        .and_then(|s| s.windows(4).position(|w| w == magic))
    {
        let p = at + rel;
        if let Some(c) = BtfCandidate::parse(image, p) {
            out.push(c)
        }
        at = p + 1;
    }
    out
}

pub fn find_kernel_btf(image: &[u8]) -> Result<KernelBtf> {
    let candidates = find_btf_candidates(image);
    ensure!(
        candidates.len() == 1,
        "expected one valid kernel BTF blob, found {}",
        candidates.len()
    );
    candidates[0].to_kernel_btf()
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn rejects_short_blob() {
        assert!(find_btf_candidates(&[0x9f, 0xeb, 1, 0]).is_empty());
    }
    #[test]
    fn finds_two_blobs() {
        let mut b = vec![
            0x9f, 0xeb, 1, 0, 24, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0,
        ];
        b.extend_from_slice(&b.clone());
        assert_eq!(find_btf_candidates(&b).len(), 2);
    }
}
