/* SPDX-License-Identifier: GPL-2.0-only */
/*
 * The Rust injector stores this object beside the module capsule.  The final
 * kernel-specific branch/call relocations are deliberately left to the
 * architecture linker; no host address is baked into this source.
 */
    .section .text.ksu_bootstrap,"ax"
    .balign 16
    .global ksu_bootstrap
    .type ksu_bootstrap,%function
ksu_bootstrap:
    .inst 0xd503245f        /* bti c */
    ret
    .size ksu_bootstrap, .-ksu_bootstrap
