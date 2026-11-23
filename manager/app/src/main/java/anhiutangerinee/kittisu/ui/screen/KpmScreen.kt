package anhiutangerinee.kittisu.ui.screen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Key
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Terminal
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import anhiutangerinee.kittisu.KernelPatchManager
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ui.component.EmptyState
import anhiutangerinee.kittisu.ui.component.SwipeableSnackbarHost
import anhiutangerinee.kittisu.ui.component.settings.AppBackButton
import anhiutangerinee.kittisu.ui.navigation.LocalNavigator
import anhiutangerinee.kittisu.ui.navigation.Route
import anhiutangerinee.kittisu.ui.theme.CardConfig
import anhiutangerinee.kittisu.ui.theme.ThemeConfig
import anhiutangerinee.kittisu.ui.theme.blurEffect
import anhiutangerinee.kittisu.ui.theme.blurSource
import anhiutangerinee.kittisu.ui.theme.m3CardContainerColor
import anhiutangerinee.kittisu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KpmScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackBarHost = LocalSnackbarHost.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val pullRefreshState = rememberPullToRefreshState()

    val isKpActive = KernelPatchManager.isKernelPatchActive
    var modules by remember { mutableStateOf<List<KernelPatchManager.KpmModuleInfo>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }

    var pendingLoadUri by remember { mutableStateOf<Uri?>(null) }
    var pendingLoadName by remember { mutableStateOf("") }
    var controlTarget by remember { mutableStateOf<KernelPatchManager.KpmModuleInfo?>(null) }
    var unloadTarget by remember { mutableStateOf<KernelPatchManager.KpmModuleInfo?>(null) }

    fun refreshList() {
        if (!KernelPatchManager.isKernelPatchActive) {
            modules = emptyList()
            return
        }
        scope.launch {
            isRefreshing = true
            modules = withContext(Dispatchers.IO) {
                KernelPatchManager.listKpmModules()
            }
            isRefreshing = false
        }
    }

    LaunchedEffect(isKpActive) {
        if (isKpActive) {
            refreshList()
        } else {
            modules = emptyList()
        }
    }

    val pickKpmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val displayName = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "module.kpm"
        pendingLoadUri = uri
        pendingLoadName = displayName
    }

    val kpmLoadSuccessStr = stringResource(R.string.kpm_load_success)
    val kpmLoadFailedStr = stringResource(R.string.kpm_load_failed)
    val kpmUnloadSuccessStr = stringResource(R.string.kpm_unload_success)
    val kpmUnloadFailedStr = stringResource(R.string.kpm_unload_failed)

    // Load KPM dialog (optional arguments)
    pendingLoadUri?.let { uri ->
        var loadArgs by remember { mutableStateOf("") }
        var loading by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!loading) pendingLoadUri = null },
            icon = {
                Icon(
                    imageVector = Icons.TwoTone.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(stringResource(R.string.kpm_load_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = pendingLoadName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = loadArgs,
                        onValueChange = { loadArgs = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.kpm_args_optional)) }
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !loading,
                    onClick = {
                        loading = true
                        scope.launch {
                            val rc = withContext(Dispatchers.IO) {
                                val tmp = File(context.cacheDir, pendingLoadName.ifBlank { "module.kpm" })
                                runCatching {
                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                        tmp.outputStream().use { input.copyTo(it) }
                                    } ?: return@withContext -1L
                                    KernelPatchManager.loadKpmModule(tmp, loadArgs.trim())
                                }.getOrDefault(-1L).also {
                                    tmp.delete()
                                }
                            }
                            loading = false
                            pendingLoadUri = null
                            if (rc == 0L) {
                                snackBarHost.showSnackbar(kpmLoadSuccessStr)
                                refreshList()
                            } else {
                                snackBarHost.showSnackbar(kpmLoadFailedStr.format(rc))
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.kpm_load_action))
                }
            },
            dismissButton = {
                TextButton(enabled = !loading, onClick = { pendingLoadUri = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // Control KPM dialog
    controlTarget?.let { target ->
        var ctlArgs by remember { mutableStateOf("") }
        var outputText by remember { mutableStateOf<String?>(null) }
        var running by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!running) controlTarget = null },
            icon = {
                Icon(
                    imageVector = Icons.TwoTone.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(stringResource(R.string.kpm_control_title, target.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = ctlArgs,
                        onValueChange = { ctlArgs = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.kpm_control_args_hint)) }
                    )
                    outputText?.let { out ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = out,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = ctlArgs.isNotBlank() && !running,
                    onClick = {
                        running = true
                        scope.launch {
                            val (rc, msg) = withContext(Dispatchers.IO) {
                                KernelPatchManager.controlKpmModule(target.name, ctlArgs.trim())
                            }
                            running = false
                            outputText = buildString {
                                append("rc = $rc")
                                if (msg.isNotEmpty()) {
                                    append('\n').append(msg)
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.kpm_control_send))
                }
            },
            dismissButton = {
                TextButton(enabled = !running, onClick = { controlTarget = null }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    // Unload KPM confirm dialog
    unloadTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { unloadTarget = null },
            title = { Text(stringResource(R.string.kpm_unload_confirm_title)) },
            text = { Text(stringResource(R.string.kpm_unload_confirm_msg, target.name)) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    onClick = {
                        val mod = target
                        unloadTarget = null
                        scope.launch {
                            val rc = withContext(Dispatchers.IO) {
                                KernelPatchManager.unloadKpmModule(mod.name)
                            }
                            if (rc == 0L) {
                                Toast.makeText(context, kpmUnloadSuccessStr, Toast.LENGTH_SHORT).show()
                                refreshList()
                            } else {
                                snackBarHost.showSnackbar(kpmUnloadFailedStr.format(rc))
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.kpm_unload_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { unloadTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = {
                    Text(
                        text = stringResource(R.string.kpm_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                subtitle = {
                    if (isKpActive) {
                        val ver = KernelPatchManager.formatVersion()
                        Text(
                            text = if (ver.isNotEmpty()) "KernelPatch $ver" else "KernelPatch",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    AppBackButton(onClick = { navigator.pop() })
                },
                actions = {
                    if (isKpActive) {
                        IconButton(onClick = { refreshList() }) {
                            Icon(
                                imageVector = Icons.TwoTone.Refresh,
                                contentDescription = stringResource(R.string.refresh)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (ThemeConfig.isEnableBlur || ThemeConfig.isFullScreenBackgroundEnabled) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    scrolledContainerColor = if (ThemeConfig.isEnableBlur || ThemeConfig.isFullScreenBackgroundEnabled) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
                ),
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            if (isKpActive) {
                ExtendedFloatingActionButton(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    shape = MaterialTheme.shapes.extraLarge,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = {
                        pickKpmLauncher.launch(
                            Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "*/*"
                            }
                        )
                    },
                    icon = { Icon(Icons.TwoTone.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.kpm_load_action)) }
                )
            }
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        if (!isKpActive) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                EmptyState(
                    modifier = Modifier.weight(1f, fill = false),
                    icon = Icons.TwoTone.Memory,
                    title = stringResource(R.string.kpm_not_active_title),
                    description = stringResource(R.string.kpm_not_active_desc),
                    actionLabel = stringResource(R.string.kpm_go_to_install),
                    onAction = {
                        navigator.push(Route.Install(preselectedKernelUri = null))
                    }
                )
            }
            return@Scaffold
        }

        PullToRefreshBox(
            state = pullRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = { refreshList() },
            modifier = Modifier
                .fillMaxSize()
                .blurSource(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier
                        .padding(top = innerPadding.calculateTopPadding())
                        .align(Alignment.TopCenter),
                    state = pullRefreshState,
                    isRefreshing = isRefreshing
                )
            }
        ) {
            if (modules.isEmpty() && !isRefreshing) {
                EmptyState(
                    icon = Icons.TwoTone.Memory,
                    title = stringResource(R.string.kpm_empty_title),
                    description = stringResource(R.string.kpm_empty_desc)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding())
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items = modules, key = { it.name }) { mod ->
                        KpmModuleCard(
                            module = mod,
                            onControl = { controlTarget = mod },
                            onUnload = { unloadTarget = mod }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KpmModuleCard(
    module: KernelPatchManager.KpmModuleInfo,
    onControl: () -> Unit,
    onUnload: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = m3CardContainerColor().copy(alpha = CardConfig.cardAlpha),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.TwoTone.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (module.author.isNotBlank() || module.version.isNotBlank()) {
                        Text(
                            text = listOfNotNull(
                                module.version.takeIf { it.isNotBlank() }?.let { "v$it" },
                                module.author.takeIf { it.isNotBlank() }
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (module.license.isNotBlank()) {
                    LabelText(
                        label = module.license,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                }
            }

            if (module.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (module.args.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "args: ${module.args}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(onClick = onControl) {
                    Icon(
                        imageVector = Icons.TwoTone.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.kpm_control_action))
                }
                FilledTonalButton(
                    onClick = onUnload,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.kpm_unload_action))
                }
            }
        }
    }
}
