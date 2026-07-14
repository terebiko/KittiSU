/* SPDX-License-Identifier: GPL-2.0-or-later */
/* 
 * Copyright (C) 2025 Liankong (xhsw.new@outlook.com). All Rights Reserved.
 * 本代码由GPL-2授权
 * 
 * 适配KernelSU的KPM 内核模块加载器兼容实现
 * 
 * 集成了 ELF 解析、内存布局、符号处理、重定位（支持 ARM64 重定位类型）
 * 并参照KernelPatch的标准KPM格式实现加载和控制
 *
 * ABI aligned with SukiSU-Ultra (struct ksu_kpm_cmd: control_code is a u64
 * command value; result_code is a user pointer to int). Stub exports are
 * intentionally non-optimized so KernelPatch can replace them at runtime.
 */

#include <linux/kernel.h>
#include <linux/fs.h>
#include <linux/kernfs.h>
#include <linux/file.h>
#include <linux/vmalloc.h>
#include <linux/uaccess.h>
#include <linux/elf.h>
#include <linux/kallsyms.h>
#include <linux/version.h>
#include <linux/list.h>
#include <linux/spinlock.h>
#include <linux/rcupdate.h>
#include <asm/elf.h>
#include <linux/mm.h>
#include <linux/string.h>
#include <asm/cacheflush.h>
#include <linux/module.h>
#include <linux/set_memory.h>
#include <linux/export.h>
#include <linux/slab.h>
#include <asm/insn.h>
#include <linux/kprobes.h>
#include <linux/stacktrace.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 0, 0) && defined(CONFIG_MODULES)
#include <linux/moduleloader.h>
#endif
#include "kpm.h"
#include "compact.h"
#include "compat/kernel_compat.h"
#include "uapi/supercall.h"

#define KPM_NAME_LEN 32
#define KPM_ARGS_LEN 1024
#define KPM_PATH_LEN 256
#define KPM_LIST_BUF_LEN 1024
#define KPM_INFO_BUF_LEN 256
#define KPM_VERSION_BUF_LEN 256

#ifndef NO_OPTIMIZE
#if defined(__GNUC__) && !defined(__clang__)
#define NO_OPTIMIZE __attribute__((optimize("O0")))
#elif defined(__clang__)
#define NO_OPTIMIZE __attribute__((optnone))
#else
#define NO_OPTIMIZE
#endif
#endif

/* Stubs: KernelPatch replaces these at runtime. Until then return -ENOSYS. */

noinline NO_OPTIMIZE void sukisu_kpm_load_module_path(const char *path, const char *args, void *ptr, int *result)
{
    pr_info("kpm: Stub function called (sukisu_kpm_load_module_path). "
            "path=%s args=%s ptr=%p\n",
            path, args, ptr);

    if (result)
        *result = -ENOSYS;
    __asm__ volatile("nop");
}
EXPORT_SYMBOL(sukisu_kpm_load_module_path);

noinline NO_OPTIMIZE void sukisu_kpm_unload_module(const char *name, void *ptr, int *result)
{
    pr_info("kpm: Stub function called (sukisu_kpm_unload_module). "
            "name=%s ptr=%p\n",
            name, ptr);

    if (result)
        *result = -ENOSYS;
    __asm__ volatile("nop");
}
EXPORT_SYMBOL(sukisu_kpm_unload_module);

noinline NO_OPTIMIZE void sukisu_kpm_num(int *result)
{
    pr_info("kpm: Stub function called (sukisu_kpm_num).\n");

    if (result)
        *result = -ENOSYS;
    __asm__ volatile("nop");
}
EXPORT_SYMBOL(sukisu_kpm_num);

noinline NO_OPTIMIZE void sukisu_kpm_info(const char *name, char *buf, int bufferSize, int *size)
{
    pr_info("kpm: Stub function called (sukisu_kpm_info). "
            "name=%s buffer=%p\n",
            name, buf);

    if (size)
        *size = 0;
    if (buf && bufferSize > 0)
        buf[0] = '\0';
    __asm__ volatile("nop");
}
EXPORT_SYMBOL(sukisu_kpm_info);

noinline NO_OPTIMIZE void sukisu_kpm_list(void *out, int bufferSize, int *result)
{
    pr_info("kpm: Stub function called (sukisu_kpm_list). "
            "buffer=%p size=%d\n",
            out, bufferSize);

    if (out && bufferSize > 0)
        ((char *)out)[0] = '\0';
    if (result)
        *result = 0; /* 0 bytes written when no modules */
    __asm__ volatile("nop");
}
EXPORT_SYMBOL(sukisu_kpm_list);

