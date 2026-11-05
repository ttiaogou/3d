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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns editor state, image import/decode work, and callbacks passed to the UI. */
@Composable
fun Mobile3DStudioApp() {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedUriText by rememberSaveable { mutableStateOf<String?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var imageScale by rememberSaveable { mutableFloatStateOf(1f) }
    var imageOffsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var imageOffsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var imageRotation by rememberSaveable { mutableFloatStateOf(0f) }

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
        imageScale = 1f
        imageOffsetX = 0f
        imageOffsetY = 0f
        imageRotation = 0f
        imageBitmap = if (text == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                decodeScaledBitmap(context, Uri.parse(text))
            }
        }
    }

    EditorScreen(
        snackbarHostState = snackbarHostState,
        hasImage = selectedUriText != null,
        imageBitmap = imageBitmap,
        imageScale = imageScale,
        imageOffsetX = imageOffsetX,
        imageOffsetY = imageOffsetY,
        imageRotation = imageRotation,
        onImportImage = {
            imagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onRemoveImage = {
            selectedUriText = null
            imageBitmap = null
        },
        onImageTransform = { scale, offsetX, offsetY, rotation ->
            imageScale = scale
            imageOffsetX = offsetX
            imageOffsetY = offsetY
            imageRotation = rotation
        },
        onExport = { format ->
            val message = when (format) {
                "GLB" -> "GLB 导出器尚未实现"
                "PNG" -> "PNG 场景图片导出尚未实现"
                "JPG" -> "JPG 场景图片导出尚未实现"
                else -> "不支持的导出格式"
            }
            scope.launch { snackbarHostState.showSnackbar(message) }
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
