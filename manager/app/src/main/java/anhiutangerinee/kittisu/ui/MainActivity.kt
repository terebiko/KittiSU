package anhiutangerinee.kittisu.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.NavigationEventState
import androidx.navigationevent.compose.rememberNavigationEventState
import anhiutangerinee.kittisu.Natives
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.security.LockScreen
import anhiutangerinee.kittisu.security.ManagerSecurity
import anhiutangerinee.kittisu.security.RebootRequiredScreen
import anhiutangerinee.kittisu.security.SecurityUiState
import anhiutangerinee.kittisu.security.WorkingOverlay
import anhiutangerinee.kittisu.security.messageResForKey
import anhiutangerinee.kittisu.ui.activity.PermissionRequestInterface
import anhiutangerinee.kittisu.ui.activity.component.BottomBar
import anhiutangerinee.kittisu.ui.activity.component.NavigationBar
import anhiutangerinee.kittisu.ui.activity.util.ThemeChangeContentObserver
import anhiutangerinee.kittisu.ui.activity.util.ThemeUtils
import anhiutangerinee.kittisu.ui.animation.predictiveback.AOSPCrossActivityAnimation
import anhiutangerinee.kittisu.ui.animation.predictiveback.KernelSUClassicPredictiveBackAnimation
import anhiutangerinee.kittisu.ui.animation.predictiveback.MiuixPredictiveBackAnimation
import anhiutangerinee.kittisu.ui.animation.predictiveback.NoPredictiveBackAnimation
import anhiutangerinee.kittisu.ui.animation.predictiveback.ScalePredictiveBackAnimation
import anhiutangerinee.kittisu.ui.component.InstallConfirmationDialog
import anhiutangerinee.kittisu.ui.component.ZipFileDetector
import anhiutangerinee.kittisu.ui.component.ZipFileInfo
import anhiutangerinee.kittisu.ui.component.ZipType
import anhiutangerinee.kittisu.ui.navigation.HandleDeepLink
import anhiutangerinee.kittisu.ui.navigation.DeepLinkResolver
import anhiutangerinee.kittisu.ui.navigation.LocalNavigator
import anhiutangerinee.kittisu.ui.navigation.ModuleDeepLink
import anhiutangerinee.kittisu.ui.navigation.Route
import anhiutangerinee.kittisu.ui.navigation.rememberNavigator
import anhiutangerinee.kittisu.ui.screen.AppProfileScreen
import anhiutangerinee.kittisu.ui.screen.AppProfileTemplateScreen
import anhiutangerinee.kittisu.ui.screen.BottomBarDestination
import anhiutangerinee.kittisu.ui.screen.ExecuteModuleActionScreen
import anhiutangerinee.kittisu.ui.screen.FlashIt
import anhiutangerinee.kittisu.ui.screen.FlashScreen
import anhiutangerinee.kittisu.ui.screen.InstallScreen
import anhiutangerinee.kittisu.ui.screen.SulogScreen
import anhiutangerinee.kittisu.ui.screen.TemplateEditorScreen
import anhiutangerinee.kittisu.ui.screen.UmountManagerScreen
import anhiutangerinee.kittisu.ui.screen.DynamicManagerScreen
import anhiutangerinee.kittisu.ui.screen.about.AboutScreen
import anhiutangerinee.kittisu.ui.screen.about.OpenSourceLicenseScreen
import anhiutangerinee.kittisu.ui.screen.moduleRepo.ModuleRepoScreen
import anhiutangerinee.kittisu.ui.screen.moduleRepo.OnlineModuleDetailScreen
import anhiutangerinee.kittisu.ui.screen.modulePreset.ModulePresetDetailScreen
import anhiutangerinee.kittisu.ui.screen.modulePreset.ModulePresetEditorScreen
import anhiutangerinee.kittisu.ui.screen.modulePreset.ModulePresetSourcesScreen
import anhiutangerinee.kittisu.ui.screen.modulePreset.ModulePresetsScreen
import anhiutangerinee.kittisu.ui.screen.susfs.SuSFSConfigScreen
import anhiutangerinee.kittisu.ui.theme.KernelSUTheme
import anhiutangerinee.kittisu.ui.theme.ThemeConfig
import anhiutangerinee.kittisu.ui.theme.backgroundImagePainter
import anhiutangerinee.kittisu.ui.theme.blurSource
import anhiutangerinee.kittisu.ui.theme.fullScreenBackgroundPainter
import anhiutangerinee.kittisu.ui.util.LocalBlurState
import anhiutangerinee.kittisu.ui.util.LocalHandlePageChange
import anhiutangerinee.kittisu.ui.util.LocalPagerState
import anhiutangerinee.kittisu.ui.util.LocalPermissionRequestInterface
import anhiutangerinee.kittisu.ui.util.LocalSelectedPage
import anhiutangerinee.kittisu.ui.util.LocalSnackbarHost
import anhiutangerinee.kittisu.ui.util.install
import anhiutangerinee.kittisu.ui.util.module.PresetPostInstallManager
import anhiutangerinee.kittisu.ui.util.rootAvailable
import anhiutangerinee.kittisu.ui.viewmodel.HomeViewModel
import anhiutangerinee.kittisu.ui.viewmodel.SuperUserViewModel
import anhiutangerinee.kittisu.ui.webui.WebUIActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import zako.zako.zako.zakoui.screen.kernelFlash.KernelFlashScreen
import zako.zako.zako.zakoui.screen.moreSettings.MoreSettingsScreen
import zako.zako.zako.zakoui.screen.moreSettings.util.LocaleHelper
import kotlin.coroutines.resume
import kotlin.math.abs

