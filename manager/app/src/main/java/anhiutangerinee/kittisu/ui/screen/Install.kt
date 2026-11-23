package anhiutangerinee.kittisu.ui.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AutoFixHigh
import androidx.compose.material.icons.twotone.ChevronRight
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.DeveloperBoard
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.ExpandLess
import androidx.compose.material.icons.twotone.ExpandMore
import androidx.compose.material.icons.twotone.FileUpload
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Key
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Shield
import androidx.compose.material.icons.twotone.UploadFile
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.maxkeppeker.sheets.core.models.base.Header
import com.maxkeppeker.sheets.core.models.base.rememberUseCaseState
import com.maxkeppeler.sheets.list.ListDialog
import com.maxkeppeler.sheets.list.models.ListOption
import com.maxkeppeler.sheets.list.models.ListSelection
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.getKernelVersion
import anhiutangerinee.kittisu.ui.component.DialogHandle
import anhiutangerinee.kittisu.ui.component.rememberConfirmDialog
import anhiutangerinee.kittisu.ui.component.rememberCustomDialog
import anhiutangerinee.kittisu.ui.component.settings.AppBackButton
import anhiutangerinee.kittisu.ui.component.settings.SettingsDropdownWidget
import anhiutangerinee.kittisu.ui.component.settings.SettingsSwitchWidget
import anhiutangerinee.kittisu.ui.navigation.LocalNavigator
import anhiutangerinee.kittisu.ui.navigation.Route
import anhiutangerinee.kittisu.ui.theme.CardConfig.cardAlpha
import anhiutangerinee.kittisu.ui.theme.ThemeConfig
import anhiutangerinee.kittisu.ui.theme.blurEffect
import anhiutangerinee.kittisu.ui.theme.blurSource
import anhiutangerinee.kittisu.ui.theme.getCardColors
import anhiutangerinee.kittisu.ui.theme.getCardElevation
import anhiutangerinee.kittisu.ui.util.FolkPatchMode
import anhiutangerinee.kittisu.ui.util.LkmSelection
import anhiutangerinee.kittisu.ui.util.generateRandomSuperKey
import anhiutangerinee.kittisu.ui.util.getAvailablePartitions
import anhiutangerinee.kittisu.ui.util.getCurrentKmi
import anhiutangerinee.kittisu.ui.util.getDefaultPartition
import anhiutangerinee.kittisu.ui.util.getSlotSuffix
import anhiutangerinee.kittisu.ui.util.getSupportedKmis
import anhiutangerinee.kittisu.ui.util.isAbDevice
import anhiutangerinee.kittisu.ui.util.isValidSuperKey
import anhiutangerinee.kittisu.ui.util.rootAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zako.zako.zako.zakoui.screen.kernelFlash.component.SlotSelectionDialog

/**
 * @author ShirkNeko
 * @date 2025/5/31.
 */

private enum class InstallModeTab {
    KERNEL_LKM,
    FOLKPATCH_BOOT,
}

