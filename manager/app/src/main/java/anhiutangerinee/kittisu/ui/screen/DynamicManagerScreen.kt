package anhiutangerinee.kittisu.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ui.component.ConfirmResult
import anhiutangerinee.kittisu.ui.component.SwipeableSnackbarHost
import anhiutangerinee.kittisu.ui.component.rememberConfirmDialog
import anhiutangerinee.kittisu.ui.component.settings.AppBackButton
import anhiutangerinee.kittisu.ui.component.settings.SettingsBaseWidget
import anhiutangerinee.kittisu.ui.component.settings.lazySegmentColumn
import anhiutangerinee.kittisu.ui.navigation.LocalNavigator
import anhiutangerinee.kittisu.ui.theme.CardConfig
import anhiutangerinee.kittisu.ui.theme.ThemeConfig
import anhiutangerinee.kittisu.ui.theme.blurEffect
import anhiutangerinee.kittisu.ui.theme.blurSource
import anhiutangerinee.kittisu.ui.util.LocalSnackbarHost
import anhiutangerinee.kittisu.ui.viewmodel.DynamicManagerViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DynamicManagerScreen() {
    val viewModel = viewModel<DynamicManagerViewModel>()
    val navigator = LocalNavigator.current
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val confirm = rememberConfirmDialog()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val setSuccess = stringResource(R.string.dynamic_manager_set_success)
    val setFailed = stringResource(R.string.dynamic_manager_set_failed)
    val clearSuccess = stringResource(R.string.dynamic_manager_disabled_success)
    val clearFailed = stringResource(R.string.dynamic_manager_clear_failed)
    val clearTitle = stringResource(R.string.dynamic_manager_clear_config)
    val clearMessage = stringResource(R.string.dynamic_manager_clear_confirm_message)
    val grantTitle = stringResource(R.string.dynamic_manager_grant_confirm_title)
    val grantMessage = stringResource(R.string.dynamic_manager_grant_confirm_message)

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.dynamic_manager_title)) },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (ThemeConfig.isEnableBlur) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha),
                    scrolledContainerColor = if (ThemeConfig.isEnableBlur) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha),
                ),
            )
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackbar) },
        containerColor = Color.Transparent,
    ) { padding ->
        if (viewModel.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection).blurSource(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 5.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                item {
                    SettingsBaseWidget(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        icon = Icons.TwoTone.Security,
                        title = stringResource(R.string.dynamic_manager_current_status),
                        description = viewModel.config?.takeIf { it.isValid() }?.let {
                            stringResource(R.string.dynamic_manager_enabled_summary, it.size.toString())
                        } ?: stringResource(R.string.dynamic_manager_disabled),
                        onClick = null,
                    )
                }

                if (viewModel.config?.isValid() == true) {
                    item {
                        SettingsBaseWidget(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            icon = Icons.TwoTone.Delete,
                            title = stringResource(R.string.dynamic_manager_clear_config),
                            description = stringResource(R.string.dynamic_manager_clear_config_summary),
                            onClick = {
                                scope.launch {
                                    if (confirm.awaitConfirm(
                                            title = clearTitle,
                                            content = clearMessage,
                                        ) == ConfirmResult.Confirmed) {
                                        snackbar.showSnackbar(if (viewModel.clearSelection()) clearSuccess else clearFailed)
                                    }
                                }
                            },
                        )
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.manage_managers),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 32.dp, top = 20.dp, bottom = 8.dp),
                    )
                }

                lazySegmentColumn(
                    items = viewModel.apps,
                    key = { _, app -> app.packageName },
                ) { _, app ->
                    val context = LocalContext.current
                    SettingsBaseWidget(
                        title = app.label,
                        description = app.packageName,
                        enabled = app.changeable,
                        selected = app.selected,
                        leadingContent = {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(app.packageInfo).build(),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                            )
                        },
                        iconPlaceholder = false,
                        onClick = {
                            scope.launch {
                                if (confirm.awaitConfirm(
                                    title = grantTitle,
                                    content = grantMessage,
                                ) == ConfirmResult.Confirmed) {
                                    snackbar.showSnackbar(if (viewModel.select(app)) setSuccess else setFailed)
                                }
                            }
                        },
                        trailingContent = {
                            Checkbox(app.selected || !app.changeable, null, enabled = app.changeable)
                        },
                    )
                }
            }
        }
    }
}
