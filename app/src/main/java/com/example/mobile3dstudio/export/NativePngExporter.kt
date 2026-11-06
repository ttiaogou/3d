package com.example.mobile3dstudio.export

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** Exports the image and the transparent Filament layer without the preview workspace background. */
internal object NativePngExporter {
    suspend fun export(
        context: Context,
        previewBoundsInWindow: Rect,
        imageBitmap: Bitmap?,
        imageScale: Float,
        imageOffsetX: Float,
        imageOffsetY: Float,
        imageRotation: Float
    ): File {
        require(!previewBoundsInWindow.isEmpty) { "预览画布尚未完成布局，暂时无法导出 PNG。" }

        val activity = context.findActivity()
            ?: throw IOException("无法取得当前窗口，不能导出预览画布。")
        val decor = activity.window.decorView
        val textureView = withContext(Dispatchers.Main.immediate) {
            findPreviewTextureView(decor, previewBoundsInWindow)
                ?: throw IOException("未找到预览区的透明 3D 渲染层，请等待模型显示后重试。")
        }

        val width = previewBoundsInWindow.width()
        val height = previewBoundsInWindow.height()
        val modelLayer = withContext(Dispatchers.Main.immediate) {
            if (!textureView.isAvailable) {
                throw IOException("3D 渲染画布尚未就绪，请稍后重试。")
            }
            textureView.getBitmap(width, height)
                ?: throw IOException("无法读取透明 3D 渲染层，请重新导出。")
        }

        val outputBitmap = try {
            withContext(Dispatchers.Default) {
                composeExportBitmap(
                    width = width,
                    height = height,
                    imageBitmap = imageBitmap,
                    imageScale = imageScale,
                    imageOffsetX = imageOffsetX,
                    imageOffsetY = imageOffsetY,
                    imageRotation = imageRotation,
                    modelLayer = modelLayer
                )
            }
        } finally {
            modelLayer.recycle()
        }

        return withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "exports")
            if (!directory.exists() && !directory.mkdirs()) {
                outputBitmap.recycle()
                throw IOException("无法创建 PNG 导出缓存目录。")
            }
            val output = File(directory, "Mobile3DStudio-${UUID.randomUUID()}.png")
            try {
                FileOutputStream(output).use { stream ->
                    if (!outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        throw IOException("PNG 图像编码失败。")
                    }
                    stream.fd.sync()
                }
                output
            } catch (error: Exception) {
                output.delete()
                throw error
            } finally {
                outputBitmap.recycle()
            }
        }
    }

    private fun composeExportBitmap(
        width: Int,
        height: Int,
        imageBitmap: Bitmap?,
        imageScale: Float,
        imageOffsetX: Float,
        imageOffsetY: Float,
        imageRotation: Float,
        modelLayer: Bitmap
    ): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        if (imageBitmap != null && !imageBitmap.isRecycled) {
            // Match Image(contentScale = ContentScale.Crop, alignment = Alignment.Center).
            val cropScale = maxOf(
                width.toFloat() / imageBitmap.width,
                height.toFloat() / imageBitmap.height
            )
            val drawWidth = imageBitmap.width * cropScale
            val drawHeight = imageBitmap.height * cropScale

            // Match the preview graphicsLayer transform: translate, rotate, then scale
            // around the center of the preview viewport.
            canvas.save()
            canvas.translate(
                width * 0.5f + imageOffsetX * width,
                height * 0.5f + imageOffsetY * height
            )
            canvas.rotate(imageRotation)
            val safeScale = imageScale.coerceIn(0.1f, 8f)
            canvas.scale(safeScale, safeScale)
            canvas.drawBitmap(
                imageBitmap,
                null,
                RectF(-drawWidth * 0.5f, -drawHeight * 0.5f, drawWidth * 0.5f, drawHeight * 0.5f),
                paint
            )
            canvas.restore()
        }

        // The SceneView is a TextureSurface with isOpaque=false and a transparent Filament
        // clear color. Draw its pixels over the source image; do not capture the Compose parent.
        canvas.drawBitmap(
            modelLayer,
            null,
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            paint
        )
        return output
    }

    private fun findPreviewTextureView(root: View, previewBounds: Rect): TextureView? {
        val candidates = mutableListOf<Pair<TextureView, Float>>()
        val targetArea = previewBounds.width().toFloat() * previewBounds.height().toFloat()
        if (targetArea <= 0f) return null

        fun visit(view: View) {
            if (view is TextureView && view.isAvailable && view.width > 0 && view.height > 0) {
                val location = IntArray(2)
                view.getLocationInWindow(location)
                val viewBounds = Rect(
                    location[0],
                    location[1],
                    location[0] + view.width,
                    location[1] + view.height
                )
                val intersection = Rect(viewBounds)
                if (intersection.intersect(previewBounds)) {
                    val coverage = intersection.width().toFloat() * intersection.height() / targetArea
                    candidates += view to coverage
                }
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
        }

        visit(root)
        return candidates
            .filter { (_, coverage) -> coverage >= 0.90f }
            .maxByOrNull { (_, coverage) -> coverage }
            ?.first
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
