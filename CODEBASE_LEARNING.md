# 代码仓学习笔记 (Codebase Learning Guide) - KittiSU

## 1. 项目概览

**这个项目做什么：**
KittiSU 是一个基于 Linux Kernel 的 Android Root 解决方案与模块管理系统。它是 **ReSukiSU** 的下游分支（Fork），源自 **SukiSU-Ultra** 与 **KernelSU**。KittiSU 提供了内核级 `su` 权限管理、Metamodule 模块挂载、App Profile 权限沙箱、SuSFS 防检测集成、AnyKernel3 刷写引擎、模块备份与故障恢复机制，以及全新的 Jetpack Compose 现代化管理器（Manager UI）。

**项目类型：**
跨内核（Linux Kernel C）+ 用户空间守护进程/CLI（Rust & C++）+ Android 应用（Kotlin / Jetpack Compose）的三层混合系统项目。

**主要语言和框架：**
- **Kernel:** C (Linux Kernel 3.4 ~ 6.18, ARM64 / x86_64 / ARM32)
- **Userspace (`ksud`, `ksuinit`):** Rust (Edition 2024), C++20 (`mkbootfs.cpp`), Bionic C
- **Manager:** Kotlin (2.3.x), Android Jetpack Compose, Material 3, Navigation3, Miuix Blur, Libsu 6.0

**如何运行或检查：**
- **Manager 构建:** `cd manager && ./gradlew assembleRelease`
- **Userspace (`ksud`) 构建:** `cargo build --release --manifest-path userspace/ksud/Cargo.toml`
- **Kernel 检查:** `kernel/setup.sh` 自动集成进目标内核源码树；CI 驱动 LKM 与 DDK 构建。
- **Lint & Clippy:** `cargo clippy --manifest-path userspace/ksud/Cargo.toml`

**建议优先阅读路径：**
1. `README.md` & `kernel/Kconfig`: 理解 Hook 机制选择与功能开关。
2. `kernel/manager/apk_sign.c` & `kernel/feature/dynamic_manager.c`: 理解 Manager 认证与 Dynamic Manager 动态信任机制。
3. `userspace/ksud/src/anykernel3.rs` & `userspace/ksud/src/mkbootfs.cpp`: 理解内置 AnyKernel3 刷写内核实现。
4. `userspace/ksud/src/android/module/backup.rs` & `recovery.rs`: 理解模块备份还原与防 Bootloop 自动禁用机制。
5. `manager/app/src/main/java/anhiutangerinee/kittisu/ui/util/module/`: 理解 Module Presets 远程预设与 Post-install Bash 执行逻辑。

---

## 2. 学习关注点

**用户指定重点模块与功能：**
- KittiSU 是什么、包含哪些组件、现存技术局限与安全隐患。
- 与上游母仓库 ReSukiSU 的详细差异与对比（功能增删、上游补丁滞后情况）。
- KittiSU 需进一步开发或升级重构的功能清单。

**本指南如何围绕这些重点组织：**
- 详细梳理 Kernel、Userspace (`ksud`/`ksuinit`)、Manager 三层架构及其核心数据流。
- 逐项对比 ReSukiSU 与 KittiSU 的 Commit 差异与设计哲学分歧（如 KPM 移除、Dynamic Manager、Presets 系统）。
- 标明经过代码行号审计的真实 Bug、安全漏洞与局限性。

---

## 3. 高价值地图

| 区域 | 路径 | 为什么重要 | 优先级 |
| --- | --- | --- | --- |
| **Kernel Hook** | `kernel/core/`, `kernel/arch/` | Syscall 拦截与 su 提权核心实现 | 高 |
| **Manager Auth** | `kernel/manager/apk_sign.c`, `dynamic_manager.c` | 内核对 Manager APK 的 V2 签名校验与动态信任机制 | 高 |
| **SELinux Hide** | `kernel/feature/selinux_hide.c` | 伪造 SELinux 状态，绕过企业级与银行级检测 | 高 |
| **AnyKernel3 Backend** | `userspace/ksud/src/anykernel3.rs`, `mkbootfs.cpp` | Userspace 内置解包/补丁/打包 boot.img 并刷写内核 | 高 |
| **Boot Recovery** | `userspace/ksud/src/android/recovery.rs` | 启动失败计数器，自动禁用导致 Bootloop 的故障模块 | 高 |
| **Module Backup** | `userspace/ksud/src/android/module/backup.rs` | 模块安全备份、版本兼容性校验与安全还原 | 中 |
| **SuSFS Backend** | `userspace/ksud/src/android/susfs/` | SuSFS 内核隐藏特性的配置与应用 | 中 |
| **Manager Presets** | `manager/.../ui/util/module/PresetPostInstallManager.kt` | 预设源解析、github-latest:// 下载与 root 脚本执行 | 高 |
| **Manager UI** | `manager/.../ui/component/FloatingBottomBar.kt` | 现代化 UI、浮动导航栏与全屏 9:16 自定义背景 | 中 |

