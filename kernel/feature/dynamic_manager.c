#include <linux/err.h>
#include <linux/fs.h>
#include <linux/gfp.h>
#include <linux/kernel.h>
#include <linux/ktime.h>
#include <linux/math64.h>
#include <linux/mutex.h>
#include <linux/rcupdate.h>
#include <linux/slab.h>
#include <linux/spinlock.h>
#include <linux/version.h>
#include <linux/sched.h>
#include <linux/pid.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0)
#include <linux/sched/task.h>
#endif
#ifdef CONFIG_KSU_DEBUG
#include <linux/moduleparam.h>
#endif
#include <crypto/hash.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 11, 0)
#include <crypto/sha2.h>
#else
#include <crypto/sha.h>
#endif

#include "manager/throne_tracker.h"
#include "compat/kernel_compat.h"
#include "dynamic_manager.h"
#include "klog.h" // IWYU pragma: keep
#include "manager/manager_identity.h"
#include "ksu.h"

#define KSU_ANDROID_UID_RANGE 100000

// Dynamic sign configuration
static struct dynamic_manager_config dynamic_manager = {
    .size = 0x300,
    .hash = "0000000000000000000000000000000000000000000000000000000000000000",
    .is_set = 0
};

static struct {
    struct pid *owner;
    u64 deadline_ns;
    bool active;
} dynamic_manager_session;

static DEFINE_SPINLOCK(dynamic_manager_session_lock);
static DEFINE_MUTEX(dynamic_manager_operation_lock);

static u64 dynamic_manager_now_ns(void)
{
#if LINUX_VERSION_CODE >= KERNEL_VERSION(3, 17, 0)
    return ktime_to_ns(ktime_get_boottime());
#else
    return ktime_get_ns();
#endif
}

static bool dynamic_manager_session_valid_locked(u64 now_ns)
{
    struct task_struct *task;
    bool owner_alive;

    if (!dynamic_manager_session.active || !dynamic_manager_session.owner)
        return false;

    if (dynamic_manager_session.deadline_ns && now_ns >= dynamic_manager_session.deadline_ns)
        return false;

    rcu_read_lock();
    task = pid_task(dynamic_manager_session.owner, PIDTYPE_TGID);
    owner_alive = task && pid_alive(task) && !(READ_ONCE(task->flags) & PF_EXITING);
    rcu_read_unlock();

    return owner_alive;
}

static bool dynamic_manager_session_valid(void)
{
    unsigned long flags;
    bool valid;

    spin_lock_irqsave(&dynamic_manager_session_lock, flags);
    valid = dynamic_manager_session_valid_locked(dynamic_manager_now_ns());
    spin_unlock_irqrestore(&dynamic_manager_session_lock, flags);

    return valid;
}

static bool current_is_static_manager(void)
{
    u32 uid = ksu_get_uid_t(current_uid());
    u16 appid;
    int signature_index;

    if (uid == 0)
        return false;

    appid = uid % KSU_ANDROID_UID_RANGE;
    signature_index = ksu_get_manager_signature_index_by_appid(appid);

    return signature_index >= 0 && signature_index != KSU_SIGNATURE_INDEX_DYNAMIC_MANAGER;
}

static bool current_owns_dynamic_manager_session_locked(void)
{
    return dynamic_manager_session.owner == task_tgid(current);
}

static void reset_dynamic_manager_session(void)
{
    struct pid *owner;
    unsigned long flags;

    spin_lock_irqsave(&dynamic_manager_session_lock, flags);
    owner = dynamic_manager_session.owner;
    dynamic_manager_session.owner = NULL;
    dynamic_manager_session.deadline_ns = 0;
    dynamic_manager_session.active = false;
    spin_unlock_irqrestore(&dynamic_manager_session_lock, flags);

    if (owner)
        put_pid(owner);
    ksu_unregister_manager_by_signature_index(KSU_SIGNATURE_INDEX_DYNAMIC_MANAGER);
}

