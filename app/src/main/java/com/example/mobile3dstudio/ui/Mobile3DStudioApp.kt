package com.example.mobile3dstudio.ui

import java.io.DataInputStream
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
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
import com.example.mobile3dstudio.core.model.NativeModelKind
import com.example.mobile3dstudio.core.model.NativeProjectSnapshot
import com.example.mobile3dstudio.core.model.NativeProjectStore
import com.example.mobile3dstudio.core.model.NativeSceneStateHolder
import com.example.mobile3dstudio.core.model.NativeTransform
import com.example.mobile3dstudio.export.NativeGlbExporter
import com.example.mobile3dstudio.export.NativePngExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@Composable
fun Mobile3DStudioApp() {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var savedImagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var imageLoadState by remember { mutableStateOf<ImageLoadState>(ImageLoadState.Empty) }
    var importJob by remember { mutableStateOf<Job?>(null) }
    var restoreComplete by remember { mutableStateOf(false) }

    var flatScale by rememberSaveable { mutableFloatStateOf(1f) }
    var flatOffsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var flatOffsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var flatRotation by rememberSaveable { mutableFloatStateOf(0f) }

    var isModelWorkspace by rememberSaveable { mutableStateOf(false) }
    var selectedModelType by rememberSaveable { mutableStateOf(MODEL_FRAGMENT) }
    var pointInput by rememberSaveable { mutableStateOf("9") }
    val nativeScene = remember { NativeSceneStateHolder() }
    val nativeModels = nativeScene.models
    val selectedObjectId = nativeScene.selectedModelId
    val selectedTransform = nativeScene.selectedTransform()?.let { transform ->
        ModelTransformUi(
            x = transform.x / .55f,
            y = -transform.y / .55f,
            z = transform.z / .35f,
            rx = transform.rx,
            ry = transform.ry,
            rz = transform.rz,
            scale = transform.scale
        )
    } ?: ModelTransformUi()
    val canUndo = nativeScene.canUndo
    val canRedo = nativeScene.canRedo
    var rendererReady by remember { mutableStateOf(false) }
    var rendererError by remember { mutableStateOf<String?>(null) }
    var previewBounds by remember { mutableStateOf<Rect?>(null) }
    var pendingExportFile by remember { mutableStateOf<File?>(null) }
    var showPreviewControls by remember { mutableStateOf(true) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importJob?.cancel()
            importJob = scope.launch {
                imageLoadState = ImageLoadState.Loading
                var importedFile: File? = null
                try {
                    val copiedFile = withContext(Dispatchers.IO) { copyImageToPrivateStorage(context, uri) }
                    importedFile = copiedFile
                    val bitmap = withContext(Dispatchers.IO) { decodeImage(copiedFile) }
                    val oldPath = savedImagePath
                    savedImagePath = copiedFile.absolutePath
                    imageLoadState = ImageLoadState.Success(bitmap)
                    isModelWorkspace = false
                    resetFlatTransform(
                        onScale = { flatScale = it },
                        onX = { flatOffsetX = it },
                        onY = { flatOffsetY = it },
                        onRotation = { flatRotation = it }
                    )
                    if (oldPath != null && oldPath != copiedFile.absolutePath) {
                        withContext(Dispatchers.IO) { File(oldPath).delete() }
                    }
                    snackbarHostState.showSnackbar("图片载入完成")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    importedFile?.let { file -> withContext(Dispatchers.IO) { file.delete() } }
                    imageLoadState = ImageLoadState.Failure(
                        error.message?.takeIf(String::isNotBlank) ?: "无法读取图片，请重新选择。"
                    )
                    snackbarHostState.showSnackbar("图片载入失败")
                }
            }
        }
    }

    val glbPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                var importedFile: File? = null
                try {
                    val imported = withContext(Dispatchers.IO) { copyGlbToPrivateStorage(context, uri) }
                    importedFile = imported
                    nativeScene.addImportedGlb(imported.absolutePath)
                    isModelWorkspace = true
                    snackbarHostState.showSnackbar("GLB 模型已导入")
                } catch (error: Exception) {
                    importedFile?.delete()
                    snackbarHostState.showSnackbar("GLB 导入失败：${error.message ?: "文件无效"}")
                }
            }
        }
    }

    fun finishExportSave(uri: Uri?, typeLabel: String) {
        val file = pendingExportFile
        pendingExportFile = null
        if (file == null) return
        if (uri == null) {
            file.delete()
            scope.launch { snackbarHostState.showSnackbar("已取消保存 ${typeLabel} 文件") }
            return
        }
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri, "w")
                        ?: throw IOException("无法创建导出文件。")
                    output.use { sink -> FileInputStream(file).use { source -> source.copyTo(sink) } }
                    file.delete()
                }
                snackbarHostState.showSnackbar("${typeLabel} 已保存")
            } catch (error: Exception) {
                file.delete()
                snackbarHostState.showSnackbar("${typeLabel} 保存失败：${error.message ?: "未知错误"}")
            }
        }
    }

    val savePngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri -> finishExportSave(uri, "PNG") }
    val saveGlbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("model/gltf-binary")
    ) { uri -> finishExportSave(uri, "GLB") }

    LaunchedEffect(Unit) {
        val snapshot = withContext(Dispatchers.IO) { NativeProjectStore.load(context) }
        if (snapshot != null) {
            savedImagePath = snapshot.imagePath
            flatScale = snapshot.imageScale
            flatOffsetX = snapshot.imageOffsetX
            flatOffsetY = snapshot.imageOffsetY
            flatRotation = snapshot.imageRotation
            isModelWorkspace = snapshot.isModelWorkspace
            selectedModelType = snapshot.selectedModelType
            pointInput = snapshot.pointInput
            nativeScene.restoreScene(snapshot.models, snapshot.selectedModelId)
            val path = snapshot.imagePath
            if (path != null) {
                val file = File(path)
                imageLoadState = ImageLoadState.Loading
                imageLoadState = try {
                    ImageLoadState.Success(withContext(Dispatchers.IO) { decodeImage(file) })
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    ImageLoadState.Failure(error.message?.takeIf(String::isNotBlank) ?: "图片读取失败。")
                }
            }
        }
        restoreComplete = true
    }

    LaunchedEffect(
        restoreComplete,
        savedImagePath,
        flatScale,
        flatOffsetX,
        flatOffsetY,
        flatRotation,
        isModelWorkspace,
        selectedModelType,
        pointInput,
        nativeModels,
        selectedObjectId
    ) {
        if (!restoreComplete) return@LaunchedEffect
        delay(250)
        val snapshot = NativeProjectSnapshot(
            imagePath = savedImagePath,
            imageScale = flatScale,
            imageOffsetX = flatOffsetX,
            imageOffsetY = flatOffsetY,
            imageRotation = flatRotation,
            isModelWorkspace = isModelWorkspace,
            selectedModelType = selectedModelType,
            pointInput = pointInput,
            models = nativeModels,
            selectedModelId = selectedObjectId
        )
        runCatching { withContext(Dispatchers.IO) { NativeProjectStore.save(context, snapshot) } }
            .onFailure { android.util.Log.e("Mobile3DStudio", "Project state save failed", it) }
    }

    val imageBitmap = (imageLoadState as? ImageLoadState.Success)?.bitmap

    EditorScreen(
        snackbarHostState = snackbarHostState,
        imageLoadState = imageLoadState,
        imageBitmap = imageBitmap,
        imageScale = flatScale,
        imageOffsetX = flatOffsetX,
        imageOffsetY = flatOffsetY,
        imageRotation = flatRotation,
        isModelWorkspace = isModelWorkspace,
        selectedModelType = selectedModelType,
        pointInput = pointInput,
        hasExportContent = imageBitmap != null || nativeModels.isNotEmpty(),
        selectedObjectId = selectedObjectId,
        selectedTransform = selectedTransform,
        canUndo = canUndo,
        canRedo = canRedo,
        rendererReady = rendererReady,
        rendererError = rendererError,
        nativeModels = nativeModels,
        onNativeModelSelected = { id -> nativeScene.select(id) },
        onLongPressModel = { id -> nativeScene.select(id) },
        onDeleteModel = { id -> nativeScene.remove(id) },
        onRendererFrame = {
            if (!rendererReady) rendererReady = true
            rendererError = null
        },
        onRendererError = { message -> rendererError = message },
        onPreviewBoundsChanged = { bounds -> if (previewBounds != bounds) previewBounds = Rect(bounds) },
        onImportImage = { imagePicker.launch(arrayOf("image/*")) },
        onImportGlb = { glbPicker.launch(arrayOf("model/gltf-binary", "application/octet-stream", "*/*")) },
        onRemoveImage = {
            savedImagePath?.let { path -> scope.launch(Dispatchers.IO) { File(path).delete() } }
            savedImagePath = null
            imageLoadState = ImageLoadState.Empty
            resetFlatTransform(
                onScale = { flatScale = it },
                onX = { flatOffsetX = it },
                onY = { flatOffsetY = it },
                onRotation = { flatRotation = it }
            )
        },
        onFlatGesture = { centroidX, centroidY, panX, panY, zoom, rotation, width, height ->
            val safeWidth = width.coerceAtLeast(1f)
            val safeHeight = height.coerceAtLeast(1f)
            val localX = centroidX - safeWidth / 2f
            val localY = centroidY - safeHeight / 2f
            flatOffsetX = (flatOffsetX * zoom + localX * (1f - zoom) / safeWidth + panX / safeWidth).coerceIn(-4f, 4f)
            flatOffsetY = (flatOffsetY * zoom + localY * (1f - zoom) / safeHeight + panY / safeHeight).coerceIn(-4f, 4f)
            flatScale = (flatScale * zoom).coerceIn(0.1f, 8f)
            flatRotation = normalizeDegrees(flatRotation + rotation)
        },
        onEnterModels = { isModelWorkspace = true },
        onReturnToImage = { isModelWorkspace = false },
        onModelTypeSelect = { selectedModelType = it },
        onPointInputChange = { pointInput = it },
        onGenerateModel = {
            runCatching {
                when (selectedModelType) {
                    MODEL_FRAGMENT -> nativeScene.add(NativeModelKind.IRREGULAR_DICE, pointInput.toIntOrNull() ?: 9)
                    MODEL_CUT -> nativeScene.add(NativeModelKind.DOUBLE_CUT_DICE, pointInput.toIntOrNull() ?: 9)
                    MODEL_POKER -> nativeScene.add(NativeModelKind.POKER_CARD)
                    else -> throw IllegalArgumentException("未知模型类型")
                }
            }.onSuccess {
                scope.launch { snackbarHostState.showSnackbar("原生 3D 模型已加入场景") }
            }.onFailure { error ->
                scope.launch { snackbarHostState.showSnackbar(error.message ?: "模型生成失败") }
            }
        },
        onModelTransformChange = { axis, value -> nativeScene.updateTransform(axis, value) },
        onModelTransformGesture = { id, transform -> nativeScene.updateTransformFromGesture(id, transform) },
        onModelTransformFinish = { nativeScene.finishTransformEdit() },
        onDefaultModel = { nativeScene.resetSelected() },
        onUndo = { if (!nativeScene.undo()) scope.launch { snackbarHostState.showSnackbar("没有可撤销的操作") } },
        onRedo = { if (!nativeScene.redo()) scope.launch { snackbarHostState.showSnackbar("没有可重做的操作") } },
        onExportPng = {
            val bounds = previewBounds?.let { Rect(it) }
            if (bounds == null || bounds.isEmpty) {
                scope.launch { snackbarHostState.showSnackbar("预览画布尚未就绪，无法导出 PNG") }
            } else {
                showPreviewControls = false
                scope.launch {
                    try {
                        delay(180)
                        val file = NativePngExporter.export(context, bounds)
                        pendingExportFile = file
                        savePngLauncher.launch("Mobile3DStudio-Preview.png")
                    } catch (error: Exception) {
                        snackbarHostState.showSnackbar("PNG 导出失败：${error.message ?: "未知错误"}")
                    } finally {
                        showPreviewControls = true
                    }
                }
            }
        },
        onExportGlb = {
            if (nativeModels.any { it.sourceFilePath != null }) {
                scope.launch { snackbarHostState.showSnackbar("场景包含外部 GLB 模型，请保留其原始文件。") }
            } else {
                scope.launch {
                    val bounds = previewBounds
                    val aspect = if (bounds != null && bounds.height() > 0) bounds.width().toFloat() / bounds.height().toFloat() else 1f
                    runCatching {
                        NativeGlbExporter.export(
                            context = context,
                            models = nativeModels,
                            imageBitmap = imageBitmap,
                            imageScale = flatScale,
                            imageOffsetX = flatOffsetX,
                            imageOffsetY = flatOffsetY,
                            imageRotation = flatRotation,
                            viewportAspectRatio = aspect
                        )
                    }.onSuccess { file ->
                        pendingExportFile = file
                        saveGlbLauncher.launch("Mobile3DStudio-Scene.glb")
                    }.onFailure { error ->
                        snackbarHostState.showSnackbar("GLB 导出失败：${error.message ?: "未知错误"}")
                    }
                }
            }
        },
        showPreviewControls = showPreviewControls
    )
}

