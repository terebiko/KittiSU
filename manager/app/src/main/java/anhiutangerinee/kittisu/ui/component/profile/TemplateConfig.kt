package anhiutangerinee.kittisu.ui.component.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ReadMore
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import anhiutangerinee.kittisu.Natives
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ui.component.settings.SettingsDropdownWidget
import anhiutangerinee.kittisu.ui.util.listAppProfileTemplates
import anhiutangerinee.kittisu.ui.util.setSepolicy
import anhiutangerinee.kittisu.ui.viewmodel.getTemplateInfoById

/**
 * @author weishu
 * @date 2023/10/21.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateConfig(
    profile: Natives.Profile,
    onViewTemplate: (id: String) -> Unit = {},
    onManageTemplate: () -> Unit = {},
    onProfileChange: (Natives.Profile) -> Unit
) {
//    var expanded by remember { mutableStateOf(false) }
    var template by rememberSaveable {
        mutableStateOf(profile.rootTemplate ?: "")
    }
    val profileTemplates = listOf("None") + listAppProfileTemplates()
//    val noTemplates = profileTemplates.isEmpty()

    SettingsDropdownWidget(
        icon = Icons.AutoMirrored.TwoTone.Article,
        title = stringResource(R.string.profile_template),
        items = profileTemplates,
        selectedIndex = profileTemplates.indexOf(template) + 1,
        afterContent = { index ->
            if (index == 0) return@SettingsDropdownWidget
            Icon(
                imageVector = Icons.AutoMirrored.TwoTone.ReadMore,
                contentDescription = null,
                modifier = Modifier
                    .size(35.dp)
                    .clip(CircleShape)
                    .clickable {
                        onViewTemplate(profileTemplates[index])
                    }
                    .padding(5.dp)
            )
        }
    ) { index ->
        if (index == 0) {
            template = ""
            return@SettingsDropdownWidget
        }

        template = profileTemplates[index - 1]

        val templateInfo =
            getTemplateInfoById(template) ?: return@SettingsDropdownWidget

        if (setSepolicy(template, templateInfo.rules.joinToString("\n"))) {
            onProfileChange(
                profile.copy(
                    rootTemplate = template,
                    rootUseDefault = false,
                    uid = templateInfo.uid,
                    gid = templateInfo.gid,
                    groups = templateInfo.groups,
                    capabilities = templateInfo.capabilities,
                    context = templateInfo.context,
                    namespace = templateInfo.namespace,
                )
            )
        }
    }
}