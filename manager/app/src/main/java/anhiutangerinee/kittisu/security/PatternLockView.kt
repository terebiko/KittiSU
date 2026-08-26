package anhiutangerinee.kittisu.security

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 3x3 pattern input mirroring AOSP LockPatternView behaviour:
 * - gap-filling: dragging across the middle dot auto-selects it (detectAndAddHit heuristic)
 * - haptic feedback on every added cell
 * - trailing segment follows the finger while drawing
 * Reports the selected dot indices (row-major, 0..8) via [onPatternCompleted].
 */
@Composable
fun PatternLockView(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorColor: Color = Color.Unspecified,
    onPatternCompleted: (List<Int>) -> Unit,
) {
    val activeDots = remember { mutableStateListOf<Int>() }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    val haptic = LocalHapticFeedback.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val lineColor = if (errorColor == Color.Unspecified) primaryColor else errorColor

    fun dotCenter(index: Int, side: Float): Offset {
        val cell = side / 3f
        return Offset(
            (index % 3) * cell + cell / 2f,
            (index / 3) * cell + cell / 2f,
        )
    }

    fun addCellHit(cell: Int) {
        if (activeDots.isNotEmpty()) {
            // AOSP detectAndAddHit gap-filling: a straight jump of two cells
            // auto-selects the intermediate cell.
            val last = activeDots.last()
            val dRow = cell / 3 - last / 3
            val dCol = cell % 3 - last % 3
            val fillRow = last / 3 +
                if (kotlin.math.abs(dRow) == 2 && kotlin.math.abs(dCol) != 1) {
                    if (dRow > 0) 1 else -1
                } else 0
            val fillCol = last % 3 +
                if (kotlin.math.abs(dCol) == 2 && kotlin.math.abs(dRow) != 1) {
                    if (dCol > 0) 1 else -1
                } else 0
            val gapCell = fillRow * 3 + fillCol
            if (gapCell != cell && gapCell !in activeDots) {
                activeDots.add(gapCell)
            }
        }
        if (cell !in activeDots) {
            activeDots.add(cell)
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription = "pattern lock" }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        activeDots.clear()
                        dotIndexAt(offset, size.width.toFloat())?.let(::addCellHit)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragPosition = change.position
                        dotIndexAt(change.position, size.width.toFloat())?.let(::addCellHit)
                    },
                    onDragEnd = {
                        // Always report so callers can flag too-short patterns.
                        if (activeDots.isNotEmpty()) onPatternCompleted(activeDots.toList())
                        activeDots.clear()
                        dragPosition = null
                    },
                    onDragCancel = { activeDots.clear(); dragPosition = null },
                )
            },
    ) {
        val side = size.width
        val dotRadius = side / 18f

        // Connection lines between active dots...
        for (i in 0 until maxOf(0, activeDots.size - 1)) {
            drawLine(
                color = lineColor,
                start = dotCenter(activeDots[i], side),
                end = dotCenter(activeDots[i + 1], side),
                strokeWidth = LINE_WIDTH.toPx(),
                cap = StrokeCap.Round,
            )
        }
        // ...and the trailing segment following the finger (AOSP behaviour).
        val lastDot = activeDots.lastOrNull()
        if (lastDot != null) {
            dragPosition?.let { position ->
                drawLine(
                    color = lineColor,
                    start = dotCenter(lastDot, side),
                    end = position,
                    strokeWidth = LINE_WIDTH.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        for (index in 0 until 9) {
            val center = dotCenter(index, side)
            val isActive = index in activeDots
            if (isActive) {
                drawCircle(color = lineColor, radius = dotRadius * 1.15f, center = center)
            } else {
                drawCircle(
                    color = outlineColor,
                    radius = dotRadius,
                    center = center,
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }
    }
}

private val LINE_WIDTH = 6.dp
