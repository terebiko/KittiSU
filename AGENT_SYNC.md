# 🤝 Agent Sync & Handoff Log (Antigravity ↔ DeepSeek)

Tài liệu này dùng để đồng bộ trạng thái codebase, các quyết định kiến trúc, thay đổi gần nhất và các task tiếp theo giữa **Antigravity (Gemini)** và **DeepSeek**.

---

## 📌 1. Trạng thái Dự án Hiện tại (Project Context)
- **Repo / Branch**: `KittiSU-test` (tên hiển thị **SovaSU**, package `anhiutangerinee.kittisu`).
- **Chế độ Root Thật (Production Real-Root)**:
  - Đã gỡ bỏ hoàn toàn chế độ giả lập `FAKE_ROOT` (`build.gradle.kts`, `ksu.c`, `KsuCli.kt`, `SuperUserViewModel.kt`, `SuSFSConfigHelper.kt`). App hiện nhận diện kernel driver & quyền root thật 100%, kèm `libksud.so` biên dịch mới nhất bằng Rust `nightly` (`arm64-v8a` & `x86_64`).
  - Các bản APK thật được xuất tại `out/SovaSU-release.apk` (`arm64-v8a`), `out/SovaSU-arm64-v8a-release.apk`, và `out/SovaSU-universal-release.apk`.
- **Môi trường Build trên máy local**:
  - Project yêu cầu **JDK 21** (`JavaVersion.VERSION_21`), nhưng `/usr/bin/java` mặc định là OpenJDK 17.
  - Khi chạy `./gradlew` trên máy này, luôn export:
    ```bash
    export JAVA_HOME=/home/thinh/.local/share/PrismLauncher/java/java-runtime-delta
    ```
  - Output APK được copy ra thư mục `out/` (ví dụ: `out/SovaSU-fakeroot-test.apk`).

---

## 🛠️ 2. Các thay đổi vừa thực hiện (By Antigravity)

### A. Fix lỗi văng / báo "Chưa hỗ trợ chỉnh sửa cho hình ảnh này" trên ROM AOSP khi chọn ảnh thẻ Working & Hình nền toàn màn hình
- **Nguyên nhân gốc**:
  1. `MoreSettings.kt` gọi `Intent("com.android.camera.action.CROP")` truyền URI `FileProvider` của file tạm không có đuôi mở rộng (`background_crop_cache`, `fullscreen_background_crop_cache`). Trên ROM AOSP / Google Photos / AOSP Gallery, trình sửa ảnh từ chối URI này với thông báo *"Editing is not supported for this image"* hoặc crash do `ActivityNotFoundException`.
  2. Ngay cả khi trình crop lưu trực tiếp vào `MediaStore.EXTRA_OUTPUT`, nhiều app Gallery trên AOSP trả về `intent?.data == null` trong `onActivityResult`, khiến callback bị bỏ qua.
  3. Trong `Theme.kt`, `rememberAsyncImagePainter` được khởi tạo trong `BackgroundLayer()` nhưng không được gắn trực tiếp vào lệnh vẽ trong cùng scope nên Coil 2.x không kích hoạt `onRemembered()` để tải ảnh.
