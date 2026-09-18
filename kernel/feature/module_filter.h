#ifndef __KSU_MODULE_FILTER_H
#define __KSU_MODULE_FILTER_H

#include <linux/types.h>

int ksu_filter_init_module(const void __user *image, unsigned long length);
int ksu_filter_finit_module(int fd, int flags);
void ksu_module_filter_init(void);
void ksu_module_filter_exit(void);

#endif
