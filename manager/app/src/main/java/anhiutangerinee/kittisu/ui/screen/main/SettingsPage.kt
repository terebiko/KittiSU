package anhiutangerinee.kittisu.ui.screen.main

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.system.OsConstants
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Undo
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.twotone.Adb
import androidx.compose.material.icons.twotone.BookmarkBorder
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.DeleteForever
import androidx.compose.material.icons.twotone.Fence
import androidx.compose.material.icons.twotone.FolderOff
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Policy
import androidx.compose.material.icons.twotone.RadioButtonChecked
import androidx.compose.material.icons.twotone.RadioButtonUnchecked
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Share
import androidx.compose.material.icons.twotone.Update
import androidx.compose.material.icons.twotone.ElectricalServices
import androidx.compose.material.icons.twotone.FolderDelete
import androidx.compose.material.icons.twotone.RemoveCircle
import androidx.compose.material.icons.twotone.RemoveModerator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import com.maxkeppeker.sheets.core.models.base.IconSource
import com.maxkeppeler.sheets.list.models.ListOption
import anhiutangerinee.kittisu.BuildConfig
import anhiutangerinee.kittisu.Natives
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.data.update.UpdateChannel
import anhiutangerinee.kittisu.ksuApp
import anhiutangerinee.kittisu.magica.BootCompletedReceiver
import anhiutangerinee.kittisu.ui.component.ConfirmResult
import anhiutangerinee.kittisu.ui.component.DialogHandle
import anhiutangerinee.kittisu.ui.component.SwipeableSnackbarHost
import anhiutangerinee.kittisu.ui.component.ksuIsValid
import anhiutangerinee.kittisu.ui.component.rememberConfirmDialog
import anhiutangerinee.kittisu.ui.component.rememberCustomDialog
import anhiutangerinee.kittisu.ui.component.rememberLoadingDialog
import anhiutangerinee.kittisu.ui.component.settings.SegmentedColumn
import anhiutangerinee.kittisu.ui.component.settings.SettingsBaseWidget
import anhiutangerinee.kittisu.ui.component.settings.SettingsDropdownWidget
import anhiutangerinee.kittisu.ui.component.settings.SettingsJumpPageWidget
import anhiutangerinee.kittisu.ui.component.settings.SettingsSwitchWidget
import anhiutangerinee.kittisu.ui.navigation.LocalNavigator
import anhiutangerinee.kittisu.ui.navigation.Route
import anhiutangerinee.kittisu.ui.screen.FlashIt
import anhiutangerinee.kittisu.ui.theme.CardConfig
import anhiutangerinee.kittisu.ui.theme.ThemeConfig
import anhiutangerinee.kittisu.ui.theme.blurEffect
import anhiutangerinee.kittisu.ui.theme.blurSource
import anhiutangerinee.kittisu.ui.util.LocalSnackbarHost
import anhiutangerinee.kittisu.ui.util.backupModules
import anhiutangerinee.kittisu.ui.util.execKsud
import anhiutangerinee.kittisu.ui.util.getBugreportFile
import anhiutangerinee.kittisu.ui.util.getFeaturePersistValue
import anhiutangerinee.kittisu.ui.util.getFeatureStatus
import anhiutangerinee.kittisu.ui.util.inspectModuleBackup
import anhiutangerinee.kittisu.ui.util.restoreModules
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * @author ShirkNeko
 * @date 2025/9/29.
 */
