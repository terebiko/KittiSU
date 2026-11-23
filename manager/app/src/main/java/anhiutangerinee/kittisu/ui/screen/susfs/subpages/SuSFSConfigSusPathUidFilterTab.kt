package anhiutangerinee.kittisu.ui.screen.susfs.subpages

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.res.stringResource
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.data.susfs.SuSFSConfigHelper
import anhiutangerinee.kittisu.data.susfs.SuSFSUidFilterApp
import anhiutangerinee.kittisu.ui.component.SuSFSAppSelectionList
import anhiutangerinee.kittisu.ui.screen.susfs.RegisterSuSFSRefresh
import anhiutangerinee.kittisu.ui.screen.susfs.SuSFSRefreshRegistrar
import anhiutangerinee.kittisu.ui.screen.susfs.component.SuSFSDescriptionCard
import anhiutangerinee.kittisu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.launch

@Composable
fun SusPathUidFilterTab(
    nestedScrollConnection: NestedScrollConnection,
    innerPadding: PaddingValues,
    onRegisterRefresh: SuSFSRefreshRegistrar,
) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHost.current
    val failedMessage = stringResource(R.string.susfs_operation_failed)
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var installedApps by remember { mutableStateOf<List<SuSFSUidFilterApp>>(emptyList()) }
    var appsLoaded by remember { mutableStateOf(false) }
    var configLoaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    RegisterSuSFSRefresh(onRegisterRefresh) { config, _ ->
        if (!saving) {
            selected = config.sus_path_uid_filter.orEmpty()
            configLoaded = true
        }
        if (!appsLoaded) {
            installedApps = SuSFSConfigHelper.loadUidFilterApps()
            appsLoaded = true
        }
    }

    SuSFSAppSelectionList(
        nestedScrollConnection = nestedScrollConnection,
        innerPadding = innerPadding,
        installedApps = installedApps,
        appsLoaded = appsLoaded,
        selected = selected,
        enabled = configLoaded && !saving,
        onToggle = { packageName ->
            val installed = installedApps.mapTo(hashSetOf()) { it.packageName }
            val current = selected.intersect(installed)
            val updated = if (packageName in selected) current - packageName else current + packageName
            saving = true
            scope.launch {
                try {
                    if (SuSFSConfigHelper.setSusPathUidFilter(updated)) {
                        selected = updated
                    } else {
                        snackbar.showSnackbar(failedMessage)
                    }
                } finally {
                    saving = false
                }
            }
        },
    ) {
        SuSFSDescriptionCard(
            title = stringResource(R.string.susfs_sus_path_uid_filter_title),
            description = stringResource(R.string.susfs_sus_path_uid_filter_description),
        )
    }
}
