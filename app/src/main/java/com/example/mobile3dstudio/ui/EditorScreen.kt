package com.example.mobile3dstudio.ui

import android.graphics.Bitmap
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mobile3dstudio.ui.scene.DiceSceneWebView

const val MODEL_FRAGMENT = "fragment-dice"
const val MODEL_CUT = "cut-dice"
const val MODEL_POKER = "poker"

data class SceneObjectUi(
    val id: Long,
    val kind: String,
    val value: String,
    val cutPercent: Float? = null
) {
    val title: String
        get() = when (kind) {
            MODEL_FRAGMENT -> "骰子1 · ${value}点 · #$id"
            MODEL_CUT -> "骰子2 · ${value}点 · ${cutPercent?.let { "${it.toInt()}% · " } ?: ""}#$id"
            MODEL_POKER -> "红桃Q · #$id"
            else -> "模型 #$id"
        }
}

data class ModelTransformUi(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val rx: Float = 0f,
    val ry: Float = 0f,
    val rz: Float = 0f
)

@Composable
fun EditorScreen(
    snackbarHostState: SnackbarHostState,
    imageLoadState: ImageLoadState,
    imageBitmap: Bitmap?,
    imageScale: Float,
    imageOffsetX: Float,
    imageOffsetY: Float,
    imageRotation: Float,
    isModelWorkspace: Boolean,
    selectedModelType: String,
    pointInput: String,
    sceneObjects: List<SceneObjectUi>,
    selectedObjectId: Long?,
    selectedTransform: ModelTransformUi,
    canUndo: Boolean,
    rendererReady: Boolean,
    onWebViewReady: (WebView) -> Unit,
    onSceneState: (String) -> Unit,
    onImportImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onFlatGesture: (Float, Float, Float, Float, Float, Float, Float, Float) -> Unit,
    onEnterModels: () -> Unit,
    onReturnToImage: () -> Unit,
    onModelTypeSelect: (String) -> Unit,
    onPointInputChange: (String) -> Unit,
    onGenerateModel: () -> Unit,
    onSelectObject: (Long) -> Unit,
    onModelTransformChange: (String, Float) -> Unit,
    onModelTransformFinish: () -> Unit,
    onDefaultModel: () -> Unit,
    onUndo: () -> Unit,
    onExportPng: () -> Unit,
    onExportGlb: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.625f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                // Keep the original WebGL renderer alive while flat-image editing is active.
                DiceSceneWebView(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (isModelWorkspace) 1f else 0.001f),
                    onWebViewReady = onWebViewReady,
                    onSceneState = onSceneState
                )

                if (!isModelWorkspace) {
                    FlatImageWorkspace(
                        imageLoadState = imageLoadState,
                        imageBitmap = imageBitmap,
                        imageScale = imageScale,
                        imageOffsetX = imageOffsetX,
                        imageOffsetY = imageOffsetY,
                        imageRotation = imageRotation,
                        onImportImage = onImportImage,
                        onFlatGesture = onFlatGesture,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (isModelWorkspace) {
                ModelWorkbench(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.375f),
                    hasImage = imageBitmap != null,
                    onImportImage = onImportImage,
                    onRemoveImage = onRemoveImage,
                    onExportPng = onExportPng,
                    onExportGlb = onExportGlb,
                    selectedModelType = selectedModelType,
                    pointInput = pointInput,
                    sceneObjects = sceneObjects,
                    selectedObjectId = selectedObjectId,
                    selectedTransform = selectedTransform,
                    canUndo = canUndo,
                    rendererReady = rendererReady,
                    onModelTypeSelect = onModelTypeSelect,
                    onPointInputChange = onPointInputChange,
                    onGenerateModel = onGenerateModel,
                    onSelectObject = onSelectObject,
                    onModelTransformChange = onModelTransformChange,
                    onModelTransformFinish = onModelTransformFinish,
                    onDefaultModel = onDefaultModel,
                    onUndo = onUndo,
                    onReturnToImage = onReturnToImage
                )
            } else {
                FlatImagePanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.375f),
                    hasImage = imageBitmap != null,
                    onEnterModels = onEnterModels,
                    onImportImage = onImportImage,
                    onRemoveImage = onRemoveImage,
                    onExportPng = onExportPng,
                    onExportGlb = onExportGlb
                )
            }
        }
    }
}

@Composable
private fun FlatImageWorkspace(
    imageLoadState: ImageLoadState,
    imageBitmap: Bitmap?,
    imageScale: Float,
    imageOffsetX: Float,
    imageOffsetY: Float,
    imageRotation: Float,
    onImportImage: () -> Unit,
    onFlatGesture: (Float, Float, Float, Float, Float, Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap.asImageBitmap(),
                contentDescription = "平面图片预览",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = imageScale
                        scaleY = imageScale
                        translationX = imageOffsetX * size.width
                        translationY = imageOffsetY * size.height
                        rotationZ = imageRotation
                    },
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center
            )
            FlatGestureLayer(
                imageBitmap = imageBitmap,
                onFlatGesture = onFlatGesture,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (imageLoadState) {
                    ImageLoadState.Empty -> Text("导入图片开始编辑", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ImageLoadState.Loading -> Text("正在载入图片…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    is ImageLoadState.Failure -> Text(imageLoadState.message, color = MaterialTheme.colorScheme.error)
                    is ImageLoadState.Success -> Unit
                }
                OutlinedButton(onClick = onImportImage) { Text("选择图片") }
            }
        }
    }
}

