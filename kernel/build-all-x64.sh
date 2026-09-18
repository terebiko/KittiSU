#!/bin/bash
set -euo pipefail

TARGETS="${1:-android12-5.10 android13-5.10 android13-5.15 android14-5.15 android14-6.1 android15-6.6 android16-6.12 android17-6.18}"
ROOT=$(cd "$(dirname "$0")" && pwd)
OUT="$ROOT/output/x86_64"
mkdir -p "$OUT"

for kmi in $TARGETS; do
    echo "========== Building x86_64 $kmi =========="
    export DDK_TARGET="$kmi" ARCH=x86_64
    ddk build -e ARCH=x86_64 -e CONFIG_KSU=m -e CONFIG_KSU_TRACEPOINT_HOOK=y -- -C "$ROOT"
    cp "$ROOT/kernelsu.ko" "$OUT/${kmi}_kernelsu.ko"
    llvm-strip -d "$OUT/${kmi}_kernelsu.ko"
done
