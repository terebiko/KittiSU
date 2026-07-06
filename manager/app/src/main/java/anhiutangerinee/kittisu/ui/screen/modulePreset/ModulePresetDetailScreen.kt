package anhiutangerinee.kittisu.ui.screen.modulePreset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ui.component.ConfirmResult
import anhiutangerinee.kittisu.ui.component.SwipeableSnackbarHost
import anhiutangerinee.kittisu.ui.component.rememberConfirmDialog
import anhiutangerinee.kittisu.ui.component.settings.AppBackButton
import anhiutangerinee.kittisu.ui.navigation.LocalNavigator
import anhiutangerinee.kittisu.ui.navigation.Route
import anhiutangerinee.kittisu.ui.screen.FlashIt
import anhiutangerinee.kittisu.ui.theme.CardConfig
import anhiutangerinee.kittisu.ui.theme.ThemeConfig
import anhiutangerinee.kittisu.ui.theme.blurEffect
import anhiutangerinee.kittisu.ui.theme.blurSource
import anhiutangerinee.kittisu.ui.util.LocalSnackbarHost
import anhiutangerinee.kittisu.ui.util.module.LoadedPreset
import anhiutangerinee.kittisu.ui.util.module.PresetModule
import anhiutangerinee.kittisu.ui.util.module.PresetVerificationStatus
import anhiutangerinee.kittisu.ui.util.module.RequirementCheckResult
import anhiutangerinee.kittisu.ui.util.module.RequirementType
import anhiutangerinee.kittisu.ui.util.module.areDependenciesSatisfied
import anhiutangerinee.kittisu.ui.util.module.checkPresetRequirements
import anhiutangerinee.kittisu.ui.util.module.isModuleInstalled
import anhiutangerinee.kittisu.ui.viewmodel.ModulePresetViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulePresetDetailScreen(preset: LoadedPreset) {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<ModulePresetViewModel>()
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val confirmDialog = rememberConfirmDialog()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    var downloadInProgress by remember { mutableStateOf(false) }
    var progressCurrent by remember { mutableStateOf(0) }
    var progressTotal by remember { mutableStateOf(0) }
    var errorAlert by remember { mutableStateOf<String?>(null) }

    val allInstalledString = stringResource(R.string.preset_all_installed)
    val unverifiedTitle = stringResource(R.string.preset_unverified_confirm_title)
    val unverifiedContent = stringResource(R.string.preset_unverified_confirm_content)
    val requirementFmt = stringResource(R.string.preset_requirement_failed)
    val depsFmt = stringResource(R.string.preset_dependencies_missing)
    val commandExecutionFailed = stringResource(R.string.command_execution_failed)

    LaunchedEffect(Unit) { scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(preset.presetEntry.destination, maxLines = 1) },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (ThemeConfig.isEnableBlur) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha),
                    scrolledContainerColor = if (ThemeConfig.isEnableBlur) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha)
                )
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha), tonalElevation = 4.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                    Button(onClick = {
                        scope.launch {
                            if (preset.verificationStatus == PresetVerificationStatus.UNVERIFIED) {
                                val r = confirmDialog.awaitConfirm(title = unverifiedTitle, content = unverifiedContent)
                                if (r != ConfirmResult.Confirmed) return@launch
                            }
                            val planResult = viewModel.buildInstallPlan(preset, skipInstalled = true)
                            val plan = planResult.getOrNull()
                            if (plan == null) {
                                val ex = planResult.exceptionOrNull()
                                snackBarHost.showSnackbar(commandExecutionFailed.format(ex?.localizedMessage ?: ex?.toString() ?: ""))
                                return@launch
                            }
                            for (pm in plan.modules) {
                                val req = checkPresetRequirements(pm.presetModule.requirement)
                                if (req is RequirementCheckResult.Failed) {
                                    errorAlert = requirementFmt.format(reasonForRequirement(req))
                                    return@launch
                                }
                                if (!areDependenciesSatisfied(pm.presetModule.dependsOn)) {
                                    errorAlert = depsFmt.format(pm.presetModule.dependsOn.joinToString(", "))
                                    return@launch
                                }
                            }
                            val toInstall = plan.modules.count { !it.skip }
                            if (toInstall == 0) {
                                snackBarHost.showSnackbar(allInstalledString)
                                return@launch
                            }
                            progressCurrent = 0
                            progressTotal = plan.modules.count { !it.skip }
                            downloadInProgress = true
                            val downloadResult = viewModel.downloadAllModules(plan) { c, t -> progressCurrent = c; progressTotal = t }
                            downloadInProgress = false
                            val downloaded = downloadResult.getOrNull()
                            if (downloaded == null) {
                                val ex = downloadResult.exceptionOrNull()
                                snackBarHost.showSnackbar(commandExecutionFailed.format(ex?.localizedMessage ?: ex?.toString() ?: ""))
                                return@launch
                            }
                            val uris = downloaded.modules.mapNotNull { it.cacheUri }
                            if (uris.isNotEmpty()) navigator.push(Route.Flash(FlashIt.FlashModules(uris)))
                            else snackBarHost.showSnackbar(allInstalledString)
                        }
                    }) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.preset_install))
                    }
                }
            }
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection).blurSource(),
            contentPadding = PaddingValues(start = 16.dp, top = innerPadding.calculateTopPadding(), end = 16.dp, bottom = innerPadding.calculateBottomPadding() + 8.dp)
        ) {
            item { PresetHeaderCard(preset); Spacer(Modifier.height(16.dp)) }
            items(preset.presetEntry.modules.size) { i ->
                val pm = preset.presetEntry.modules[i]
                ModuleRowCard(pm)
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (downloadInProgress) DownloadProgressDialog(progressCurrent, progressTotal)

    errorAlert?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorAlert = null },
            title = { Text(stringResource(R.string.operation_failed)) },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { errorAlert = null }) { Text(stringResource(android.R.string.ok)) } }
        )
    }
}