static int open_dynamic_manager_session(void)
{
    struct pid *owner;
    unsigned long flags;

    if (!current_is_static_manager())
        return -EPERM;

    owner = get_task_pid(current, PIDTYPE_TGID);
    if (!owner)
        return -ESRCH;

    spin_lock_irqsave(&dynamic_manager_session_lock, flags);
    if (dynamic_manager_session_valid_locked(dynamic_manager_now_ns()) &&
        !current_owns_dynamic_manager_session_locked()) {
        spin_unlock_irqrestore(&dynamic_manager_session_lock, flags);
        put_pid(owner);
        return -EBUSY;
    }

    spin_unlock_irqrestore(&dynamic_manager_session_lock, flags);

    reset_dynamic_manager_session();

    spin_lock_irqsave(&dynamic_manager_session_lock, flags);
    dynamic_manager_session.owner = owner;
    dynamic_manager_session.deadline_ns = 0;
    dynamic_manager_session.active = true;
    spin_unlock_irqrestore(&dynamic_manager_session_lock, flags);

    track_throne(TRACK_THRONE_FORCE_SEARCH_MGR);
    return 0;
}

static int arm_dynamic_manager_session_timeout(u32 timeout_ms)
{
    unsigned long flags;
    u64 now_ns;
    int ret = 0;

    if (!timeout_ms)
        return -EINVAL;
    if (!current_is_static_manager())
        return -EPERM;

    now_ns = dynamic_manager_now_ns();
    spin_lock_irqsave(&dynamic_manager_session_lock, flags);
    if (!dynamic_manager_session_valid_locked(now_ns) ||
        !current_owns_dynamic_manager_session_locked()) {
        ret = -EPERM;
    } else {
        dynamic_manager_session.deadline_ns = now_ns + (u64)timeout_ms * NSEC_PER_MSEC;
    }
    spin_unlock_irqrestore(&dynamic_manager_session_lock, flags);

    return ret;
}

static int cancel_dynamic_manager_session_timeout(void)
{
    unsigned long flags;
    int ret = 0;

    if (!current_is_static_manager())
        return -EPERM;

    spin_lock_irqsave(&dynamic_manager_session_lock, flags);
    if (!dynamic_manager_session_valid_locked(dynamic_manager_now_ns()) ||
        !current_owns_dynamic_manager_session_locked()) {
        ret = -EPERM;
    } else {
        dynamic_manager_session.deadline_ns = 0;
    }
    spin_unlock_irqrestore(&dynamic_manager_session_lock, flags);

    return ret;
}

static int close_dynamic_manager_session(void)
{
    unsigned long flags;
    bool valid;

    if (!current_is_static_manager())
        return -EPERM;

    spin_lock_irqsave(&dynamic_manager_session_lock, flags);
    valid = dynamic_manager_session_valid_locked(dynamic_manager_now_ns());
    if (valid && !current_owns_dynamic_manager_session_locked()) {
        spin_unlock_irqrestore(&dynamic_manager_session_lock, flags);
        return -EPERM;
    }
    spin_unlock_irqrestore(&dynamic_manager_session_lock, flags);

    reset_dynamic_manager_session();
    return 0;
}

static void get_dynamic_manager_session_status(struct ksu_dynamic_manager_cmd *cmd)
{
    unsigned long flags;
    u64 now_ns = dynamic_manager_now_ns();
    u64 remaining_ns;

    spin_lock_irqsave(&dynamic_manager_session_lock, flags);
    cmd->session_active = dynamic_manager_session_valid_locked(now_ns);
    if (cmd->session_active && dynamic_manager_session.deadline_ns) {
        remaining_ns = dynamic_manager_session.deadline_ns - now_ns;
        cmd->timeout_armed = 1;
        cmd->timeout_ms = div_u64(remaining_ns + NSEC_PER_MSEC - 1, NSEC_PER_MSEC);
        cmd->deadline_ms = div_u64(dynamic_manager_session.deadline_ns, NSEC_PER_MSEC);
    }
    spin_unlock_irqrestore(&dynamic_manager_session_lock, flags);
}

