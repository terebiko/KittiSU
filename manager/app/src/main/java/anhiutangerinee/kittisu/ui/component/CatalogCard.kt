package anhiutangerinee.kittisu.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.domain.model.ModuleReleaseAsset
import anhiutangerinee.kittisu.ui.theme.CardConfig
import anhiutangerinee.kittisu.ui.theme.ThemeConfig
import anhiutangerinee.kittisu.ui.theme.renderBackgroundBlur
import anhiutangerinee.kittisu.ui.viewmodel.formatFileSize
import org.koin.compose.koinInject

@Composable
fun CatalogCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color? = null,
    backgroundEnabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val themeConfig = koinInject<ThemeConfig>()
    val cardConfig = koinInject<CardConfig>()
    Surface(
        color = if (!backgroundEnabled || themeConfig.isEnableBlurExp) {
            Color.Transparent
        } else {
            (containerColor ?: MaterialTheme.colorScheme.surfaceBright).copy(cardConfig.cardAlpha)
        },
        modifier = modifier.clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (backgroundEnabled) Modifier.renderBackgroundBlur() else Modifier),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun CatalogCardHeading(title: String, leadingContent: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        leadingContent()
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun moduleAssetDetails(asset: ModuleReleaseAsset, format: Int = R.string.assert_support_content): String? {
    val size = asset.size?.let(::formatFileSize)
    return when {
        size != null && asset.downloadCount != null -> stringResource(format, size, asset.downloadCount)
        size != null -> size
        asset.downloadCount != null -> stringResource(R.string.repo_download_count, asset.downloadCount)
        else -> null
    }
}
