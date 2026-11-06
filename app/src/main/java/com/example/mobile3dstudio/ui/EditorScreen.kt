package com.example.mobile3dstudio.ui

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mobile3dstudio.core.model.NativeSceneModel
import com.example.mobile3dstudio.core.model.NativeTransform
import com.example.mobile3dstudio.ui.scene.NativeSceneViewport
import kotlin.math.roundToInt

const val MODEL_FRAGMENT = "fragment-dice"
const val MODEL_CUT = "cut-dice"
const val MODEL_POKER = "poker"

data class ModelTransformUi(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val rx: Float = 0f,
    val ry: Float = 0f,
    val rz: Float = 0f,
    val scale: Float = 1f
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
    hasExportContent: Boolean,
    selectedObjectId: Long?,
    selectedTransform: ModelTransformUi,
    canUndo: Boolean,
    canRedo: Boolean,
    rendererReady: Boolean,
    rendererError: String?,
    nativeModels: List<NativeSceneModel>,
    onNativeModelSelected: (Long?) -> Unit,
    onLongPressModel: (Long) -> Unit,
    onDeleteModel: (Long) -> Unit,
    onRendererFrame: () -> Unit,
    onRendererError: (String) -> Unit,
    onPreviewBoundsChanged: (Rect) -> Unit,
    onImportImage: () -> Unit,
    onImportGlb: () -> Unit,
    onRemoveImage: () -> Unit,
    onFlatGesture: (Float, Float, Float, Float, Float, Float, Float, Float) -> Unit,
    onEnterModels: () -> Unit,
    onReturnToImage: () -> Unit,
    onModelTypeSelect: (String) -> Unit,
    onPointInputChange: (String) -> Unit,
    onGenerateModel: () -> Unit,
    onModelTransformChange: (String, Float) -> Unit,
    onModelTransformGesture: (Long, NativeTransform) -> Unit,
    onModelTransformFinish: () -> Unit,
    onDefaultModel: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExportPng: () -> Unit,
    onExportGlb: () -> Unit,
    showPreviewControls: Boolean
) {
    var deleteDialogModelId by remember { mutableStateOf<Long?>(null) }

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
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        onPreviewBoundsChanged(
                            Rect(
                                bounds.left.roundToInt(), bounds.top.roundToInt(),
                                bounds.right.roundToInt(), bounds.bottom.roundToInt()
                            )
                        )
                    }
            ) {
                FlatImageWorkspace(
                    imageBitmap = imageBitmap,
                    imageScale = imageScale,
                    imageOffsetX = imageOffsetX,
                    imageOffsetY = imageOffsetY,
                    imageRotation = imageRotation,
                    modifier = Modifier.fillMaxSize()
                )

                NativeSceneViewport(
                    modifier = Modifier.fillMaxSize(),
                    models = nativeModels,
                    selectedModelId = selectedObjectId,
                    onModelSelected = onNativeModelSelected,
                    onLongPressModel = { id ->
                        onLongPressModel(id)
                        deleteDialogModelId = id
                    },
                    onModelTransform = { id, transform -> onModelTransformGesture(id, transform) },
                    onModelTransformFinished = onModelTransformFinish,
                    backgroundEnabled = imageBitmap != null,
                    onBackgroundGesture = onFlatGesture,
                    onBackgroundGestureFinished = onModelTransformFinish,
                    onRendererFrame = onRendererFrame,
                    onRendererError = onRendererError
                )


                if (showPreviewControls) {
                    if (imageBitmap == null) {
                        OutlinedButton(
                            onClick = onImportImage,
                            modifier = Modifier.align(Alignment.Center)
                        ) { Text("选择图片/导入") }
                    }
                    PreviewToolbar(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        canUndo = canUndo,
                        canRedo = canRedo,
                        hasImage = imageBitmap != null,
                        hasExportContent = hasExportContent,
                        isModelWorkspace = isModelWorkspace,
                        onUndo = onUndo,
                        onRedo = onRedo,
                        onImportImage = onImportImage,
                        onImportGlb = onImportGlb,
                        onRemoveImage = onRemoveImage,
                        onExportPng = onExportPng,
                        onExportGlb = onExportGlb,
                        onEnterModels = onEnterModels,
                        onReturnToImage = onReturnToImage
                    )
                }
            }

            if (isModelWorkspace) {
                ModelWorkbench(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.375f),
                    selectedModelType = selectedModelType,
                    pointInput = pointInput,
                    selectedObjectId = selectedObjectId,
                    selectedTransform = selectedTransform,
                    rendererReady = rendererReady,
                    rendererError = rendererError,
                    onModelTypeSelect = onModelTypeSelect,
                    onPointInputChange = onPointInputChange,
                    onGenerateModel = onGenerateModel,
                    onModelTransformChange = onModelTransformChange,
                    onModelTransformFinish = onModelTransformFinish,
                    onDefaultModel = onDefaultModel
                )
            } else {
                FlatImagePanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.375f),
                    onEnterModels = onEnterModels
                )
            }
        }
    }

    deleteDialogModelId?.let { modelId ->
        AlertDialog(
            onDismissRequest = { deleteDialogModelId = null },
            title = { Text("删除模型") },
            text = { Text("删除当前选中的模型？") },
            confirmButton = {
                Button(onClick = {
                    onDeleteModel(modelId)
                    deleteDialogModelId = null
                }) { Text("删除") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteDialogModelId = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun FlatImageWorkspace(
    imageBitmap: Bitmap?,
    imageScale: Float,
    imageOffsetX: Float,
    imageOffsetY: Float,
    imageRotation: Float,
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
        }
    }
}

@Composable
private fun FlatImagePanel(
    modifier: Modifier,
    onEnterModels: () -> Unit
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
            horizontalArrangement = Arrangement.Start
        ) {
            OutlinedButton(onClick = onEnterModels) { Text("模型") }
        }
    }
}

