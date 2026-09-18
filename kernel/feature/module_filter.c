#include <linux/file.h>
#include <linux/fs.h>
#include <linux/module.h>
#include <linux/moduleparam.h>
#include <linux/slab.h>
#include <linux/string.h>
#include <linux/uaccess.h>
#include <linux/version.h>
#include <linux/vmalloc.h>

#include "arch.h"
#include "compat/kernel_compat.h"
#include "feature/module_filter.h"
#include "klog.h"

#ifdef CONFIG_KSU_TRACEPOINT_HOOK
#include "hook/syscall_hook.h"
#endif

#define KSU_MODULE_FILTER_LIST_SIZE 256
#define KSU_MODULE_SCAN_LIMIT (64UL * 1024UL * 1024UL)

static char blocked_modules[KSU_MODULE_FILTER_LIST_SIZE] = "vr,vklp,oplus_secure_guard,oplus_secure_guard_new,mkp";
module_param_string(block_modules, blocked_modules, sizeof(blocked_modules), 0400);

static bool module_name_equal(const char *candidate, size_t length, const char *configured, size_t configured_length)
{
    size_t index;

    if (length != configured_length)
        return false;
    for (index = 0; index < length; index++) {
        char left = candidate[index] == '-' ? '_' : candidate[index];
        char right = configured[index] == '-' ? '_' : configured[index];
        if (left != right)
            return false;
    }
    return true;
}

static bool name_is_blocked(const char *name, size_t length)
{
    const char *entry = blocked_modules;
    const char *limit = blocked_modules + strnlen(blocked_modules, sizeof(blocked_modules));

    while (entry < limit) {
        const char *comma = memchr(entry, ',', limit - entry);
        size_t entry_length = comma ? (size_t)(comma - entry) : (size_t)(limit - entry);

        if (entry_length && module_name_equal(name, length, entry, entry_length))
            return true;
        if (!comma)
            break;
        entry = comma + 1;
    }
    return false;
}

static bool image_contains_blocked_name(const char *image, size_t length)
{
    size_t offset = 0;

    while (offset + 6 < length) {
        const char *field;
        const char *terminator;

        if (offset && image[offset - 1] != '\0') {
            offset++;
            continue;
        }
        field = image + offset;
        terminator = memchr(field, '\0', length - offset);
        if (!terminator)
            break;
        if (terminator - field > 5 && !memcmp(field, "name=", 5) && name_is_blocked(field + 5, terminator - field - 5))
            return true;
        offset = terminator - image + 1;
    }
    return false;
}

static bool filename_is_blocked(const struct file *file)
{
    const struct qstr *filename = &file->f_path.dentry->d_name;
    size_t length = filename->len;
    const char *name = filename->name;

    if (length > 3 && !memcmp(name + length - 3, ".ko", 3))
        length -= 3;
    else if (length > 6 && (!memcmp(name + length - 6, ".ko.gz", 6) || !memcmp(name + length - 6, ".ko.xz", 6)))
        length -= 6;
    else if (length > 7 && !memcmp(name + length - 7, ".ko.zst", 7))
        length -= 7;
    else
        return false;
    return name_is_blocked(name, length);
}

int ksu_filter_init_module(const void __user *image, unsigned long length)
{
    char *copy;
    bool blocked;

    if (!blocked_modules[0] || !image || !length || length > KSU_MODULE_SCAN_LIMIT)
        return 1;
    copy = vmalloc(length);
    if (!copy)
        return 1;
    if (copy_from_user(copy, image, length)) {
        vfree(copy);
        return 1;
    }
    blocked = image_contains_blocked_name(copy, length);
    vfree(copy);
    if (blocked)
        pr_info("module filter rejected init_module request\n");
    return blocked ? 0 : 1;
}

int ksu_filter_finit_module(int fd, int flags)
{
    struct file *file;
    loff_t position = 0;
    loff_t size;
    char *copy = NULL;
    bool blocked = false;

    if (!blocked_modules[0])
        return 1;
    file = fget(fd);
    if (!file)
        return 1;

    blocked = filename_is_blocked(file);
    size = i_size_read(file_inode(file));
    if (!blocked && size > 0 && size <= KSU_MODULE_SCAN_LIMIT) {
        copy = vmalloc(size);
        if (copy && ksu_kernel_read_compat(file, copy, size, &position) == size)
            blocked = image_contains_blocked_name(copy, size);
    }
    vfree(copy);
    fput(file);

    if (blocked)
        pr_info("module filter rejected finit_module request (flags=%d)\n", flags);
    return blocked ? 0 : 1;
}

#ifdef CONFIG_KSU_TRACEPOINT_HOOK
static long (*original_init_module)(const struct pt_regs *regs);
static long filtered_init_module(const struct pt_regs *regs)
{
    if (!ksu_filter_init_module((const void __user *)PT_REGS_PARM1(regs), PT_REGS_PARM2(regs)))
        return 0;
    return original_init_module(regs);
}

static long (*original_finit_module)(const struct pt_regs *regs);
static long filtered_finit_module(const struct pt_regs *regs)
{
    if (!ksu_filter_finit_module((int)PT_REGS_PARM1(regs), (int)PT_REGS_PARM3(regs)))
        return 0;
    return original_finit_module(regs);
}
#endif

void ksu_module_filter_init(void)
{
    if (!blocked_modules[0]) {
        pr_info("module filter disabled\n");
        return;
    }
#ifdef CONFIG_KSU_TRACEPOINT_HOOK
    ksu_syscall_table_hook(__NR_init_module, filtered_init_module, &original_init_module);
    ksu_syscall_table_hook(__NR_finit_module, filtered_finit_module, &original_finit_module);
#endif
    pr_info("module filter enabled for: %s\n", blocked_modules);
}

void ksu_module_filter_exit(void)
{
#ifdef CONFIG_KSU_TRACEPOINT_HOOK
    if (blocked_modules[0]) {
        ksu_syscall_table_unhook(__NR_init_module);
        ksu_syscall_table_unhook(__NR_finit_module);
    }
#endif
}