- **Giải pháp đã áp dụng**:
  - Tạo hộp thoại cắt ảnh tương tác trực tiếp trong ứng dụng **`ImageCropDialog`** (`manager/app/src/main/java/zako/zako/zako/zakoui/screen/moreSettings/component/ImageCropDialog.kt`) theo phong cách Material Design 3 Expressive:
    - Hiển thị khung cắt bo góc với lưới bố cục 1/3 (rule-of-thirds) đúng theo tỉ lệ đích (`16:9` cho thẻ Working và `screenW : screenH` cho hình nền toàn màn hình), làm tối vùng ngoài khung cắt.
    - Hỗ trợ cử chỉ **kéo để di chuyển (drag-to-pan)**, **chụm 2 ngón để thu phóng (pinch-to-zoom)**, kết hợp **thanh trượt Zoom (`100%` – `400%`)** và nút **Reset** về vị trí chuẩn.
    - Giải mã bitmap preview an toàn (`inSampleSize` giới hạn `maxDimension = 1600`) trên `Dispatchers.IO` để không gây giật lag trên máy yếu / máy ảo.
  - Viết hàm xử lý cắt & nén ảnh nội bộ `Context.cropAndSaveImage(uri, isFullScreen, transformation)` trong `manager/app/src/main/java/anhiutangerinee/kittisu/ui/theme/util/BackgroundUtils.kt`:
    - Nhận `BackgroundTransformation(scale, offsetX, offsetY)` từ `ImageCropDialog` để tính chính xác tọa độ hình chữ nhật (`cropLeft`, `cropTop`, `cropWidth`, `cropHeight`) trên ảnh gốc.
    - Scale an toàn để tránh tràn bộ nhớ (OOM) với ảnh độ phân giải lớn và nén JPEG chất lượng cao (`92`).
  - Cập nhật `MoreSettings.kt`, `MoreSettingsHandlers.kt`, `Theme.kt`, `HomePage.kt` và `MainActivity.kt`:
    - Bỏ hoàn toàn sự phụ thuộc vào `com.android.camera.action.CROP` bên ngoài. Khi chọn ảnh từ `ActivityResultContracts.GetContent()`, app mở ngay `ImageCropDialog` để người dùng tự căn chỉnh khung cắt rồi mới lưu trên `Dispatchers.IO`.
    - Sử dụng trực tiếp `AsyncImage` với `java.io.File` và `memoryCacheKey`/`diskCacheKey` gắn với `backgroundRefreshTick` / `fullScreenRefreshTick` để ảnh hiển thị ngay lập tức trên thẻ Working và hình nền toàn màn hình.

### B. Tăng tốc độ tải danh sách SuperUser & Tối ưu hiệu năng cho máy yếu / máy ảo (`SuperUserViewModel.kt`, `KsuCli.kt`, `SuSFSConfigHelper.kt`, `SettingsBaseWidget.kt`, `KernelSUApplication.kt`)
- **Nguyên nhân gốc**:
  1. Trong `KernelSUApplication.onCreate()`, 3 hàm `homeViewModel.refreshData()`, `superUserViewModel.fetchAppList()`, `moduleViewModel.fetchModuleList()` được gọi tuần tự trong cùng 1 coroutine.
  2. Trong `SuperUserViewModel.connectKsuService()`, khi chạy bản test không có daemon `libksud.so` (hoặc máy chưa root), `withTimeoutOrNull(8_000)` làm treo việc load danh sách ứng dụng mất **8 giây** trước khi fallback sang `PackageManager.getInstalledPackages(0)`.
  3. Các hàm trong `KsuCli.kt` và `SuSFSConfigHelper.kt` liên tục spawn tiến trình shell khi `libksud.so` không tồn tại, gây tốn CPU/RAM lúc khởi động và chuyển tab.
  4. `SettingsBaseWidget` gắn `pointerInput(Unit) { awaitFirstDown(...) }` cho mọi dòng danh sách trong `LazyColumn`, gây tốn bộ nhớ và CPU khi cuộn hàng trăm ứng dụng.
  5. `appInfo.loadLabel(pm)` chạy tuần tự trên 1 luồng IO cho hàng trăm gói ứng dụng, đồng thời `HanziToPinyin` và `.lowercase()` bị tính toán lại liên tục trên luồng UI khi lọc/sắp xếp.
- **Giải pháp đã áp dụng**:
  - Chạy song song 3 coroutine khởi tạo trong `KernelSUApplication.onCreate()` (giảm thời gian cold-start trên máy ảo từ ~3.8s xuống ~2.0s).
  - Kiểm tra nhanh `hasRealRootDaemon()` / `hasKsuDaemon()` (nếu không có `libksud.so` thì bỏ qua `RootService` và các lệnh shell `ksud` ngay lập tức: **0ms delay**).
  - Chia batch (`chunked`) và giải quyết `loadLabel(pm)` + `Natives.getAppProfile()` song song trên `appProcessingThreadPool` (8–16 luồng), kết hợp `labelCache` (`ConcurrentHashMap<String, Pair<Long, String>>` theo `lastUpdateTime`).
  - Cache sẵn `lowerLabel`, `lowerPackageName`, và `lazy` `pinyinLabel` trong `AppInfo`.
  - Bỏ `pointerInput` khỏi `SettingsBaseWidget` (chỉ gắn ở `SettingsDropDownPopupMenuWidget` nơi thực sự cần vị trí popup), nhớ (`remember(packageName)`) `ImageRequest` và tắt `crossfade` cho icon ứng dụng để cuộn danh sách 60fps trên máy yếu.
  - Khởi tạo đồng bộ `BottomBarDestination.getPages()` và thêm `beyondViewportPageCount = 1` cho `HorizontalPager` trong `MainActivity.kt`.

