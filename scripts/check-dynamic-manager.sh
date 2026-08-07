#!/bin/sh
set -eu

test "$(grep -c '^#define EXPECTED_SIZE_' kernel/manager/manager_sign.h)" -eq 1
test "$(grep -c '^#define EXPECTED_HASH_' kernel/manager/manager_sign.h)" -eq 1
grep -q 'KSU_SIGNATURE_INDEX_DYNAMIC_MANAGER' kernel/manager/apk_sign.c
grep -q 'set-apk' manager/app/src/main/java/anhiutangerinee/kittisu/ui/util/KsuCli.kt
grep -q 'dynamic_manager::booted_load()' userspace/ksud/src/android/late_load/mod.rs
sed -n '/struct ksu_get_info_cmd get_info()/,/^}/p' manager/app/src/main/cpp/ksu.c |
    grep -q 'KSU_IOCTL_GET_INFO_LEGACY'
grep -q '\.cmd = KSU_IOCTL_GET_INFO_LEGACY' kernel/supercall/dispatch.c
