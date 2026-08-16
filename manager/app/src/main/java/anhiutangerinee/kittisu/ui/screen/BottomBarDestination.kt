package anhiutangerinee.kittisu.ui.screen

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AdminPanelSettings
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.Layers
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ui.component.ksuIsValid
import anhiutangerinee.kittisu.ui.screen.main.HomePage
import anhiutangerinee.kittisu.ui.screen.main.ModulePage
import anhiutangerinee.kittisu.ui.screen.main.SettingsPage
import anhiutangerinee.kittisu.ui.screen.main.SuperUserPage

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
        fun getPages(): List<BottomBarDestination> {
            if (ksuIsValid()) {
                return BottomBarDestination.entries
            } else {
                return BottomBarDestination.entries.filter {
                    !it.rootRequired
                }
            }
        }
    }
}
