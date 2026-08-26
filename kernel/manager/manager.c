#include <linux/slab.h>
#include <linux/rculist.h>
#include <linux/uaccess.h>
#include <linux/cred.h>
#include <linux/sched.h>
#include "manager_identity.h"
#include "feature/dynamic_manager.h"
#include "ksu.h"
#include "uapi/supercall.h"
#include "compat/kernel_compat.h"

u16 ksu_last_manager_appid = KSU_INVALID_APPID;

struct ksu_manager_node {
    u8 signature_index;
    u16 appid;
    struct list_head list;
    struct rcu_head rcu;
};

static LIST_HEAD(ksu_manager_appid_list);
static DEFINE_SPINLOCK(ksu_manager_list_write_lock);

static bool ksu_manager_node_authorized(const struct ksu_manager_node *node)
{
    return node->signature_index != KSU_SIGNATURE_INDEX_DYNAMIC_MANAGER || ksu_is_dynamic_manager_enabled();
}

static bool ksu_manager_appid_registered(u16 appid)
{
    struct ksu_manager_node *pos;
    bool found = false;

    rcu_read_lock();
    list_for_each_entry_rcu (pos, &ksu_manager_appid_list, list) {
        if (pos->appid == appid) {
            found = true;
            break;
        }
    }
    rcu_read_unlock();

    return found;
}

bool ksu_is_manager_appid(u16 appid)
{
    bool found = false;
    struct ksu_manager_node *pos;

    rcu_read_lock();
    list_for_each_entry_rcu (pos, &ksu_manager_appid_list, list) {
        if (pos->appid == appid && ksu_manager_node_authorized(pos)) {
            found = true;
            break;
        }
    }
    rcu_read_unlock();

    return found;
}

bool ksu_is_manager_uid(u32 uid)
{
    u16 appid = uid % PER_USER_RANGE;

    return ksu_is_manager_appid(appid);
}

bool is_manager(void)
{
    return ksu_is_manager_uid(ksu_get_uid_t(current_uid()));
}

void ksu_register_manager(u32 uid, u8 signature_index)
{
    struct ksu_manager_node *node;
    u16 appid;

    appid = uid % PER_USER_RANGE;
    if (ksu_manager_appid_registered(appid))
        return;

    node = kzalloc(sizeof(*node), GFP_ATOMIC);
    if (unlikely(!node))
        return;

    node->appid = appid;
    node->signature_index = signature_index;

    spin_lock(&ksu_manager_list_write_lock);

    if (ksu_manager_appid_registered(appid)) {
        spin_unlock(&ksu_manager_list_write_lock);
        kfree(node);
        return;
    }

    list_add_tail_rcu(&node->list, &ksu_manager_appid_list);

    spin_unlock(&ksu_manager_list_write_lock);

    if (ksu_last_manager_appid == KSU_INVALID_APPID)
        ksu_last_manager_appid = appid;
    return;
}

void ksu_unregister_manager(u32 uid)
{
    struct ksu_manager_node *pos, *tmp;
    bool removed_last = false;
    u16 last_alive_appid = KSU_INVALID_APPID;
    u16 appid = uid % PER_USER_RANGE;

    spin_lock(&ksu_manager_list_write_lock);

    list_for_each_entry_safe (pos, tmp, &ksu_manager_appid_list, list) {
        if (pos->appid == appid) {
            removed_last = pos->appid == ksu_last_manager_appid;
            list_del_rcu(&pos->list);
            kfree_rcu(pos, rcu);
            continue;
        }
        last_alive_appid = pos->appid;
    }

    if (removed_last)
        ksu_last_manager_appid = last_alive_appid;
    spin_unlock(&ksu_manager_list_write_lock);
}

void ksu_unregister_manager_by_signature_index(u8 signature_index)
{
    struct ksu_manager_node *pos, *tmp;
    bool removed_last = false;
    u16 last_alive_appid = KSU_INVALID_APPID;

    spin_lock(&ksu_manager_list_write_lock);

    list_for_each_entry_safe (pos, tmp, &ksu_manager_appid_list, list) {
        if (pos->signature_index == signature_index) {
            if (pos->appid == ksu_last_manager_appid) {
                removed_last = true;
            }

            list_del_rcu(&pos->list);
            kfree_rcu(pos, rcu);
            continue;
        }

        last_alive_appid = pos->appid;
    }

    if (removed_last)
        ksu_last_manager_appid = last_alive_appid;
    spin_unlock(&ksu_manager_list_write_lock);
}

bool ksu_has_manager(void)
{
    bool found = false;
    struct ksu_manager_node *pos;

    rcu_read_lock();
    list_for_each_entry_rcu (pos, &ksu_manager_appid_list, list) {
        if (ksu_manager_node_authorized(pos)) {
            found = true;
            break;
        }
    }
    rcu_read_unlock();

    return found;
}

int ksu_handle_get_managers_cmd(struct ksu_get_managers_cmd __user *arg, struct ksu_get_managers_cmd *cmd)
{
    struct ksu_manager_node *pos;
    int count = 0;
    u16 max_allowed = cmd->count;

    rcu_read_lock();
    list_for_each_entry_rcu (pos, &ksu_manager_appid_list, list) {
        if (!ksu_manager_node_authorized(pos))
            continue;

        if (count < max_allowed) {
            struct ksu_manager_entry entry = { .uid = pos->appid, .signature_index = pos->signature_index };

            void __user *dest = (void __user *)((char *)arg + sizeof(struct ksu_get_managers_cmd) +
                                                (count * sizeof(struct ksu_manager_entry)));

            if (copy_to_user(dest, &entry, sizeof(entry))) {
                rcu_read_unlock();
                return -EFAULT;
            }
        }
        count++;
    }
    rcu_read_unlock();

    cmd->total_count = count;
    return 0;
}

int ksu_get_manager_signature_index_by_appid(u16 appid)
{
    struct ksu_manager_node *pos;

    rcu_read_lock();
    list_for_each_entry_rcu (pos, &ksu_manager_appid_list, list) {
        if (pos->appid == appid && ksu_manager_node_authorized(pos)) {
            rcu_read_unlock();
            return pos->signature_index;
        }
    }
    rcu_read_unlock();
    return -ENODATA;
}
