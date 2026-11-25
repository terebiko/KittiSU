@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package anhiutangerinee.kittisu.ui.component.settings

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

private const val PADDING_HORIZONTAL = 16
private const val PADDING_VERTICAL = 8

private const val bouncyStiffness = 500f
private const val bouncyDamping = 0.72f
private val segmentedFloatSpring = spring<Float>(dampingRatio = bouncyDamping, stiffness = bouncyStiffness)
private val segmentedDpSpring = spring<Dp>(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy, stiffness = bouncyStiffness)

@DslMarker
annotation class SegmentedColumnDsl

/**
 * Represents the configuration and content of an individual item within a [SegmentedColumn].
 */
@Immutable
data class SegmentedItemData(
    val key: Any?,
    val visible: Boolean,
    val customTopPadding: Dp? = null,
    val forceFlatTop: Boolean = false,
    val forceFlatBottom: Boolean = false,
    val content: @Composable (Shape) -> Unit
)

/**
 * A DSL scope used to define the items and their layout behaviors within a [SegmentedColumn].
 */
@SegmentedColumnDsl
class SegmentedColumnScope {
    val items = mutableListOf<SegmentedItemData>()

    // 内部维护的嵌套上下文状态：用于无感向后代传递“顶部强制扁平”和“全局可见性遮罩”
    private var isInsideExpandableBody: Boolean = false
    private var parentVisibilityMask: Boolean = true

    /**
     * Registers a standard item within the group.
     */
    fun item(
        key: Any? = null,
        visible: Boolean = true,
        topPadding: Dp? = null,
        forceFlatTop: Boolean = false,
        forceFlatBottom: Boolean = false,
        content: @Composable (Shape) -> Unit
    ) {
        val resolvedForceFlatTop = forceFlatTop || isInsideExpandableBody
        val resolvedVisible = visible && parentVisibilityMask

        items.add(
            SegmentedItemData(
                key = key ?: items.size,
                visible = resolvedVisible,
                customTopPadding = topPadding,
                forceFlatTop = resolvedForceFlatTop,
                forceFlatBottom = forceFlatBottom,
                content = content
            )
        )
    }

    /**
     * Registers an expandable item pairing a header with conditionally visible body content.
     * Supports infinite seamless nesting.
     */
    fun expandableItem(
        animatedVisibility: Boolean = true,
        expanded: Boolean,
        topPadding: Dp? = null,
        topContent: @Composable (Shape) -> Unit,
        bottomContent: (SegmentedColumnScope.() -> Unit),
    ) {
        val previousInsideBody = isInsideExpandableBody
        val previousVisibilityMask = parentVisibilityMask

        item(
            visible = animatedVisibility,
            topPadding = topPadding,
            forceFlatBottom = expanded,
            content = topContent
        )

        isInsideExpandableBody = true
        parentVisibilityMask = previousVisibilityMask && animatedVisibility && expanded

        bottomContent()

        isInsideExpandableBody = previousInsideBody
        parentVisibilityMask = previousVisibilityMask
    }
}

/**
 * A highly customized vertical layout group that visually splices multiple composable items together.
 */