@Composable
fun rememberScrollConnection(
    isScrollingDown: MutableState<Boolean>,
    scrollOffset: MutableState<Float>,
    previousScrollOffset: MutableState<Float>,
    threshold: Float = 50f
): NestedScrollConnection {
    return remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val newOffset = scrollOffset.value + delta
                scrollOffset.value = newOffset
                val scrollDelta = previousScrollOffset.value - newOffset

                if (abs(scrollDelta) > threshold) {
                    isScrollingDown.value = scrollDelta > 0
                    previousScrollOffset.value = newOffset
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                previousScrollOffset.value = scrollOffset.value
                return super.onPostFling(consumed, available)
            }
        }
    }
}

class MainActivity : FragmentActivity() {
    private lateinit var superUserViewModel: SuperUserViewModel
    private lateinit var homeViewModel: HomeViewModel
    internal val settingsStateFlow = MutableStateFlow(SettingsState())

    data class SettingsState(
        val isHideOtherInfo: Boolean = false,
        val dpi: Int = 0,
        val predictiveBackAnimation: PredictiveBackAnimation = PredictiveBackAnimation.Scale,
        val predictiveBackExitDirection: PredictiveBackExitDirection = PredictiveBackExitDirection.FOLLOW_GESTURE
    )

    enum class PredictiveBackAnimation(val value: String) {
        None("none"),
        AOSP("aosp"),
        MIUIX("miuix"),
        Scale("scale"),
        KernelSUClassic("ksu_classic");

        companion object {
            fun fromValueOrDefault(value: String) =
                PredictiveBackAnimation.entries.find { it.value == value } ?: Scale
        }
    }

    enum class PredictiveBackExitDirection(val value: String) {
        /** Follows the user's swipe gesture direction (e.g., swipe left -> exit right). */
        FOLLOW_GESTURE("follow_gesture"),

        /** Always translates to the right, regardless of swipe edge. */
        ALWAYS_RIGHT("always_right"),

        /** Always translates to the left, regardless of swipe edge. */
        ALWAYS_LEFT("always_left");

        companion object {
            fun fromValueOrDefault(value: String) =
                PredictiveBackExitDirection.entries.find { it.value == value } ?: FOLLOW_GESTURE
        }
    }

    private var showConfirmationDialog = mutableStateOf(false)
    private var pendingZipFiles = mutableStateOf<List<ZipFileInfo>>(emptyList())