### C. Nâng cấp Hệ thống Bo góc & Chuyển động theo Material Design 3 Expressive (`@material-design-3-ui`)
- **Shape Tokens (`ui/theme/Shapes.kt` & các màn hình)**:
  - Nâng cấp thang bo góc `AppShapes`:
    - `extraSmall = 8.dp` (badges / `LabelText`)
    - `small = 12.dp`
    - `medium = 16.dp` (app icons, inner items)
    - `large = 24.dp` (cards, segmented column outer corners, dialogs, warning banners)
    - `largeIncreased = 28.dp` (pressed state morphing)
    - `extraLarge = 32.dp` (hero `StatusCard` Working, `BottomBar` floating pill, `ExtendedFloatingActionButton`)
    - `extraLargeIncreased = 36.dp`, `extraExtraLarge = 48.dp`
  - Chuẩn hóa các khối `SegmentedColumn` & `LazySegmentedColumn`: góc ngoài `24.dp`, góc nối bên trong `8.dp`, khi nhấn (`pressedShape`) morph mượt sang `28.dp`.
  - Đồng bộ hóa các `RoundedCornerShape(16.dp)` rời rạc ở `HomePage`, `AppProfile`, `About`, `ModuleRepo`, `ModulePresets`, `SuSFSConfig`, `UmountManager`, `WarningCard`, `Dialog` sang semantic token `MaterialTheme.shapes.large` / `extraLarge`.
- **Motion & Animation**:
  - `SegmentedColumn`: Tinh chỉnh spring (`stiffness = 500f`, `dampingRatio = 0.72f`) giúp đóng/mở mục cài đặt mượt, đầm và không bị rung quá đà.
  - `BottomBar` & `MainActivity` HorizontalPager: Chuyển tab bằng physics spring mượt mà, thêm hiệu ứng đàn hồi `pillScale` khi kéo/chạm thanh điều hướng.
  - `ScalePredictiveBackAnimation`: Áp dụng đường cong M3 Emphasized Decelerate (`CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)`) kết hợp `scaleIn`/`scaleOut` + `slideHorizontally` + `fade` tạo chiều sâu không gian giữa các màn hình.
  - `VerticalExpandableFab`: Chuyển từ linear/tween delay sang staggered spring physics theo từng nút menu.

---

## 📋 3. Quy ước Giao tiếp giữa 2 Agent (Antigravity & DeepSeek)
1. **Khi bắt đầu phiên làm việc mới**: Đọc file `AGENT_SYNC.md` này và chạy `git log -n 5 --stat` để nắm các commit mới nhất.
2. **Khi hoàn thành một tính năng / sửa lỗi**:
   - Cập nhật mục **Changelog / Handoff Notes** bên dưới trong file `AGENT_SYNC.md`.
   - Ghi rõ: File nào đã sửa, lý do sửa, và các lưu ý (nếu có).
   - Commit lại để agent kia có điểm mốc git sạch sẽ.

---

