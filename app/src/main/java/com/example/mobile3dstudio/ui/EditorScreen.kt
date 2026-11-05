package com.example.mobile3dstudio.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.mobile3dstudio.ui.scene.Scene3DViewport

private const val MIN_PREVIEW_ASPECT = 9f / 16f
private const val MAX_PREVIEW_ASPECT = 16f / 9f

@Composable
fun EditorScreen(
    snackbarHostState: SnackbarHostState,
    hasImage: Boolean,
    imageLoadState: ImageLoadState,
    imageBitmap: Bitmap?,
    imageScale: Float,
    imageOffsetX: Float,
    imageOffsetY: Float,
    imageRotationZ: Float,
    imageRotationX: Float,
    imageRotationY: Float,
    isThreeDMode: Boolean,
    onImportImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onScaleChange: (Float) -> Unit,
    onOffsetXChange: (Float) -> Unit,
    onOffsetYChange: (Float) -> Unit,
    onRotationZChange: (Float) -> Unit,
    onRotationXChange: (Float) -> Unit,
    onRotationYChange: (Float) -> Unit,
    onGestureTransform: (
        centroidX: Float,
        centroidY: Float,
        panX: Float,
        panY: Float,
        zoom: Float,
        rotation: Float,
        width: Float,
        height: Float
    ) -> Unit,
    onToggleThreeD: () -> Unit,
    onResetTransforms: () -> Unit,
    onExport: (String) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EditorTopBar(
                hasImage = hasImage,
                onImportImage = onImportImage,
                onRemoveImage = onRemoveImage,
                onExport = onExport
            )

            PreviewWorkspace(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.60f),
                imageBitmap = imageBitmap,
                imageLoadState = imageLoadState,
                imageScale = imageScale,
                imageOffsetX = imageOffsetX,
                imageOffsetY = imageOffsetY,
                imageRotationZ = imageRotationZ,
                imageRotationX = imageRotationX,
                imageRotationY = imageRotationY,
                isThreeDMode = isThreeDMode,
                onImportImage = onImportImage,
                onGestureTransform = onGestureTransform
            )

            ImageParameterPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.40f),
                hasImage = imageBitmap != null,
                imageScale = imageScale,
                imageOffsetX = imageOffsetX,
                imageOffsetY = imageOffsetY,
                imageRotationZ = imageRotationZ,
                imageRotationX = imageRotationX,
                imageRotationY = imageRotationY,
                isThreeDMode = isThreeDMode,
                onScaleChange = onScaleChange,
                onOffsetXChange = onOffsetXChange,
                onOffsetYChange = onOffsetYChange,
                onRotationZChange = onRotationZChange,
                onRotationXChange = onRotationXChange,
                onRotationYChange = onRotationYChange,
                onToggleThreeD = onToggleThreeD,
                onResetTransforms = onResetTransforms
            )
        }
    }
}

