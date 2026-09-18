#!/bin/bash
set -euo pipefail

TARGETS="${1:-android12-5.10 android13-5.10 android13-5.15 android14-5.15 android14-6.1 android15-6.6 android16-6.12 android17-6.18}"

for kmi in $TARGETS; do
    src="/opt/ddk/src/$kmi"
    out="/opt/ddk/kdir-x86_64/$kmi"
    test -d "$src" || { echo "missing DDK source: $src" >&2; exit 1; }
    mkdir -p "$out"
    make -C "$src" O="$out" ARCH=x86_64 gki_defconfig
    scripts/config --file "$out/.config" -d LTO_CLANG -e LTO_NONE -d LTO_CLANG_THIN -d LTO_CLANG_FULL -d THINLTO
    if [ "$kmi" = "android16-6.12" ] || [ "$kmi" = "android17-6.18" ]; then
        scripts/config --file "$out/.config" -e CFI_ICALL_NORMALIZE_INTEGERS
    fi
    make -C "$src" O="$out" ARCH=x86_64 modules_prepare
done