noinline NO_OPTIMIZE void sukisu_kpm_control(const char *name, const char *args, long arg_len, int *result)
{
    pr_info("kpm: Stub function called (sukisu_kpm_control). "
            "name=%p args=%p arg_len=%ld\n",
            name, args, arg_len);

    if (result)
        *result = -ENOSYS;
    __asm__ volatile("nop");
}
EXPORT_SYMBOL(sukisu_kpm_control);

noinline NO_OPTIMIZE void sukisu_kpm_version(char *buf, int bufferSize)
{
    pr_info("kpm: Stub function called (sukisu_kpm_version). "
            "buffer=%p\n",
            buf);

    if (buf && bufferSize > 0)
        buf[0] = '\0';
    __asm__ volatile("nop");
}
EXPORT_SYMBOL(sukisu_kpm_version);

static int kpm_copy_result(unsigned long result_code, int res)
{
    if (!result_code)
        return -EFAULT;
    if (copy_to_user((void __user *)(uintptr_t)result_code, &res, sizeof(res)))
        return -EFAULT;
    return 0;
}

noinline int sukisu_handle_kpm(unsigned long control_code, unsigned long arg1, unsigned long arg2,
                               unsigned long result_code)
{
    int res = -EINVAL;

    if (control_code == KSU_KPM_LOAD) {
        char kernel_load_path[KPM_PATH_LEN] = { 0 };
        char kernel_args_buffer[KPM_PATH_LEN] = { 0 };
        long n;

        if (arg1 == 0) {
            res = -EINVAL;
            goto exit;
        }

        if (!ksu_access_ok((void __user *)(uintptr_t)arg1, 1))
            goto invalid_arg;

        n = strncpy_from_user(kernel_load_path, (const char __user *)(uintptr_t)arg1, sizeof(kernel_load_path));
        if (n < 0) {
            res = (int)n;
            goto exit;
        }
        if (n == 0 || n >= (long)sizeof(kernel_load_path)) {
            res = -ENAMETOOLONG;
            goto exit;
        }

        if (arg2 != 0) {
            const char __user *args_user = (const char __user *)(uintptr_t)arg2;

            if (!ksu_access_ok(args_user, 1))
                goto invalid_arg;

            n = strncpy_from_user(kernel_args_buffer, args_user, sizeof(kernel_args_buffer));
            if (n < 0) {
                res = (int)n;
                goto exit;
            }
            if (n >= (long)sizeof(kernel_args_buffer)) {
                res = -ENAMETOOLONG;
                goto exit;
            }
        }

        sukisu_kpm_load_module_path(kernel_load_path, kernel_args_buffer, NULL, &res);
    } else if (control_code == KSU_KPM_UNLOAD) {
        char kernel_name_buffer[KPM_PATH_LEN] = { 0 };
        long n;

        if (arg1 == 0) {
            res = -EINVAL;
            goto exit;
        }

        if (!ksu_access_ok((void __user *)(uintptr_t)arg1, 1))
            goto invalid_arg;

        n = strncpy_from_user(kernel_name_buffer, (const char __user *)(uintptr_t)arg1, sizeof(kernel_name_buffer));
        if (n < 0) {
            res = (int)n;
            goto exit;
        }
        if (n == 0 || n >= (long)sizeof(kernel_name_buffer)) {
            res = -ENAMETOOLONG;
            goto exit;
        }

        sukisu_kpm_unload_module(kernel_name_buffer, NULL, &res);
    } else if (control_code == KSU_KPM_NUM) {
        sukisu_kpm_num(&res);
    } else if (control_code == KSU_KPM_INFO) {
        char kernel_name_buffer[KPM_PATH_LEN] = { 0 };
        char buf[KPM_INFO_BUF_LEN] = { 0 };
        int size = 0;
        long n;

        if (arg1 == 0 || arg2 == 0) {
            res = -EINVAL;
            goto exit;
        }

        if (!ksu_access_ok((void __user *)(uintptr_t)arg1, 1))
            goto invalid_arg;

        n = strncpy_from_user(kernel_name_buffer, (const char __user *)(uintptr_t)arg1, sizeof(kernel_name_buffer));
        if (n < 0) {
            res = (int)n;
            goto exit;
        }
        if (n == 0 || n >= (long)sizeof(kernel_name_buffer)) {
            res = -ENAMETOOLONG;
            goto exit;
        }

        sukisu_kpm_info(kernel_name_buffer, buf, sizeof(buf), &size);

        if (size < 0 || size > (int)sizeof(buf)) {
            res = -EINVAL;
            goto exit;
        }

        if (size > 0) {
            if (!ksu_access_ok((void __user *)(uintptr_t)arg2, size))
                goto invalid_arg;

            if (copy_to_user((void __user *)(uintptr_t)arg2, buf, size)) {
                res = -EFAULT;
                goto exit;
            }
        }
        res = 0;
    } else if (control_code == KSU_KPM_LIST) {
        char buf[KPM_LIST_BUF_LEN] = { 0 };
        int len = (int)arg2;
        int copy_len;

        if (arg1 == 0 || len <= 0) {
            res = -EINVAL;
            goto exit;
        }

        /* arg1 = output buffer pointer, arg2 = buffer capacity */
        if (!ksu_access_ok((void __user *)(uintptr_t)arg1, len))
            goto invalid_arg;

        sukisu_kpm_list(buf, sizeof(buf), &res);

        if (res < 0)
            goto exit;

        if (res > (int)sizeof(buf)) {
            res = -EOVERFLOW;
            goto exit;
        }

        if (res > len) {
            res = -ENOBUFS;
            goto exit;
        }

        copy_len = res;
        if (copy_len > 0 && copy_to_user((void __user *)(uintptr_t)arg1, buf, copy_len)) {
            res = -EFAULT;
            goto exit;
        }
    } else if (control_code == KSU_KPM_CONTROL) {
        char kpm_name[KPM_NAME_LEN] = { 0 };
        char kpm_args[KPM_ARGS_LEN] = { 0 };
        long name_len;
        long arg_len;

        if (arg1 == 0) {
            res = -EINVAL;
            goto exit;
        }

        if (!ksu_access_ok((void __user *)(uintptr_t)arg1, 1))
            goto invalid_arg;

        name_len = strncpy_from_user(kpm_name, (const char __user *)(uintptr_t)arg1, sizeof(kpm_name));
        if (name_len <= 0) {
            res = name_len < 0 ? (int)name_len : -EINVAL;
            goto exit;
        }
        if (name_len >= (long)sizeof(kpm_name)) {
            res = -ENAMETOOLONG;
            goto exit;
        }

        if (arg2 != 0) {
            if (!ksu_access_ok((void __user *)(uintptr_t)arg2, 1))
                goto invalid_arg;

            arg_len = strncpy_from_user(kpm_args, (const char __user *)(uintptr_t)arg2, sizeof(kpm_args));
            if (arg_len < 0) {
                res = (int)arg_len;
                goto exit;
            }
            if (arg_len >= (long)sizeof(kpm_args)) {
                res = -ENAMETOOLONG;
                goto exit;
            }
        } else {
            arg_len = 0;
        }

        sukisu_kpm_control(kpm_name, kpm_args, arg_len, &res);
    } else if (control_code == KSU_KPM_VERSION) {
        char buffer[KPM_VERSION_BUF_LEN] = { 0 };
        unsigned int outlen = (unsigned int)arg2;
        int len;

        if (arg1 == 0 || outlen == 0) {
            res = -EINVAL;
            goto exit;
        }

        if (!ksu_access_ok((void __user *)(uintptr_t)arg1, outlen))
            goto invalid_arg;

        sukisu_kpm_version(buffer, sizeof(buffer));

        len = (int)strnlen(buffer, sizeof(buffer));
        if (len >= (int)outlen)
            len = (int)outlen - 1;

        if (copy_to_user((void __user *)(uintptr_t)arg1, buffer, len + 1)) {
            res = -EFAULT;
            goto exit;
        }
        res = 0;
    } else {
        res = -EINVAL;
    }

exit:
    if (kpm_copy_result(result_code, res))
        pr_info("kpm: Copy result to user failed.\n");

    return 0;

invalid_arg:
    pr_err("kpm: invalid user pointer arg1=%px arg2=%px\n", (void *)(uintptr_t)arg1, (void *)(uintptr_t)arg2);
    res = -EFAULT;
    goto exit;
}
EXPORT_SYMBOL(sukisu_handle_kpm);

int sukisu_is_kpm_control_code(unsigned long control_code)
{
    return (control_code >= CMD_KPM_CONTROL && control_code <= CMD_KPM_CONTROL_MAX) ? 1 : 0;
}

int do_kpm(void __user *arg)
{
    struct ksu_kpm_cmd cmd;

    if (copy_from_user(&cmd, arg, sizeof(cmd))) {
        pr_err("kpm: copy_from_user failed\n");
        return -EFAULT;
    }

    /* control_code is a command value (KSU_KPM_*), not a user pointer. */
    if (cmd.control_code < CMD_KPM_CONTROL || cmd.control_code > CMD_KPM_CONTROL_MAX) {
        pr_err("kpm: invalid control_code %llu\n", (unsigned long long)cmd.control_code);
        return -EINVAL;
    }

    if (!cmd.result_code || !ksu_access_ok((void __user *)(uintptr_t)cmd.result_code, sizeof(int))) {
        pr_err("kpm: invalid result_code pointer %px\n", (void *)(uintptr_t)cmd.result_code);
        return -EFAULT;
    }

    return sukisu_handle_kpm(cmd.control_code, cmd.arg1, cmd.arg2, cmd.result_code);
}
