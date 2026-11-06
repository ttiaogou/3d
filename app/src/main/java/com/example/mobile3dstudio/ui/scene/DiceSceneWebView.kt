package com.example.mobile3dstudio.ui.scene

import android.graphics.Color
import android.os.Handler
import android.os.Looper
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
    onSceneState: (String) -> Unit
) {
    val context = LocalContext.current
    val latestOnSceneState = rememberUpdatedState(onSceneState)
    val latestOnWebViewReady = rememberUpdatedState(onWebViewReady)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val bridge = remember {
        object {
            @JavascriptInterface
            fun onSceneState(json: String) {
                mainHandler.post { latestOnSceneState.value(json) }
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
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        // Ask the page to publish its real scene state; do not overwrite it
                        // with a fabricated empty state during the page-finished callback.
                        view.evaluateJavascript(
                            "if(window.Mobile3DStudio&&window.Mobile3DStudio.sync){window.Mobile3DStudio.sync();}",
                            null
                        )
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
