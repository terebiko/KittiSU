package anhiutangerinee.kittisu.ui.screen

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AdminPanelSettings
import androidx.compose.material.icons.twotone.Archive
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.Layers
import androidx.compose.material.icons.twotone.Settings
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
        Icons.TwoTone.Home,
        Icons.TwoTone.Home,
        false
    ),
    Kpm(
        { bottomPadding -> KpmPage(bottomPadding) },
        R.string.kpm_title,
        Icons.TwoTone.Archive,
        Icons.TwoTone.Archive,
        true
    ),
    SuperUser(
        { bottomPadding -> SuperUserPage(bottomPadding) },
        R.string.superuser,
        Icons.TwoTone.AdminPanelSettings,
        Icons.TwoTone.AdminPanelSettings,
        true
    ),
    Module(
        { bottomPadding -> ModulePage(bottomPadding) },
        R.string.module,
        Icons.TwoTone.Layers,
        Icons.TwoTone.Layers,
        true
    ),
    Settings(
        { bottomPadding -> SettingsPage(bottomPadding) },
        R.string.settings,
        Icons.TwoTone.Settings,
        Icons.TwoTone.Settings,
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