@Composable
private fun PresetHeaderCard(preset: LoadedPreset) {
    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(preset.presetEntry.destination, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            preset.presetEntry.author?.let { a ->
                Spacer(Modifier.height(4.dp))
                Text(a, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerificationBadge(preset)
                if (preset.isLocal) { Spacer(Modifier.size(8.dp)); LocalBadge() }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.preset_modules_count, preset.presetEntry.modules.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ModuleRowCard(pm: PresetModule) {
    val installed = isModuleInstalled(pm.moduleId)
    val req = checkPresetRequirements(pm.requirement)
    val depsOk = areDependenciesSatisfied(pm.dependsOn)
    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pm.moduleName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (pm.rebootAfter) RebootBadge()
            }
            Spacer(Modifier.height(4.dp))
            Text(pm.moduleId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            pm.moduleVersion?.let { v ->
                Text(stringResource(R.string.preset_module_version) + ": " + v, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(shortenUrl(pm.directUrl), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(
                    if (installed) stringResource(R.string.preset_status_installed) else stringResource(R.string.preset_status_not_installed),
                    if (installed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
                )
                Spacer(Modifier.size(8.dp))
                Badge(
                    if (depsOk) stringResource(R.string.preset_dependencies_ok) else stringResource(R.string.preset_dependencies_missing_short, pm.dependsOn.joinToString(", ")),
                    if (depsOk) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
                )
            }
            if (req is RequirementCheckResult.Failed) {
                Spacer(Modifier.height(6.dp))
                Text(reasonForRequirement(req), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DownloadProgressDialog(current: Int, total: Int) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
        title = { Text(stringResource(R.string.preset_downloading)) },
        text = {
            Column {
                Text(stringResource(R.string.preset_download_progress, current, total))
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { if (total == 0) 0f else (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {}
    )
}

private fun shortenUrl(url: String): String = if (url.length <= 48) url else url.take(45) + "..."

private fun reasonForRequirement(failed: RequirementCheckResult.Failed): String = when (failed.type) {
    RequirementType.SUSFS -> "SuSFS: ${failed.reason}"
    RequirementType.KERNELSU -> "KernelSU: ${failed.reason}"
    RequirementType.ANDROID -> "Android: ${failed.reason}"
    RequirementType.METADATA -> failed.reason
}