bool ksu_is_dynamic_manager_enabled(void)
{
    return READ_ONCE(dynamic_manager.is_set) && dynamic_manager_session_valid();
}

apk_sign_key_t ksu_get_dynamic_manager_sign(void)
{
    apk_sign_key_t sign_key = { .size = dynamic_manager.size, .sha256 = dynamic_manager.hash };

    return sign_key;
}

int ksu_handle_dynamic_manager(struct ksu_dynamic_manager_cmd *cmd)
{
    int ret = 0;
    int i;

    if (!cmd) {
        return -EINVAL;
    }

    if ((cmd->operation == DYNAMIC_MANAGER_OP_SET || cmd->operation == DYNAMIC_MANAGER_OP_GET ||
         cmd->operation == DYNAMIC_MANAGER_OP_WIPE) &&
        ksu_get_uid_t(current_uid()) != 0)
        return -EPERM;

    mutex_lock(&dynamic_manager_operation_lock);
    switch (cmd->operation) {
    case DYNAMIC_MANAGER_OP_SET:
        if (cmd->size < 0x100 || cmd->size > 0x1000) {
            pr_err("invalid size: 0x%x\n", cmd->size);
            ret = -EINVAL;
            break;
        }

        // Validate hash format
        for (i = 0; i < 64; i++) {
            char c = cmd->hash[i];
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                pr_err("invalid hash character at position %d: %c\n", i, c);
                ret = -EINVAL;
                break;
            }
        }

        if (ret)
            break;

        if (dynamic_manager.is_set) {
            ksu_unregister_manager_by_signature_index(KSU_SIGNATURE_INDEX_DYNAMIC_MANAGER);
        }

        dynamic_manager.size = cmd->size;
        // userspace always put an char[64] to our
        // we just use memcpy to copy memory, and flag [64] to \0 by ourselves
        memcpy(dynamic_manager.hash, cmd->hash, 64);
        dynamic_manager.hash[64] = '\0';

        dynamic_manager.is_set = 1;

        track_throne(TRACK_THRONE_FORCE_SEARCH_MGR);
        pr_info("dynamic manager updated: size=0x%x, hash=%.16s\n", cmd->size, cmd->hash);
        break;

    case DYNAMIC_MANAGER_OP_GET:
        if (dynamic_manager.is_set) {
            cmd->size = dynamic_manager.size;
            memcpy(cmd->hash, dynamic_manager.hash,
                   64); // just copy [64] is enough, userspace will handle that
            ret = 0;
        } else {
            ret = -ENODATA;
        }
        break;
    case DYNAMIC_MANAGER_OP_WIPE:
        dynamic_manager.is_set = 0;
        reset_dynamic_manager_session();
        pr_info("dynamic manager kernel settings reseted");
        break;

    case DYNAMIC_MANAGER_OP_SESSION_OPEN:
        ret = open_dynamic_manager_session();
        break;
    case DYNAMIC_MANAGER_OP_SESSION_ARM_TIMEOUT:
        ret = arm_dynamic_manager_session_timeout(cmd->timeout_ms);
        break;
    case DYNAMIC_MANAGER_OP_SESSION_CANCEL_TIMEOUT:
        ret = cancel_dynamic_manager_session_timeout();
        break;
    case DYNAMIC_MANAGER_OP_SESSION_CLOSE:
        ret = close_dynamic_manager_session();
        break;
    case DYNAMIC_MANAGER_OP_SESSION_STATUS:
        get_dynamic_manager_session_status(cmd);
        break;

    default:
        pr_err("Invalid dynamic manager operation: %d\n", cmd->operation);
        ret = -EINVAL;
        break;
    }

    mutex_unlock(&dynamic_manager_operation_lock);
    return ret;
}

void ksu_dynamic_manager_exit(void)
{
    mutex_lock(&dynamic_manager_operation_lock);
    reset_dynamic_manager_session();
    mutex_unlock(&dynamic_manager_operation_lock);
}