private fun resetFlatTransform(
    onScale: (Float) -> Unit,
    onX: (Float) -> Unit,
    onY: (Float) -> Unit,
    onRotation: (Float) -> Unit
) {
    onScale(1f)
    onX(0f)
    onY(0f)
    onRotation(0f)
}

private fun normalizeDegrees(degrees: Float): Float =
    ((degrees + 180f) % 360f + 360f) % 360f - 180f

private fun copyImageToPrivateStorage(context: Context, uri: Uri): File {
    val extension = when (context.contentResolver.getType(uri)?.lowercase()) {
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
    val dir = File(context.filesDir, "imported-images")
    if (!dir.exists() && !dir.mkdirs()) throw IOException("无法创建图片缓存目录。")
    val destination = File(dir, "image_${UUID.randomUUID()}.$extension")
    try {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("系统无法读取所选图片，请重新选择。")
        input.use { source ->
            FileOutputStream(destination).use { output ->
                if (source.copyTo(output) <= 0L) throw IOException("所选图片文件为空。")
                output.fd.sync()
            }
        }
        return destination
    } catch (error: Exception) {
        destination.delete()
        if (error is IOException) throw error
        throw IOException("图片复制失败：${error.message ?: "未知错误"}", error)
    }
}

private fun copyGlbToPrivateStorage(context: Context, uri: Uri): File {
    val directory = File(context.filesDir, "imported-models")
    if (!directory.exists() && !directory.mkdirs()) throw IOException("无法创建模型缓存目录。")
    val destination = File(directory, "model_${UUID.randomUUID()}.glb")
    try {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("无法读取 GLB 文件。")
        input.use { source ->
            FileOutputStream(destination).use { output ->
                source.copyTo(output)
                output.fd.sync()
            }
        }
        if (destination.length() < 20L) throw IOException("GLB 文件内容不完整。")
        FileInputStream(destination).use { inputFile ->
            val header = ByteArray(12)
            DataInputStream(inputFile).readFully(header)
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val magic = buffer.int
            val version = buffer.int
            val length = buffer.int
            if (magic != 0x46546C67 || version != 2 || length != destination.length().toInt()) {
                throw IOException("文件不是有效的 glTF 2.0 GLB。")
            }
        }
        return destination
    } catch (error: Exception) {
        destination.delete()
        if (error is IOException) throw error
        throw IOException("GLB 导入失败：${error.message ?: "未知错误"}", error)
    }
}

private fun decodeImage(file: File, maxDimension: Int = 2048): Bitmap {
    if (!file.exists() || file.length() <= 0L) throw IOException("图片文件不存在或内容为空。")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("无法识别图片格式或图片已损坏。")

    var sample = 1
    while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
    val decoded = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
    ) ?: throw IOException("图片解码失败，请尝试 JPG、PNG 或 WebP 格式。")

    val orientation = runCatching {
        ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        else -> return decoded
    }
    return try {
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
        }
    } catch (error: Exception) {
        decoded.recycle()
        throw IOException("图片方向处理失败：${error.message ?: "未知错误"}", error)
    }
}