    private lateinit var themeChangeObserver: ThemeChangeContentObserver
    private var isInitialized = false

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.applyLanguage(it) })
    }

    private val intentState = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        try {

            // Enable edge to edge
            enableEdgeToEdge()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }

            super.onCreate(savedInstanceState)

            val isManager = Natives.isManager
            if (isManager && !Natives.requireNewKernel()) {
                install()
            }

            // 使用标记控制初始化流程
            if (!isInitialized) {
                initializeViewModels()
                initializeData()
                isInitialized = true
            }

            // Check if launched with a ZIP file
            val zipUri: ArrayList<Uri>? = when (intent?.action) {
                Intent.ACTION_SEND -> {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                    uri?.let { arrayListOf(it) }
                }

                Intent.ACTION_SEND_MULTIPLE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                    }
                }

                else -> when {
                    intent?.data != null -> arrayListOf(intent.data!!)
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                        intent.getParcelableArrayListExtra("uris", Uri::class.java)
                    }
                    else -> {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra("uris")
                    }
                }
            }

            setContent {
                KernelSUTheme {
                    val context = LocalContext.current

                    LaunchedEffect(Unit) {
                        ManagerSecurity.initialize()
                    }

                    val securityState by ManagerSecurity.state.collectAsState()

                    // Hide manager content from recents screenshots while gated.
                    LaunchedEffect(securityState) {
                        if (securityState == SecurityUiState.Unlocked) {
                            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                            window.setFlags(
                                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                                android.view.WindowManager.LayoutParams.FLAG_SECURE
                            )
                        }
                    }

                    val securityScope = rememberCoroutineScope()
                    val showManagerUi = securityState == SecurityUiState.Unconfigured ||
                        securityState == SecurityUiState.Unlocked

                    if (showManagerUi) {
                        LaunchedEffect(securityState) {
                            ManagerSecurity.onEnterForeground()
                        }
                    }

                    LaunchedEffect(zipUri, securityState) {
                        if (!showManagerUi) return@LaunchedEffect
                        if (zipUri.isNullOrEmpty()) return@LaunchedEffect

                        lifecycleScope.launch(Dispatchers.IO) {
                            val zipFileInfos = zipUri.map { uri ->
                                ZipFileDetector.parseZipFile(context, uri)
                            }.filter { it.type != ZipType.UNKNOWN }

                            withContext(Dispatchers.Main) {
                                if (zipFileInfos.isNotEmpty()) {
                                    pendingZipFiles.value = zipFileInfos
                                    showConfirmationDialog.value = true
                                } else {
                                    finish()
                                }
                            }
                        }
                    }

                    val settings by settingsStateFlow.collectAsState()
                    val systemDensity = LocalDensity.current

                    val density = remember(systemDensity, settings.dpi) {
                        if (settings.dpi <= 0f) {
                            systemDensity
                        } else {
                            val targetDensity = settings.dpi / 160f
                            Density(density = targetDensity, fontScale = systemDensity.fontScale)
                        }
                    }

                    val navigator = rememberNavigator(Route.Main)

                    LaunchedEffect(Unit) {
                        if (PresetPostInstallManager.hasPendingScript()) {
                            val pending = PresetPostInstallManager.loadPendingScripts()
                            pending?.let {
                                navigator.push(
                                    Route.Flash(
                                        FlashIt.FlashScripts(
                                            scripts = it.scripts,
                                            fromPending = true
                                        )
                                    )
                                )
                            }
                        }
                    }

                    lateinit var permissionRequestHandler: ManagedActivityResultLauncher<Array<String>, Map<String, @JvmSuppressWildcards Boolean>>

                    val permissionRequestInterface = object : PermissionRequestInterface {
                        private val mutex = Mutex()
                        private var currentCallback: ((Map<String, @JvmSuppressWildcards Boolean>) -> Unit)? =
                            null

                        override fun requestPermission(
                            permission: String,
                            callback: (Boolean) -> Unit,
                            requestDescription: String
                        ) {
                            if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                                callback(true)
                                return
                            }

                            lifecycleScope.launch {
                                mutex.withLock {
                                    suspendCancellableCoroutine { continuation ->
                                        currentCallback = { result ->
                                            callback(result.any { it.value })
                                            continuation.resume(Unit)
                                        }

                                        if (requestDescription.isNotBlank() && ActivityCompat.shouldShowRequestPermissionRationale(
                                                this@MainActivity,
                                                permission
                                            )
                                        )
                                            Toast.makeText(
                                                context,
                                                requestDescription,
                                                Toast.LENGTH_SHORT
                                            ).show()

                                        permissionRequestHandler.launch(arrayOf(permission))
                                    }
                                }
                            }
                        }

                        override fun requestPermissions(
                            permissions: Array<String>,
                            callback: (Map<String, @JvmSuppressWildcards Boolean>) -> Unit,
                            requestDescription: Map<String, String>
                        ) {
                            val permissionsToRequest = permissions.filter {
                                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
                            }.toTypedArray()

                            if (permissionsToRequest.isEmpty()) {
                                callback(permissions.associateWith { true })
                                return
                            }

                            lifecycleScope.launch {
                                mutex.withLock {
                                    suspendCancellableCoroutine { continuation ->
                                        currentCallback = { result ->
                                            val finalResult = permissions.associateWith { perm ->
                                                result[perm] ?: true
                                            }
                                            callback(finalResult)
                                            continuation.resume(Unit)
                                        }

                                        permissionsToRequest.forEach { perm ->
                                            if (ActivityCompat.shouldShowRequestPermissionRationale(
                                                    this@MainActivity,
                                                    perm
                                                )
                                            ) {
                                                val msg = requestDescription[perm]
                                                if (!msg.isNullOrBlank()) {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        msg,
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }

                                        permissionRequestHandler.launch(permissionsToRequest)
                                    }
                                }
                            }
                        }

                        fun onPermissionRequestCallback(result: Map<String, @JvmSuppressWildcards Boolean>) =
                            currentCallback?.invoke(result)
                    }

                    permissionRequestHandler = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions(),
                        onResult = permissionRequestInterface::onPermissionRequestCallback
                    )

                    CompositionLocalProvider(
                        LocalPermissionRequestInterface provides permissionRequestInterface,
                        LocalNavigator provides navigator,
                        LocalDensity provides density
                    ) {
                        Box(Modifier.fillMaxSize()) {
                        if (showManagerUi) {
                            HandleDeepLink(
                                intentState = intentState.collectAsState()
                            )

                            ShortcutIntentHandler(
                                intentState = intentState
                            )
                        }

                        InstallConfirmationDialog(
                            show = showConfirmationDialog.value && showManagerUi,
                            zipFiles = pendingZipFiles.value,
                            onConfirm = { confirmedFiles ->
                                showConfirmationDialog.value = false
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val moduleUris =
                                        confirmedFiles.filter { it.type == ZipType.MODULE }
                                            .map { it.uri }
                                    val kernelUris =
                                        confirmedFiles.filter { it.type == ZipType.KERNEL }
                                            .map { it.uri }

                                    when {
                                        kernelUris.isNotEmpty() && moduleUris.isEmpty() -> {
                                            if (kernelUris.size == 1 && rootAvailable()) {
                                                withContext(Dispatchers.Main) {
                                                    navigator.push(
                                                        Route.Install(
                                                            preselectedKernelUri = kernelUris.first()
                                                                .toString()
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        moduleUris.isNotEmpty() -> {
                                            withContext(Dispatchers.Main) {
                                                navigator.push(
                                                    Route.Flash(
                                                        FlashIt.FlashModules(ArrayList(moduleUris))
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            onDismiss = {
                                showConfirmationDialog.value = false
                                pendingZipFiles.value = emptyList()
                                finish()
                            }
                        )

                        val predictiveBackAnimationHandler = remember(
                            settings.predictiveBackAnimation,
                            settings.predictiveBackExitDirection
                        ) {
                            when (settings.predictiveBackAnimation) {
                                PredictiveBackAnimation.None -> NoPredictiveBackAnimation()
                                PredictiveBackAnimation.AOSP -> AOSPCrossActivityAnimation(settings.predictiveBackExitDirection)
                                PredictiveBackAnimation.Scale -> ScalePredictiveBackAnimation(
                                    settings.predictiveBackExitDirection
                                )

                                PredictiveBackAnimation.KernelSUClassic -> KernelSUClassicPredictiveBackAnimation()
                                PredictiveBackAnimation.MIUIX -> MiuixPredictiveBackAnimation()
                            }
                        }
                        val currentPredictiveBackAnimationHandler =
                            rememberUpdatedState(predictiveBackAnimationHandler)

                        var gestureState: NavigationEventState<SceneInfo<NavKey>>? = null
                        val navigationScope = rememberCoroutineScope()
                        val onBack: (() -> Unit) -> Unit = { callBack ->
                            navigationScope.launch {
                                currentPredictiveBackAnimationHandler.value.onBackPressed(
                                    transitionState = gestureState?.transitionState,
                                    currentPageKey = navigator.current()
                                )

                                callBack()

                                when (val top = navigator.current()) {
                                    is Route.TemplateEditor -> {
                                        if (!top.readOnly) {
                                            navigator.setResult("template_edit", true)
                                        } else {
                                            navigator.pop()
                                        }
                                    }

                                    else -> navigator.pop()
                                }
                            }
                        }

                        val entries =
                            rememberDecoratedNavEntries(
                                backStack = navigator.backStack,
                                entryDecorators = listOf(
                                    rememberSaveableStateHolderNavEntryDecorator(),
                                    rememberViewModelStoreNavEntryDecorator(),
                                    NavEntryDecorator(
                                        onPop = { key ->
                                            currentPredictiveBackAnimationHandler.value.onPagePop(
                                                contentPageKey = key,
                                                animationScope = navigationScope
                                            )
                                        }
                                    ) { content ->
                                        val snackBarHostState = remember { SnackbarHostState() }
                                        with(currentPredictiveBackAnimationHandler.value) {
                                            Box(
                                                modifier = Modifier
                                                    .predictiveBackAnimationDecorator(
                                                        gestureState?.transitionState,
                                                        content.contentKey,
                                                        navigator.current()
                                                    )
                                                    .then(
                                                        if (!ThemeConfig.backgroundImageLoaded) Modifier.background(
                                                            MaterialTheme.colorScheme.surfaceContainer
                                                        ) else Modifier
                                                    )
                                            ) {
                                                val surfaceContainer =
                                                    MaterialTheme.colorScheme.surfaceContainer

                                                CompositionLocalProvider(
                                                    LocalBlurState provides rememberMaterial3BlurBackdrop(
                                                        ThemeConfig.isEnableBlur
                                                    ),
                                                    LocalSnackbarHost provides snackBarHostState,
                                                ) {
                                                    if (ThemeConfig.fullScreenBackgroundUri != null && fullScreenBackgroundPainter != null) {
                                                        val it = fullScreenBackgroundPainter!!
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .zIndex(-1f)
                                                                .paint(
                                                                    painter = it,
                                                                    contentScale = ContentScale.Crop,
                                                                )
                                                                .drawWithContent {
                                                                    drawContent()
                                                                    drawRect(
                                                                        color = Color.Black.copy(
                                                                            alpha = ThemeConfig.fullScreenBackgroundDim
                                                                        )
                                                                    )
                                                                }
                                                        )
                                                    }
                                                    content.Content()
                                                }
                                            }
                                        }
                                    }
                                ),
                                entryProvider = entryProvider {
                                    entry<Route.About> { AboutScreen() }
                                    entry<Route.OpenSourceLicense> { OpenSourceLicenseScreen() }
                                    entry<Route.Sulog> { SulogScreen() }
                                    entry<Route.Main> { MainScreen() }
                                    entry<Route.AppProfileTemplate> { AppProfileTemplateScreen() }
                                    entry<Route.TemplateEditor> { key ->
                                        TemplateEditorScreen(
                                            key.template,
                                            key.readOnly
                                        )
                                    }
                                    entry<Route.AppProfile> { key -> AppProfileScreen(key.appGroup) }
                                    entry<Route.ModuleRepo> { ModuleRepoScreen() }
                                    entry<Route.ModuleRepoDetail> { key ->
                                        OnlineModuleDetailScreen(
                                            key.module
                                        )
                                    }
                                    entry<Route.ModulePresets> { ModulePresetsScreen() }
                                    entry<Route.ModulePresetEditor> { key -> ModulePresetEditorScreen(key.preset) }
                                    entry<Route.ModulePresetDetail> { key -> ModulePresetDetailScreen(key.preset) }
                                    entry<Route.ModulePresetSources> { ModulePresetSourcesScreen() }
                                    entry<Route.Install> { key -> InstallScreen(key.preselectedKernelUri) }
                                    entry<Route.Flash> { key -> FlashScreen(key.flashIt) }
                                    entry<Route.ExecuteModuleAction> { key ->
                                        ExecuteModuleActionScreen(
                                            key.moduleId
                                        )
                                    }
                                    entry<Route.Home> { MainScreen() }
                                    entry<Route.SuperUser> { MainScreen() }
                                    entry<Route.Module> { MainScreen() }
                                    entry<Route.Settings> { MainScreen() }
                                    entry<Route.MoreSettings> { MoreSettingsScreen() }
                                    entry<Route.SuSFSConfig> { SuSFSConfigScreen() }
                                    entry<Route.UmountManager> { UmountManagerScreen() }
                                    entry<Route.DynamicManager> { DynamicManagerScreen() }
                                    entry<Route.SecuritySettings> {
                                        anhiutangerinee.kittisu.security.SecuritySettingsScreen()
                                    }
                                    entry<Route.KernelFlash> { key ->
                                        KernelFlashScreen(
                                            key.kernelUri,
                                            key.selectedSlot
                                        )
                                    }
                                },
                            )

                        val sceneState =
                            rememberSceneState(
                                entries = entries,
                                sceneStrategies = listOf(SinglePaneSceneStrategy()),
                                sceneDecoratorStrategies = emptyList(),
                                sharedTransitionScope = null,
                                onBack = {
                                    onBack {}
                                },
                            )
                        val scene = sceneState.currentScene

                        // Predictive Back Handling
                        val currentInfo = SceneInfo(scene)
                        val previousSceneInfos = sceneState.previousScenes.map { SceneInfo(it) }
                        gestureState = rememberNavigationEventState(
                            currentInfo = currentInfo,
                            backInfo = previousSceneInfos
                        )

                        NavigationBackHandler(
                            state = gestureState,
                            isBackEnabled = scene.previousEntries.isNotEmpty(),
                            onBackCompleted = { callBack ->
                                onBack(callBack)
                            },
                            onBackCancelled = { callBack ->
                                callBack()
                            }
                        )

                        NavDisplay(
                            sceneState = sceneState,
                            navigationEventState = gestureState,
                            contentAlignment = Alignment.TopStart,
                            sizeTransform = null,
                            predictivePopTransitionSpec = { swipeEdge ->
                                with(currentPredictiveBackAnimationHandler.value) {
                                    onPredictivePopTransitionSpec(swipeEdge = swipeEdge)
                                }
                            },
                            popTransitionSpec = {
                                with(currentPredictiveBackAnimationHandler.value) {
                                    onPopTransitionSpec()
                                }
                            },
                            transitionSpec = {
                                with(currentPredictiveBackAnimationHandler.value) {
                                    onTransitionSpec()
                                }
                            }
                        )

                        SecurityGateOverlay(
                            state = securityState,
                            activity = this@MainActivity,
                            scope = securityScope,
                            onDestructiveReset = {
                                securityScope.launch { ManagerSecurity.beginDestructiveReset() }
                            },
                        )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Increment intentState to trigger LaunchedEffect re-execution
        intentState.value += 1
    }

    private fun initializeViewModels() {
        superUserViewModel = SuperUserViewModel()
        homeViewModel = HomeViewModel()

        // 设置主题变化监听器
        themeChangeObserver = ThemeUtils.registerThemeChangeObserver(this)
    }

    private fun initializeData() {
        lifecycleScope.launch {
            try {
                superUserViewModel.fetchAppList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 初始化主题相关设置
        ThemeUtils.initializeThemeSettings(this, settingsStateFlow)
    }

    override fun onResume() {
        try {
            super.onResume()
            ManagerSecurity.onEnterForeground()
            ThemeUtils.onActivityResume()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        try {
            super.onPause()
            ManagerSecurity.onEnterBackground()
            ThemeUtils.onActivityPause(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        try {
            ThemeUtils.unregisterThemeChangeObserver(this, themeChangeObserver)
            super.onDestroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Remember a LayerBackdrop for Material 3 with a surfaceContainer background
 * to prevent alpha-blending artifacts.
 *
 * @param enableBlur Whether the blur effect is globally enabled.
 * @return A LayerBackdrop instance if supported and enabled, null otherwise.
 */@Composable
fun rememberMaterial3BlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null

    val backgroundColor =
        MaterialTheme.colorScheme.surfaceContainer

    return rememberLayerBackdrop {
        if (ThemeConfig.isEnableBlurExp) {
            if(false) {
                val painter = backgroundImagePainter!!
                with(painter) {
                    draw(size = drawContext.size)
                }
            }
        } else {
            drawRect(backgroundColor)
        }

        drawRect(
            color = backgroundColor.copy(alpha = ThemeConfig.backgroundDim)
        )

        drawContent()
    }
}

@Composable
fun MainScreen() {
    // 页面隐藏处理
    val activity = LocalActivity.current as MainActivity

    var savedPages by rememberSaveable<MutableState<List<BottomBarDestination>>> {
        mutableStateOf(emptyList())
    }

    val pages by produceState(initialValue = savedPages) {
        value = withContext(Dispatchers.IO) {
            savedPages = BottomBarDestination.getPages()
            return@withContext savedPages
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var uiSelectedPage by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = uiSelectedPage,
        pageCount = { pages.size }
    )
    val isScrollingDown = remember { mutableStateOf(false) }
    val scrollOffset = remember { mutableFloatStateOf(0f) }
    val previousScrollOffset = remember { mutableFloatStateOf(0f) }
    var userScrollEnabled by remember { mutableStateOf(true) }
    var animating by remember { mutableStateOf(false) }
    var animateJob by remember { mutableStateOf<Job?>(null) }
    var lastRequestedPage by remember { mutableIntStateOf(pagerState.currentPage) }

    val handlePageChange: (Int) -> Unit = remember(pagerState, coroutineScope) {
        { page ->
            uiSelectedPage = page
            if (page == pagerState.currentPage) {
                if (animateJob != null && lastRequestedPage != page) {
                    animateJob?.cancel()
                    animateJob = null
                    animating = false
                    userScrollEnabled = true
                }
                lastRequestedPage = page
            } else {
                if (animateJob != null && lastRequestedPage == page) {
                    // Already animating to the requested page
                } else {
                    animateJob?.cancel()
                    animating = true
                    userScrollEnabled = false
                    val job = coroutineScope.launch {
                        try {
                            pagerState.animateScrollToPage(page)
                        } finally {
                            if (animateJob === this) {
                                userScrollEnabled = true
                                animating = false
                                animateJob = null
                            }
                        }
                    }
                    animateJob = job
                    lastRequestedPage = page
                }
            }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (!animating) uiSelectedPage = page
        }
    }

    BackHandler {
        if (pagerState.currentPage != 0) {
            handlePageChange(0)
        } else {
            activity.moveTaskToBack(true)
        }
    }

    CompositionLocalProvider(
        LocalPagerState provides pagerState,
        LocalHandlePageChange provides handlePageChange,
        LocalSelectedPage provides uiSelectedPage
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val isPortrait = maxWidth < maxHeight || (maxHeight / maxWidth > 1.4f)

            if (isPortrait) {
                val bottomBarScrollConnection = rememberScrollConnection(
                    isScrollingDown = isScrollingDown,
                    scrollOffset = scrollOffset,
                    previousScrollOffset = previousScrollOffset
                )
                val showBottomBar = !isScrollingDown.value

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            modifier = Modifier
                                .fillMaxSize()
                                .blurSource()
                                .nestedScroll(bottomBarScrollConnection),
                            state = pagerState,
                            userScrollEnabled = userScrollEnabled,
                        ) { pageIndex ->
                            if (pages.isEmpty()) return@HorizontalPager

                            val snackBarHostState = remember { SnackbarHostState() }
                            CompositionLocalProvider(
                                LocalSnackbarHost provides snackBarHostState,
                                LocalBlurState provides rememberMaterial3BlurBackdrop(ThemeConfig.isEnableBlur),
                            ) {
                                val destination = pages[pageIndex]
                                destination.direction(innerPadding.calculateBottomPadding())
                            }
                        }

                        AnimatedVisibility(
                            visible = showBottomBar,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .windowInsetsPadding(WindowInsets.navigationBars),
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            BottomBar(
                                destinations = pages,
                                selectedPage = pagerState.currentPage,
                                onPageChange = handlePageChange
                            )
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationBar(destinations = pages)
                    HorizontalPager(
                        modifier = Modifier
                            .fillMaxSize()
                            .blurSource(),
                        state = pagerState,
                        userScrollEnabled = userScrollEnabled,
                    ) { pageIndex ->
                        if (pages.isEmpty()) return@HorizontalPager

                        val snackBarHostState = remember { SnackbarHostState() }
                        CompositionLocalProvider(
                            LocalSnackbarHost provides snackBarHostState,
                            LocalBlurState provides rememberMaterial3BlurBackdrop(ThemeConfig.isEnableBlur),
                        ) {
                            val destination = pages[pageIndex]
                            destination.direction(0.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityGateOverlay(
    state: SecurityUiState,
    activity: FragmentActivity,
    scope: kotlinx.coroutines.CoroutineScope,
    onDestructiveReset: () -> Unit,
) {
    when (state) {
        SecurityUiState.Loading,
        SecurityUiState.Unconfigured,
        SecurityUiState.Unlocked -> Unit

        is SecurityUiState.Locked -> {
            GateSurface {
                val biometricLauncher = rememberBiometricLauncher(
                    activity = activity,
                    enabled = state.biometricEnabled && !state.lockdown,
                    onSuccess = { scope.launch { ManagerSecurity.unlockWithBiometric() } },
                    onError = {
                        scope.launch { ManagerSecurity.noteMessage("security_biometric_failed") }
                    },
                )
                LockScreen(
                    method = state.method,
                    lockdown = state.lockdown,
                    biometricPrompt = biometricLauncher,
                    onVerified = { credential ->
                        scope.launch { ManagerSecurity.verify(credential) }
                    },
                    onPatternVerified = { pattern ->
                        scope.launch { ManagerSecurity.verify(pattern.toCharArray()) }
                    },
                    onDestructiveReset = onDestructiveReset,
                )
            }
        }

        is SecurityUiState.Working -> {
            GateSurface { WorkingOverlay(state.messageKey) }
        }

        SecurityUiState.Resetting -> {
            GateSurface {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.security_resetting))
                }
            }
        }

        SecurityUiState.RebootRequired -> {
            GateSurface { RebootRequiredScreen() }
        }

        SecurityUiState.Corrupted -> {
            // Fail closed: security state unreadable -> only the destructive reset remains.
            GateSurface {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                ) {
                    Text(
                        stringResource(R.string.security_corrupted_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.security_corrupted_summary),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(24.dp))
                    androidx.compose.material3.TextButton(onClick = onDestructiveReset) {
                        Text(stringResource(R.string.security_reset_link))
                    }
                }
            }
        }
    }
}

@Composable
private fun GateSurface(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        content()
    }
}

@Composable
private fun rememberBiometricLauncher(
    activity: FragmentActivity,
    enabled: Boolean,
    onSuccess: () -> Unit,
    onError: () -> Unit,
): (() -> Unit)? {
    if (!enabled) return null
    val context = LocalContext.current
    val canAuthenticate = remember(enabled) {
        runCatching {
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        }.getOrDefault(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
    if (!canAuthenticate) return null
    return {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.security_biometric_unlock))
            .setNegativeButtonText(activity.getString(android.R.string.cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        onError()
                    }
                }
            },
        )
        runCatching { prompt.authenticate(promptInfo) }
            .onFailure { onError() }
    }
}

@Composable
private fun ShortcutIntentHandler(
    intentState: MutableStateFlow<Int>
) {
    val activity = LocalActivity.current ?: return
    val context = LocalContext.current
    val intentStateValue by intentState.collectAsState()
    LaunchedEffect(intentStateValue) {
        val intent = activity.intent
        when (val link = DeepLinkResolver.parseModuleDeepLink(context, intent?.data)) {
            is ModuleDeepLink.WebUi -> {
                val webIntent = Intent(context, WebUIActivity::class.java)
                    .putExtra("id", link.moduleId)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
                context.startActivity(webIntent)
            }

            else -> Unit
        }
    }
}