@Composable
private fun FlatGestureLayer(
    imageBitmap: Bitmap,
    onFlatGesture: (Float, Float, Float, Float, Float, Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestGesture by rememberUpdatedState(onFlatGesture)
    Box(
        modifier = modifier.pointerInput(imageBitmap) {
            detectTransformGestures { centroid, pan, zoom, rotation ->
                latestGesture(
                    centroid.x, centroid.y, pan.x, pan.y, zoom, rotation,
                    size.width.toFloat(), size.height.toFloat()
                )
            }
        }
    )
}

@Composable
private fun FlatImagePanel(
    modifier: Modifier,
    hasImage: Boolean,
    onEnterModels: () -> Unit,
    onImportImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onExportPng: () -> Unit,
    onExportGlb: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onEnterModels) { Text("模型") }
            PanelMenuButton(
                onImportImage = onImportImage,
                onRemoveImage = onRemoveImage,
                onExportPng = onExportPng,
                onExportGlb = onExportGlb,
                hasImage = hasImage
            )
        }
    }
}

@Composable
private fun PanelMenuButton(
    onImportImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onExportPng: () -> Unit,
    onExportGlb: () -> Unit,
    hasImage: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(if (hasImage) "更换图片" else "导入图片") },
                onClick = { expanded = false; onImportImage() }
            )
            if (hasImage) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("移除图片") },
                    onClick = { expanded = false; onRemoveImage() }
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("导出 PNG") },
                    onClick = { expanded = false; onExportPng() }
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("导出 GLB") },
                    enabled = false,
                    onClick = { expanded = false; onExportGlb() }
                )
            }
        }
    }
}

@Composable
private fun ModelWorkbench(
    modifier: Modifier,
    hasImage: Boolean,
    onImportImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onExportPng: () -> Unit,
    onExportGlb: () -> Unit,
    selectedModelType: String,
    pointInput: String,
    sceneObjects: List<SceneObjectUi>,
    selectedObjectId: Long?,
    selectedTransform: ModelTransformUi,
    canUndo: Boolean,
    rendererReady: Boolean,
    onModelTypeSelect: (String) -> Unit,
    onPointInputChange: (String) -> Unit,
    onGenerateModel: () -> Unit,
    onSelectObject: (Long) -> Unit,
    onModelTransformChange: (String, Float) -> Unit,
    onModelTransformFinish: () -> Unit,
    onDefaultModel: () -> Unit,
    onUndo: () -> Unit,
    onReturnToImage: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("模型生成", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onUndo, enabled = canUndo) {
                        Text("↶", style = MaterialTheme.typography.headlineSmall)
                    }
                    IconButton(onClick = onReturnToImage) {
                        Text("↪", style = MaterialTheme.typography.headlineSmall)
                    }
                    PanelMenuButton(
                        onImportImage = onImportImage,
                        onRemoveImage = onRemoveImage,
                        onExportPng = onExportPng,
                        onExportGlb = onExportGlb,
                        hasImage = hasImage
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ModelTypeChip("骰子 1", selectedModelType == MODEL_FRAGMENT) { onModelTypeSelect(MODEL_FRAGMENT) }
                ModelTypeChip("骰子 2", selectedModelType == MODEL_CUT) { onModelTypeSelect(MODEL_CUT) }
                ModelTypeChip("扑克", selectedModelType == MODEL_POKER) { onModelTypeSelect(MODEL_POKER) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedModelType != MODEL_POKER) {
                    OutlinedTextField(
                        value = pointInput,
                        onValueChange = { raw -> onPointInputChange(raw.filter(Char::isDigit).take(1)) },
                        modifier = Modifier.weight(1f),
                        label = { Text("点数 0–9") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                } else {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("红桃 Q", modifier = Modifier.padding(horizontal = 12.dp, vertical = 17.dp))
                    }
                }
                Button(
                    onClick = onGenerateModel,
                    enabled = rendererReady && (selectedModelType == MODEL_POKER || pointInput.isNotBlank())
                ) { Text("生成模型") }
            }
            if (!rendererReady) {
                Text("3D 渲染器初始化中…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (sceneObjects.isNotEmpty()) {
                Text("场景对象", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sceneObjects, key = { it.id }) { obj ->
                        FilterChip(
                            selected = selectedObjectId == obj.id,
                            onClick = { onSelectObject(obj.id) },
                            label = { Text(obj.title) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("XYZ 移动", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onDefaultModel, enabled = selectedObjectId != null) { Text("默认") }
            }
            ModelSlider("X 移动", selectedTransform.x, -2f..2f, selectedObjectId != null, onModelTransformChange, onModelTransformFinish, "x")
            ModelSlider("Y 移动", selectedTransform.y, -2f..2f, selectedObjectId != null, onModelTransformChange, onModelTransformFinish, "y")
            ModelSlider("Z 移动", selectedTransform.z, -2f..2f, selectedObjectId != null, onModelTransformChange, onModelTransformFinish, "z")

            Text("XYZ 旋转", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ModelSlider("X 旋转", selectedTransform.rx, -180f..180f, selectedObjectId != null, onModelTransformChange, onModelTransformFinish, "rx")
            ModelSlider("Y 旋转", selectedTransform.ry, -180f..180f, selectedObjectId != null, onModelTransformChange, onModelTransformFinish, "ry")
            ModelSlider("Z 旋转", selectedTransform.rz, -180f..180f, selectedObjectId != null, onModelTransformChange, onModelTransformFinish, "rz")
        }
    }
}

@Composable
private fun ModelTypeChip(title: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(title) })
}

@Composable
private fun ModelSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onChange: (String, Float) -> Unit,
    onFinished: () -> Unit,
    axis: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Text("${"%.2f".format(value)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = { onChange(axis, it) },
            onValueChangeFinished = onFinished,
            valueRange = range,
            enabled = enabled
        )
    }
}