private val SPACING_MEDIUM = 8.dp
private val SPACING_LARGE = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(bottomPadding: Dp) {
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    Scaffold(
        topBar = {
            TopBar(scrollBehavior = scrollBehavior)
        },
        snackbarHost = {
            SwipeableSnackbarHost(
                modifier = Modifier.padding(bottom = bottomPadding),
                hostState = snackBarHost
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        val loadingDialog = rememberLoadingDialog()
        var showBottomsheet by remember { mutableStateOf(false) }
        val logSaved = stringResource(R.string.log_saved)
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val exportBugreportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/gzip")
        ) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch(Dispatchers.IO) {
                loadingDialog.show()
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    getBugreportFile(context).inputStream().use {
                        it.copyTo(output)
                    }
                }
                loadingDialog.hide()
                snackBarHost.showSnackbar(logSaved)
            }
        }
        val moduleOperationFailed = stringResource(R.string.operation_failed)
        val moduleRestoreComplete = stringResource(R.string.module_restore_complete)
        var pendingRestoreArchive by remember { mutableStateOf<File?>(null) }
        var availableRestoreModules by remember { mutableStateOf(emptyList<String>()) }
        var selectedRestoreModules by remember { mutableStateOf(emptySet<String>()) }
        val exportModulesLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/x-tar")
        ) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch(Dispatchers.IO) {
                val archive = File(context.cacheDir, "kittisu-modules.tar")
                val success = backupModules(archive.absolutePath)
                if (success) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        archive.inputStream().use { it.copyTo(output) }
                    }
                }
                archive.delete()
                snackBarHost.showSnackbar(if (success) logSaved else moduleOperationFailed)
            }
        }
        val restoreModulesLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch(Dispatchers.IO) {
                val archive = File(context.cacheDir, "kittisu-modules-restore.tar")
                val copied = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        archive.outputStream().use { input.copyTo(it) }
                    } ?: error("cannot open module backup")
                }.isSuccess
                val modules = if (copied) inspectModuleBackup(archive.absolutePath) else null
                withContext(Dispatchers.Main) {
                    if (modules.isNullOrEmpty()) {
                        archive.delete()
                        snackBarHost.showSnackbar(moduleOperationFailed)
                    } else {
                        pendingRestoreArchive = archive
                        availableRestoreModules = modules
                        selectedRestoreModules = modules.toSet()
                    }
                }
            }
        }

        pendingRestoreArchive?.let { archive ->
            AlertDialog(
                onDismissRequest = {
                    archive.delete()
                    pendingRestoreArchive = null
                },
                title = { Text(stringResource(R.string.module_restore_select)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        availableRestoreModules.forEach { id ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedRestoreModules = if (id in selectedRestoreModules) {
                                            selectedRestoreModules - id
                                        } else {
                                            selectedRestoreModules + id
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = id in selectedRestoreModules,
                                    onCheckedChange = null
                                )
                                Text(id)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = selectedRestoreModules.isNotEmpty(),
                        onClick = {
                            val selected = selectedRestoreModules
                            pendingRestoreArchive = null
                            scope.launch(Dispatchers.IO) {
                                val success = restoreModules(archive.absolutePath, selected)
                                archive.delete()
                                snackBarHost.showSnackbar(
                                    if (success) moduleRestoreComplete else moduleOperationFailed
                                )
                            }
                        }
                    ) { Text(stringResource(R.string.restore)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        archive.delete()
                        pendingRestoreArchive = null
                    }) { Text(stringResource(android.R.string.cancel)) }
                }
            )
        }

        var isKernelUmountEnabled by rememberSaveable {
            mutableStateOf(
                Natives.isKernelUmountEnabled()
            )
        }

        LazyColumn(
            modifier =
                Modifier
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .blurSource(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 5.dp,
                start = 0.dp,
                end = 0.dp,
                bottom = innerPadding.calculateBottomPadding() + bottomPadding + 15.dp
            )
        ) {
            // 配置卡片
            if (ksuIsValid()) {
                item {
                    val modeItems = listOf(
                        stringResource(id = R.string.settings_mode_default),
                        stringResource(id = R.string.settings_mode_disable_until_reboot),
                        stringResource(id = R.string.settings_mode_disable_always),
                    )

                    SegmentedColumn(
                        title = stringResource(R.string.configuration),
                        content = {
                            item {
                                // 配置文件模板入口
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Fence,
                                    title = stringResource(R.string.settings_profile_template),
                                    description = stringResource(R.string.settings_profile_template_summary),
                                    onClick = {
                                        navigator.push(Route.AppProfileTemplate)
                                    }
                                )
                            }

                            item {
                                val currentSuEnabled = Natives.isSuEnabled()
                                var suCompatMode by rememberSaveable { mutableIntStateOf(if (!currentSuEnabled) 1 else 0) }
                                val suPersistValue by produceState(initialValue = null as Long?) {
                                    value = getFeaturePersistValue("su_compat")
                                }
                                LaunchedEffect(suPersistValue) {
                                    suPersistValue?.let { v ->
                                        suCompatMode =
                                            if (v == 0L) 2 else if (!currentSuEnabled) 1 else 0
                                    }
                                }

                                val suStatus by produceState(initialValue = "") {
                                    value = getFeatureStatus("su_compat")
                                }
                                val suSummary = when (suStatus) {
                                    "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                    else -> stringResource(id = R.string.settings_sucompat_summary)
                                }
                                SettingsDropdownWidget(
                                    icon = Icons.TwoTone.RemoveModerator,
                                    title = stringResource(id = R.string.settings_sucompat),
                                    description = suSummary,
                                    items = modeItems,
                                    enabled = suStatus == "supported",
                                    selectedIndex = suCompatMode,
                                    onSelectedIndexChange = { index ->
                                        when (index) {
                                            // Default: enable and save to persist
                                            0 -> if (Natives.setSuEnabled(true)) {
                                                execKsud("feature save", true)
                                                prefs.edit { putInt("su_compat_mode", 0) }
                                                suCompatMode = 0
                                            }

                                            // Temporarily disable: save enabled state first, then disable
                                            1 -> if (Natives.setSuEnabled(true)) {
                                                execKsud("feature save", true)
                                                if (Natives.setSuEnabled(false)) {
                                                    prefs.edit { putInt("su_compat_mode", 0) }
                                                    suCompatMode = 1
                                                }
                                            }

                                            // Permanently disable: disable and save
                                            2 -> if (Natives.setSuEnabled(false)) {
                                                execKsud("feature save", true)
                                                prefs.edit { putInt("su_compat_mode", 2) }
                                                suCompatMode = 2
                                            }
                                        }
                                    }
                                )
                            }

                            item {
                                var savedUmountStatus by rememberSaveable { mutableStateOf("") }
                                val umountStatus by produceState(initialValue = savedUmountStatus) {
                                    value = withContext(Dispatchers.IO) {
                                        savedUmountStatus = getFeatureStatus("kernel_umount")
                                        return@withContext savedUmountStatus
                                    }
                                }
                                val umountSummary = when (umountStatus) {
                                    "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                    else -> stringResource(id = R.string.settings_kernel_umount_summary)
                                }
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.RemoveCircle,
                                    title = stringResource(id = R.string.settings_kernel_umount),
                                    description = umountSummary,
                                    enabled = umountStatus == "supported",
                                    checked = isKernelUmountEnabled,
                                    onCheckedChange = { checked ->
                                        if (Natives.setKernelUmountEnabled(checked)) {
                                            execKsud("feature save", true)
                                            isKernelUmountEnabled = checked
                                        }
                                    }
                                )
                            }

                            item(
                                visible = Natives.isLateLoadMode
                            ) {
                                var savedAutoJailbreakStatus by rememberSaveable {
                                    mutableStateOf(
                                        prefs.getBoolean("auto_jailbreak", false)
                                    )
                                }

                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.ElectricalServices,
                                    title = stringResource(id = R.string.settings_auto_jailbreak),
                                    description = stringResource(id = R.string.settings_auto_jailbreak_summary),
                                    checked = savedAutoJailbreakStatus,
                                    onCheckedChange = { value ->
                                        runCatching {
                                            ksuApp.packageManager.setComponentEnabledSetting(
                                                ComponentName(
                                                    ksuApp,
                                                    BootCompletedReceiver::class.java
                                                ),
                                                if (value) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                                PackageManager.DONT_KILL_APP
                                            )
                                        }.onFailure {
                                            Log.e(
                                                "Settings",
                                                "failed to change boot receiver state to $value",
                                                it
                                            )
                                        }
                                        prefs.edit {
                                            putBoolean("auto_jailbreak", value)
                                        }
                                        savedAutoJailbreakStatus = value
                                    }
                                )
                            }

                            item(
                                visible = Build.VERSION.SDK_INT > Build.VERSION_CODES.Q
                            ) {
                                var isAdbRootEnabled by remember { mutableStateOf(false) }

                                var savedAdbRootStatus by rememberSaveable { mutableStateOf("") }
                                val adbRootStatus by produceState(initialValue = savedAdbRootStatus) {
                                    value = withContext(Dispatchers.IO) {
                                        savedAdbRootStatus = getFeatureStatus("adb_root")
                                        isAdbRootEnabled = getFeaturePersistValue("adb_root") == 1L
                                        return@withContext savedAdbRootStatus
                                    }
                                }
                                val adbRootSummary = when (adbRootStatus) {
                                    "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                    else -> stringResource(id = R.string.settings_adb_root_summary)
                                }

                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Adb,
                                    title = stringResource(id = R.string.settings_adb_root),
                                    description = adbRootSummary,
                                    checked = isAdbRootEnabled,
                                    enabled = adbRootStatus == "supported",
                                    onCheckedChange = { checked ->
                                        if (execKsud("feature set adb_root ${if (checked) 1 else 0}", true)) {
                                            ShellUtils.fastCmd("setprop ctl.restart adbd")
                                            execKsud("feature save", true)
                                        }
                                        isAdbRootEnabled = checked
                                    }
                                )
                            }


                            item {
                                var isSuLogEnabled by remember { mutableStateOf(Natives.isSuLogEnabled()) }

                                var savedSulogStatus by rememberSaveable { mutableStateOf("") }
                                val sulogStatus by produceState(initialValue = savedSulogStatus) {
                                    value = withContext(Dispatchers.IO) {
                                        savedSulogStatus = getFeatureStatus("sulog")
                                        return@withContext savedSulogStatus
                                    }
                                }
                                val sulogSummary = when (sulogStatus) {
                                    "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                    else -> stringResource(id = R.string.settings_sulog_summary)
                                }
                                SettingsSwitchWidget(
                                    icon = Icons.AutoMirrored.TwoTone.Article,
                                    title = stringResource(id = R.string.settings_sulog),
                                    description = sulogSummary,
                                    enabled = sulogStatus == "supported",
                                    checked = isSuLogEnabled,
                                    onCheckedChange = { checked ->
                                        if (Natives.setSuLogEnabled(checked)) {
                                            execKsud("feature save", true)
                                            isSuLogEnabled = checked
                                        }
                                    }
                                )
                            }


                            item {
                                var isSelinuxHideEnabled by remember { mutableStateOf(Natives.isSelinuxHideEnabled()) }

                                var savedSelinuxHideStatus by rememberSaveable { mutableStateOf("") }
                                val selinuxHideStatus by produceState(initialValue = savedSelinuxHideStatus) {
                                    value = withContext(Dispatchers.IO) {
                                        savedSelinuxHideStatus = getFeatureStatus("selinux_hide")
                                        return@withContext savedSelinuxHideStatus
                                    }
                                }
                                val selinuxHideSummary = when (selinuxHideStatus) {
                                    "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                    else -> stringResource(id = R.string.settings_selinux_hide_summary)
                                }
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.Policy,
                                    title = stringResource(id = R.string.settings_selinux_hide),
                                    description = selinuxHideSummary,
                                    enabled = selinuxHideStatus == "supported",
                                    checked = isSelinuxHideEnabled,
                                    onCheckedChange = { checked ->
                                        val status = Natives.setSelinuxHideEnabled(checked)
                                        execKsud("feature save", true)
                                        isSelinuxHideEnabled = checked

                                        when (status) {
                                            0 -> {}
                                            -OsConstants.EAGAIN -> {
                                                Toast.makeText(
                                                    context,
                                                    R.string.settings_selinux_hide_reboot_required,
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                            else -> {
                                                Toast.makeText(
                                                    context,
                                                    ksuApp.getString(
                                                        R.string.settings_selinux_hide_failed,
                                                        status
                                                    ),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                )
                            }

                            item {
                                // 卸载模块开关
                                var umountChecked by rememberSaveable { mutableStateOf(Natives.isDefaultUmountModules()) }
                                SettingsSwitchWidget(
                                    icon = Icons.TwoTone.FolderDelete,
                                    title = stringResource(id = R.string.settings_umount_modules_default),
                                    description = stringResource(id = R.string.settings_umount_modules_default_summary),
                                    checked = umountChecked,
                                    onCheckedChange = {
                                        if (Natives.setDefaultUmountModules(it)) {
                                            umountChecked = it
                                        }
                                    }
                                )
                            }
                        }
                    )
                }
            }

            item {
                // 应用设置卡片
                SegmentedColumn(
                    title = stringResource(R.string.app_settings),
                    content = {
                        item {
                            // 更新检查开关
                            var checkUpdate by rememberSaveable {
                                mutableStateOf(prefs.getBoolean("check_update", true))
                            }
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Update,
                                title = stringResource(R.string.settings_check_update),
                                description = stringResource(R.string.settings_check_update_summary),
                                checked = checkUpdate,
                                onCheckedChange = { enabled ->
                                    prefs.edit { putBoolean("check_update", enabled) }
                                    checkUpdate = enabled
                                }
                            )
                        }

                        item {
                            val channels = listOf(
                                stringResource(R.string.update_channel_stable),
                                stringResource(R.string.update_channel_beta),
                                stringResource(R.string.update_channel_nightly),
                            )
                            var selectedChannel by rememberSaveable {
                                mutableIntStateOf(
                                    UpdateChannel.entries.indexOf(
                                        UpdateChannel.fromPreference(
                                            prefs.getString("update_channel", null)
                                        )
                                    )
                                )
                            }
                            SettingsDropdownWidget(
                                icon = Icons.TwoTone.Update,
                                title = stringResource(R.string.update_channel),
                                description = stringResource(R.string.update_channel_summary),
                                items = channels,
                                selectedIndex = selectedChannel,
                                onSelectedIndexChange = { index ->
                                    selectedChannel = index
                                    prefs.edit {
                                        putString(
                                            "update_channel",
                                            UpdateChannel.entries[index].preferenceValue
                                        )
                                    }
                                }
                            )
                        }

                        item {
                            // 更多设置
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Settings,
                                title = stringResource(R.string.more_settings),
                                description = stringResource(R.string.more_settings),
                                onClick = {
                                    navigator.push(Route.MoreSettings)
                                }
                            )
                        }
                    }
                )
            }

            item {
                // 工具卡片
                SegmentedColumn(
                    title = stringResource(R.string.tools),
                    content = {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.BugReport,
                                title = stringResource(R.string.send_log),
                                onClick = {
                                    showBottomsheet = true
                                }
                            ) {}
                        }

                        if (ksuIsValid()) {
                            item {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.Save,
                                    title = stringResource(R.string.module_backup),
                                    onClick = { exportModulesLauncher.launch("kittisu-modules.tar") }
                                ) {}
                            }

                            item {
                                SettingsBaseWidget(
                                    icon = Icons.AutoMirrored.TwoTone.Undo,
                                    title = stringResource(R.string.module_restore),
                                    onClick = {
                                        restoreModulesLauncher.launch(
                                            arrayOf("application/x-tar", "application/octet-stream")
                                        )
                                    }
                                ) {}
                            }

                            item {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.Security,
                                    title = stringResource(R.string.dynamic_manager_title),
                                    description = stringResource(R.string.dynamic_manager_settings_summary),
                                    onClick = { navigator.push(Route.DynamicManager) }
                                )
                            }

                            item(visible = isKernelUmountEnabled) {
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.FolderOff,
                                    title = stringResource(R.string.umount_path_manager),
                                    description = stringResource(R.string.umount_path_manager_summary),
                                    onClick = {
                                        navigator.push(Route.UmountManager)
                                    }
                                )
                            }
                        }

                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.BookmarkBorder,
                                title = stringResource(R.string.settings_module_presets),
                                description = stringResource(R.string.settings_module_presets_summary),
                                onClick = {
                                    navigator.push(Route.ModulePresets)
                                }
                            )
                        }

                        if (Natives.isLkmMode) {
                            item {
                                UninstallItem {
                                    loadingDialog.withLoading(it)
                                }
                            }
                        }
                    }
                )
            }

            if (showBottomsheet) {
                item {
                    val sendLog = stringResource(R.string.send_log)
                    LogBottomSheet(
                        onDismiss = { showBottomsheet = false },
                        onSaveLog = {
                            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm")
                            val current = LocalDateTime.now().format(formatter)
                            exportBugreportLauncher.launch("KernelSU_bugreport_${current}.tar.gz")
                            showBottomsheet = false
                        },
                        onShareLog = {
                            scope.launch {
                                val bugreport = loadingDialog.withLoading {
                                    withContext(Dispatchers.IO) {
                                        getBugreportFile(context)
                                    }
                                }

                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                                    bugreport
                                )

                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    setDataAndType(uri, "application/gzip")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }

                                context.startActivity(
                                    Intent.createChooser(
                                        shareIntent,
                                        sendLog
                                    )
                                )

                                showBottomsheet = false
                            }
                        }
                    )
                }
            }

            // 关于卡片
            item {
                SegmentedColumn(
                    title = stringResource(R.string.about),
                    content = {
                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Info,
                                title = stringResource(R.string.about),
                                onClick = {
                                    navigator.push(Route.About)
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogBottomSheet(
    onDismiss: () -> Unit,
    onSaveLog: () -> Unit,
    onShareLog: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SPACING_LARGE),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LogActionButton(
                icon = Icons.TwoTone.Save,
                text = stringResource(R.string.save_log),
                onClick = onSaveLog
            )

            LogActionButton(
                icon = Icons.TwoTone.Share,
                text = stringResource(R.string.send_log),
                onClick = onShareLog
            )
        }
        Spacer(modifier = Modifier.height(SPACING_LARGE))
    }
}

@Composable
fun LogActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(SPACING_MEDIUM)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(SPACING_MEDIUM))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun UninstallItem(
    withLoading: suspend (suspend () -> Unit) -> Unit
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uninstallConfirmDialog = rememberConfirmDialog()
    val showTodo = {
        Toast.makeText(context, "TODO", Toast.LENGTH_SHORT).show()
    }
    val uninstallDialog = rememberUninstallDialog { uninstallType ->
        scope.launch {
            val result = uninstallConfirmDialog.awaitConfirm(
                title = context.getString(uninstallType.title),
                content = context.getString(uninstallType.message)
            )
            if (result == ConfirmResult.Confirmed) {
                withLoading {
                    when (uninstallType) {
                        UninstallType.TEMPORARY -> showTodo()
                        UninstallType.PERMANENT -> navigator.push(Route.Flash(FlashIt.FlashUninstall))
                        UninstallType.RESTORE_STOCK_IMAGE -> navigator.push(Route.Flash(FlashIt.FlashRestore))
                        UninstallType.NONE -> Unit
                    }
                }
            }
        }
    }

    SettingsJumpPageWidget(
        icon = Icons.TwoTone.Delete,
        title = stringResource(id = R.string.settings_uninstall),
        onClick = {
            uninstallDialog.show()
        }
    )
}

