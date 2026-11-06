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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Captures only the editor preview region, including the native transparent 3D surface. */
internal object NativePngExporter {
    suspend fun export(context: Context, previewBoundsInWindow: Rect): File {
        require(!previewBoundsInWindow.isEmpty) { "预览画布尚未完成布局，暂时无法导出 PNG。" }
        val activity = context.findActivity() ?: throw IOException("无法取得当前窗口，不能截取预览画布。")
        val window = activity.window
        val decor = window.decorView
        val width = decor.width.takeIf { it > 0 } ?: throw IOException("预览窗口宽度无效。")
        val height = decor.height.takeIf { it > 0 } ?: throw IOException("预览窗口高度无效。")

        val windowBitmap = captureWindow(window, decor, width, height)
        val crop = Rect(previewBoundsInWindow).apply {
            intersect(0, 0, windowBitmap.width, windowBitmap.height)
        }
        if (crop.width() <= 0 || crop.height() <= 0) {
            windowBitmap.recycle()
            throw IOException("预览画布区域超出窗口范围，无法导出 PNG。")
        }
        val preview = Bitmap.createBitmap(windowBitmap, crop.left, crop.top, crop.width(), crop.height())
        windowBitmap.recycle()

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

    private suspend fun captureWindow(
        window: Window,
        decor: android.view.View,
        width: Int,
        height: Int
    ): Bitmap = suspendCancellableCoroutine { continuation ->
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PixelCopy.request(
                window,
                bitmap,
                { result ->
                    if (!continuation.isActive) {
                        bitmap.recycle()
                    } else if (result == PixelCopy.SUCCESS) {
                        continuation.resume(bitmap)
                    } else {
                        bitmap.recycle()
                        continuation.resumeWithException(
                            IOException("窗口像素复制失败，PixelCopy 错误码：$result。请确认预览画布已显示后重试。")
                        )
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } else {
            // API 24–25 fallback. Texture surfaces may not be included by View.draw on all devices.
            decor.post {
                if (!continuation.isActive) {
                    bitmap.recycle()
                } else {
                    runCatching { decor.draw(Canvas(bitmap)) }
                        .onSuccess { continuation.resume(bitmap) }
                        .onFailure { error ->
                            bitmap.recycle()
                            continuation.resumeWithException(IOException("截图失败：${error.message}", error))
                        }
                }
            }
        }
        continuation.invokeOnCancellation {
            // PixelCopy owns the destination until its callback; it is recycled there.
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
