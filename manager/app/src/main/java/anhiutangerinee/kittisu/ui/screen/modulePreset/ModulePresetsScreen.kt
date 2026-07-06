package anhiutangerinee.kittisu.ui.screen.modulePreset

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ui.component.SwipeableSnackbarHost
import anhiutangerinee.kittisu.ui.component.settings.AppBackButton
import anhiutangerinee.kittisu.ui.navigation.LocalNavigator
import anhiutangerinee.kittisu.ui.navigation.Route
import anhiutangerinee.kittisu.ui.theme.CardConfig
import anhiutangerinee.kittisu.ui.theme.ThemeConfig
import anhiutangerinee.kittisu.ui.theme.blurEffect
import anhiutangerinee.kittisu.ui.theme.blurSource
import anhiutangerinee.kittisu.ui.util.LocalSnackbarHost
import anhiutangerinee.kittisu.ui.util.module.LoadedPreset
import anhiutangerinee.kittisu.ui.util.module.PresetVerificationStatus
import anhiutangerinee.kittisu.ui.viewmodel.ModulePresetViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulePresetsScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val viewModel = viewModel<ModulePresetViewModel>()
    val snackBarHost = LocalSnackbarHost.current
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
        if (viewModel.sources.isEmpty()) viewModel.loadSources(context)
        if (viewModel.presets.isEmpty() && !viewModel.isRefreshing) viewModel.refreshPresets(context)
    }

    val showInitialLoading = viewModel.presets.isEmpty() && viewModel.isRefreshing

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.module_presets)) },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                actions = {
                    IconButton(onClick = { viewModel.refreshPresets(context) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.preset_retry))
                    }
                    IconButton(onClick = { navigator.push(Route.ModulePresetSources) }) {
                        Icon(Icons.Filled.Source, contentDescription = stringResource(R.string.preset_sources))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (ThemeConfig.isEnableBlur) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha),
                    scrolledContainerColor = if (ThemeConfig.isEnableBlur) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navigator.push(Route.ModulePresetEditor(null)) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.preset_create_local))
            }
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        )
    ) { innerPadding ->
        if (showInitialLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@Scaffold
        }

        PullToRefreshBox(
            state = pullRefreshState,
            isRefreshing = viewModel.isRefreshing,
            onRefresh = { viewModel.refreshPresets(context) },
            modifier = Modifier.blurSource(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullRefreshState,
                    isRefreshing = viewModel.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = innerPadding.calculateTopPadding())
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding(),
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 80.dp
                )
            ) {
                viewModel.errorMessage?.let { err ->
                    item {
                        ErrorBanner(err) { viewModel.refreshPresets(context) }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                if (viewModel.presets.isEmpty()) {
                    item { EmptyState() }
                } else {
                    items(viewModel.presets, key = { it.sourceId + ":" + it.fileName + ":" + it.presetEntry.id }) { p ->
                        PresetCard(p) { navigator.push(Route.ModulePresetDetail(p)) }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.size(12.dp))
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.preset_retry), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Source, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.preset_no_presets), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun PresetCard(preset: LoadedPreset, onClick: () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(preset.presetEntry.destination, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.size(8.dp))
                VerificationBadge(preset)
            }
            preset.presetEntry.author?.takeIf { it.isNotBlank() }?.let { a ->
                Spacer(Modifier.height(6.dp))
                Text(a, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.preset_modules_count, preset.presetEntry.modules.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (preset.isLocal) {
                    Spacer(Modifier.size(8.dp))
                    LocalBadge()
                }
            }
        }
    }
}

@Composable
fun VerificationBadge(preset: LoadedPreset) {
    val (text, color) = when (preset.verificationStatus) {
        PresetVerificationStatus.VERIFIED -> stringResource(R.string.preset_verified) to MaterialTheme.colorScheme.secondaryContainer
        PresetVerificationStatus.UNVERIFIED -> stringResource(R.string.preset_unverified) to MaterialTheme.colorScheme.errorContainer
        PresetVerificationStatus.MISSING_SIGNATURE -> stringResource(R.string.preset_missing_signature) to MaterialTheme.colorScheme.errorContainer
        PresetVerificationStatus.INVALID_SIGNATURE -> stringResource(R.string.preset_invalid_signature) to MaterialTheme.colorScheme.errorContainer
    }
    Badge(text, color)
}

@Composable
fun LocalBadge() {
    Badge(stringResource(R.string.preset_local), MaterialTheme.colorScheme.tertiaryContainer)
}

@Composable
fun RebootBadge() {
    Badge(stringResource(R.string.preset_reboot), MaterialTheme.colorScheme.primaryContainer)
}

@Composable
fun Badge(text: String, color: Color) {
    Surface(color = color, shape = RoundedCornerShape(50)) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
