package com.example.mobile3dstudio.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

@Composable
fun Mobile3DStudioApp() {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var savedImagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var imageLoadState by remember { mutableStateOf<ImageLoadState>(ImageLoadState.Empty) }
    var importJob by remember { mutableStateOf<Job?>(null) }

    // Flat image editing defaults. Values are normalized against the preview viewport.
    var imageScale by rememberSaveable { mutableFloatStateOf(1f) }
    var imageOffsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var imageOffsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var imageRotationZ by rememberSaveable { mutableFloatStateOf(0f) }

    // 3D view is opt-in and currently means a textured plane, not an AI reconstructed solid.
    var isThreeDMode by rememberSaveable { mutableStateOf(false) }
    var imageRotationX by rememberSaveable { mutableFloatStateOf(0f) }
    var imageRotationY by rememberSaveable { mutableFloatStateOf(0f) }

    // Restore the already-copied private image after activity/process recreation.
    LaunchedEffect(Unit) {
        val restoredPath = savedImagePath
        if (restoredPath != null) {
            imageLoadState = ImageLoadState.Loading
            imageLoadState = try {
                val bitmap = withContext(Dispatchers.IO) {
                    decodeImage(File(restoredPath))
                }
                ImageLoadState.Success(bitmap)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ImageLoadState.Failure(
                    error.message?.takeIf(String::isNotBlank)
                        ?: "图片读取失败，请重新选择图片。"
                )
            }
        }
    }

    // OpenDocument supplies a document URI; copy it immediately into app-private storage.
    // The workspace and renderer no longer depend on a temporary Photo Picker content URI.
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importJob?.cancel()
            importJob = scope.launch {
                imageLoadState = ImageLoadState.Loading
                try {
                    val importedFile = withContext(Dispatchers.IO) {
                        copyImageToPrivateStorage(context, uri)
                    }
                    val bitmap = withContext(Dispatchers.IO) {
                        decodeImage(importedFile)
                    }

                    val previousPath = savedImagePath
                    savedImagePath = importedFile.absolutePath
                    imageLoadState = ImageLoadState.Success(bitmap)
                    isThreeDMode = false
                    resetImageTransforms(
                        onScale = { imageScale = it },
                        onOffsetX = { imageOffsetX = it },
                        onOffsetY = { imageOffsetY = it },
                        onRotationZ = { imageRotationZ = it },
                        onRotationX = { imageRotationX = it },
                        onRotationY = { imageRotationY = it }
                    )

                    if (previousPath != null && previousPath != importedFile.absolutePath) {
                        withContext(Dispatchers.IO) { File(previousPath).delete() }
                    }
                    snackbarHostState.showSnackbar("图片载入完成")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    imageLoadState = ImageLoadState.Failure(
                        error.message?.takeIf(String::isNotBlank)
                            ?: "图片载入失败，请重新选择。"
                    )
                    snackbarHostState.showSnackbar("图片载入失败")
                }
            }
        }
    }

    val imageBitmap = (imageLoadState as? ImageLoadState.Success)?.bitmap

    EditorScreen(
        snackbarHostState = snackbarHostState,
        hasImage = savedImagePath != null,
        imageLoadState = imageLoadState,
        imageBitmap = imageBitmap,
        imageScale = imageScale,
        imageOffsetX = imageOffsetX,
        imageOffsetY = imageOffsetY,
        imageRotationZ = imageRotationZ,
        imageRotationX = imageRotationX,
        imageRotationY = imageRotationY,
        isThreeDMode = isThreeDMode,
        onImportImage = {
            imagePicker.launch(arrayOf("image/*"))
        },
        onScaleChange = { imageScale = it },
        onOffsetXChange = { imageOffsetX = it },
        onOffsetYChange = { imageOffsetY = it },
        onRotationZChange = { imageRotationZ = it },
        onRotationXChange = { imageRotationX = it },
        onRotationYChange = { imageRotationY = it },
        onGestureTransform = { centroidX, centroidY, panX, panY, zoom, rotation, width, height ->
            val safeWidth = width.coerceAtLeast(1f)
            val safeHeight = height.coerceAtLeast(1f)
            val localCentroidX = centroidX - safeWidth / 2f
            val localCentroidY = centroidY - safeHeight / 2f

            imageOffsetX = (
                imageOffsetX * zoom +
                    localCentroidX * (1f - zoom) / safeWidth +
                    panX / safeWidth
                ).coerceIn(-4f, 4f)

            imageOffsetY = (
                imageOffsetY * zoom +
                    localCentroidY * (1f - zoom) / safeHeight +
                    panY / safeHeight
                ).coerceIn(-4f, 4f)

            imageScale = (imageScale * zoom).coerceIn(0.1f, 8f)
            imageRotationZ = normalizeDegrees(imageRotationZ + rotation)
        },
        onToggleThreeD = {
            isThreeDMode = !isThreeDMode
            if (isThreeDMode) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "当前为 3D 纹理平面预览，尚未进行真实模型重建。"
                    )
                }
            }
        },
        onResetTransforms = {
            resetImageTransforms(
                onScale = { imageScale = it },
                onOffsetX = { imageOffsetX = it },
                onOffsetY = { imageOffsetY = it },
                onRotationZ = { imageRotationZ = it },
                onRotationX = { imageRotationX = it },
                onRotationY = { imageRotationY = it }
            )
        },
        onExport = { format ->
            val message = when (format) {
                "PNG" -> "PNG 导出功能尚未接入"
                "GLB" -> "GLB 模型导出功能尚未接入"
                else -> "不支持的导出格式"
            }
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    )
}

