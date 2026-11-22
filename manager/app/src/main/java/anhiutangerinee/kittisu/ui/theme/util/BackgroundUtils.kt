package anhiutangerinee.kittisu.ui.theme.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.min

data class BackgroundTransformation(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)

internal fun calculateCropSize(
    imageWidth: Int,
    imageHeight: Int,
    screenWidth: Int,
    screenHeight: Int,
): Pair<Int, Int> {
    val imageRatio = imageWidth.toFloat() / imageHeight
    val screenRatio = screenWidth.toFloat() / screenHeight
    return if (imageRatio > screenRatio) {
        (imageHeight * screenRatio).toInt().coerceAtLeast(1) to imageHeight
    } else {
        imageWidth to (imageWidth / screenRatio).toInt().coerceAtLeast(1)
    }
}

fun Context.getImageBitmap(uri: Uri): Bitmap? {
    return try {
        val contentResolver: ContentResolver = contentResolver
        val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return null
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        bitmap
    } catch (e: Exception) {
        Log.e("BackgroundUtils", "Failed to get image bitmap: ${e.message}")
        null
    }
}

/**
 * Decodes an image from [uri], center-crops it to the given aspect ratio
 * ([aspectX] : [aspectY]), scales it to fit within [maxWidth] x [maxHeight]
 * while preserving quality, and writes it as a JPEG to [destFile].
 *
 * Used as an internal cropper fallback on AOSP ROMs where no external app
 * supports `com.android.camera.action.CROP` or where the system gallery
 * rejects `FileProvider` URIs with "editing is not supported for this image".
 */
fun Context.cropAndSaveImage(
    uri: Uri,
    destFile: File,
    aspectX: Int,
    aspectY: Int,
    maxWidth: Int = 2160,
    maxHeight: Int = 3840
): Uri? {
    return try {
        val original = contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null

        val srcW = original.width
        val srcH = original.height
        if (srcW <= 0 || srcH <= 0) return null

        val targetRatio = aspectX.toFloat() / aspectY.toFloat()
        val srcRatio = srcW.toFloat() / srcH.toFloat()

        val cropW: Int
        val cropH: Int
        if (srcRatio > targetRatio) {
            cropH = srcH
            cropW = (srcH * targetRatio).toInt().coerceIn(1, srcW)
        } else {
            cropW = srcW
            cropH = (srcW / targetRatio).toInt().coerceIn(1, srcH)
        }

        val cropX = ((srcW - cropW) / 2).coerceAtLeast(0)
        val cropY = ((srcH - cropH) / 2).coerceAtLeast(0)

        val cropped = Bitmap.createBitmap(original, cropX, cropY, cropW, cropH)
        if (cropped !== original) {
            original.recycle()
        }

        val scaleFactor = min(
            1f,
            min(maxWidth.toFloat() / cropW.toFloat(), maxHeight.toFloat() / cropH.toFloat())
        )
        val finalBitmap = if (scaleFactor < 1f) {
            val outW = (cropW * scaleFactor).toInt().coerceAtLeast(1)
            val outH = (cropH * scaleFactor).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(cropped, outW, outH, true).also {
                if (it !== cropped) cropped.recycle()
            }
        } else {
            cropped
        }

        destFile.parentFile?.mkdirs()
        FileOutputStream(destFile).use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            out.flush()
        }
        finalBitmap.recycle()
        Uri.fromFile(destFile)
    } catch (e: Exception) {
        Log.e("BackgroundUtils", "cropAndSaveImage failed: ${e.message}", e)
        null
    }
}

fun Context.applyTransformationToBitmap(bitmap: Bitmap, transformation: BackgroundTransformation): Bitmap {
    val width = bitmap.width
    val height = bitmap.height

    // 创建与屏幕比例相同的目标位图
    val displayMetrics = resources.displayMetrics
    val screenWidth = displayMetrics.widthPixels
    val screenHeight = displayMetrics.heightPixels
    val (targetWidth, targetHeight) = calculateCropSize(width, height, screenWidth, screenHeight)

    // 创建与目标相同大小的位图
    val scaledBitmap = createBitmap(targetWidth, targetHeight)
    val canvas = Canvas(scaledBitmap)

    val matrix = Matrix()

    // 确保缩放值有效
    val safeScale = maxOf(0.1f, transformation.scale)
    matrix.postScale(safeScale, safeScale)

    // 计算偏移量，确保不会出现负最大值的问题
    val widthDiff = (bitmap.width * safeScale - targetWidth)
    val heightDiff = (bitmap.height * safeScale - targetHeight)

    // 安全计算偏移量边界
    val maxOffsetX = maxOf(0f, widthDiff / 2)
    val maxOffsetY = maxOf(0f, heightDiff / 2)

    // 限制偏移范围
    val safeOffsetX = if (maxOffsetX > 0)
        transformation.offsetX.coerceIn(-maxOffsetX, maxOffsetX) else 0f
    val safeOffsetY = if (maxOffsetY > 0)
        transformation.offsetY.coerceIn(-maxOffsetY, maxOffsetY) else 0f

    // 应用偏移量到矩阵
    val translationX = -widthDiff / 2 + safeOffsetX
    val translationY = -heightDiff / 2 + safeOffsetY

    matrix.postTranslate(translationX, translationY)

    // 将原始位图绘制到新位图上
    canvas.drawBitmap(bitmap, matrix, null)

    return scaledBitmap
}

fun Context.saveTransformedBackground(uri: Uri, transformation: BackgroundTransformation): Uri? {
    try {
        val bitmap = getImageBitmap(uri) ?: return null
        val transformedBitmap = applyTransformationToBitmap(bitmap, transformation)

        val fileName = "custom_background_transformed.jpg"
        val file = File(filesDir, fileName)
        val outputStream = FileOutputStream(file)

        transformedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        outputStream.flush()
        outputStream.close()

        return Uri.fromFile(file)
    } catch (e: Exception) {
        Log.e("BackgroundUtils", "Failed to save transformed image: ${e.message}", e)
        return null
    }
}
