package anhiutangerinee.kittisu.ui.activity.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anhiutangerinee.kittisu.ui.screen.BottomBarDestination
import anhiutangerinee.kittisu.ui.theme.CardConfig
import anhiutangerinee.kittisu.ui.theme.m3CardContainerColor
import kotlin.math.roundToInt

/**
 * Vertical space the floating bottom bar occupies: 64.dp capsule + 14.dp vertical padding
 * on each side. Pages add this to their scaffold insets so FABs and list content
 * clear the bar instead of sliding underneath it.
 */
val BottomBarHeight = 92.dp

@Composable
fun BottomBar(
    destinations: List<BottomBarDestination>,
    selectedPage: Int,
    onPageChange: (Int) -> Unit,
    visible: Boolean = true,
) {
    if (destinations.isEmpty()) return

    val density = LocalDensity.current
    val itemSpacing = 4.dp
    val containerPadding = 6.dp
    val pillHeight = 52.dp

    val itemSpacingPx = with(density) { itemSpacing.toPx() }
    val containerPaddingPx = with(density) { containerPadding.toPx() }
    val extraTouchAreaPx = with(density) { 18.dp.toPx() }

    var lastValidSelection by remember { mutableIntStateOf(selectedPage.coerceIn(0, destinations.lastIndex)) }

    val effectiveSelectedIndex = if (selectedPage in destinations.indices) {
        lastValidSelection = selectedPage
        selectedPage
    } else {
        lastValidSelection
    }

    var isDraggingPill by remember { mutableStateOf(false) }
    var dragTargetIndex by remember { mutableIntStateOf(effectiveSelectedIndex) }
    var dragPointerX by remember { mutableFloatStateOf(-1f) }

    val animatedSelectedIndex by animateFloatAsState(
        targetValue = (if (isDraggingPill) dragTargetIndex else effectiveSelectedIndex).toFloat(),
        animationSpec = if (isDraggingPill) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        } else {
            spring(dampingRatio = 0.74f, stiffness = 420f)
        },
        label = "bottom_bar_selected_index"
    )

    val pillScale by animateFloatAsState(
        targetValue = if (isDraggingPill) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bottom_bar_pill_scale"
    )

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars)
        ) {
            val horizontalScreenPadding = when {
                maxWidth > 600.dp -> 32.dp
                maxWidth > 400.dp -> 20.dp
                else -> 12.dp
            }

            val maxAvailableForItems =
                (maxWidth - horizontalScreenPadding * 2 - containerPadding * 2 - itemSpacing * (destinations.size - 1))
                    .coerceAtLeast(56.dp * destinations.size)
            val preferredItemWidth = if (destinations.size <= 2) 96.dp else if (destinations.size == 3) 84.dp else 74.dp
            val itemWidth = minOf(preferredItemWidth, maxAvailableForItems / destinations.size)
            val itemWidthPx = with(density) { itemWidth.toPx() }
            val navBarWidth = (itemWidth * destinations.size) +
                (itemSpacing * (destinations.size - 1)) +
                (containerPadding * 2)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalScreenPadding, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.wrapContentWidth(),
                    shape = CircleShape,
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp,
                    border = BorderStroke(
                        width = 0.8.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    ),
                    color = m3CardContainerColor().copy(
                        alpha = CardConfig.cardAlpha.coerceAtLeast(0.92f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .width(navBarWidth)
                            .height(64.dp)
                            .pointerInput(destinations, itemWidthPx) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        if (!isDraggingPill) {
                                            val index = ((offset.x - containerPaddingPx) /
                                                    (itemWidthPx + itemSpacingPx))
                                                .toInt()
                                                .coerceIn(0, destinations.lastIndex)
                                            onPageChange(index)
                                        }
                                    }
                                )
                            }
                            .pointerInput(destinations, effectiveSelectedIndex, itemWidthPx) {
                                detectDragGestures(
                                    onDragStart = { offset: Offset ->
                                        val pillLeft = containerPaddingPx +
                                                effectiveSelectedIndex * (itemWidthPx + itemSpacingPx) -
                                                extraTouchAreaPx
                                        val pillRight = pillLeft + itemWidthPx + (extraTouchAreaPx * 2)

                                        if (offset.x in pillLeft..pillRight) {
                                            isDraggingPill = true
                                            dragTargetIndex = effectiveSelectedIndex
                                            dragPointerX = offset.x
                                        }
                                    },
                                    onDragEnd = {
                                        if (isDraggingPill) {
                                            onPageChange(dragTargetIndex)
                                            isDraggingPill = false
                                            dragPointerX = -1f
                                        }
                                    },
                                    onDragCancel = {
                                        isDraggingPill = false
                                        dragPointerX = -1f
                                    },
                                    onDrag = { change, _ ->
                                        if (isDraggingPill) {
                                            change.consume()
                                            dragPointerX = change.position.x
                                            val index = ((change.position.x - containerPaddingPx) /
                                                    (itemWidthPx + itemSpacingPx))
                                                .roundToInt()
                                                .coerceIn(0, destinations.lastIndex)
                                            dragTargetIndex = index
                                        }
                                    }
                                )
                            }
                    ) {
                        var totalWidth by remember { mutableIntStateOf(0) }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = containerPadding)
                                .onSizeChanged { totalWidth = it.width }
                        ) {
                            if (totalWidth > 0 && destinations.isNotEmpty()) {
                                val indicatorOffset = if (isDraggingPill && dragPointerX >= 0f) {
                                    (dragPointerX - containerPaddingPx - itemWidthPx / 2f)
                                        .coerceIn(
                                            0f,
                                            (itemWidthPx + itemSpacingPx) * destinations.lastIndex
                                        )
                                } else {
                                    (itemWidthPx + itemSpacingPx) * animatedSelectedIndex
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .offset { IntOffset(x = indicatorOffset.roundToInt(), y = 0) }
                                        .width(itemWidth)
                                        .graphicsLayer {
                                            scaleX = pillScale
                                            scaleY = pillScale
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(itemWidth)
                                            .height(pillHeight)
                                            .background(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                destinations.forEachIndexed { index, destination ->
                                    val isSelected = index == if (isDraggingPill) dragTargetIndex else effectiveSelectedIndex
                                    val labelText = stringResource(destination.label)
                                    val contentTint by animateColorAsState(
                                        targetValue = if (isSelected) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        label = "bottom_bar_item_tint"
                                    )

                                    Column(
                                        modifier = Modifier
                                            .width(itemWidth)
                                            .height(pillHeight)
                                            .clip(CircleShape)
                                            .semantics(mergeDescendants = true) {
                                                role = Role.Tab
                                                selected = isSelected
                                                contentDescription = labelText
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) destination.iconSelected else destination.iconNotSelected,
                                            contentDescription = null,
                                            tint = contentTint,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = labelText,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                lineHeight = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                            ),
                                            color = contentTint,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
