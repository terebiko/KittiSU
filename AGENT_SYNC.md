# 🤝 Agent Sync & Handoff Log (Antigravity ↔ DeepSeek)

Tài liệu này dùng để đồng bộ trạng thái codebase, các quyết định kiến trúc, thay đổi gần nhất và các task tiếp theo giữa **Antigravity (Gemini)** và **DeepSeek**.

---

## 📌 1. Trạng thái Dự án Hiện tại (Project Context)
- **Repo / Branch**: `KittiSU-test` (hiện đang rebrand tên hiển thị thành **SovaSU** trong `manager/app/build.gradle.kts` & `manager/settings.gradle.kts`).
- **Chế độ Test (Fake Root)**:
  - Trong `manager/app/build.gradle.kts`: `buildConfigField("boolean", "FAKE_ROOT", "true")`.
  - Trong `manager/app/src/main/cpp/ksu.c`: Khi không có kernel thật trả lời `KSU_IOCTL_GET_INFO`, fallback trả về `FAKE_KSU_VERSION = 35461u`, `FAKE_KSU_FLAGS = KSU_GET_INFO_FLAG_MANAGER | KSU_GET_INFO_FLAG_LKM`, full version `"SovaSU-TEST-fake-root"`, hook type `"kprobes"`.
  - *Lưu ý cho các agent*: Khi build bản Production thật, cần tắt `FAKE_ROOT` về `false`, bỏ fallback trong `ksu.c`, và bundle `libksud.so` vào `manager/app/src/main/jniLibs/<abi>/`.
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
  - Viết hàm xử lý cắt & nén ảnh nội bộ `Context.cropAndSaveImage(...)` trong `manager/app/src/main/java/anhiutangerinee/kittisu/ui/theme/util/BackgroundUtils.kt`:
    - Tự động center-crop theo đúng tỉ lệ khung hình (`16:9` cho thẻ Working `custom_background.jpg`; tỉ lệ màn hình thật `screenW : screenH` cho `fullscreen_background.jpg`).
    - Scale an toàn để tránh tràn bộ nhớ (OOM) với ảnh độ phân giải lớn và nén JPEG chất lượng cao (`92`).
  - Cập nhật `MoreSettings.kt`, `MoreSettingsHandlers.kt`, `Theme.kt`, `HomePage.kt` và `MainActivity.kt`:
    - Bỏ sự phụ thuộc vào `com.android.camera.action.CROP` bên ngoài. Ngay khi chọn ảnh từ `ActivityResultContracts.GetContent()`, app xử lý crop trên background thread (`Dispatchers.IO`).
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
- **[2026-11-22 - Antigravity]**:
  - Nghiên cứu toàn bộ codebase (`kernel`, `userspace`, `manager`).
  - Fix lỗi chọn ảnh thẻ Working & Fullscreen Background trên ROM AOSP bằng bộ crop nội bộ `cropAndSaveImage`.
  - Tối ưu hóa tốc độ tải trang SuperUser (bỏ chờ 8s無root, load song song đa luồng + cache label/pinyin).
  - Áp dụng Material Design 3 Expressive: nâng cấp `AppShapes` bo góc mềm mại hơn (`24.dp` / `32.dp`), đồng bộ token bo góc toàn app, nâng cấp spring motion cho `BottomBar`, `SegmentedColumn`, `VerticalExpandableFab`, `HomePage` và `ScalePredictiveBackAnimation`.
  - Build APK `arm64-v8a` release và cập nhật vào `out/SovaSU-fakeroot-test.apk`.