@Composable
fun SegmentedColumn(
    modifier: Modifier = Modifier,
    title: String = "",
    contentPadding: PaddingValues = PaddingValues(
        horizontal = PADDING_HORIZONTAL.dp,
        vertical = PADDING_VERTICAL.dp
    ),
    content: SegmentedColumnScope.() -> Unit
) {
    val scope = SegmentedColumnScope().apply(content)
    val allItems = scope.items

    if (allItems.isEmpty()) return

    val isBakaSu = anhiutangerinee.kittisu.ui.theme.ThemeConfig.isBakaSuTheme

    Column(modifier = modifier.padding(contentPadding)) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = if (isBakaSu) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                },
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    start = PADDING_HORIZONTAL.dp,
                    top = if (isBakaSu) PADDING_VERTICAL.dp else 10.dp,
                    bottom = 8.dp
                )
            )
        }

        val progresses = allItems.mapIndexed { index, item ->
            key(item.key ?: index) {
                animateFloatAsState(
                    targetValue = if (item.visible) 1f else 0f,
                    animationSpec = segmentedFloatSpring,
                    label = "progress"
                )
            }
        }

        val firstVisibleIndex = allItems.indexOfFirst { it.visible }
        val lastVisibleIndex = allItems.indexOfLast { it.visible }

        val focusManager = LocalFocusManager.current
        val isDynamicDpSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        Layout(
            content = {
                allItems.forEachIndexed { index, itemData ->
                    key(itemData.key ?: index) {
                        val isFirst =
                            index == firstVisibleIndex || (index == 0 && !itemData.visible)
                        val isLast =
                            index == lastVisibleIndex || (index == allItems.lastIndex && !itemData.visible)

                        val outerRadius = if (isBakaSu) 16.dp else 20.dp
                        val innerRadius = if (isBakaSu) 5.dp else 6.dp
                        val baseTopRadius = if (isFirst) outerRadius else innerRadius
                        val baseBottomRadius = if (isLast) outerRadius else innerRadius

                        val targetTopRadius = if (itemData.forceFlatTop) 0.dp else baseTopRadius
                        val targetBottomRadius =
                            if (itemData.forceFlatBottom) 0.dp else baseBottomRadius

                        val currentTopRadius = if (isDynamicDpSupported) {
                            animateDpAsState(targetTopRadius, segmentedDpSpring, label = "TopRadius")
                                .value.coerceAtLeast(0.dp)
                        } else targetTopRadius

                        val currentBottomRadius = if (isDynamicDpSupported) {
                            animateDpAsState(
                                targetBottomRadius,
                                segmentedDpSpring,
                                label = "BottomRadius"
                            ).value.coerceAtLeast(0.dp)
                        } else targetBottomRadius

                        val shape = remember(currentTopRadius, currentBottomRadius) {
                            RoundedCornerShape(
                                topStart = currentTopRadius,
                                topEnd = currentTopRadius,
                                bottomStart = currentBottomRadius,
                                bottomEnd = currentBottomRadius
                            )
                        }

                        val targetTopPadding = itemData.customTopPadding
                            ?: (if (isFirst) 0.dp else ListItemDefaults.SegmentedGap)
                        val currentTopPadding = if (isDynamicDpSupported) {
                            animateDpAsState(targetTopPadding, segmentedDpSpring, label = "TopPadding")
                                .value.coerceAtLeast(0.dp)
                        } else targetTopPadding

                        var hasFocus by remember { mutableStateOf(false) }

                        LaunchedEffect(itemData.visible) {
                            if (!itemData.visible && hasFocus) {
                                focusManager.clearFocus()
                            }
                        }

                        val progressState = progresses[index]

                        Box(
                            modifier = Modifier
                                .zIndex(if (itemData.visible) (allItems.size - index).toFloat() else -index.toFloat())
                                .onFocusChanged { hasFocus = it.hasFocus }
                                .semantics {
                                    if (!itemData.visible) hideFromAccessibility()
                                }
                                .graphicsLayer {
                                    val currentProgress = progressState.value
                                    if (currentProgress >= 0.999f) {
                                        clip = false
                                        alpha = 1f
                                    } else {
                                        val safeProgress = currentProgress.coerceAtLeast(0f)
                                        clip = true
                                        this.shape = object : Shape {
                                            override fun createOutline(
                                                size: Size,
                                                layoutDirection: LayoutDirection,
                                                density: Density
                                            ) =
                                                Outline.Rectangle(
                                                    Rect(
                                                        0f,
                                                        0f,
                                                        size.width,
                                                        size.height * safeProgress
                                                    )
                                                )
                                        }
                                        alpha = (currentProgress * 1.5f).coerceIn(0f, 1f)
                                    }
                                }
                        ) {
                            CompositionLocalProvider(LocalSegmentedItemShape provides shape) {
                                Column(modifier = Modifier.padding(top = currentTopPadding)) {
                                    itemData.content(shape)
                                }
                            }
                        }
                    }
                }
            }
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints) }
            var currentY = 0f
            val positions = mutableListOf<Int>()

            placeables.forEachIndexed { index, placeable ->
                positions.add(currentY.roundToInt())
                val progress = progresses[index].value
                currentY += placeable.height * progress
            }

            layout(constraints.maxWidth, currentY.roundToInt().coerceAtLeast(0)) {
                placeables.forEachIndexed { index, placeable ->
                    placeable.placeRelative(x = 0, y = positions[index])
                }
            }
        }
    }
}