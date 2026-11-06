package com.example.mobile3dstudio.ui.scene

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

/** Hosts the original offline WebGL engine from Dice Fragment 3D v1.4. */
@Composable
fun DiceSceneWebView(
    modifier: Modifier = Modifier,
    onWebViewReady: (WebView) -> Unit,
    onSceneState: (String) -> Unit,
    onCommandResult: (Boolean, String) -> Unit
) {
    val context = LocalContext.current
    val latestOnSceneState = rememberUpdatedState(onSceneState)
    val latestOnCommandResult = rememberUpdatedState(onCommandResult)
    val latestOnWebViewReady = rememberUpdatedState(onWebViewReady)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val bridge = remember {
        object {
            @JavascriptInterface
            fun onSceneState(json: String) {
                mainHandler.post { latestOnSceneState.value(json) }
            }

            @JavascriptInterface
            fun onCommandResult(success: Boolean, message: String) {
                mainHandler.post { latestOnCommandResult.value(success, message) }
            }

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
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(Color.rgb(17, 23, 34))
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
                        // Publish the renderer's true state. A missing JS bridge is reported
                        // explicitly instead of leaving the native Generate button inert.
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
                            val message = "3D 场景页面加载失败：${'$'}{error.description}"
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
