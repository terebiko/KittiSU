package anhiutangerinee.kittisu.security

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Minimal 3x3 pattern input. Reports the selected dot indices (row-major, 0..8)
 * in order via [onPatternCompleted].
 */
@Composable
fun PatternLockView(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorColor: Color = Color.Unspecified,
    onPatternCompleted: (List<Int>) -> Unit,
) {
    val activeDots = remember { mutableStateListOf<Int>() }
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

    fun dotIndexAt(position: Offset, side: Float): Int? {
        if (side <= 0f) return null
        val cell = side / 3f
        val col = (position.x / cell).toInt().coerceIn(0, 2)
        val row = (position.y / cell).toInt().coerceIn(0, 2)
        val center = dotCenter(row * 3 + col, side)
        return if ((position - center).getDistance() <= cell * 0.75f) row * 3 + col else null
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
                        dotIndexAt(offset, size.width.toFloat())?.let(activeDots::add)
                    },
                    onDrag = { change, _ ->
                        dotIndexAt(change.position, size.width.toFloat())?.let { index ->
                            if (index !in activeDots) activeDots.add(index)
                        }
                    },
                    onDragEnd = {
                        if (activeDots.isNotEmpty()) onPatternCompleted(activeDots.toList())
                        activeDots.clear()
                    },
                    onDragCancel = { activeDots.clear() },
                )
            },
    ) {
        val side = size.width
        val dotRadius = side / 18f

        // Connection lines between active dots.
        for (i in 0 until maxOf(0, activeDots.size - 1)) {
            drawLine(
                color = lineColor,
                start = dotCenter(activeDots[i], side),
                end = dotCenter(activeDots[i + 1], side),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        for (index in 0 until 9) {
            val center = dotCenter(index, side)
            val isActive = index in activeDots
            if (isActive) {
                drawCircle(color = lineColor, radius = dotRadius, center = center)
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