---

## 4. 仓库结构

- `kernel/`: Linux 内核驱动源码（GPL-2.0）
  - `core/`: 核心调度、throne tracker (Manager 进程识别)、pkg_observer (监控 packages.list)
  - `feature/`: SELinux hide, Dynamic manager, sucompat 等内核特性
  - `manager/`: APK 签名校验逻辑 (`apk_sign.c`), `manager_sign.h`
  - `supercall/`: 系统调用分发与 ioctl 处理 (`dispatch.c`)
  - `compat/`: 跨内核版本兼容层 (Linux 3.4 ~ 6.x)
- `userspace/`: 用户空间 Rust 项目
  - `ksud/`: 核心守护程序与 CLI 工具，管理模块、挂载、备份、SuSFS、AnyKernel3 刷写
  - `ksuinit/`: 作为 init 的注入垫片，负责加载 LKM 并包含 vermagic 重试修改逻辑
- `manager/`: Android 应用（GPL-3.0）
  - `app/src/main/java/anhiutangerinee/kittisu/`: Kotlin 源码，UI 与业务逻辑
  - `app/src/main/cpp/`: JNI C 绑定 (`ksu.c`, `scan_driver_fd`)，通过 `[ksu_driver]` 文件描述符与内核通信
  - `randomizer`: 混淆 Manager 包名与签名的重命名自动化脚本
- `scripts/`: CI 与辅助脚本 (`ksubot.py`, `check-dynamic-manager.sh`)

---

## 5. 主要入口

1. **Kernel 驱动入口:** `kernel/core/ksu.c:ksu_init()`
2. **Ksud 命令行入口:** `userspace/ksud/src/main.rs:main()` -> `android::cli::run()`
3. **Ksuinit 注入入口:** `userspace/ksuinit/src/lib.rs` (作为 1 号进程启动前置运行)
4. **Manager Android 入口:** `manager/app/src/main/java/anhiutangerinee/kittisu/ui/MainActivity.kt`
5. **Manager Native 通信入口:** `manager/app/src/main/cpp/ksu.c:scan_driver_fd()`

---

## 6. 核心流程走读

### 流程一：Manager 发现与内核通信流程
1. 系统启动时，`pkg_observer.c` 通过 `fsnotify` 监听 `/data/system/packages.list`。
2. 当 Manager APK 安装或更新时，触发 `track_throne()`。
3. `apk_sign.c:check_v2_signature()` 校验 APK 的 V2 签名哈希是否匹配 `EXPECTED_HASH_KITTISU` 或 `dynamic_manager` 设定的动态哈希。
4. 校验通过后，内核将该 App ID 记录进 `ksu_manager_appid_list`。
5. Manager 启动时，`scan_driver_fd()` 在 `/proc/self/fd` 查找内核赋予的 `[ksu_driver]` 虚拟文件，通过 `ioctl` 发起 `KSU_IOCTL_GET_INFO`。

### 流程二：AnyKernel3 刷写流程
1. 用户在 Manager 中选择 AnyKernel3 内核 zip 包。
2. Manager 通过 `KsuCli.kt:flashAnyKernel()` 执行 `libksud.so anykernel3 <zip> --slot <a/b>`。
3. `userspace/ksud/src/anykernel3.rs`:
   - 解压 ZIP 包并查找 `META-INF/com/google/android/update-binary`。
   - 寻找标记 `chmod -R 755 tools bin;`，就地注入 `cp -f <builtin_mkbootfs> "$AKHOME/tools/mkbootfs"`。
   - 提取设备当前 boot 分区，调用注入后的 update-binary 执行内核打包与刷写。
   - 捕获 `ui_print` 输出流并实时回传给 Manager 终端 UI。

