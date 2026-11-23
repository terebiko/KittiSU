package anhiutangerinee.kittisu.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.data.susfs.SuSFSUidFilterApp
import anhiutangerinee.kittisu.ui.component.settings.SegmentedColumn
import anhiutangerinee.kittisu.ui.component.settings.SettingsBaseWidget
import anhiutangerinee.kittisu.ui.component.settings.SettingsTextFieldWidget
import anhiutangerinee.kittisu.ui.component.settings.lazySegmentColumn

@Composable
fun SuSFSAppSelectionList(
    nestedScrollConnection: NestedScrollConnection,
    innerPadding: PaddingValues,
    installedApps: List<SuSFSUidFilterApp>,
    appsLoaded: Boolean,
    selected: Set<String>,
    enabled: Boolean,
    onToggle: (String) -> Unit,
    header: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val search = remember { TextFieldState() }
    val apps = remember(installedApps, selected, search.text.toString()) {
        val installed = installedApps.associateBy { it.packageName }
        val missing = (selected - installed.keys).map { SuSFSUidFilterApp(it, it, null) }
        (missing + installedApps).filter {
            it.label.contains(search.text, ignoreCase = true) ||
                it.packageName.contains(search.text, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = innerPadding.calculateTopPadding())
            .nestedScroll(nestedScrollConnection),
    ) {
        item {
            header()
        }
        item {
            SegmentedColumn {
                item {
                    SettingsTextFieldWidget(
                        state = search,
                        title = stringResource(R.string.susfs_uid_filter_search),
                        useLabelAsPlaceholder = true,
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                }
            }
        }
        if (!appsLoaded) {
            item {
                SegmentedColumn {
                    item {
                        SettingsBaseWidget(
                            title = stringResource(R.string.susfs_uid_filter_loading),
                            iconPlaceholder = false,
                        )
                    }
                }
            }
        }
        lazySegmentColumn(items = apps, key = { _, app -> app.packageName }) { _, app ->
            val checked = app.packageName in selected
            SettingsBaseWidget(
                modifier = Modifier.semantics(mergeDescendants = true) {
                    role = Role.Checkbox
                    toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                },
                title = app.label,
                description = app.packageName,
                enabled = enabled && appsLoaded,
                selected = checked,
                iconPlaceholder = false,
                leadingContent = {
                    val pkgInfo = app.packageInfo
                    if (pkgInfo != null) {
                        val iconReq = remember(app.packageName) {
                            ImageRequest.Builder(context)
                                .data(pkgInfo)
                                .crossfade(false)
                                .build()
                        }
                        AsyncImage(
                            model = iconReq,
                            contentDescription = app.label,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.medium)
                        )
                    }
                },
                onClick = {
                    onToggle(app.packageName)
                },
            )
        }
        item { Spacer(Modifier.height(innerPadding.calculateBottomPadding())) }
    }
}
