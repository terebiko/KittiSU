package anhiutangerinee.kittisu.ui.screen.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.system.Os
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.MenuBook
import androidx.compose.material.icons.automirrored.twotone.OpenInNew
import androidx.compose.material.icons.twotone.Adb
import androidx.compose.material.icons.twotone.Android
import androidx.compose.material.icons.twotone.ChevronRight
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.Group
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Key
import androidx.compose.material.icons.twotone.Link
import androidx.compose.material.icons.twotone.LocalPolice
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.PhoneAndroid
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.SettingsSuggest
import androidx.compose.material.icons.twotone.Storage
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.material.icons.twotone.Block
import androidx.compose.material.icons.twotone.TaskAlt
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import anhiutangerinee.kittisu.BuildConfig
import anhiutangerinee.kittisu.KernelSUApplication
import anhiutangerinee.kittisu.KernelVersion
import anhiutangerinee.kittisu.Natives
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.data.update.UpdateChannel
import anhiutangerinee.kittisu.data.update.checkManagerUpdate
import anhiutangerinee.kittisu.magica.MagicaService
import anhiutangerinee.kittisu.ui.component.KsuIsValid
import anhiutangerinee.kittisu.ui.component.SwipeableSnackbarHost
import anhiutangerinee.kittisu.ui.component.WarningCard
import anhiutangerinee.kittisu.ui.component.ksuIsValid
import anhiutangerinee.kittisu.ui.component.rememberConfirmDialog
import anhiutangerinee.kittisu.ui.component.rememberLoadingDialog
import anhiutangerinee.kittisu.ui.navigation.LocalNavigator
import anhiutangerinee.kittisu.ui.navigation.Route
import anhiutangerinee.kittisu.ui.screen.LabelText
import anhiutangerinee.kittisu.ui.theme.CardConfig
import anhiutangerinee.kittisu.ui.theme.CardConfig.cardElevation
import anhiutangerinee.kittisu.ui.theme.ThemeConfig
import anhiutangerinee.kittisu.ui.theme.blurEffect
import anhiutangerinee.kittisu.ui.theme.blurSource
import anhiutangerinee.kittisu.ui.theme.getCardColors
import anhiutangerinee.kittisu.ui.theme.getCardElevation
import anhiutangerinee.kittisu.ui.theme.m3CardContainerColor
import anhiutangerinee.kittisu.ui.util.LocalSnackbarHost
import anhiutangerinee.kittisu.ui.util.module.LatestVersionInfo
import anhiutangerinee.kittisu.ui.util.reboot
import anhiutangerinee.kittisu.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @author ShirkNeko
 * @date 2025/9/29.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomePage(
    bottomPadding: Dp,
) {
    val context = LocalContext.current
    val viewModel = viewModel<HomeViewModel>(
        viewModelStoreOwner = context.applicationContext as KernelSUApplication
    )
    val coroutineScope = rememberCoroutineScope()

    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            viewModel.refreshData(context, false)
        }
    }

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val scrollState = rememberScrollState()
    val navigator = LocalNavigator.current
    val loadingDialog = rememberLoadingDialog()
    val scope = rememberCoroutineScope()
    var showSuperKeyDialog by remember { mutableStateOf(false) }

    if (showSuperKeyDialog) {
        var inputKey by remember { mutableStateOf(anhiutangerinee.kittisu.KernelPatchManager.loadSavedSuperKey()) }
        var showKey by remember { mutableStateOf(false) }
        var isVerifying by remember { mutableStateOf(false) }
        val unlockOkMsg = stringResource(R.string.home_superkey_unlock_success)
        val unlockFailMsg = stringResource(R.string.home_superkey_unlock_failed)

        AlertDialog(
            onDismissRequest = { if (!isVerifying) showSuperKeyDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.TwoTone.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(stringResource(R.string.home_superkey_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.home_superkey_dialog_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inputKey,
                        onValueChange = { inputKey = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.install_folkpatch_superkey_title)) },
                        visualTransformation = if (showKey) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    imageVector = if (showKey) Icons.TwoTone.VisibilityOff else Icons.TwoTone.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = inputKey.isNotBlank() && !isVerifying,
                    onClick = {
                        isVerifying = true
                        viewModel.unlockKernelPatch(context, inputKey.trim()) { ok ->
                            isVerifying = false
                            if (ok) {
                                showSuperKeyDialog = false
                                Toast.makeText(context, unlockOkMsg, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, unlockFailMsg, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.home_superkey_unlock_action))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isVerifying,
                    onClick = { showSuperKeyDialog = false }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopBar(
                viewModel = viewModel,
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        snackbarHost = {
            SwipeableSnackbarHost(
                modifier = Modifier.padding(bottom = bottomPadding),
                hostState = LocalSnackbarHost.current
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            state = pullRefreshState,
            isRefreshing = viewModel.isRefreshing,
            onRefresh = { viewModel.refreshData(context) },
            modifier = Modifier
                .fillMaxSize()
                .blurSource(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier
                        .padding(top = innerPadding.calculateTopPadding())
                        .align(Alignment.TopCenter),
                    state = pullRefreshState,
                    isRefreshing = viewModel.isRefreshing,
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding() + 2.dp)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(scrollState)
                    .padding(
                        start = 16.dp,
                        end = 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 状态卡片
                if (viewModel.isCoreDataLoaded) {
                    val apdInstallSuccess = stringResource(R.string.home_apd_install_success)
                    val apdInstallFailed = stringResource(R.string.home_apd_install_failed)
                    StatusCard(
                        systemStatus = viewModel.systemStatus,
                        onClickInstall = {
                            navigator.push(Route.Install(preselectedKernelUri = null))
                        },
                        onClickSuperKey = {
                            showSuperKeyDialog = true
                        },
                        onClickInstallApd = {
                            loadingDialog.showLoading()
                            viewModel.installApdDaemon(context) { ok ->
                                loadingDialog.hide()
                                Toast.makeText(
                                    context,
                                    if (ok) apdInstallSuccess else apdInstallFailed,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onClickJailbreak = {
                            loadingDialog.showLoading()
                            context.startService(Intent(context, MagicaService::class.java))
                            // Manager will be force-stopped and restarted by late-load on success.
                            // If that doesn't happen within timeout, jailbreak likely failed.
                            scope.launch(Dispatchers.IO) {
                                delay(30_000)
                                withContext(Dispatchers.Main) {
                                    loadingDialog.hide()
                                    Toast.makeText(
                                        context,
                                        R.string.jailbreak_timeout,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    )

                    if (viewModel.systemStatus.requireNewKernel) {
                        WarningCard(
                            message = stringResource(
                                id = R.string.incompatible_kernel_msg,
                                Natives.version,
                                Natives.MINIMAL_SUPPORTED_KERNEL
                            ),
                            icon = {
                                Icon(
                                    imageVector = Icons.TwoTone.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }

                    // 警告信息
                    if (BuildConfig.DEBUG) {
                        WarningCard(
                            message = stringResource(R.string.debug_version_notice),
                            icon = {
                                Icon(
                                    imageVector = Icons.TwoTone.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }

                    if (!viewModel.systemStatus.isOfficialSignature) {
                        WarningCard(
                            message = stringResource(
                                R.string.unofficial_version_notice,
                                stringResource(R.string.app_name)
                            ),
                            icon = {
                                Icon(
                                    imageVector = Icons.TwoTone.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }

                    if (BuildConfig.IS_PR_BUILD || Natives.isPrBuild) {
                        WarningCard(
                            message = stringResource(
                                id = R.string.home_pr_build_warning
                            ),
                            icon = {
                                Icon(
                                    imageVector = Icons.TwoTone.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }

                    if (viewModel.systemStatus.ksuVersion != null &&
                        viewModel.systemStatus.kernelPatchImplement == Natives.KernelPatchImplement.KERNEL_PATCH_OFFICIAL
                    ) {
                        WarningCard(
                            message = stringResource(
                                R.string.conflict_with_apatch,
                            ),
                            icon = {
                                Icon(
                                    imageVector = Icons.TwoTone.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }

                    if (viewModel.systemStatus.ksuVersion != null && !viewModel.systemStatus.isRootAvailable) {
                        WarningCard(
                            message = stringResource(id = R.string.grant_root_failed),
                            icon = {
                                Icon(
                                    imageVector = Icons.TwoTone.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }

                val checkUpdate = remember {
                    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .getBoolean("check_update", true)
                }
                if (checkUpdate) {
                    UpdateCard()
                }

                AnimatedVisibility(
                    visible = viewModel.isExtendedDataLoaded,
                    enter = fadeIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    InfoCard(
                        systemInfo = viewModel.systemInfo,
                        isSimpleMode = viewModel.isSimpleMode,
                        isHideSusfsStatus = viewModel.isHideSusfsStatus,
                        isHideZygiskImplement = viewModel.isHideZygiskImplement,
                        isHideMetaModuleImplement = viewModel.isHideMetaModuleImplement,
                    )
                }

                // 链接卡片
                if (!viewModel.isSimpleMode && !viewModel.isHideLinkCard) {
                    DonateCard()
                    LearnMoreCard()
                }

                Spacer(Modifier.height(bottomPadding + 16.dp))
            }
        }
    }
}

@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val channel = remember {
        UpdateChannel.fromPreference(
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("update_channel", null)
        )
    }
    val latestVersionInfo = remember { LatestVersionInfo() }
    val newVersion by produceState(initialValue = latestVersionInfo, channel) {
        value = withContext(Dispatchers.IO) {
            checkManagerUpdate(channel)
        }
    }

    val currentVersionCode = remember { getManagerVersion(context).second }
    val newVersionUrl = newVersion.downloadUrl
    val changelog = newVersion.changelog

    val uriHandler = LocalUriHandler.current
    val title = stringResource(id = R.string.module_changelog)
    val updateText = stringResource(id = R.string.module_update)

    AnimatedVisibility(
        visible = newVersion.isNewerThan(currentVersionCode, BuildConfig.GIT_COMMIT),
        enter = fadeIn() + expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ),
        exit = shrinkVertically() + fadeOut()
    ) {
        val updateDialog = rememberConfirmDialog(onConfirm = { uriHandler.openUri(newVersionUrl) })
        WarningCard(
            message = stringResource(
                R.string.manager_update_available,
                newVersion.versionName.ifBlank { newVersion.versionCode.toString() }
            ),
            color = MaterialTheme.colorScheme.outlineVariant,
            icon = {
                Icon(
                    imageVector = Icons.TwoTone.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                if (changelog.isEmpty()) {
                    uriHandler.openUri(newVersionUrl)
                } else {
                    updateDialog.showConfirm(
                        title = title,
                        content = changelog,
                        markdown = true,
                        confirm = updateText
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RebootDropdownItems(items: Map<Int, String>) {
    items.onEachIndexed { index, (id, reason) ->
        DropdownMenuItem(
            selected = false,
            text = { Text(stringResource(id)) },
            onClick = { reboot(reason) },
            shapes = MenuDefaults.itemShape(
                index = index,
                count = items.size
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBar(
    viewModel: HomeViewModel,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val navigator = LocalNavigator.current
    val appName = stringResource(R.string.app_name)
    val primaryColor = MaterialTheme.colorScheme.primary

    LargeFlexibleTopAppBar(
        modifier = Modifier.blurEffect(),
        title = {
            val styledTitle = remember(appName, primaryColor) {
                androidx.compose.ui.text.buildAnnotatedString {
                    if (appName.endsWith("SU", ignoreCase = true) && appName.length > 2) {
                        val prefix = appName.dropLast(2)
                        val suffix = appName.takeLast(2)
                        pushStyle(
                            androidx.compose.ui.text.SpanStyle(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                            )
                        )
                        append(prefix)
                        pop()
                        pushStyle(
                            androidx.compose.ui.text.SpanStyle(
                                color = primaryColor,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Black
                            )
                        )
                        append(suffix)
                        pop()
                    } else {
                        pushStyle(
                            androidx.compose.ui.text.SpanStyle(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                            )
                        )
                        append(appName)
                        pop()
                    }
                }
            }
            Text(
                text = styledTitle,
                letterSpacing = androidx.compose.ui.unit.TextUnit(
                    -0.5f,
                    androidx.compose.ui.unit.TextUnitType.Sp
                )
            )
        },
        subtitle = {
            if (viewModel.isExtendedDataLoaded) {
                Text(
                    text = "${viewModel.systemInfo.managerVersion.first} (${viewModel.systemInfo.managerVersion.second.toInt()})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor =
                if (ThemeConfig.isEnableBlur || ThemeConfig.isFullScreenBackgroundEnabled)
                    Color.Transparent
                else
                    MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor =
                if (ThemeConfig.isEnableBlur || ThemeConfig.isFullScreenBackgroundEnabled)
                    Color.Transparent
                else
                    MaterialTheme.colorScheme.surfaceContainer,
        ),
        actions = {
            if (viewModel.isCoreDataLoaded) {
                if (viewModel.systemInfo.susfsVersionSupported) {
                    IconButton(onClick = {
                        navigator.push(Route.SuSFSConfig)
                    }) {
                        Icon(
                            imageVector = Icons.TwoTone.Tune,
                            contentDescription = stringResource(R.string.susfs_config_setting_title)
                        )
                    }
                }
                var showDropdown by remember { mutableStateOf(false) }
                KsuIsValid {
                    IconButton(onClick = { showDropdown = true }) {
                        Icon(
                            imageVector = Icons.TwoTone.PowerSettingsNew,
                            contentDescription = stringResource(id = R.string.reboot)
                        )
                        DropdownMenuPopup(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                                val pm = LocalContext.current.getSystemService(Context.POWER_SERVICE) as PowerManager?
                                var methods = mapOf(
                                    R.string.reboot to "",
                                    R.string.reboot_soft to "soft_reboot",
                                    R.string.reboot_recovery to "recovery",
                                    R.string.reboot_bootloader to "bootloader",
                                    R.string.reboot_download to "download",
                                    R.string.reboot_edl to "edl"
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pm?.isRebootingUserspaceSupported == true) {
                                    methods = methods + (R.string.reboot_userspace to "userspace")
                                }
                                RebootDropdownItems(methods)
                            }
                        }
                    }
                }
            }
        },
        windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun StatusCard(
    systemStatus: HomeViewModel.SystemStatus,
    onClickInstall: () -> Unit = {},
    onClickSuperKey: () -> Unit = {},
    onClickInstallApd: () -> Unit = {},
    onClickJailbreak: () -> Unit = {}
) {
    // These values are static for the lifetime of the process; read them once
    // instead of doing JNI/uname work on every recomposition.
    val isSafeMode = remember { Natives.isSafeMode } || anhiutangerinee.kittisu.KernelPatchManager.isSafeMode
    val isLateLoadMode = remember { Natives.isLateLoadMode }
    val machine = remember { Os.uname().machine }
    val isWorking = systemStatus.ksuVersion != null || systemStatus.isKernelPatchActive

    // Container role drives the content role: secondaryContainer -> onSecondaryContainer,
    // errorContainer -> onErrorContainer. Using onSurface here broke contrast in dark mode.
    val statusContainer = if (isWorking) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val hasCustomCardBg = ThemeConfig.customBackgroundUri != null
    val statusOnContainer = when {
        hasCustomCardBg -> Color.White
        isWorking -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }
    val hasAnyCustomBg = hasCustomCardBg || ThemeConfig.isFullScreenBackgroundEnabled
    val badgeContainerColor = when {
        hasCustomCardBg -> Color.Black.copy(alpha = 0.38f)
        hasAnyCustomBg -> MaterialTheme.colorScheme.surface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.50f)
    }
    val badgeBorder = remember(statusOnContainer, hasCustomCardBg, hasAnyCustomBg) {
        androidx.compose.foundation.BorderStroke(
            width = 0.9.dp,
            color = when {
                hasCustomCardBg -> Color.White.copy(alpha = 0.42f)
                hasAnyCustomBg -> statusOnContainer.copy(alpha = 0.28f)
                else -> statusOnContainer.copy(alpha = 0.18f)
            }
        )
    }
    val customBgTextShadow = remember(hasCustomCardBg) {
        if (hasCustomCardBg) {
            androidx.compose.ui.graphics.Shadow(
                color = Color.Black.copy(alpha = 0.75f),
                offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                blurRadius = 8f
            )
        } else null
    }

    ElevatedCard(
        shape = MaterialTheme.shapes.extraLarge,
        colors = getCardColors(statusContainer).let {
            if (hasCustomCardBg) {
                CardDefaults.elevatedCardColors(
                    containerColor = Color.Transparent,
                    contentColor = statusOnContainer
                )
            } else {
                CardDefaults.elevatedCardColors(
                    containerColor = statusContainer.copy(alpha = CardConfig.cardAlpha),
                    contentColor = statusOnContainer
                )
            }
        },
        elevation = getCardElevation(),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            val customBgUri = ThemeConfig.customBackgroundUri
            if (customBgUri != null) {
                val context = LocalContext.current
                val tick = ThemeConfig.backgroundRefreshTick
                val bgFile = remember(customBgUri, tick) {
                    if (customBgUri.scheme == "file" && customBgUri.path != null) {
                        java.io.File(customBgUri.path!!)
                    } else {
                        customBgUri
                    }
                }
                val cacheKey = remember(customBgUri, tick) {
                    if (bgFile is java.io.File) {
                        "${bgFile.absolutePath}_${bgFile.lastModified()}_${bgFile.length()}_$tick"
                    } else {
                        "${customBgUri}_$tick"
                    }
                }
                coil.compose.AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(bgFile)
                        .memoryCacheKey(cacheKey)
                        .diskCacheKey(cacheKey)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                val dimAlpha = ThemeConfig.backgroundDim.coerceIn(0f, 0.85f)
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(
                                        alpha = (0.32f + dimAlpha * 0.50f).coerceIn(0f, 0.88f)
                                    ),
                                    Color.Black.copy(
                                        alpha = (0.56f + dimAlpha * 0.40f).coerceIn(0f, 0.92f)
                                    )
                                )
                            )
                        )
                )
            }

            val isClassicUi = ThemeConfig.isClassicHomeUi
            val isClickableStatus = true
            val statusIcon = when {
                isWorking && isSafeMode -> Icons.TwoTone.Warning
                isWorking -> Icons.TwoTone.TaskAlt
                systemStatus.kernelVersion.isGKI() -> Icons.TwoTone.Download
                else -> Icons.TwoTone.Download
            }

            if (isClassicUi) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isClickableStatus) {
                            onClickInstall()
                        }
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = badgeContainerColor,
                        border = badgeBorder
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusOnContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        when {
                            isWorking -> {
                                val workingModeText = if (isSafeMode) {
                                    stringResource(id = R.string.safe_mode)
                                } else {
                                    stringResource(id = R.string.home_working)
                                }
                                val workingModeSurfaceText = when {
                                    systemStatus.isKernelPatchActive -> "KernelPatch"
                                    systemStatus.lkmMode == true -> "LKM"
                                    else -> "Built-in"
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = workingModeText,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            shadow = customBgTextShadow
                                        ),
                                        color = statusOnContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    LabelText(
                                        label = workingModeSurfaceText,
                                        containerColor = badgeContainerColor,
                                        contentColor = statusOnContainer,
                                        border = badgeBorder
                                    )
                                    if (systemStatus.isKernelPatchActive) {
                                        Spacer(Modifier.width(6.dp))
                                        LabelText(
                                            label = "APatch",
                                            containerColor = badgeContainerColor,
                                            contentColor = statusOnContainer,
                                            border = badgeBorder
                                        )
                                    }
                                    if (isLateLoadMode) {
                                        Spacer(Modifier.width(6.dp))
                                        LabelText(
                                            label = stringResource(id = R.string.jailbreak_mode),
                                            containerColor = badgeContainerColor,
                                            contentColor = statusOnContainer,
                                            border = badgeBorder
                                        )
                                    }
                                    if (machine != "aarch64") {
                                        Spacer(Modifier.width(6.dp))
                                        LabelText(
                                            label = machine,
                                            containerColor = badgeContainerColor,
                                            contentColor = statusOnContainer,
                                            border = badgeBorder
                                        )
                                    }
                                }
                                val isHideVersion = LocalContext.current
                                    .getSharedPreferences("settings", Context.MODE_PRIVATE)
                                    .getBoolean("is_hide_version", false)
                                if (!isHideVersion) {
                                    Spacer(Modifier.height(4.dp))
                                    val versionDisplay = if (systemStatus.isKernelPatchActive) {
                                        systemStatus.kernelPatchVersion?.let { "KernelPatch $it" }
                                    } else {
                                        systemStatus.ksuFullVersion
                                    }
                                    versionDisplay?.let {
                                        Text(
                                            text = stringResource(R.string.home_working_version, it),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                shadow = customBgTextShadow
                                            ),
                                            color = statusOnContainer.copy(alpha = 0.9f),
                                        )
                                    }
                                }
                            }
                            else -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.home_not_installed),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            shadow = customBgTextShadow
                                        ),
                                        color = statusOnContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    LabelText(
                                        label = if (systemStatus.kernelVersion.isGKI()) "GKI" else "Boot Patch",
                                        containerColor = badgeContainerColor,
                                        contentColor = statusOnContainer,
                                        border = badgeBorder
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.home_click_to_install),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        shadow = customBgTextShadow
                                    ),
                                    color = statusOnContainer.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                    if (isClickableStatus) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.TwoTone.ChevronRight,
                            contentDescription = null,
                            tint = statusOnContainer.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isClickableStatus) {
                            onClickInstall()
                        }
                        .padding(vertical = 24.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = badgeContainerColor,
                        border = badgeBorder
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusOnContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    when {
                        isWorking -> {
                            val workingModeText = when {
                                isSafeMode -> stringResource(id = R.string.safe_mode)
                                else -> stringResource(id = R.string.home_working)
                            }
                            val workingModeSurfaceText = when {
                                systemStatus.isKernelPatchActive -> "KernelPatch"
                                systemStatus.lkmMode == true -> "LKM"
                                else -> "Built-in"
                            }

                            Text(
                                text = workingModeText,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    shadow = customBgTextShadow
                                ),
                                color = statusOnContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LabelText(
                                    label = workingModeSurfaceText,
                                    containerColor = badgeContainerColor,
                                    contentColor = statusOnContainer,
                                    border = badgeBorder
                                )
                                if (systemStatus.isKernelPatchActive) {
                                    LabelText(
                                        label = "APatch",
                                        containerColor = badgeContainerColor,
                                        contentColor = statusOnContainer,
                                        border = badgeBorder
                                    )
                                }
                                if (isLateLoadMode) {
                                    LabelText(
                                        label = stringResource(id = R.string.jailbreak_mode),
                                        containerColor = badgeContainerColor,
                                        contentColor = statusOnContainer,
                                        border = badgeBorder
                                    )
                                }
                                if (machine != "aarch64") {
                                    LabelText(
                                        label = machine,
                                        containerColor = badgeContainerColor,
                                        contentColor = statusOnContainer,
                                        border = badgeBorder
                                    )
                                }
                            }
                            val isHideVersion = LocalContext.current
                                .getSharedPreferences("settings", Context.MODE_PRIVATE)
                                .getBoolean("is_hide_version", false)
                            if (!isHideVersion) {
                                Spacer(Modifier.height(8.dp))
                                val versionDisplay = if (systemStatus.isKernelPatchActive) {
                                    systemStatus.kernelPatchVersion?.let { "KernelPatch $it" }
                                } else {
                                    systemStatus.ksuFullVersion
                                }
                                versionDisplay?.let {
                                    Text(
                                        text = stringResource(R.string.home_working_version, it),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            shadow = customBgTextShadow
                                        ),
                                        color = statusOnContainer.copy(alpha = 0.92f),
                                    )
                                }
                            }
                            if (systemStatus.isKernelPatchActive && !systemStatus.isApdInstalled) {
                                Spacer(Modifier.height(12.dp))
                                Surface(
                                    onClick = onClickInstallApd,
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    tonalElevation = 2.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.TwoTone.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.home_apd_install_button),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            Text(
                                text = stringResource(R.string.home_not_installed),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    shadow = customBgTextShadow
                                ),
                                color = statusOnContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            LabelText(
                                label = if (systemStatus.kernelVersion.isGKI()) "GKI 5.10+" else "Boot Patch",
                                containerColor = badgeContainerColor,
                                contentColor = statusOnContainer,
                                border = badgeBorder
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = onClickInstall,
                                    shape = CircleShape,
                                    color = if (hasCustomCardBg) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f)
                                    } else {
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                                    },
                                    contentColor = if (hasCustomCardBg) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    tonalElevation = 2.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.TwoTone.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.home_click_to_install),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Surface(
                                    onClick = onClickSuperKey,
                                    shape = CircleShape,
                                    color = badgeContainerColor,
                                    contentColor = statusOnContainer,
                                    border = badgeBorder
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.TwoTone.Key,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "SuperKey",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            if (systemStatus.kernelVersion.isGKI() && systemStatus.isSELinuxPermissive) {
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = onClickJailbreak,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Text(stringResource(R.string.home_jailbreak))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LearnMoreCard() {
    val uriHandler = LocalUriHandler.current
    val url = stringResource(R.string.home_learn_kernelsu_url)

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = getCardColors(m3CardContainerColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    uriHandler.openUri(url)
                }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.TwoTone.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_learn_kernelsu),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.home_click_to_learn_kernelsu),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.AutoMirrored.TwoTone.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun DonateCard() {
    val uriHandler = LocalUriHandler.current

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = getCardColors(m3CardContainerColor()),
        elevation = getCardElevation(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    uriHandler.openUri("https://patreon.com/weishu")
                }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.TwoTone.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_support_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.home_support_content),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.AutoMirrored.TwoTone.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun InfoCard(
    systemInfo: HomeViewModel.SystemInfo,
    isSimpleMode: Boolean,
    isHideSusfsStatus: Boolean,
    isHideZygiskImplement: Boolean,
    isHideMetaModuleImplement: Boolean,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hookType = remember { if (ksuIsValid()) Natives.getHookType() else "" }

    data class InfoEntry(
        val label: String,
        val content: String,
        val icon: ImageVector,
    )

    val entries = mutableListOf<InfoEntry>()
    entries += InfoEntry(
        stringResource(R.string.home_kernel),
        systemInfo.kernelRelease,
        Icons.TwoTone.Memory
    )
    if (!isSimpleMode) {
        entries += InfoEntry(
            stringResource(R.string.home_android_version),
            systemInfo.androidVersion,
            Icons.TwoTone.Android
        )
    }
    entries += InfoEntry(
        stringResource(R.string.home_device_model),
        systemInfo.deviceModel,
        Icons.TwoTone.PhoneAndroid
    )
    entries += InfoEntry(
        stringResource(R.string.home_manager_version),
        "${systemInfo.managerVersion.first} (${systemInfo.managerVersion.second.toInt()})",
        Icons.TwoTone.SettingsSuggest
    )
    if (!isSimpleMode && ksuIsValid()) {
        entries += InfoEntry(
            stringResource(R.string.home_hook_type),
            hookType,
            Icons.TwoTone.Link
        )
    }
    if (!isSimpleMode && systemInfo.managersList != null) {
        val signatureMap = systemInfo.managersList.managers.groupBy { it.signatureIndex }
        val managersText = buildString {
            signatureMap.toSortedMap().forEach { (signatureIndex, managers) ->
                append(managers.joinToString(", ") { "UID: ${it.uid}" })
                append(" ")
                append(
                    when (signatureIndex) {
                        0 -> "(${stringResource(R.string.app_name)})"
                        255 -> "(${stringResource(R.string.dynamic_managerature)})"
                        else -> if (signatureIndex >= 1) "(${
                            stringResource(R.string.signature_index, signatureIndex)
                        })" else "(${stringResource(R.string.unknown_signature)})"
                    }
                )
                append(" | ")
            }
        }.trimEnd(' ', '|')
        entries += InfoEntry(
            stringResource(R.string.multi_manager_list),
            managersText.ifEmpty { stringResource(R.string.no_active_manager) },
            Icons.TwoTone.Group
        )
    }
    entries += InfoEntry(
        stringResource(R.string.home_selinux_status),
        systemInfo.selinuxStatus,
        Icons.TwoTone.Security
    )
    val seccompDisplay = when (systemInfo.seccompStatus) {
        -1 -> stringResource(R.string.seccomp_status_not_supported)
        0 -> stringResource(R.string.seccomp_status_disabled)
        1 -> stringResource(R.string.seccomp_status_strict)
        2 -> stringResource(R.string.seccomp_status_filter)
        else -> stringResource(R.string.seccomp_status_unknown)
    }
    entries += InfoEntry(
        stringResource(R.string.home_seccomp_status),
        seccompDisplay,
        Icons.TwoTone.LocalPolice
    )
    if (!isHideZygiskImplement && !isSimpleMode && systemInfo.zygiskImplement.isNotEmpty() && systemInfo.zygiskImplement != "None") {
        entries += InfoEntry(
            stringResource(R.string.home_zygisk_implement),
            systemInfo.zygiskImplement,
            Icons.TwoTone.Adb
        )
    }
    if (!isHideMetaModuleImplement && !isSimpleMode && systemInfo.metaModuleImplement.isNotEmpty() && systemInfo.metaModuleImplement != "None") {
        entries += InfoEntry(
            stringResource(R.string.home_meta_module_implement),
            systemInfo.metaModuleImplement,
            Icons.TwoTone.Extension
        )
    }
    if (!isSimpleMode && !isHideSusfsStatus && systemInfo.susfsEnabled && systemInfo.susfsVersion.isNotEmpty()) {
        entries += InfoEntry(
            stringResource(R.string.home_susfs_version),
            systemInfo.susfsVersion,
            Icons.TwoTone.Storage
        )
    }

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = getCardColors(m3CardContainerColor()),
        elevation = getCardElevation(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            entries.forEachIndexed { index, entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText(entry.label, entry.content))
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            Toast.makeText(context, "${entry.label}: ${entry.content}", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 18.dp, vertical = 11.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = entry.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = entry.content,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            softWrap = true
                        )
                    }
                }
                if (index < entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp, end = 18.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

fun getManagerVersion(context: Context): Pair<String, Long> {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)!!
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    return Pair(packageInfo.versionName!!, versionCode)
}

@Preview
@Composable
private fun StatusCardPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusCard(
            HomeViewModel.SystemStatus(
                isManager = true,
                ksuVersion = 1,
                lkmMode = null,
                kernelVersion = KernelVersion(5, 10, 101),
                isRootAvailable = true
            )
        )

        StatusCard(
            HomeViewModel.SystemStatus(
                isManager = true,
                ksuVersion = 30000,
                lkmMode = true,
                kernelVersion = KernelVersion(5, 10, 101),
                isRootAvailable = true
            )
        )

        StatusCard(
            HomeViewModel.SystemStatus(
                isManager = false,
                ksuVersion = null,
                lkmMode = true,
                kernelVersion = KernelVersion(5, 10, 101),
                isRootAvailable = false
            )
        )

        StatusCard(
            HomeViewModel.SystemStatus(
                isManager = false,
                ksuVersion = null,
                lkmMode = false,
                kernelVersion = KernelVersion(4, 10, 101),
                isRootAvailable = false
            )
        )
    }
}