@Composable
private fun EditorTopBar(
    hasImage: Boolean,
    onImportImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onExport: (String) -> Unit
) {
    var imageMenuExpanded by remember { mutableStateOf(false) }
    var exportMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            OutlinedButton(onClick = { imageMenuExpanded = true }) {
                Text(if (hasImage) "图片" else "导入图片")
            }
            DropdownMenu(
                expanded = imageMenuExpanded,
                onDismissRequest = { imageMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(if (hasImage) "更换图片" else "导入图片") },
                    onClick = {
                        imageMenuExpanded = false
                        onImportImage()
                    }
                )
                if (hasImage) {
                    DropdownMenuItem(
                        text = { Text("移除图片") },
                        onClick = {
                            imageMenuExpanded = false
                            onRemoveImage()
                        }
                    )
                }
            }
        }

        Box {
            OutlinedButton(onClick = { exportMenuExpanded = true }) {
                Text("导出")
            }
            DropdownMenu(
                expanded = exportMenuExpanded,
                onDismissRequest = { exportMenuExpanded = false }
            ) {
                listOf("GLB", "PNG", "JPG").forEach { format ->
                    DropdownMenuItem(
                        text = { Text(format) },
                        onClick = {
                            exportMenuExpanded = false
                            onExport(format)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewWorkspace(
    modifier: Modifier = Modifier,
    imageBitmap: Bitmap?,
    imageLoadState: ImageLoadState,
    imageScale: Float,
    imageOffsetX: Float,
    imageOffsetY: Float,
    imageRotationZ: Float,
    imageRotationX: Float,
    imageRotationY: Float,
    isThreeDMode: Boolean,
    onImportImage: () -> Unit,
    onGestureTransform: (
        Float, Float, Float, Float, Float, Float, Float, Float
    ) -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val sourceAspect = imageBitmap?.let {
            it.width.toFloat() / it.height.toFloat()
        } ?: (3f / 4f)
        val previewAspect = sourceAspect.coerceIn(MIN_PREVIEW_ASPECT, MAX_PREVIEW_ASPECT)
        val frameModifier = if (maxWidth / maxHeight > previewAspect) {
            Modifier
                .fillMaxHeight()
                .aspectRatio(previewAspect)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(previewAspect)
        }

        Box(
            modifier = frameModifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            when {
                imageBitmap != null && isThreeDMode -> {
                    Scene3DViewport(
                        modifier = Modifier.fillMaxSize(),
                        imageBitmap = imageBitmap,
                        imageScale = imageScale,
                        imageOffsetX = imageOffsetX,
                        imageOffsetY = imageOffsetY,
                        rotationX = imageRotationX,
                        rotationY = imageRotationY,
                        rotationZ = imageRotationZ
                    )
                }

                imageBitmap != null -> {
                    Image(
                        bitmap = imageBitmap.asImageBitmap(),
                        contentDescription = "图片预览",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = imageScale
                                scaleY = imageScale
                                translationX = imageOffsetX * size.width
                                translationY = imageOffsetY * size.height
                                rotationZ = imageRotationZ
                            },
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.Center
                    )
                    ImageGestureLayer(
                        imageBitmap = imageBitmap,
                        onGestureTransform = onGestureTransform,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    EmptyOrLoadingState(
                        imageLoadState = imageLoadState,
                        onImportImage = onImportImage,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageGestureLayer(
    imageBitmap: Bitmap,
    onGestureTransform: (
        Float, Float, Float, Float, Float, Float, Float, Float
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnGestureTransform by rememberUpdatedState(onGestureTransform)

    // Flat editing gestures: drag moves, pinch zooms around the fingers, twist rotates.
    Box(
        modifier = modifier.pointerInput(imageBitmap) {
            detectTransformGestures { centroid, pan, zoom, rotation ->
                latestOnGestureTransform(
                    centroid.x,
                    centroid.y,
                    pan.x,
                    pan.y,
                    zoom,
                    rotation,
                    size.width.toFloat(),
                    size.height.toFloat()
                )
            }
        }
    )
}

@Composable
private fun EmptyOrLoadingState(
    imageLoadState: ImageLoadState,
    onImportImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (imageLoadState) {
            ImageLoadState.Empty -> {
                Text("尚未导入图片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onImportImage) {
                    Text("导入图片")
                }
            }
            ImageLoadState.Loading -> {
                Text("正在载入图片…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is ImageLoadState.Failure -> {
                Text(
                    imageLoadState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = onImportImage) {
                    Text("重新选择")
                }
            }
            is ImageLoadState.Success -> Unit
        }
    }
}

@Composable
private fun ImageParameterPanel(
    modifier: Modifier = Modifier,
    hasImage: Boolean,
    imageScale: Float,
    imageOffsetX: Float,
    imageOffsetY: Float,
    imageRotationZ: Float,
    imageRotationX: Float,
    imageRotationY: Float,
    isThreeDMode: Boolean,
    onScaleChange: (Float) -> Unit,
    onOffsetXChange: (Float) -> Unit,
    onOffsetYChange: (Float) -> Unit,
    onRotationZChange: (Float) -> Unit,
    onRotationXChange: (Float) -> Unit,
    onRotationYChange: (Float) -> Unit,
    onToggleThreeD: () -> Unit,
    onResetTransforms: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("参数调节", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onResetTransforms,
                        enabled = hasImage
                    ) {
                        Text("重置")
                    }
                    Button(
                        onClick = onToggleThreeD,
                        enabled = hasImage
                    ) {
                        Text(if (isThreeDMode) "返回平面" else "进入 3D 平面")
                    }
                }
            }

            ParameterSlider(
                title = "缩放",
                value = imageScale,
                valueRange = 0.1f..8f,
                enabled = hasImage,
                valueText = "${"%.2f".format(imageScale)}×",
                onValueChange = onScaleChange
            )
            ParameterSlider(
                title = "水平位置",
                value = imageOffsetX,
                valueRange = -2f..2f,
                enabled = hasImage,
                valueText = "${"%.2f".format(imageOffsetX)}",
                onValueChange = onOffsetXChange
            )
            ParameterSlider(
                title = "垂直位置",
                value = imageOffsetY,
                valueRange = -2f..2f,
                enabled = hasImage,
                valueText = "${"%.2f".format(imageOffsetY)}",
                onValueChange = onOffsetYChange
            )
            ParameterSlider(
                title = "平面旋转",
                value = imageRotationZ,
                valueRange = -180f..180f,
                enabled = hasImage,
                valueText = "${imageRotationZ.toInt()}°",
                onValueChange = onRotationZChange
            )

            if (isThreeDMode) {
                ParameterSlider(
                    title = "3D X 轴旋转",
                    value = imageRotationX,
                    valueRange = -90f..90f,
                    enabled = hasImage,
                    valueText = "${imageRotationX.toInt()}°",
                    onValueChange = onRotationXChange
                )
                ParameterSlider(
                    title = "3D Y 轴旋转",
                    value = imageRotationY,
                    valueRange = -90f..90f,
                    enabled = hasImage,
                    valueText = "${imageRotationY.toInt()}°",
                    onValueChange = onRotationYChange
                )
            }
        }
    }
}

@Composable
private fun ParameterSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    valueText: String,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Text(
                valueText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled
        )
    }
}