### 流程三：Module Presets 预设与 Post-install 执行流程
1. Manager 访问预设源（如 `https://raw.githubusercontent.com/terebiko/KittiSU/presets/index.json`）。
2. 解析预设中的模块条目，若 URL 为 `github-latest://<owner>/<repo>`，调用 GitHub API 动态解析最新 release zip。
3. 下载并依次刷写各模块。
4. 若预设包含 `postInstalls`，`PresetPostInstallManager.kt` 将远程脚本下载至私有目录，并通过 `getRootShell(globalMnt = true)` 在全局命名空间以 root 执行 `/system/bin/sh <script>`。

---

## 7. 后端与系统架构相关笔记

- **UAPI 兼容性:** KittiSU 支持 `KSU_IOCTL_GET_INFO` 以及 `KSU_IOCTL_GET_INFO_LEGACY`，当运行在旧版内核驱动上时自动回退。
- **Dynamic Manager:** 允许在运行时通过 root shell 调用 `ksud kernel dynamic-manager set-apk <path>` 动态向内核注册任意签名的 Manager，极大便利了开发、定制版 Manager 与 Spoof 打包。
- **SELinux 深度隐藏:** 通过在内核中 hook `selinux_write_op`、`security_compute_av` 和 `SEL_STATUS` mmap 映射页，使用 `fake_status` 向用户空间伪造 Enforcing 状态，即使内核处于 Permissive 或规则被篡改。

---

## 8. 测试和质量信号

- **CI 工作流:**
  - `build-manager.yml`: 构建 APK，支持 PR 临时 keystore 生成与签名提取。
  - `clippy.yml`, `rustfmt.yml`: 自动化 Rust 代码规范检查。
  - `ddk-lkm.yml`: 针对 Android GKI 16k/4k 内核的 DDK 构建验证。
- **测试缺口:**
  - 缺乏针对 Kernel panic 场景的本地自动化 QEMU 回归测试套件。
  - 缺乏针对 AnyKernel3 异常 zip 包注入失败时的完备断言测试。

---

## 9. 学习路线

### 阶段 1：建立整体认识
阅读 `README.md`、`kernel/Kconfig` 以及 `userspace/ksud/src/android/cli.rs`，了解 KittiSU 包含的命令集。

### 阶段 2：跟踪一条真实流程
跟踪 `anykernel3.rs` 如何解构 update-binary 并通过管道重定向 `ui_print` 到 Manager 界面。

### 阶段 3：做一个小的安全改动
在 `AndroidManifest.xml` 中将未授权导出的 `BootCompletedReceiver` 修改为 `android:exported="false"` 并验证构建。

### 阶段 4：阅读测试和运维相关代码
阅读 `.github/workflows/build-manager.yml` 与 `scripts/check-dynamic-manager.sh`。

### 阶段 5：深入子系统
深入 `kernel/feature/selinux_hide.c`，理解内核级 LSM hook 与 SELinux 状态欺骗机制。

---

## 10. 练习任务

1. **小型阅读任务:** 对比 `kernel/manager/manager.c` 中的 `ksu_handle_get_managers_cmd` 与 ReSukiSU 对应函数，识别 RCU critical section 内调用 `copy_to_user` 的潜在风险。
2. **小型代码改动:** 在 `userspace/ksud/src/android/cli.rs` 中将默认包名从 `com.resukisu.resukisu` 更新为 `anhiutangerinee.kittisu`。
3. **测试或调试任务:** 编写测试用例验证 `userspace/ksud/src/android/recovery.rs` 在 `state.failures >= 2` 时能否可靠禁用更新目录下的故障模块。
4. **更深入的扩展任务:** 为 `PresetPostInstallManager.kt` 恢复或重新设计 Ed25519 签名校验机制，防止未受信任的远程预设 root 脚本注入执行。

---

## 11. 未解问题

1. 移除 KPM 后，对于部分深度依赖 KernelPatch 独有特性（如动态 hook 特殊内核私有符号）的用户群体，KittiSU 目前缺乏对等替代品。
2. AnyKernel3 注入器硬编码了 `chmod -R 755 tools bin;` 作为锚点，如果遇到非标准 AnyKernel3 模板包将直接报错拒绝。

---

## 12. 下一步阅读目标

1. `userspace/ksud/src/android/susfs/`: 深入 SuSFS 配置同步模型。
2. `kernel/feature/selinux_hide.c`: 分析 `fake_status` 与 `static_key` 优化。
3. `manager/app/src/main/java/anhiutangerinee/kittisu/ui/screen/modulePreset/`: 分析预设编辑与导入导出状态机。
