package anhiutangerinee.kittisu.ui.activity.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import anhiutangerinee.kittisu.ui.screen.BottomBarDestination

@Composable
fun BottomBar(
    destinations: List<BottomBarDestination>,
    selectedPage: Int,
    onPageChange: (Int) -> Unit,
    visible: Boolean = true,
) {
    if (destinations.isEmpty()) return

    val density = LocalDensity.current

    val itemSize = 56.dp
    val itemSpacing = 4.dp
    val containerPadding = 7.dp

    val itemSizePx = with(density) { itemSize.toPx() }
    val itemSpacingPx = with(density) { itemSpacing.toPx() }
    val containerPaddingPx = with(density) { containerPadding.toPx() }

    val navBarWidth = (itemSize * destinations.size) +
            (itemSpacing * (destinations.size - 1)) +
            (containerPadding * 2)

    var lastValidSelection by remember { mutableIntStateOf(selectedPage.coerceIn(0, destinations.lastIndex)) }

    val effectiveSelectedIndex = if (selectedPage in destinations.indices) {
        lastValidSelection = selectedPage
        selectedPage
    } else {
        lastValidSelection
    }

    var isDraggingPill by remember { mutableStateOf(false) }
    var dragTargetIndex by remember { mutableIntStateOf(effectiveSelectedIndex) }

    val animatedSelectedIndex by animateFloatAsState(
        targetValue = (if (isDraggingPill) dragTargetIndex else effectiveSelectedIndex).toFloat(),
        animationSpec = if (isDraggingPill) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        },
        label = "bottom_bar_selected_index"
    )

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars)
        ) {
            val horizontalScreenPadding = when {
                maxWidth > 600.dp -> 32.dp
                maxWidth > 400.dp -> 24.dp
                else -> 16.dp
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalScreenPadding, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.wrapContentWidth(),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Box(
                        modifier = Modifier
                            .width(navBarWidth)
                            .height(72.dp)
                            .pointerInput(destinations, effectiveSelectedIndex) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val extraTouchArea = 20.dp.toPx()
                                        val pillLeft = containerPaddingPx +
                                                effectiveSelectedIndex * (itemSizePx + itemSpacingPx) -
                                                extraTouchArea
                                        val pillRight = pillLeft + itemSizePx + (extraTouchArea * 2)

                                        if (offset.x in pillLeft..pillRight) {
                                            isDraggingPill = true
                                            dragTargetIndex = effectiveSelectedIndex
                                        }
                                    },
                                    onDragEnd = {
                                        if (isDraggingPill) {
                                            onPageChange(dragTargetIndex)
                                            isDraggingPill = false
                                        }
                                    },
                                    onDragCancel = {
                                        isDraggingPill = false
                                    },
                                    onDrag = { change, _ ->
                                        if (isDraggingPill) {
                                            change.consume()
                                            val index = ((change.position.x - containerPaddingPx) /
                                                    (itemSizePx + itemSpacingPx))
                                                .toInt()
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
                                val indicatorOffset = (itemSizePx + itemSpacingPx) * animatedSelectedIndex

                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(vertical = 8.dp)
                                        .offset { IntOffset(x = indicatorOffset.toInt(), y = 0) }
                                        .width(itemSize)
                                        .graphicsLayer {
                                            scaleX = if (isDraggingPill) 1.1f else 1f
                                            scaleY = if (isDraggingPill) 1.1f else 1f
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(itemSize)
                                            .background(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(16.dp)
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

                                    Box(
                                        modifier = Modifier
                                            .size(itemSize)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable(
                                                enabled = !isDraggingPill,
                                                onClick = { onPageChange(index) }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) destination.iconSelected else destination.iconNotSelected,
                                            contentDescription = stringResource(destination.label),
                                            tint = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
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
