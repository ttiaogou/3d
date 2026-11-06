package com.example.mobile3dstudio.export

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Captures only the preview rectangle, excluding the editor controls outside that area. */
internal object NativePngExporter {
    suspend fun export(context: Context, previewBoundsInWindow: Rect): File {
        require(!previewBoundsInWindow.isEmpty) { "预览画布尚未完成布局，暂时无法导出 PNG。" }
        val activity = context.findActivity() ?: throw IOException("无法取得当前窗口，不能截取预览画布。")
        val window = activity.window
        val decor = window.decorView
        val width = decor.width.takeIf { it > 0 } ?: throw IOException("预览窗口宽度无效。")
        val height = decor.height.takeIf { it > 0 } ?: throw IOException("预览窗口高度无效。")
        val crop = Rect(previewBoundsInWindow).apply { intersect(0, 0, width, height) }
        if (crop.isEmpty) throw IOException("预览画布区域超出窗口范围，无法导出 PNG。")

        val preview = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            capturePreview(window, crop)
        } else {
            capturePreviewFallback(decor, crop, width, height)
        }

        return withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "exports")
            if (!directory.exists() && !directory.mkdirs()) {
                preview.recycle()
                throw IOException("无法创建 PNG 导出缓存目录。")
            }
            val output = File(directory, "Mobile3DStudio-${UUID.randomUUID()}.png")
            try {
                FileOutputStream(output).use { stream ->
                    if (!preview.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        throw IOException("PNG 图像编码失败。")
                    }
                    stream.fd.sync()
                }
                output
            } catch (error: Exception) {
                output.delete()
                throw error
            } finally {
                preview.recycle()
            }
        }
    }

    private suspend fun capturePreview(window: Window, sourceRect: Rect): Bitmap =
        suspendCancellableCoroutine { continuation ->
            val bitmap = Bitmap.createBitmap(sourceRect.width(), sourceRect.height(), Bitmap.Config.ARGB_8888)
            PixelCopy.request(
                window,
                Rect(sourceRect),
                bitmap,
                { result ->
                    if (!continuation.isActive) {
                        bitmap.recycle()
                    } else if (result == PixelCopy.SUCCESS) {
                        continuation.resume(bitmap)
                    } else {
                        bitmap.recycle()
                        continuation.resumeWithException(
                            IOException("预览画布像素复制失败，PixelCopy 错误码：$result。请确认画布已显示后重试。")
                        )
                    }
                },
                Handler(Looper.getMainLooper())
            )
            continuation.invokeOnCancellation {
                // PixelCopy retains the destination until its completion callback.
            }
        }

    private suspend fun capturePreviewFallback(
        decor: android.view.View,
        crop: Rect,
        width: Int,
        height: Int
    ): Bitmap = suspendCancellableCoroutine { continuation ->
        val full = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        decor.post {
            if (!continuation.isActive) {
                full.recycle()
            } else {
                runCatching {
                    decor.draw(Canvas(full))
                    Bitmap.createBitmap(full, crop.left, crop.top, crop.width(), crop.height())
                }.onSuccess { preview ->
                    full.recycle()
                    continuation.resume(preview)
                }.onFailure { error ->
                    full.recycle()
                    continuation.resumeWithException(IOException("预览截图失败：${error.message}", error))
                }
            }
        }
        continuation.invokeOnCancellation { }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
