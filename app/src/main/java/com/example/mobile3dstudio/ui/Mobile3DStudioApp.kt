package com.example.mobile3dstudio.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.webkit.WebView
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
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
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

    // Flat image transform state is independent from the model scene.
    var flatScale by rememberSaveable { mutableFloatStateOf(1f) }
    var flatOffsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var flatOffsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var flatRotation by rememberSaveable { mutableFloatStateOf(0f) }

    // Native workbench UI state.
    var isModelWorkspace by rememberSaveable { mutableStateOf(false) }
    var selectedModelType by rememberSaveable { mutableStateOf(MODEL_FRAGMENT) }
    var pointInput by rememberSaveable { mutableStateOf("9") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var rendererReady by remember { mutableStateOf(false) }
    var rendererError by remember { mutableStateOf<String?>(null) }
    var sceneObjectCount by remember { mutableStateOf(0) }
    var pendingExportFile by remember { mutableStateOf<File?>(null) }
    var selectedObjectId by remember { mutableStateOf<Long?>(null) }
    var selectedTransform by remember { mutableStateOf(ModelTransformUi()) }
    var canUndo by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importJob?.cancel()
            importJob = scope.launch {
                imageLoadState = ImageLoadState.Loading
                var importedFile: File? = null
                try {
                    val copiedFile = withContext(Dispatchers.IO) {
                        copyImageToPrivateStorage(context, uri)
                    }
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
                    output.use { sink ->
                        FileInputStream(file).use { source -> source.copyTo(sink) }
                    }
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

    // Restore the local image copy after activity/process recreation.
    LaunchedEffect(Unit) {
        val path = savedImagePath
        if (path != null) {
            val file = File(path)
            imageLoadState = ImageLoadState.Loading
            imageLoadState = try {
                val bitmap = withContext(Dispatchers.IO) { decodeImage(file) }
                ImageLoadState.Success(bitmap)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ImageLoadState.Failure(error.message?.takeIf(String::isNotBlank) ?: "图片读取失败。")
            }
        }
    }

    fun runJs(script: String) {
        val target = webView ?: run {
            scope.launch { snackbarHostState.showSnackbar("3D 渲染器视图尚未创建，请重新进入模型页。") }
            return
        }
        target.post { target.evaluateJavascript(script, null) }
    }

    fun runEngineCommand(expression: String, successMessage: String) {
    val target = webView ?: run {
        scope.launch {
            snackbarHostState.showSnackbar("3D 渲染视图尚未创建，请稍后重试。")
        }
        return
    }

    val script = """
        (function(){
          const report=(ok,message)=>{
            try{
              if(
                window.AndroidBridge &&
                typeof window.AndroidBridge.onCommandResult==='function'
              ){
                window.AndroidBridge.onCommandResult(
                  !!ok,
                  String(message||'')
                );
              }
            }catch(bridgeError){
              console.error('Native command callback failed',bridgeError);
            }
          };

          try{
            const api=window.Mobile3DStudio;

            // 检查 3D 引擎接口是否存在。
            if(!api){
              report(false,'3D 引擎接口尚未加载，请稍后重试。');
              return;
            }

            // 直接执行模型生成命令。
            // WebGL 和画布状态由模型接口内部负责检查。
            Promise.resolve($expression)
              .then(result=>{
                if(result===true){
                  report(true,${org.json.JSONObject.quote(successMessage)});
                }else{
                  report(false,'操作未完成，请检查 3D 引擎状态。');
                }
              })
              .catch(error=>{
                report(
                  false,
                  error&&error.message
                    ? error.message
                    : '操作时发生未知错误。'
                );
              });

          }catch(error){
            report(
              false,
              error&&error.message
                ? error.message
                : '操作调用失败。'
            );
          }
        })();
    """.trimIndent()

    target.post {
        target.evaluateJavascript(script, null)
    }
}


    fun applySceneState(json: String) {
        runCatching {
            val root = JSONObject(json)
            rendererReady = root.optBoolean("ready", false)
            rendererError = root.optString("error").takeIf { it.isNotBlank() && it != "null" }
            canUndo = root.optBoolean("canUndo", false)
            sceneObjectCount = root.optJSONArray("objects")?.length() ?: 0
            val selected = root.optJSONObject("selected")
            if (selected == null) {
                selectedObjectId = null
                selectedTransform = ModelTransformUi()
            } else {
                selectedObjectId = selected.optLong("id")
                selectedTransform = ModelTransformUi(
                    x = selected.optDouble("x", 0.0).toFloat(),
                    y = selected.optDouble("y", 0.0).toFloat(),
                    z = selected.optDouble("z", 0.0).toFloat(),
                    rx = selected.optDouble("rx", 0.0).toFloat(),
                    ry = selected.optDouble("ry", 0.0).toFloat(),
                    rz = selected.optDouble("rz", 0.0).toFloat()
                )
            }
        }.onFailure { error ->
            android.util.Log.e("Mobile3DStudio", "Invalid scene state from WebGL", error)
        }
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
        hasExportContent = imageBitmap != null || sceneObjectCount > 0,
        selectedObjectId = selectedObjectId,
        selectedTransform = selectedTransform,
        canUndo = canUndo,
        rendererReady = rendererReady,
        rendererError = rendererError,
        onExportFileReady = { type, path, fileName, success, message ->
            if (!success || path.isBlank()) {
                scope.launch { snackbarHostState.showSnackbar(message.ifBlank { "导出文件生成失败。" }) }
            } else {
                val file = File(path)
                if (!file.exists() || file.length() <= 0L) {
                    scope.launch { snackbarHostState.showSnackbar("导出文件为空，未能保存。") }
                } else {
                    pendingExportFile = file
                    when (type) {
                        "glb" -> saveGlbLauncher.launch(fileName.ifBlank { "Mobile3DStudio-Scene.glb" })
                        "png" -> savePngLauncher.launch(fileName.ifBlank { "Mobile3DStudio-Preview.png" })
                        else -> {
                            file.delete()
                            pendingExportFile = null
                            scope.launch { snackbarHostState.showSnackbar("不支持的导出类型：$type") }
                        }
                    }
                }
            }
        },
        onEngineMessage = { success, message ->
            if (!success && !rendererReady) rendererError = message
            if (success && rendererReady) rendererError = null
            if (!success || message.isNotBlank()) {
                scope.launch { snackbarHostState.showSnackbar(message.ifBlank { if (success) "模型生成完成。" else "模型生成失败。" }) }
            }
        },
        onWebViewReady = { readyView ->
            if (webView !== readyView) webView = readyView
        },
        onSceneState = { json -> applySceneState(json) },
        onImportImage = { imagePicker.launch(arrayOf("image/*")) },
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
            flatOffsetX = (
                flatOffsetX * zoom + localX * (1f - zoom) / safeWidth + panX / safeWidth
            ).coerceIn(-4f, 4f)
            flatOffsetY = (
                flatOffsetY * zoom + localY * (1f - zoom) / safeHeight + panY / safeHeight
            ).coerceIn(-4f, 4f)
            flatScale = (flatScale * zoom).coerceIn(0.1f, 8f)
            flatRotation = normalizeDegrees(flatRotation + rotation)
        },
        onEnterModels = { isModelWorkspace = true },
        onReturnToImage = { isModelWorkspace = false },
        onModelTypeSelect = { selectedModelType = it },
        onPointInputChange = { pointInput = it },
        onGenerateModel = {
    when (selectedModelType) {
        MODEL_FRAGMENT -> runEngineCommand(
            "window.Mobile3DStudio.addIrregularDice(${pointInput.toIntOrNull() ?: 9})",
            "骰子模型已生成。"
        )

        MODEL_CUT -> runEngineCommand(
            "window.Mobile3DStudio.addCutDice(${pointInput.toIntOrNull() ?: 9})",
            "双切面骰子已生成。"
        )

        MODEL_POKER -> runEngineCommand(
            "window.Mobile3DStudio.addPoker()",
            "黑桃 Q 已生成。"
        )
    }
},

        onModelTransformChange = { axis, value ->
            runJs("window.Mobile3DStudio.setTransform('$axis',$value)")
        },
        onModelTransformFinish = { runJs("window.Mobile3DStudio.endTransformBatch()") },
        onDefaultModel = { runJs("window.Mobile3DStudio.resetSelected()") },
        onUndo = { runJs("window.Mobile3DStudio.undo()") },
        onExportPng = {
            if (!rendererReady) scope.launch { snackbarHostState.showSnackbar(rendererError ?: "3D 渲染器尚未就绪") }
            else runEngineCommand("window.Mobile3DStudio.exportPng()", "PNG 图像已生成，正在选择保存位置…")
        },
        onExportGlb = {
            if (!rendererReady) scope.launch { snackbarHostState.showSnackbar(rendererError ?: "3D 渲染器尚未就绪") }
            else runEngineCommand("window.Mobile3DStudio.exportGlb()", "GLB 场景文件已生成，正在选择保存位置…")
        }
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
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("系统无法读取所选图片，请重新选择。")
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