## 📝 4. Nhật ký Phiên làm việc (Session Changelog)
- **[2026-11-23 - Antigravity]**:
  - **Chuyển sang bản Production Root Thật (`SovaSU-release.apk`) + Biên dịch mới `libksud.so`**:
    - Gỡ bỏ hoàn toàn chế độ giả lập `FAKE_ROOT` trong `manager/app/build.gradle.kts`, `manager/app/src/main/cpp/ksu.c`, `KsuCli.kt`, `SuperUserViewModel.kt`, `SuSFSConfigHelper.kt`.
    - Khôi phục nhận diện kernel driver & root thật (`Natives.isManager`, `Natives.version`, `getRootShell().isRoot`), giữ nguyên toàn bộ các bản vá chống crash `MainShell` và tối ưu hiệu năng batch `loadSettingsFeatureBatch()`.
    - Biên dịch lại `libksud.so` mới nhất bằng Rust `nightly` + `cargo-ndk` (kèm bản vá `waitsys` BakaSU và `profile.rs` KOWX712) cho `arm64-v8a` và `x86_64`.
    - Xuất APK bản thật ra `out/SovaSU-release.apk` (`arm64-v8a`), `out/SovaSU-arm64-v8a-release.apk`, và `out/SovaSU-universal-release.apk`.
    - Chữ ký APK V2 hiện tại (để khai báo Dynamic Manager / build kernel): `size = 0x2e8` (`744`), `sha256 = 76eaf07585cb9e74163cadc7f6619a547aa0a3ac6a099c7c25ddc440445a0dd5`.
  - **Sửa triệt để lỗi crash khi mới mở app vào thẳng tab Cài đặt (Settings) & Tối ưu chống lag toàn diện**:
    - **Nguyên nhân gốc của lỗi crash cold-start -> Settings**:
      1. `KsuCli.generateMainShellBuilder()` gọi `builder.setCommands(libksud.so, "debug", "su")` vô điều kiện. Khi chạy bản `FAKE_ROOT` trên máy chưa có kernel driver thật, lệnh `libksud.so debug su` thất bại khiến `libsu` `MainShell.get()` ném `NoShellException` và **kẹt cờ `MainShell.isInitMain = true` vĩnh viễn** (`mainShell[0] == null`).
      2. Trong `SettingsPage.kt`, `isWebuiModuleInstalled("ksu_toolkit" / "KPatch-Next")` gọi trực tiếp `SuFile(...).exists()` không có `try/catch` -> gọi `MainShell.get()` -> văng `NoShellException: The main shell died during initialization`. Khi người dùng qua tab Superuser & Module trước, `ModulePage` gọi `hasMagisk()` -> `KsuCli.GLOBAL_MNT_SHELL` fallback sang `"sh"` vô tình kích hoạt `MainShell.setCached(sh)` nên không bị crash nữa.
      3. `SegmentedColumn.kt` dùng spring đàn hồi (`dampingRatio = 0.72f`) cho `currentTopRadius`/`currentBottomRadius`/`currentTopPadding`, có thể vọt lố xuống giá trị âm (`< 0.dp`) trên Android 13+ gây `IllegalArgumentException`.
    - **Các bản vá & tối ưu chống lag đã triển khai**:
      - `KsuCli.kt`: Thêm `isFakeRootNoKernel` (cache lazy kiểm tra kernel thật vs fake-root), chỉ gọi `builder.setCommands(...)` trong `generateMainShellBuilder()` khi thực sự có kernel driver; bọc `runCatching` cho `isWebuiModuleInstalled`, `getBootRecoveryState`, `getFeatureStatus`, `getFeaturePersistValue`, `hasMagisk`, `listModules`; thêm `loadSettingsFeatureBatch()` gom toàn bộ 12 truy vấn `ksud`/module của màn Settings vào **1 lệnh shell duy nhất** trên `Dispatchers.IO`.
      - `SettingsPage.kt`: Thêm `SettingsStateCache` + tải 1 lần duy nhất bằng `loadSettingsFeatureBatch()` trên `Dispatchers.IO`, chuyển toàn bộ `Natives.*` và `execKsud(...)` khi bật/tắt switch ra khỏi Main Thread, loại bỏ hoàn toàn việc thay đổi Compose `MutableState` trên luồng IO.
      - `SuperUserViewModel.kt`: Chuyển `Shell.getShell().execTask(task)` từ đồng bộ trên Main Thread sang bất đồng bộ `Shell.getShell(Shell.EXECUTOR) { ... }` (xóa điểm nghẽn ~1.6s khi khởi động lạnh) và bỏ qua timeout 1.5s `RootService` khi `isFakeRootNoKernel`.
      - `manager/app/src/main/cpp/ksu.c`: Cache cờ `driver_scanned = true` sau lần quét `/proc/self/fd` đầu tiên, tránh lặp lại hàng trăm lần `opendir`/`readlink` trên máy không có driver.
      - `MainActivity.kt`: Sửa lỗi `if (animateJob === this)` (`CoroutineScope` vs `Job`) bằng `CoroutineStart.LAZY` giúp `userScrollEnabled` không bị kẹt `false` sau lần bấm tab đầu tiên; đặt `beyondViewportPageCount = 0`; truyền `selectedPage = uiSelectedPage` vào `BottomBar`; nhảy trực tiếp (`scrollToPage`) khi chuyển vượt quá 1 tab (Home -> Settings) để không compose thừa Superuser + Module giữa chừng.
      - `SegmentedColumn.kt`: Dùng `DampingRatioNoBouncy` + `.coerceAtLeast(0.dp)` cho radius/padding, `remember` `RoundedCornerShape`, và chỉ bật `clip = true` / cấp phát `Shape` trong `graphicsLayer` khi item đang thực sự animate (`progress < 0.999f`).
      - `Theme.kt`, `SELinuxChecker.kt`, `SuSFSConfigHelper.kt`, `KernelSUApplication.kt`, `HomeViewModel.kt`, `HomePage.kt`, `ModulePage.kt`: Xóa `rememberAsyncImagePainter` thừa (không giải mã bitmap 2 lần), cache `cached_seed_key` tránh chạy `QuantizerCelebi` mỗi lần mở app, memoize `generateTypography()`, lazy hóa `okhttpClient`, bỏ `MonetCompat` trên Android 12+, gom cập nhật `HomeViewModel.systemInfo` thành 1 lần duy nhất, rút gọn `SuSFSConfigHelper.loadStatusInfo()` và đọc trực tiếp `/sys/fs/selinux/enforce` trong `SELinuxChecker.kt`.
  - **Đồng bộ các tính năng & bản vá từ `KOWX712/KernelSU` (`kowsu/master` & `kowsu/waydroid`) + Bổ sung tùy biến UI**:
    - `kernel/` & `userspace/`: Sửa `find_kernel_symbol_exact_cb` dừng ngay ở kết quả khớp đầu tiên (`#3999`), kiểm tra `if (g)` trong `ksu_observer_exit`, và thêm `profile_path()` chống path traversal trong `ksud/src/android/profile.rs`.
    - Hỗ trợ **Flash trực tiếp file APK trong Custom Recovery (TWRP / OrangeFox)** (`6e227de08`): Đóng gói `META-INF/com/google/android/update-binary` & `updater-script` vào APK (`mergeScripts` trong `build.gradle.kts`). Đổi tên `.apk` thành `.zip` để flash LKM hoặc `uninstall.zip` để gỡ cài đặt trong Recovery.
    - Tích hợp **Eruda WebUI DevTools Console** (`692e7cf5d`): Nhúng `assets/eruda.min.js` và tự động nạp bảng DevTools nổi ngay trong WebUI trên điện thoại khi bật `Web Debugging`.
    - Thêm lối tắt mở nhanh WebUI **KernelSU Toolkit** (`ksu_toolkit`) và **KPatch Next** (`KPatch-Next`) trong trang Cài đặt (`ddbfca957`), nâng cấp nút Đóng / Khởi động lại chống nhấn đúp ở màn hình `Flash.kt` (`#3998`).
    - Thêm các tùy chọn cá nhân hóa mới trong `MoreSettings.kt`: **Giao diện thẻ Working cổ điển (`Classic Home UI`)**, **Biểu tượng trên nút gạt Switch (`Switch thumb icon`)**, và **Thanh trượt chỉnh độ tối lớp phủ riêng cho thẻ Working (`WorkingCardDimSlider`)**.
  - **Đồng bộ Upstream từ `Baka-SU/BakaSU` (`7b65bde0..63ad1ea3`)**:
    - `kernel/` & `userspace/`: Đồng bộ toàn bộ 15 commit mới nhất từ `BakaSU` (chia tách `selinux_hide` thành `common/auto/susfs` và vá điểm phát hiện `/sys/fs/selinux/access` seqno + timing side-channel, thêm LSM hook `inode_setxattr`, hỗ trợ Vivo `logd` đọc thuộc tính tiến trình root, sửa thanh ghi tham số đầu syscall `arm64` trên Linux 7.3+, sửa căn chỉnh ELF `preinit_runner` và `is_compat_task` / `do_syscall_64` trên `x86_64`, cập nhật binary `waitsys`, đồng bộ `susfs` `priority_uid_filter` + `sus_path_uid_filter` + `package_list`).
    - `js/` & `manager/ui/webui/`: Bổ sung giao diện WebUI File I/O (`KsuIO`, `FileInterface`, `FileInputStreamInterface`, `FileOutputStreamInterface`, `RandomAccessFileInterface`), trình tải Blob/File (`BlobDownloadHandler`, `Download`, `WebUIDownloadInterface`, `assets/webview/download.js`) và giao diện PackageManager mới `ksu.pm()` (`KsuPm.kt`).
    - `manager/ui/screen/susfs/`: Bổ sung 2 tab cấu hình SuSFS mới `PriorityUidFilterTab` (Bộ lọc UID ưu tiên) và `SusPathUidFilterTab` (Hiển thị đường dẫn SUS theo UID) kèm `SuSFSAppSelectionList.kt`.
    - `manager/ui/screen/main/SettingsPage.kt` & `KsuCli.kt`: Bổ sung tùy chọn `Restrict shell root to ADB` (`shell_su_adb_only`) và hỗ trợ cài đặt nóng mô-đun (`moduleNeedsReboot` không bắt khởi động lại nếu mô-đun hỗ trợ hot-install).
  - Thêm hộp thoại cắt ảnh tương tác trực tiếp trong app (`ImageCropDialog.kt`) cho phép người dùng tự căn chỉnh vùng cắt (kéo di chuyển, chụm 2 ngón thu phóng, thanh trượt zoom 100%–400%, lưới 1/3 và khung bo góc đúng tỉ lệ thẻ Working `16:9` hoặc toàn màn hình) trước khi lưu.
  - Cập nhật `BackgroundUtils.cropAndSaveImage` để áp dụng chính xác `BackgroundTransformation(scale, offsetX, offsetY)` do người dùng chọn.
  - Nâng cấp thanh tiêu đề trang chủ (`HomePage.kt`) sang `LargeFlexibleTopAppBar` đồng bộ với toàn app, kết hợp typography M3 Expressive (`ExtraBold`/`Black` với điểm nhấn màu `primary` cho hậu tố `"SU"`, kerning `-0.5sp` và `subtitle` hiển thị phiên bản sắc nét).
  - Làm trong suốt / hiệu ứng kính mờ (frosted glass) cho badge `Built-in` / `LKM` / `x86_64` (`LabelText` trong `Template.kt` & `HomePage.kt`) kèm viền mảnh bán trong suốt, thêm lớp phủ gradient tối điện ảnh (`Color.Black` + `backgroundDim`) kèm chữ trắng sắc nét có bóng đổ (`Shadow`) trên thẻ Working khi cài nền tùy chỉnh để chữ "Đang hoạt động" luôn nổi bật và không bị tiệp màu với ảnh nền.
  - Sửa lỗi cuộn danh sách làm chữ đè lên thanh tiêu đề (`LargeFlexibleTopAppBar` / `SearchAppBar`) ở `SettingsPage.kt`, `MoreSettings.kt`, `HomePage.kt`, `SuperUserPage.kt`, `ModulePage.kt`, `About.kt`, `AppProfile.kt` bằng cách áp dụng `.padding(top = innerPadding.calculateTopPadding())` trực tiếp lên `Modifier` của container cuộn để cắt (clip) vùng cuộn ngay mép dưới TopBar, đồng thời giữ `TopBar` có màu nền đặc `surfaceContainer` khi không bật Blur hoặc Hình nền toàn màn hình.
  - Sửa `CardStyleProvider.determineContentColor` trong `CardManage.kt` dùng chuẩn `MaterialTheme.colorScheme.onSurface` / `isInDarkTheme(ThemeConfig.forceDarkMode)` tránh lỗi trùng màu chữ và màu nền thẻ.
- **[2026-11-22 - Antigravity]**:
  - Nghiên cứu toàn bộ codebase (`kernel`, `userspace`, `manager`).
  - Fix lỗi chọn ảnh thẻ Working & Fullscreen Background trên ROM AOSP bằng bộ crop nội bộ `cropAndSaveImage`.
  - Tối ưu hóa tốc độ tải trang SuperUser (bỏ chờ 8s無root, load song song đa luồng + cache label/pinyin).
  - Áp dụng Material Design 3 Expressive: nâng cấp `AppShapes` bo góc mềm mại hơn (`24.dp` / `32.dp`), đồng bộ token bo góc toàn app, nâng cấp spring motion cho `BottomBar`, `SegmentedColumn`, `VerticalExpandableFab`, `HomePage` và `ScalePredictiveBackAnimation`.
  - Build APK `arm64-v8a` release và cập nhật vào `out/SovaSU-fakeroot-test.apk`.
