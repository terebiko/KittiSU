#ifndef __SUKISU_KPM_H
#define __SUKISU_KPM_H

#include <linux/types.h>
#include <linux/ioctl.h>
#include "uapi/supercall.h"

int sukisu_handle_kpm(unsigned long control_code, unsigned long arg1, unsigned long arg2, unsigned long result_code);
int sukisu_is_kpm_control_code(unsigned long control_code);
int do_kpm(void __user *arg);

/* KPM Control Code range (matches KSU_KPM_* in uapi/supercall.h) */
#define CMD_KPM_CONTROL KSU_KPM_LOAD
#define CMD_KPM_CONTROL_MAX 10

#endif
