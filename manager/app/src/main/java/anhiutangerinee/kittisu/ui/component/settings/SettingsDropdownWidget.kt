package anhiutangerinee.kittisu.ui.component.settings

import android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsDropdownWidget(
    icon: ImageVector? = null,
    iconPlaceholder: Boolean = true,
    title: String,
    description: String? = null,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    isError: Boolean = false,
    hapticFeedbackType: HapticFeedbackType = HapticFeedbackType.ContextClick,
    leadingContent: (@Composable () -> Unit)? = null,
    foreContent: @Composable BoxScope.() -> Unit = {},
    afterContent: @Composable RowScope.(Int) -> Unit = {},
    items: List<String>,
    selectedIndex: Int,
    maxHeight: Dp? = 400.dp,
    colors: SettingsDropdownWidgetColors = SettingsDropdownWidgetDefault.colors(),
    onSelectedIndexChange: (Int) -> Unit
) {
    val alpha = if (enabled) 1f else 0.38f
    var showDialog by remember { mutableStateOf(false) }
    var currentIndex by remember(selectedIndex, showDialog) { mutableIntStateOf(selectedIndex) }

    fun setCurrentIndex(index: Int) {
        currentIndex = index
    }

    fun dismiss() {
        showDialog = false
    }

    val itemsNotEmpty = items.isNotEmpty()
    val safeSelectedIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))

    SettingsBaseWidget(
        icon = icon,
        iconPlaceholder = iconPlaceholder,
        title = title,
        description = description,
        descriptionColor = descriptionColor,
        enabled = enabled,
        isError = isError,
        onClick = {
            showDialog = true
        },
        clickHaptic = hapticFeedbackType,
        leadingContent = leadingContent,
        foreContent = foreContent,
        descriptionColumnContent = {
            if (itemsNotEmpty) {
                Spacer(modifier = Modifier.height(6.dp))
                val pillContainer = if (isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                }
                val pillContent = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = pillContainer
                ) {
                    Text(
                        text = items[safeSelectedIndex],
                        color = pillContent.copy(alpha = alpha),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    ) {
        Icon(
            imageVector = Icons.TwoTone.UnfoldMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }

    if (showDialog && itemsNotEmpty) {
        AlertDialog(
            onDismissRequest = { dismiss() },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            },
            text = {
                val dialogMaxHeight = maxHeight ?: 400.dp
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = dialogMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items.size) { index ->
                        DropdownItem(
                            text = items[index],
                            isSelected = currentIndex == index,
                            colors = colors,
                            afterContent = { item ->
                                afterContent(items.indexOf(item))
                            },
                            onClick = {
                                setCurrentIndex(index)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSelectedIndexChange(currentIndex)
                    dismiss()
                }) {
                    Text(text = stringResource(id = R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    setCurrentIndex(selectedIndex)
                    dismiss()
                }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun DropdownItem(
    text: String,
    isSelected: Boolean,
    colors: SettingsDropdownWidgetColors,
    afterContent: @Composable RowScope.(String) -> Unit = {},
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        colors.selectedBackgroundColor
    } else {
        Color.Transparent
    }

    val contentColor = if (isSelected) {
        colors.selectedContentColor
    } else {
        colors.contentColor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.selectedContentColor,
                unselectedColor = colors.contentColor
            )
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        afterContent(text)
    }
}

@Immutable
data class SettingsDropdownWidgetColors(
    val titleColor: Color,
    val summaryColor: Color,
    val valueColor: Color,
    val iconColor: Color,
    val arrowColor: Color,
    val disabledTitleColor: Color,
    val disabledSummaryColor: Color,
    val disabledValueColor: Color,
    val disabledIconColor: Color,
    val disabledArrowColor: Color,
    val contentColor: Color,
    val selectedContentColor: Color,
    val selectedBackgroundColor: Color
)

object SettingsDropdownWidgetDefault {
    @Composable
    fun colors(
        titleColor: Color = MaterialTheme.colorScheme.onSurface,
        summaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        iconColor: Color = MaterialTheme.colorScheme.primary,
        arrowColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledTitleColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        disabledSummaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        disabledValueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        disabledIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        disabledArrowColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        contentColor: Color = MaterialTheme.colorScheme.onSurface,
        selectedContentColor: Color = MaterialTheme.colorScheme.primary,
        selectedBackgroundColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ): SettingsDropdownWidgetColors {
        return SettingsDropdownWidgetColors(
            titleColor = titleColor,
            summaryColor = summaryColor,
            valueColor = valueColor,
            iconColor = iconColor,
            arrowColor = arrowColor,
            disabledTitleColor = disabledTitleColor,
            disabledSummaryColor = disabledSummaryColor,
            disabledValueColor = disabledValueColor,
            disabledIconColor = disabledIconColor,
            disabledArrowColor = disabledArrowColor,
            contentColor = contentColor,
            selectedContentColor = selectedContentColor,
            selectedBackgroundColor = selectedBackgroundColor
        )
    }
}