enum class UninstallType(val title: Int, val message: Int, val icon: ImageVector) {
    TEMPORARY(
        R.string.settings_uninstall_temporary,
        R.string.settings_uninstall_temporary_message,
        Icons.TwoTone.Delete
    ),
    PERMANENT(
        R.string.settings_uninstall_permanent,
        R.string.settings_uninstall_permanent_message,
        Icons.TwoTone.DeleteForever
    ),
    RESTORE_STOCK_IMAGE(
        R.string.settings_restore_stock_image,
        R.string.settings_restore_stock_image_message,
        Icons.AutoMirrored.TwoTone.Undo
    ),
    NONE(0, 0, Icons.TwoTone.Delete)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberUninstallDialog(onSelected: (UninstallType) -> Unit): DialogHandle {
    return rememberCustomDialog { dismiss ->
        val options = listOf(
            UninstallType.PERMANENT,
            UninstallType.RESTORE_STOCK_IMAGE
        )
        val listOptions = options.map {
            ListOption(
                titleText = stringResource(it.title),
                subtitleText = if (it.message != 0) stringResource(it.message) else null,
                icon = IconSource(it.icon)
            )
        }

        var selectedOption by remember { mutableStateOf<UninstallType?>(null) }

        AlertDialog(
            onDismissRequest = {
                dismiss()
            },
            title = {
                Text(
                    text = stringResource(R.string.settings_uninstall),
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            text = {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    options.forEachIndexed { index, option ->
                        val isSelected = selectedOption == option
                        val backgroundColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            Color.Transparent
                        val contentColor = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(backgroundColor)
                                .clickable {
                                    selectedOption = option
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .size(24.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = listOptions[index].titleText,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                listOptions[index].subtitleText?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected)
                                            contentColor.copy(alpha = 0.8f)
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.TwoTone.RadioButtonChecked,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.TwoTone.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedOption?.let { onSelected(it) }
                        dismiss()
                    },
                    enabled = selectedOption != null,
                ) {
                    Text(
                        text = stringResource(android.R.string.ok)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dismiss()
                    }
                ) {
                    Text(
                        text = stringResource(android.R.string.cancel),
                    )
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 4.dp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    LargeFlexibleTopAppBar(
        modifier = Modifier.blurEffect(
        ),
        title = {
            Text(text = stringResource(R.string.settings))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor =
                if (ThemeConfig.isEnableBlur || ThemeConfig.isFullScreenBackgroundEnabled)
                    Color.Transparent
                else
                    MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha),
            scrolledContainerColor =
                if (ThemeConfig.isEnableBlur || ThemeConfig.isFullScreenBackgroundEnabled)
                    Color.Transparent
                else
                    MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha)
        ),
        windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
        scrollBehavior = scrollBehavior
    )
}