private sealed class FolkPatchSelection {
    data class SelectBoot(val uri: Uri? = null, val fileName: String? = null) : FolkPatchSelection()
    data object DirectInstall : FolkPatchSelection()
    data object InactiveSlot : FolkPatchSelection()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallScreen(
    preselectedKernelUri: String? = null
) {
    val context = LocalContext.current
    val kernelVersion = remember { getKernelVersion() }
    val isGKI = remember(kernelVersion) { kernelVersion.isGKI() }

    val hasRoot by produceState(initialValue = false) {
        value = withContext(Dispatchers.IO) { rootAvailable() }
    }
    val isAbDevice by produceState(initialValue = false) {
        value = withContext(Dispatchers.IO) { isAbDevice() }
    }

    var selectedTab by rememberSaveable {
        mutableStateOf(
            if (preselectedKernelUri != null || isGKI) InstallModeTab.KERNEL_LKM
            else InstallModeTab.FOLKPATCH_BOOT
        )
    }

    // --- Kernel / LKM state ---
    var installMethod by remember { mutableStateOf<InstallMethod?>(null) }
    var lkmSelection by remember { mutableStateOf<LkmSelection>(LkmSelection.KmiNone) }
    var lkmFileName by remember { mutableStateOf<String?>(null) }
    var showRebootDialog by remember { mutableStateOf(false) }
    var showSlotSelectionDialog by remember { mutableStateOf(false) }
    var tempKernelUri by remember { mutableStateOf<Uri?>(null) }
    var forceBackup by rememberSaveable { mutableStateOf(false) }
    var partitionSelectionIndex by remember { mutableIntStateOf(0) }
    var partitionsState by remember { mutableStateOf<List<String>>(emptyList()) }
    var hasCustomSelected by remember { mutableStateOf(false) }

    // --- FolkPatch / APatch Boot Image state ---
    var folkSelection by remember { mutableStateOf<FolkPatchSelection?>(null) }
    var folkSuperKey by rememberSaveable { mutableStateOf("") }
    var folkShowKey by remember { mutableStateOf(false) }
    var folkCustomKpimgUri by remember { mutableStateOf<Uri?>(null) }
    var folkCustomKpimgName by remember { mutableStateOf<String?>(null) }
    var pendingInitBootWarnUri by remember { mutableStateOf<Uri?>(null) }
    var pendingInitBootWarnName by remember { mutableStateOf<String?>(null) }

    val navigator = LocalNavigator.current
    val summary = stringResource(R.string.horizon_kernel_summary)

    LaunchedEffect(preselectedKernelUri) {
        preselectedKernelUri?.let { uriString ->
            try {
                val preselectedUri = uriString.toUri()
                selectedTab = InstallModeTab.KERNEL_LKM
                val horizonMethod = InstallMethod.HorizonKernel(
                    uri = preselectedUri,
                    summary = summary
                )
                installMethod = horizonMethod
                tempKernelUri = preselectedUri
                if (isAbDevice) {
                    showSlotSelectionDialog = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (showRebootDialog) {
        RebootDialog(
            show = true,
            onDismiss = { showRebootDialog = false },
            onConfirm = {
                showRebootDialog = false
                try {
                    val process = Runtime.getRuntime().exec("su")
                    process.outputStream.bufferedWriter().use { writer ->
                        writer.write("svc power reboot\n")
                        writer.write("exit\n")
                    }
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.failed_reboot, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Warn when user selects init_boot / vendor_boot in FolkPatch mode
    pendingInitBootWarnUri?.let { warnUri ->
        AlertDialog(
            onDismissRequest = {
                pendingInitBootWarnUri = null
                pendingInitBootWarnName = null
            },
            icon = {
                Icon(
                    imageVector = Icons.TwoTone.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.install_folkpatch_init_boot_warning_title)) },
            text = { Text(stringResource(R.string.install_folkpatch_init_boot_warning_msg)) },
            confirmButton = {
                Button(onClick = {
                    folkSelection = FolkPatchSelection.SelectBoot(warnUri, pendingInitBootWarnName)
                    pendingInitBootWarnUri = null
                    pendingInitBootWarnName = null
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingInitBootWarnUri = null
                    pendingInitBootWarnName = null
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    val onInstallKernelMode = {
        installMethod?.let { method ->
            when (method) {
                is InstallMethod.HorizonKernel -> {
                    method.uri?.let { uri ->
                        navigator.push(
                            Route.KernelFlash(
                                kernelUri = uri,
                                selectedSlot = method.slot
                            )
                        )
                    }
                }
                else -> {
                    val isOta = method is InstallMethod.DirectInstallToInactiveSlot
                    val partitionSelection = partitionsState.getOrNull(partitionSelectionIndex)
                    val flashIt = FlashIt.FlashBoot(
                        boot = if (method is InstallMethod.SelectFile) method.uri else null,
                        lkm = lkmSelection,
                        ota = isOta,
                        partition = partitionSelection,
                        backup = method is InstallMethod.SelectFile && forceBackup
                    )
                    navigator.push(Route.Flash(flashIt))
                }
            }
        }
        Unit
    }

    val onInstallFolkPatchMode = {
        val sel = folkSelection
        if (sel != null && isValidSuperKey(folkSuperKey.trim())) {
            val mode = when (sel) {
                is FolkPatchSelection.SelectBoot -> FolkPatchMode.PATCH_ONLY
                FolkPatchSelection.DirectInstall -> FolkPatchMode.DIRECT_INSTALL
                FolkPatchSelection.InactiveSlot -> FolkPatchMode.INACTIVE_SLOT
            }
            val bootUri = (sel as? FolkPatchSelection.SelectBoot)?.uri
            navigator.push(
                Route.Flash(
                    FlashIt.FlashFolkPatchBoot(
                        boot = bootUri,
                        kpimg = folkCustomKpimgUri,
                        superKey = folkSuperKey.trim(),
                        mode = mode
                    )
                )
            )
        }
    }

    SlotSelectionDialog(
        show = showSlotSelectionDialog && isAbDevice,
        onDismiss = { showSlotSelectionDialog = false },
        onSlotSelected = { slot ->
            showSlotSelectionDialog = false
            installMethod = InstallMethod.HorizonKernel(
                uri = tempKernelUri,
                slot = slot,
                summary = summary
            )
        }
    )

    val currentKmi by produceState(initialValue = "") {
        value = getCurrentKmi()
    }

    val selectKmiDialog = rememberSelectKmiDialog { kmi ->
        kmi?.let {
            lkmSelection = LkmSelection.KmiString(it)
            onInstallKernelMode()
        }
    }

    val onClickNext = {
        when (selectedTab) {
            InstallModeTab.KERNEL_LKM -> {
                if (isGKI && lkmSelection == LkmSelection.KmiNone && currentKmi.isBlank() && installMethod !is InstallMethod.HorizonKernel) {
                    selectKmiDialog.show()
                } else {
                    onInstallKernelMode()
                }
            }
            InstallModeTab.FOLKPATCH_BOOT -> {
                onInstallFolkPatchMode()
            }
        }
    }

    val installOnlySupportKoFile = stringResource(R.string.install_only_support_ko_file)
    val selectLkmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                val isKo = isKoFile(context, uri)
                if (isKo) {
                    lkmSelection = LkmSelection.LkmUri(uri)
                    lkmFileName = resolveDisplayName(context, uri)
                } else {
                    lkmSelection = LkmSelection.KmiNone
                    lkmFileName = null
                    Toast.makeText(
                        context,
                        installOnlySupportKoFile,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val selectFolkBootLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                val displayName = resolveDisplayName(context, uri)
                if (displayName.contains("init_boot", ignoreCase = true) ||
                    displayName.contains("vendor_boot", ignoreCase = true)
                ) {
                    pendingInitBootWarnUri = uri
                    pendingInitBootWarnName = displayName
                } else {
                    folkSelection = FolkPatchSelection.SelectBoot(uri, displayName)
                }
            }
        }
    }

    val selectFolkKpimgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                folkCustomKpimgUri = uri
                folkCustomKpimgName = resolveDisplayName(context, uri)
            }
        }
    }

    val folkInactiveConfirm = rememberConfirmDialog(
        onConfirm = {
            folkSelection = FolkPatchSelection.InactiveSlot
        },
        onDismiss = null
    )
    val dialogTitle = stringResource(id = android.R.string.dialog_alert_title)
    val dialogInactiveContent = stringResource(id = R.string.install_inactive_slot_warning)

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    val canProceed = when (selectedTab) {
        InstallModeTab.KERNEL_LKM -> installMethod != null
        InstallModeTab.FOLKPATCH_BOOT -> {
            val keyOk = isValidSuperKey(folkSuperKey.trim())
            val selOk = when (val s = folkSelection) {
                is FolkPatchSelection.SelectBoot -> s.uri != null
                FolkPatchSelection.DirectInstall, FolkPatchSelection.InactiveSlot -> true
                null -> false
            }
            keyOk && selOk
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                onBack = { navigator.pop() },
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .blurSource()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- 2-Mode Switcher ---
            Text(
                text = stringResource(R.string.install_mode_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InstallModeCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.TwoTone.DeveloperBoard,
                    title = stringResource(R.string.install_mode_kernel_title),
                    badge = stringResource(R.string.install_mode_kernel_badge),
                    selected = selectedTab == InstallModeTab.KERNEL_LKM,
                    onClick = { selectedTab = InstallModeTab.KERNEL_LKM }
                )
                InstallModeCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.TwoTone.AutoFixHigh,
                    title = stringResource(R.string.install_mode_folkpatch_title),
                    badge = stringResource(R.string.install_mode_folkpatch_badge),
                    selected = selectedTab == InstallModeTab.FOLKPATCH_BOOT,
                    onClick = { selectedTab = InstallModeTab.FOLKPATCH_BOOT }
                )
            }

            // --- Mode-specific Advisory Card + Controls ---
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "install_mode_content"
            ) { mode ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (mode) {
                        InstallModeTab.KERNEL_LKM -> {
                            ModeNoteCard(
                                icon = Icons.TwoTone.Shield,
                                title = stringResource(R.string.install_mode_kernel_note_title),
                                body = stringResource(R.string.install_mode_kernel_note_body),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = cardAlpha),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )

                            if (!isGKI) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = cardAlpha),
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    shape = MaterialTheme.shapes.large,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.TwoTone.Warning,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = stringResource(
                                                    R.string.install_mode_kernel_nongki_hint,
                                                    kernelVersion.toString()
                                                ),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        FilledTonalButton(
                                            onClick = { selectedTab = InstallModeTab.FOLKPATCH_BOOT },
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            Text(stringResource(R.string.install_mode_folkpatch_title))
                                        }
                                    }
                                }
                            }

                            SelectInstallMethod(
                                isGKI = isGKI,
                                rootAvailable = hasRoot,
                                isAbDevice = isAbDevice,
                                onSelected = { method ->
                                    if (method is InstallMethod.HorizonKernel && method.uri != null) {
                                        if (isAbDevice) {
                                            tempKernelUri = method.uri
                                            showSlotSelectionDialog = true
                                        } else {
                                            installMethod = method
                                        }
                                    } else {
                                        installMethod = method
                                    }
                                },
                                selectedMethod = installMethod
                            )

                            AnimatedVisibility(
                                visible = installMethod is InstallMethod.DirectInstall || installMethod is InstallMethod.DirectInstallToInactiveSlot,
                                enter = fadeIn() + expandVertically(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = cardAlpha),
                                    shape = MaterialTheme.shapes.large,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val isOta = installMethod is InstallMethod.DirectInstallToInactiveSlot
                                    val suffix = produceState(initialValue = "", isOta) {
                                        value = getSlotSuffix(isOta)
                                    }.value
                                    val partitions = produceState(initialValue = emptyList()) {
                                        value = getAvailablePartitions()
                                    }.value
                                    val defaultPartition = produceState(initialValue = "") {
                                        value = getDefaultPartition()
                                    }.value

                                    partitionsState = partitions
                                    val displayPartitions = partitions.map { name ->
                                        if (defaultPartition == name) "$name (default)" else name
                                    }
                                    val defaultIndex = partitions.indexOf(defaultPartition).takeIf { it >= 0 } ?: 0
                                    if (!hasCustomSelected) partitionSelectionIndex = defaultIndex

                                    if (displayPartitions.isNotEmpty()) {
                                        SettingsDropdownWidget(
                                            icon = Icons.TwoTone.Edit,
                                            items = displayPartitions,
                                            selectedIndex = partitionSelectionIndex,
                                            title = "${stringResource(R.string.install_select_partition)} ($suffix)",
                                            onSelectedIndexChange = { index ->
                                                hasCustomSelected = true
                                                partitionSelectionIndex = index
                                            },
                                        )
                                    }
                                }
                            }

                            if (installMethod is InstallMethod.SelectFile) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = cardAlpha),
                                    shape = MaterialTheme.shapes.large,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    SettingsSwitchWidget(
                                        icon = Icons.TwoTone.Security,
                                        title = stringResource(R.string.install_force_backup),
                                        description = stringResource(R.string.install_force_backup_summary),
                                        checked = forceBackup,
                                        onCheckedChange = { forceBackup = it },
                                    )
                                }
                            }

                            if (isGKI) {
                                // Clean single-layer M3 Expressive card with crisp UploadFile icon badge
                                FilePickerActionCard(
                                    icon = Icons.TwoTone.UploadFile,
                                    title = stringResource(id = R.string.install_upload_lkm_file),
                                    subtitle = if (lkmSelection is LkmSelection.LkmUri) {
                                        stringResource(
                                            id = R.string.selected_lkm,
                                            lkmFileName ?: "(file)"
                                        )
                                    } else {
                                        stringResource(id = R.string.install_upload_lkm_file_summary)
                                    },
                                    hasSelection = lkmSelection is LkmSelection.LkmUri,
                                    onClear = {
                                        lkmSelection = LkmSelection.KmiNone
                                        lkmFileName = null
                                    },
                                    onClick = {
                                        selectLkmLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                                            type = "application/octet-stream"
                                        })
                                    }
                                )
                            }

                            (installMethod as? InstallMethod.HorizonKernel)?.let { method ->
                                if (method.slot != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = cardAlpha),
                                        shape = MaterialTheme.shapes.large,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = stringResource(
                                                id = R.string.selected_slot,
                                                if (method.slot == "a") stringResource(id = R.string.slot_a)
                                                else stringResource(id = R.string.slot_b)
                                            ),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        InstallModeTab.FOLKPATCH_BOOT -> {
                            ModeNoteCard(
                                icon = Icons.TwoTone.Info,
                                title = stringResource(R.string.install_mode_folkpatch_note_title),
                                body = stringResource(R.string.install_mode_folkpatch_note_body),
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = cardAlpha),
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            )

                            // SuperKey Input Card
                            val keyTrimmed = folkSuperKey.trim()
                            val isKeyValid = keyTrimmed.isEmpty() || isValidSuperKey(keyTrimmed)
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = cardAlpha),
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(MaterialTheme.shapes.medium)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.TwoTone.Key,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Text(
                                                text = stringResource(R.string.install_folkpatch_superkey_title),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                folkSuperKey = generateRandomSuperKey()
                                                folkShowKey = true
                                            }
                                        ) {
                                            Text(stringResource(R.string.install_folkpatch_superkey_generate))
                                        }
                                    }

                                    OutlinedTextField(
                                        value = folkSuperKey,
                                        onValueChange = { folkSuperKey = it },
                                        singleLine = true,
                                        isError = !isKeyValid,
                                        placeholder = {
                                            Text(
                                                stringResource(R.string.install_folkpatch_superkey_hint),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        supportingText = {
                                            Text(
                                                if (!isKeyValid)
                                                    stringResource(R.string.install_folkpatch_superkey_error)
                                                else
                                                    stringResource(R.string.install_folkpatch_superkey_hint)
                                            )
                                        },
                                        visualTransformation = if (folkShowKey) {
                                            VisualTransformation.None
                                        } else {
                                            PasswordVisualTransformation()
                                        },
                                        trailingIcon = {
                                            IconButton(onClick = { folkShowKey = !folkShowKey }) {
                                                Icon(
                                                    imageVector = if (folkShowKey) Icons.TwoTone.VisibilityOff else Icons.TwoTone.Visibility,
                                                    contentDescription = null
                                                )
                                            }
                                        },
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            // Boot Patch Methods Card
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = cardAlpha),
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(MaterialTheme.shapes.medium)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.TwoTone.AutoFixHigh,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Text(
                                            text = stringResource(R.string.install_folkpatch_methods),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    val selectBootSel = folkSelection as? FolkPatchSelection.SelectBoot
                                    RadioOptionRow(
                                        title = stringResource(R.string.install_folkpatch_select_boot),
                                        summary = if (selectBootSel?.uri != null) {
                                            stringResource(
                                                R.string.install_folkpatch_selected_boot,
                                                selectBootSel.fileName ?: "boot.img"
                                            )
                                        } else {
                                            stringResource(R.string.install_folkpatch_select_boot_summary)
                                        },
                                        selected = folkSelection is FolkPatchSelection.SelectBoot,
                                        onClick = {
                                            selectFolkBootLauncher.launch(
                                                Intent(Intent.ACTION_GET_CONTENT).apply {
                                                    type = "application/octet-stream"
                                                }
                                            )
                                        }
                                    )

                                    if (hasRoot) {
                                        RadioOptionRow(
                                            title = stringResource(R.string.direct_install),
                                            summary = null,
                                            selected = folkSelection is FolkPatchSelection.DirectInstall,
                                            onClick = { folkSelection = FolkPatchSelection.DirectInstall }
                                        )
                                        if (isAbDevice) {
                                            RadioOptionRow(
                                                title = stringResource(R.string.install_inactive_slot),
                                                summary = null,
                                                selected = folkSelection is FolkPatchSelection.InactiveSlot,
                                                onClick = {
                                                    folkInactiveConfirm.showConfirm(
                                                        dialogTitle,
                                                        dialogInactiveContent
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Optional custom kpimg picker
                            FilePickerActionCard(
                                icon = Icons.TwoTone.Memory,
                                title = stringResource(R.string.install_folkpatch_custom_kpimg),
                                subtitle = if (folkCustomKpimgUri != null) {
                                    stringResource(
                                        R.string.install_folkpatch_selected_kpimg,
                                        folkCustomKpimgName ?: "kpimg"
                                    )
                                } else {
                                    stringResource(R.string.install_folkpatch_custom_kpimg_summary)
                                },
                                hasSelection = folkCustomKpimgUri != null,
                                onClear = {
                                    folkCustomKpimgUri = null
                                    folkCustomKpimgName = null
                                },
                                onClick = {
                                    selectFolkKpimgLauncher.launch(
                                        Intent(Intent.ACTION_GET_CONTENT).apply {
                                            type = "*/*"
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = canProceed,
                onClick = onClickNext,
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Text(
                    text = stringResource(id = R.string.install_next),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun InstallModeCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    badge: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = cardAlpha)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = cardAlpha)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val border = if (selected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }

    Surface(
        modifier = modifier.clip(MaterialTheme.shapes.large),
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
        border = border,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun ModeNoteCard(
    icon: ImageVector,
    title: String,
    body: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun FilePickerActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    hasSelection: Boolean,
    onClear: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = cardAlpha),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (hasSelection) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.TwoTone.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.TwoTone.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RadioOptionRow(
    title: String,
    summary: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        color = if (selected)
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = cardAlpha)
        else
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = cardAlpha),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton,
                    indication = LocalIndication.current,
                    interactionSource = interactionSource
                )
                .padding(vertical = 10.dp, horizontal = 12.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                interactionSource = interactionSource,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Column(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RebootDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(id = R.string.reboot_complete_title)) },
            text = { Text(stringResource(id = R.string.reboot_complete_msg)) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(id = R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.no))
                }
            }
        )
    }
}

sealed class InstallMethod {
    data class SelectFile(
        val uri: Uri? = null,
        @param:StringRes override val label: Int = R.string.select_file,
        override val summary: String?
    ) : InstallMethod()

    data object DirectInstall : InstallMethod() {
        override val label: Int
            get() = R.string.direct_install
    }

    data object DirectInstallToInactiveSlot : InstallMethod() {
        override val label: Int
            get() = R.string.install_inactive_slot
    }

    data class HorizonKernel(
        val uri: Uri? = null,
        val slot: String? = null,
        @param:StringRes override val label: Int = R.string.horizon_kernel,
        override val summary: String? = null
    ) : InstallMethod()

    abstract val label: Int
    open val summary: String? = null
}

@Composable
private fun SelectInstallMethod(
    isGKI: Boolean = false,
    rootAvailable: Boolean = false,
    isAbDevice: Boolean = false,
    onSelected: (InstallMethod) -> Unit = {},
    selectedMethod: InstallMethod? = null
) {
    val defaultPartitionName by produceState(initialValue = "boot") {
        value = withContext(Dispatchers.IO) { getDefaultPartition() }
    }
    val horizonKernelSummary = stringResource(R.string.horizon_kernel_summary)
    val selectFileTip = stringResource(
        id = R.string.select_file_tip, defaultPartitionName
    )

    val radioOptions = mutableListOf<InstallMethod>(
        InstallMethod.SelectFile(summary = selectFileTip)
    )

    if (rootAvailable) {
        radioOptions.add(InstallMethod.DirectInstall)
        if (isAbDevice) {
            radioOptions.add(InstallMethod.DirectInstallToInactiveSlot)
        }
        radioOptions.add(InstallMethod.HorizonKernel(summary = horizonKernelSummary))
    }

    var selectedOption by remember { mutableStateOf<InstallMethod?>(null) }
    var currentSelectingMethod by remember { mutableStateOf<InstallMethod?>(null) }

    LaunchedEffect(selectedMethod) {
        selectedOption = selectedMethod
    }

    val selectImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                val option = when (currentSelectingMethod) {
                    is InstallMethod.SelectFile -> InstallMethod.SelectFile(
                        uri,
                        summary = selectFileTip
                    )

                    is InstallMethod.HorizonKernel -> InstallMethod.HorizonKernel(
                        uri,
                        summary = horizonKernelSummary
                    )

                    else -> null
                }
                option?.let { opt ->
                    selectedOption = opt
                    onSelected(opt)
                }
            }
        }
    }

    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            selectedOption = InstallMethod.DirectInstallToInactiveSlot
            onSelected(InstallMethod.DirectInstallToInactiveSlot)
        },
        onDismiss = null
    )

    val dialogTitle = stringResource(id = android.R.string.dialog_alert_title)
    val dialogContent = stringResource(id = R.string.install_inactive_slot_warning)

    val onClick = { option: InstallMethod ->
        currentSelectingMethod = option
        when (option) {
            is InstallMethod.SelectFile, is InstallMethod.HorizonKernel -> {
                selectImageLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "application/*"
                    putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        arrayOf("application/octet-stream", "application/zip")
                    )
                })
            }

            is InstallMethod.DirectInstall -> {
                selectedOption = option
                onSelected(option)
            }

            is InstallMethod.DirectInstallToInactiveSlot -> {
                confirmDialog.showConfirm(dialogTitle, dialogContent)
            }
        }
    }

    var lkmExpanded by remember { mutableStateOf(true) }
    var gkiExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (isGKI) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = cardAlpha),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { lkmExpanded = !lkmExpanded }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.AutoFixHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.Lkm_install_methods),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (lkmExpanded) Icons.TwoTone.ExpandLess else Icons.TwoTone.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(
                        visible = lkmExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                start = 14.dp,
                                end = 14.dp,
                                bottom = 14.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            radioOptions.filter { it !is InstallMethod.HorizonKernel }.forEach { option ->
                                RadioOptionRow(
                                    title = stringResource(id = option.label),
                                    summary = option.summary,
                                    selected = option.javaClass == selectedOption?.javaClass,
                                    onClick = { onClick(option) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (rootAvailable) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = cardAlpha),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { gkiExpanded = !gkiExpanded }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.FileUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.GKI_install_methods),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (gkiExpanded) Icons.TwoTone.ExpandLess else Icons.TwoTone.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(
                        visible = gkiExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                start = 14.dp,
                                end = 14.dp,
                                bottom = 14.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            radioOptions.filterIsInstance<InstallMethod.HorizonKernel>().forEach { option ->
                                RadioOptionRow(
                                    title = stringResource(id = option.label),
                                    summary = option.summary,
                                    selected = option.javaClass == selectedOption?.javaClass,
                                    onClick = { onClick(option) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSelectKmiDialog(onSelected: (String?) -> Unit): DialogHandle {
    return rememberCustomDialog { dismiss ->
        val supportedKmi by produceState(initialValue = emptyList()) {
            value = getSupportedKmis()
        }
        val options = supportedKmi.map { value ->
            ListOption(
                titleText = value
            )
        }

        var selection by remember { mutableStateOf<String?>(null) }

        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                surface = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            ListDialog(state = rememberUseCaseState(visible = true, onFinishedRequest = {
                onSelected(selection)
            }, onCloseRequest = {
                dismiss()
            }), header = Header.Default(
                title = stringResource(R.string.select_kmi),
            ), selection = ListSelection.Single(
                showRadioButtons = true,
                options = options,
            ) { _, option ->
                selection = option.titleText
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBar(
    onBack: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior,
) {
    LargeFlexibleTopAppBar(
        modifier = Modifier.blurEffect(),
        title = {
            Text(
                text = stringResource(R.string.install),
                fontWeight = FontWeight.Bold
            )
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
        navigationIcon = {
            AppBackButton(
                onClick = onBack
            )
        },
        windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
        scrollBehavior = scrollBehavior
    )
}

private fun resolveDisplayName(context: Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "file"
}

private fun isKoFile(context: Context, uri: Uri): Boolean {
    val seg = uri.lastPathSegment ?: ""
    if (seg.endsWith(".ko", ignoreCase = true)) return true
    return resolveDisplayName(context, uri).endsWith(".ko", ignoreCase = true)
}

@Preview
@Composable
fun SelectInstallPreview() {
    InstallScreen()
}
