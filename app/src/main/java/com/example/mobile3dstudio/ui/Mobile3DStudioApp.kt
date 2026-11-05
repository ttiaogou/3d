package com.example.mobile3dstudio.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.example.mobile3dstudio.core.model.EditorMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns editor state, image import/decode work, and the callbacks passed to the UI.
 * Visual layout and reusable UI components live in EditorScreen.kt.
 */
@Composable
fun Mobile3DStudioApp() {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedUriText by rememberSaveable { mutableStateOf<String?>(null) }
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var mode by rememberSaveable { mutableStateOf(EditorMode.TWO_D) }

    var positionX by rememberSaveable { mutableFloatStateOf(0f) }
    var rotationX by rememberSaveable { mutableFloatStateOf(18f) }
    var rotationY by rememberSaveable { mutableFloatStateOf(25f) }
    var scaleFactor by rememberSaveable { mutableFloatStateOf(1f) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUriText = uri.toString()
            scope.launch { snackbarHostState.showSnackbar("图片已导入") }
        }
    }

    LaunchedEffect(selectedUriText) {
        val text = selectedUriText
        imageBitmap = if (text == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                decodeScaledBitmap(context, Uri.parse(text))?.asImageBitmap()
            }
        }
    }

    EditorScreen(
        snackbarHostState = snackbarHostState,
        hasImage = selectedUriText != null,
        imageBitmap = imageBitmap,
        mode = mode,
        onImport = {
            imagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onModeSelect = { mode = it },
        positionX = positionX,
        rotationX = rotationX,
        rotationY = rotationY,
        scaleFactor = scaleFactor,
        onPositionChange = { positionX = it },
        onRotationXChange = { rotationX = it },
        onRotationYChange = { rotationY = it },
        onScaleChange = { scaleFactor = it },
        onResetTransforms = {
            positionX = 0f
            rotationX = 18f
            rotationY = 25f
            scaleFactor = 1f
        },
        onTwoDAction = { action ->
            scope.launch {
                snackbarHostState.showSnackbar("$action：功能接口已预留，尚未接入")
            }
        },
        onTwoPointFiveDAction = {
            scope.launch {
                snackbarHostState.showSnackbar("深度估计与视差网格尚未接入")
            }
        },
        onExport = {
            scope.launch {
                snackbarHostState.showSnackbar("GLB 导出接口已定义，导出器尚未实现")
            }
        }
    )
}

private fun decodeScaledBitmap(
    context: Context,
    uri: Uri,
    maxDimension: Int = 2048
): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }

        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > maxDimension ||
            bounds.outHeight / sampleSize > maxDimension
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    } catch (_: Exception) {
        null
    }
}