@Composable
private fun PreviewToolbar(
    modifier: Modifier,
    canUndo: Boolean,
    canRedo: Boolean,
    hasImage: Boolean,
    hasExportContent: Boolean,
    isModelWorkspace: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onImportImage: () -> Unit,
    onImportGlb: () -> Unit,
    onRemoveImage: () -> Unit,
    onExportPng: () -> Unit,
    onExportGlb: () -> Unit,
    onEnterModels: () -> Unit,
    onReturnToImage: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onUndo, enabled = canUndo) {
            Text("↶", style = MaterialTheme.typography.headlineSmall)
        }
        IconButton(onClick = onRedo, enabled = canRedo) {
            Text("↪", style = MaterialTheme.typography.headlineSmall)
        }
        Box {
            IconButton(onClick = { expanded = true }) {
                Text("⋮", style = MaterialTheme.typography.titleLarge)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(if (hasImage) "更换图片" else "导入图片") },
                    onClick = { expanded = false; onImportImage() }
                )
                DropdownMenuItem(
                    text = { Text("导入 GLB") },
                    onClick = { expanded = false; onImportGlb() }
                )
                if (hasImage) {
                    DropdownMenuItem(
                        text = { Text("移除图片") },
                        onClick = { expanded = false; onRemoveImage() }
                    )
                }
                if (hasExportContent) {
                    DropdownMenuItem(
                        text = { Text("导出 PNG") },
                        onClick = { expanded = false; onExportPng() }
                    )
                    DropdownMenuItem(
                        text = { Text("导出 GLB") },
                        onClick = { expanded = false; onExportGlb() }
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (isModelWorkspace) "图片编辑" else "模型工作区") },
                    onClick = {
                        expanded = false
                        if (isModelWorkspace) onReturnToImage() else onEnterModels()
                    }
                )
            }
        }
    }
}

@Composable
private fun ModelWorkbench(
    modifier: Modifier,
    selectedModelType: String,
    pointInput: String,
    selectedObjectId: Long?,
    selectedTransform: ModelTransformUi,
    rendererReady: Boolean,
    rendererError: String?,
    onModelTypeSelect: (String) -> Unit,
    onPointInputChange: (String) -> Unit,
    onGenerateModel: () -> Unit,
    onModelTransformChange: (String, Float) -> Unit,
    onModelTransformFinish: () -> Unit,
    onDefaultModel: () -> Unit
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
                        Text("黑桃 Q", modifier = Modifier.padding(horizontal = 12.dp, vertical = 17.dp))
                    }
                }
                Button(
                    onClick = onGenerateModel,
                    enabled = rendererReady && (selectedModelType == MODEL_POKER || pointInput.isNotBlank())
                ) { Text("生成模型") }
            }
            if (!rendererReady) {
                Text(
                    rendererError ?: "3D 渲染器与预览画布初始化中…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("XYZ 移动", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onDefaultModel, enabled = selectedObjectId != null) { Text("默认") }
            }
            ModelSlider("X 移动", selectedTransform.x, 0.16f, selectedObjectId != null, onModelTransformChange, onModelTransformFinish, "x")
            ModelSlider("Y 移动", selectedTransform.y, 0.16f, selectedObjectId != null, onModelTransformChange, onModelTransformFinish, "y")
            ModelSlider("Z 移动", selectedTransform.z, 0.12f, selectedObjectId != null, onModelTransformChange, onModelTransformFinish, "z")

            Text("XYZ 旋转", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ModelSlider("X 旋转", selectedTransform.rx, 15f, selectedObjectId != null, onModelTransformChange, onModelTransformFinish, "rx")
            ModelSlider("Y 旋转", selectedTransform.ry, 15f, selectedObjectId != null, onModelTransformChange, onModelTransformFinish, "ry")
            ModelSlider("Z 旋转", selectedTransform.rz, 15f, selectedObjectId != null, onModelTransformChange, onModelTransformFinish, "rz")
            ModelSlider("缩放", selectedTransform.scale, 0.10f, selectedObjectId != null, onModelTransformChange, onModelTransformFinish, "scale")
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
    fineTuneSpan: Float,
    enabled: Boolean,
    onChange: (String, Float) -> Unit,
    onFinished: () -> Unit,
    axis: String
) {
    var sliderOffset by remember(axis) { mutableFloatStateOf(0f) }
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Text("${"%.2f".format(value)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
        Slider(
            value = sliderOffset,
            onValueChange = { nextOffset ->
                val delta = nextOffset - sliderOffset
                sliderOffset = nextOffset
                if (delta != 0f) onChange(axis, delta * fineTuneSpan)
            },
            onValueChangeFinished = {
                sliderOffset = 0f
                onFinished()
            },
            valueRange = -1f..1f,
            enabled = enabled
        )
    }
}
