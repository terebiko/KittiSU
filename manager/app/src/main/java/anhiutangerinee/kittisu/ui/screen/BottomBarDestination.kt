package anhiutangerinee.kittisu.ui.screen

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ui.MainActivity
import anhiutangerinee.kittisu.ui.component.ksuIsValid
import anhiutangerinee.kittisu.ui.screen.main.HomePage
import anhiutangerinee.kittisu.ui.screen.main.KpmPage
import anhiutangerinee.kittisu.ui.screen.main.ModulePage
import anhiutangerinee.kittisu.ui.screen.main.SettingsPage
import anhiutangerinee.kittisu.ui.screen.main.SuperUserPage
import anhiutangerinee.kittisu.ui.util.getKpmVersion

enum class BottomBarDestination(
    val direction: @Composable (bottomPadding: Dp) -> Unit,
    @param:StringRes val label: Int,
    val iconSelected: ImageVector,
    val iconNotSelected: ImageVector,
    val rootRequired: Boolean,
) {
    Home(
        { bottomPadding -> HomePage(bottomPadding) },
        R.string.home,
        Icons.Filled.Home,
        Icons.Outlined.Home,
        false
    ),
    Kpm(
        { bottomPadding -> KpmPage(bottomPadding) },
        R.string.kpm_title,
        Icons.Filled.Archive,
        Icons.Outlined.Archive,
        true
    ),
    SuperUser(
        { bottomPadding -> SuperUserPage(bottomPadding) },
        R.string.superuser,
        Icons.Filled.AdminPanelSettings,
        Icons.Outlined.AdminPanelSettings,
        true
    ),
    Module(
        { bottomPadding -> ModulePage(bottomPadding) },
        R.string.module,
        Icons.Filled.Layers,
        Icons.Outlined.Layers,
        true
    ),
    Settings(
        { bottomPadding -> SettingsPage(bottomPadding) },
        R.string.settings,
        Icons.Filled.Settings,
        Icons.Outlined.Settings,
        false
    );

    companion object {
        fun getPages(settings: MainActivity.SettingsState) : List<BottomBarDestination> {
            if (ksuIsValid()) {
                // 全功能管理器
                val kpmVersion = runCatching {
                    getKpmVersion()
                }.getOrNull()

                val showKpmInfo = settings.showKpmInfo
                return BottomBarDestination.entries.filter {
                    when (it) {
                        Kpm -> {
                            kpmVersion?.isNotEmpty() ?: false && !showKpmInfo
                        }

                        else -> true
                    }
                }
            } else {
                return BottomBarDestination.entries.filter {
                    !it.rootRequired
                }
            }
        }
    }
}
