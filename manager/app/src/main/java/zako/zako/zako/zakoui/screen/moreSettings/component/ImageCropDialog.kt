package zako.zako.zako.zakoui.screen.moreSettings.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ui.theme.util.BackgroundTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive Material 3 Expressive in-app image cropper dialog.
 *
 * Supports pinch-to-zoom, drag-to-pan, a zoom slider (for single-hand or emulator
 * use), and reset, with a live rounded crop frame matching the target aspect ratio
 * ([aspectRatio] = width / height, e.g. 16f/9f for the Working card or screenW/screenH
 * for full-screen background).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImageCropDialog(
    imageUri: Uri,
    aspectRatio: Float,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (BackgroundTransformation) -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(imageUri) { mutableStateOf(true) }

    LaunchedEffect(imageUri) {
        isLoading = true
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                // First decode bounds to downsample huge images safely for preview
                val boundsOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(imageUri)?.use {
                    BitmapFactory.decodeStream(it, null, boundsOptions)
                }
                val maxPreviewDim = 2048
                var sampleSize = 1
                while (
                    boundsOptions.outWidth / sampleSize > maxPreviewDim ||
                    boundsOptions.outHeight / sampleSize > maxPreviewDim
                ) {
                    sampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                context.contentResolver.openInputStream(imageUri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOptions)
                }
            }.getOrNull()
        }
        isLoading = false
        if (bitmap == null) {
            onDismiss()
        }
    }

    var scale by remember(imageUri) { mutableFloatStateOf(1f) }
    var normalizedOffsetX by remember(imageUri) { mutableFloatStateOf(0f) }
    var normalizedOffsetY by remember(imageUri) { mutableFloatStateOf(0f) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cropScale"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.92f),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FilledTonalIconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.TwoTone.Close,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )

                    FilledTonalIconButton(
                        onClick = {
                            scale = 1f
                            normalizedOffsetX = 0f
                            normalizedOffsetY = 0f
                        }
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Refresh,
                            contentDescription = stringResource(R.string.restore)
                        )
                    }
                }

                // Crop Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(Color(0xFF121212))
                        .onSizeChanged { viewportSize = it },
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = bitmap
                    if (isLoading) {
                        LoadingIndicator()
                    } else if (bmp != null && viewportSize.width > 0 && viewportSize.height > 0) {
                        val vw = viewportSize.width.toFloat()
                        val vh = viewportSize.height.toFloat()

                        // Calculate crop frame rectangle inside viewport with 12% margin
                        val availW = vw * 0.88f
                        val availH = vh * 0.88f
                        val cropW: Float
                        val cropH: Float
                        if (availW / availH > aspectRatio) {
                            cropH = availH
                            cropW = cropH * aspectRatio
                        } else {
                            cropW = availW
                            cropH = cropW / aspectRatio
                        }

                        // Calculate base image size so that at scale=1f it covers the crop rect
                        val imgW = bmp.width.toFloat().coerceAtLeast(1f)
                        val imgH = bmp.height.toFloat().coerceAtLeast(1f)
                        val baseFillScale = max(cropW / imgW, cropH / imgH)
                        val baseDrawW = imgW * baseFillScale
                        val baseDrawH = imgH * baseFillScale

                        val maxPanX = ((baseDrawW * scale - cropW) / 2f).coerceAtLeast(0f)
                        val maxPanY = ((baseDrawH * scale - cropH) / 2f).coerceAtLeast(0f)

                        val currentPanX = normalizedOffsetX * maxPanX
                        val currentPanY = normalizedOffsetY * maxPanY

                        val imageBitmap = remember(bmp) { bmp.asImageBitmap() }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(bmp, cropW, cropH) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (scale * zoom).coerceIn(1f, 4f)
                                        val newMaxPanX =
                                            ((baseDrawW * newScale - cropW) / 2f).coerceAtLeast(0f)
                                        val newMaxPanY =
                                            ((baseDrawH * newScale - cropH) / 2f).coerceAtLeast(0f)

                                        val rawPanX =
                                            (normalizedOffsetX * maxPanX + pan.x).coerceIn(
                                                -newMaxPanX,
                                                newMaxPanX
                                            )
                                        val rawPanY =
                                            (normalizedOffsetY * maxPanY + pan.y).coerceIn(
                                                -newMaxPanY,
                                                newMaxPanY
                                            )

                                        scale = newScale
                                        normalizedOffsetX =
                                            if (newMaxPanX > 0.5f) (rawPanX / newMaxPanX).coerceIn(-1f, 1f) else 0f
                                        normalizedOffsetY =
                                            if (newMaxPanY > 0.5f) (rawPanY / newMaxPanY).coerceIn(-1f, 1f) else 0f
                                    }
                                }
                                .graphicsLayer()
                        ) {
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f

                            val drawW = baseDrawW * animatedScale
                            val drawH = baseDrawH * animatedScale
                            val left = centerX - drawW / 2f + currentPanX
                            val top = centerY - drawH / 2f + currentPanY

                            drawImage(
                                image = imageBitmap,
                                dstOffset = androidx.compose.ui.unit.IntOffset(
                                    left.toInt(),
                                    top.toInt()
                                ),
                                dstSize = IntSize(drawW.toInt(), drawH.toInt())
                            )

                            val cropLeft = centerX - cropW / 2f
                            val cropTop = centerY - cropH / 2f
                            val cropRect = Rect(cropLeft, cropTop, cropLeft + cropW, cropTop + cropH)
                            val cornerPx = 28.dp.toPx()

                            val holePath = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        rect = cropRect,
                                        cornerRadius = CornerRadius(cornerPx, cornerPx)
                                    )
                                )
                            }

                            // Darken area outside the crop frame
                            clipPath(holePath, clipOp = ClipOp.Difference) {
                                drawRect(Color.Black.copy(alpha = 0.62f))
                            }

                            // Draw rule-of-thirds grid inside crop frame
                            clipPath(holePath) {
                                val gridColor = Color.White.copy(alpha = 0.25f)
                                val thirdW = cropW / 3f
                                val thirdH = cropH / 3f
                                for (i in 1..2) {
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(cropLeft + thirdW * i, cropTop),
                                        end = Offset(cropLeft + thirdW * i, cropTop + cropH),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(cropLeft, cropTop + thirdH * i),
                                        end = Offset(cropLeft + cropW, cropTop + thirdH * i),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }
                            }

                            // Draw crisp rounded frame border
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.9f),
                                topLeft = Offset(cropLeft, cropTop),
                                size = Size(cropW, cropH),
                                cornerRadius = CornerRadius(cornerPx, cornerPx),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }

                // Bottom Controls Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.ZoomIn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Slider(
                                value = scale,
                                onValueChange = { newScale ->
                                    scale = newScale
                                },
                                valueRange = 1f..4f,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "${(scale * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Text(stringResource(R.string.cancel))
                            }

                            Button(
                                onClick = {
                                    onConfirm(
                                        BackgroundTransformation(
                                            scale = scale,
                                            offsetX = normalizedOffsetX,
                                            offsetY = normalizedOffsetY
                                        )
                                    )
                                },
                                enabled = !isLoading && bitmap != null,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Icon(
                                    imageVector = Icons.TwoTone.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.confirm))
                            }
                        }
                    }
                }
            }
        }
    }
}