private fun resetImageTransforms(
    onScale: (Float) -> Unit,
    onOffsetX: (Float) -> Unit,
    onOffsetY: (Float) -> Unit,
    onRotationZ: (Float) -> Unit,
    onRotationX: (Float) -> Unit,
    onRotationY: (Float) -> Unit
) {
    onScale(1f)
    onOffsetX(0f)
    onOffsetY(0f)
    onRotationZ(0f)
    onRotationX(0f)
    onRotationY(0f)
}

private fun normalizeDegrees(degrees: Float): Float {
    return ((degrees + 180f) % 360f + 360f) % 360f - 180f
}

private fun copyImageToPrivateStorage(context: Context, uri: Uri): File {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri)
    val extension = when (mimeType?.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        "image/avif" -> "avif"
        "image/gif" -> "gif"
        "image/bmp" -> "bmp"
        else -> "img"
    }

    val directory = File(context.filesDir, "imported-images")
    if (!directory.exists() && !directory.mkdirs()) {
        throw IOException("无法创建图片缓存目录。")
    }

    val destination = File(directory, "image_${UUID.randomUUID()}.$extension")
    try {
        val input = resolver.openInputStream(uri)
            ?: throw IOException(
                "系统无法读取所选图片。请重新选择，或先将图片保存到设备本地。"
            )

        input.use { source ->
            FileOutputStream(destination).use { output ->
                val copiedBytes = source.copyTo(output)
                output.fd.sync()
                if (copiedBytes <= 0L) {
                    throw IOException("所选图片文件为空。")
                }
            }
        }

        if (destination.length() <= 0L) {
            throw IOException("图片复制失败，文件内容为空。")
        }
        return destination
    } catch (error: Exception) {
        destination.delete()
        if (error is IOException) throw error
        throw IOException("无法读取所选图片：${error.message ?: "未知错误"}", error)
    }
}

/**
 * Decode from an app-private file rather than reopening a transient content:// URI.
 * The longest decoded edge is limited to maxDimension to control memory consumption.
 */
private fun decodeImage(
    file: File,
    maxDimension: Int = 2048
): Bitmap {
    if (!file.exists() || file.length() <= 0L) {
        throw IOException("图片文件不存在或内容为空，请重新导入。")
    }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw IOException("无法识别图片格式或图片内容已损坏。")
    }

    var sampleSize = 1
    while (
        bounds.outWidth / sampleSize > maxDimension ||
        bounds.outHeight / sampleSize > maxDimension
    ) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inScaled = false
    }

    val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
        ?: throw IOException("图片解码失败，请尝试 JPG、PNG 或 WebP 格式。")

    val orientation = runCatching {
        ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        else -> return decoded
    }

    return try {
        Bitmap.createBitmap(
            decoded,
            0,
            0,
            decoded.width,
            decoded.height,
            matrix,
            true
        ).also { corrected ->
            if (corrected !== decoded) decoded.recycle()
        }
    } catch (error: Exception) {
        decoded.recycle()
        throw IOException(
            "图片方向处理失败：${error.message ?: "未知错误"}",
            error
        )
    }
}
