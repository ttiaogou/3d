package com.example.mobile3dstudio.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns image import/decode work and all persistent editor parameters. */
@Composable
fun Mobile3DStudioApp() {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedUriText by rememberSaveable { mutableStateOf<String?>(null) }
    var imageLoadVersion by remember { mutableIntStateOf(0) }
    var imageLoadState by remember { mutableStateOf<ImageLoadState>(ImageLoadState.Empty) }

    // Flat-image transform values. Position is stored as a fraction of the preview size
    // so it remains stable when the device orientation or available workspace changes.
    var imageScale by rememberSaveable { mutableFloatStateOf(1f) }
    var imageOffsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var imageOffsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var imageRotationZ by rememberSaveable { mutableFloatStateOf(0f) }

    // 3D is opt-in. The first stage is a textured plane inside a real 3D scene;
    // it is not a depth-reconstructed solid model.
    var isThreeDMode by rememberSaveable { mutableStateOf(false) }
    var imageRotationX by rememberSaveable { mutableFloatStateOf(0f) }
    var imageRotationY by rememberSaveable { mutableFloatStateOf(0f) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            selectedUriText = uri.toString()
            imageLoadVersion++
            resetImageTransforms(
                onScale = { imageScale = it },
                onOffsetX = { imageOffsetX = it },
                onOffsetY = { imageOffsetY = it },
                onRotationZ = { imageRotationZ = it },
                onRotationX = { imageRotationX = it },
                onRotationY = { imageRotationY = it }
            )
            isThreeDMode = false
        }
    }

    LaunchedEffect(selectedUriText, imageLoadVersion) {
        val selectedUri = selectedUriText?.let(Uri::parse)
        if (selectedUri == null) {
            imageLoadState = ImageLoadState.Empty
        } else {
            imageLoadState = ImageLoadState.Loading
            imageLoadState = try {
                val bitmap = withContext(Dispatchers.IO) {
                    decodeImage(context, selectedUri)
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
            if (imageLoadState is ImageLoadState.Success) {
                snackbarHostState.showSnackbar("图片载入完成")
            } else if (imageLoadState is ImageLoadState.Failure) {
                snackbarHostState.showSnackbar("图片载入失败")
            }
        }
    }

    val currentBitmap = (imageLoadState as? ImageLoadState.Success)?.bitmap

    EditorScreen(
        snackbarHostState = snackbarHostState,
        hasImage = selectedUriText != null,
        imageLoadState = imageLoadState,
        imageBitmap = currentBitmap,
        imageScale = imageScale,
        imageOffsetX = imageOffsetX,
        imageOffsetY = imageOffsetY,
        imageRotationZ = imageRotationZ,
        imageRotationX = imageRotationX,
        imageRotationY = imageRotationY,
        isThreeDMode = isThreeDMode,
        onImportImage = {
            imagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onRemoveImage = {
            selectedUriText = null
            imageLoadState = ImageLoadState.Empty
            isThreeDMode = false
            resetImageTransforms(
                onScale = { imageScale = it },
                onOffsetX = { imageOffsetX = it },
                onOffsetY = { imageOffsetY = it },
                onRotationZ = { imageRotationZ = it },
                onRotationX = { imageRotationX = it },
                onRotationY = { imageRotationY = it }
            )
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
            val nextScale = (imageScale * zoom).coerceIn(0.1f, 8f)
            val localCentroidX = centroidX - safeWidth / 2f
            val localCentroidY = centroidY - safeHeight / 2f

            // Keep the point under the fingers stable while pinching, then apply pan.
            imageOffsetX = (
                imageOffsetX * zoom + localCentroidX * (1f - zoom) / safeWidth + panX / safeWidth
            ).coerceIn(-4f, 4f)
            imageOffsetY = (
                imageOffsetY * zoom + localCentroidY * (1f - zoom) / safeHeight + panY / safeHeight
            ).coerceIn(-4f, 4f)
            imageScale = nextScale
            imageRotationZ = (imageRotationZ + rotation).let { value ->
                ((value + 180f) % 360f + 360f) % 360f - 180f
            }
        },
        onToggleThreeD = {
            isThreeDMode = !isThreeDMode
            if (isThreeDMode) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "已进入 3D 平面场景；图片仍是平面纹理，尚未进行真实立体重建。"
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
                "GLB" -> "GLB 导出器尚未实现"
                "PNG" -> "PNG 图片导出尚未实现"
                "JPG" -> "JPG 图片导出尚未实现"
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

/**
 * Decodes common Android image formats, downsamples large images, and applies EXIF
 * orientation before returning the Bitmap used by both the flat preview and Filament.
 */
private fun decodeImage(
    context: Context,
    uri: Uri,
    maxDimension: Int = 2048
): Bitmap {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    } ?: throw IllegalArgumentException("无法打开图片文件。")

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw IllegalArgumentException("无法识别图片格式或图片内容已损坏。")
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
    val decoded = resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    } ?: throw IllegalArgumentException("图片解码失败，请尝试使用 JPG、PNG 或 WebP 格式。")

    val orientation = runCatching {
        resolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
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
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { corrected ->
            if (corrected !== decoded) decoded.recycle()
        }
    } catch (error: Exception) {
        decoded.recycle()
        throw IllegalArgumentException("图片方向处理失败：${error.message ?: "未知错误"}", error)
    }
}
