package com.example.mobile3dstudio.ui.scene

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Hosts the original offline WebGL engine as a transparent layer above the native image preview. */
@Composable
fun DiceSceneWebView(
    modifier: Modifier = Modifier,
    previewBitmap: Bitmap?,
    previewScale: Float,
    previewOffsetX: Float,
    previewOffsetY: Float,
    previewRotation: Float,
    onWebViewReady: (WebView) -> Unit,
    onSceneState: (String) -> Unit,
    onCommandResult: (Boolean, String) -> Unit,
    onExportFileReady: (String, String, String, Boolean, String) -> Unit
) {
    val context = LocalContext.current
    val latestPreviewBitmap = rememberUpdatedState(previewBitmap)
    val latestPreviewTransform = rememberUpdatedState(
        JSONObject()
            .put("scale", previewScale)
            .put("offsetX", previewOffsetX)
            .put("offsetY", previewOffsetY)
            .put("rotation", previewRotation)
            .toString()
    )
    val latestOnSceneState = rememberUpdatedState(onSceneState)
    val latestOnCommandResult = rememberUpdatedState(onCommandResult)
    val latestOnExportFileReady = rememberUpdatedState(onExportFileReady)
    val latestOnWebViewReady = rememberUpdatedState(onWebViewReady)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val bridge = remember {
        object {
            private val exportLock = Any()
            private var exportFile: File? = null
            private var exportStream: FileOutputStream? = null
            private var exportType: String = ""
            private var exportName: String = ""

            @JavascriptInterface
            fun onSceneState(json: String) {
                mainHandler.post { latestOnSceneState.value(json) }
            }

            @JavascriptInterface
            fun onCommandResult(success: Boolean, message: String) {
                mainHandler.post { latestOnCommandResult.value(success, message) }
            }

            /** Returns the imported preview image as a compact local data URL for GLB embedding. */
            @JavascriptInterface
            fun getPreviewImageDataUrl(): String {
                val bitmap = latestPreviewBitmap.value ?: return ""
                return try {
                    val output = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)
                    "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                } catch (error: Exception) {
                    Log.e("Mobile3DStudio", "Unable to encode the preview image", error)
                    ""
                }
            }

            @JavascriptInterface
            fun getPreviewTransformJson(): String = latestPreviewTransform.value

            @JavascriptInterface
            fun getCardTextureDataUrl(fileName: String): String {
                val safeName = when (fileName) {
                    "queen-spades-front.jpg" -> fileName
                    "playing-card-back.jpg" -> fileName
                    else -> return ""
                }
                return try {
                    val bytes = context.assets.open("dice/$safeName").use { it.readBytes() }
                    "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                } catch (error: Exception) {
                    Log.e("Mobile3DStudio", "Unable to read card texture $safeName", error)
                    ""
                }
            }

            @JavascriptInterface
            fun beginExport(type: String, fileName: String): Boolean = synchronized(exportLock) {
                if (type != "glb" && type != "png") return@synchronized false
                if (exportStream != null) return@synchronized false
                return@synchronized try {
                    val safeName = fileName.substringAfterLast('/').substringAfterLast('\\')
                        .filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
                        .take(90)
                        .ifBlank { if (type == "glb") "Mobile3DStudio-Scene.glb" else "Mobile3DStudio-Preview.png" }
                    val dir = File(context.cacheDir, "exports")
                    if (!dir.exists() && !dir.mkdirs()) error("无法创建导出缓存目录")
                    val file = File(dir, "${UUID.randomUUID()}-$safeName")
                    exportStream = FileOutputStream(file)
                    exportFile = file
                    exportType = type
                    exportName = safeName
                    true
                } catch (error: Exception) {
                    Log.e("Mobile3DStudio", "Unable to begin export", error)
                    false
                }
            }

            @JavascriptInterface
            fun writeExportChunk(base64Chunk: String): Boolean = synchronized(exportLock) {
                val stream = exportStream ?: return@synchronized false
                return@synchronized try {
                    stream.write(Base64.decode(base64Chunk, Base64.DEFAULT))
                    true
                } catch (error: Exception) {
                    Log.e("Mobile3DStudio", "Unable to write export chunk", error)
                    false
                }
            }

            @JavascriptInterface
            fun finishExport(success: Boolean, errorMessage: String) {
                val result = synchronized(exportLock) {
                    val stream = exportStream
                    val file = exportFile
                    val type = exportType
                    val name = exportName
                    exportStream = null
                    exportFile = null
                    exportType = ""
                    exportName = ""
                    val finalSuccess = try {
                        stream?.flush()
                        stream?.fd?.sync()
                        stream?.close()
                        success && file != null && file.exists() && file.length() > 0L
                    } catch (error: Exception) {
                        Log.e("Mobile3DStudio", "Unable to finish export", error)
                        false
                    }
                    if (!finalSuccess) file?.delete()
                    arrayOf(type, file?.absolutePath.orEmpty(), name, finalSuccess.toString())
                }
                val type = result[0]
                val path = result[1]
                val name = result[2]
                val ok = result[3].toBoolean()
                mainHandler.post {
                    latestOnExportFileReady.value(
                        type,
                        path,
                        name,
                        ok,
                        if (ok) "" else errorMessage.ifBlank { "导出文件写入失败。" }
                    )
                }
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                // Transparency is essential: the native photo preview remains visible below WebGL.
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.loadsImagesAutomatically = true
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                addJavascriptInterface(bridge, "AndroidBridge")
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                            Log.e("Mobile3DStudio-WebGL", "${consoleMessage.message()} @ ${consoleMessage.lineNumber()}")
                        }
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        view.evaluateJavascript(
                            "(function(){if(window.Mobile3DStudio&&window.Mobile3DStudio.sync){window.Mobile3DStudio.sync();}else if(window.AndroidBridge){window.AndroidBridge.onCommandResult(false,'3D 引擎脚本未能完成初始化，请检查 WebView 控制台错误。');}})();",
                            null
                        )
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: android.webkit.WebResourceRequest,
                        error: android.webkit.WebResourceError
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame) {
                            val message = "3D 场景页面加载失败：${error.description}"
                            mainHandler.post { latestOnCommandResult.value(false, message) }
                        }
                    }
                }
                setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
                latestOnWebViewReady.value(this)
                loadUrl("file:///android_asset/dice/index.html")
            }
        },
        update = { view -> latestOnWebViewReady.value(view) }
    )
}
