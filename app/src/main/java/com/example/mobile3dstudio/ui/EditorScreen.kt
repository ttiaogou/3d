package com.example.mobile3dstudio.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mobile3dstudio.core.model.EditorMode
import com.example.mobile3dstudio.ui.scene.Scene3DViewport

@Composable
fun EditorScreen(
    snackbarHostState: SnackbarHostState,
    hasImage: Boolean,
    imageBitmap: ImageBitmap?,
    mode: EditorMode,
    onImport: () -> Unit,
    onRemoveImage: () -> Unit,
    onModeSelect: (EditorMode) -> Unit,
    positionX: Float,
    rotationX: Float,
    rotationY: Float,
    scaleFactor: Float,
    onPositionChange: (Float) -> Unit,
    onRotationXChange: (Float) -> Unit,
    onRotationYChange: (Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onResetTransforms: () -> Unit,
    onTwoDAction: (String) -> Unit,
    onTwoPointFiveDAction: () -> Unit,
    onExport: (String) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val parameterPanelHeight = when {
                maxHeight >= 680.dp -> 276.dp
                maxHeight >= 560.dp -> 260.dp
                maxHeight >= 440.dp -> 232.dp
                else -> maxHeight * 0.46f
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditorTopBar(
                    mode = mode,
                    onModeSelect = onModeSelect,
                    onExport = onExport
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (mode) {
                        EditorMode.TWO_D -> TwoDWorkspace(
                            hasImage = hasImage,
                            imageBitmap = imageBitmap,
                            onImport = onImport,
                            onRemoveImage = onRemoveImage,
                            onAction = onTwoDAction
                        )

                        EditorMode.TWO_POINT_FIVE_D -> TwoPointFiveDWorkspace(
                            hasImage = hasImage,
                            imageBitmap = imageBitmap,
                            onImport = onImport,
                            onRemoveImage = onRemoveImage,
                            onAction = onTwoPointFiveDAction
                        )

                        EditorMode.THREE_D -> ThreeDWorkspace(
                            hasImage = hasImage,
                            onImport = onImport,
                            onRemoveImage = onRemoveImage,
                            positionX = positionX,
                            rotationX = rotationX,
                            rotationY = rotationY,
                            scaleFactor = scaleFactor
                        )
                    }
                }

                if (mode == EditorMode.THREE_D) {
                    ParameterPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(parameterPanelHeight),
                        positionX = positionX,
                        rotationX = rotationX,
                        rotationY = rotationY,
                        scaleFactor = scaleFactor,
                        onPositionChange = onPositionChange,
                        onRotationXChange = onRotationXChange,
                        onRotationYChange = onRotationYChange,
                        onScaleChange = onScaleChange,
                        onResetTransforms = onResetTransforms
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorTopBar(
    mode: EditorMode,
    onModeSelect: (EditorMode) -> Unit,
    onExport: (String) -> Unit
) {
    var exportExpanded by remember { mutableStateOf(false) }
    val exportFormats = when (mode) {
        EditorMode.TWO_D -> listOf("PNG", "JPEG")
        EditorMode.TWO_POINT_FIVE_D -> listOf("GLB", "深度 PNG")
        EditorMode.THREE_D -> listOf("GLB")
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EditorMode.entries.forEach { item ->
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = mode == item,
                onClick = { onModeSelect(item) },
                label = { Text(item.title, fontWeight = FontWeight.SemiBold) }
            )
        }

        Box {
            OutlinedButton(onClick = { exportExpanded = true }) {
                Text("导出")
            }
            DropdownMenu(
                expanded = exportExpanded,
                onDismissRequest = { exportExpanded = false }
            ) {
                exportFormats.forEach { format ->
                    DropdownMenuItem(
                        text = { Text(format) },
                        onClick = {
                            exportExpanded = false
                            onExport(format)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TwoDWorkspace(
    hasImage: Boolean,
    imageBitmap: ImageBitmap?,
    onImport: () -> Unit,
    onRemoveImage: () -> Unit,
    onAction: (String) -> Unit
) {
    WorkspaceCard(
        hasImage = hasImage,
        onImport = onImport,
        onRemoveImage = onRemoveImage
    ) {
        ImagePreview(
            imageBitmap = imageBitmap,
            onEmptyClick = onImport,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onAction("裁剪") },
                modifier = Modifier.weight(1f),
                enabled = imageBitmap != null
            ) {
                Text("裁剪")
            }
            OutlinedButton(
                onClick = { onAction("抠图") },
                modifier = Modifier.weight(1f),
                enabled = imageBitmap != null
            ) {
                Text("抠图")
            }
            OutlinedButton(
                onClick = { onAction("分层") },
                modifier = Modifier.weight(1f),
                enabled = imageBitmap != null
            ) {
                Text("分层")
            }
        }
    }
}

@Composable
private fun TwoPointFiveDWorkspace(
    hasImage: Boolean,
    imageBitmap: ImageBitmap?,
    onImport: () -> Unit,
    onRemoveImage: () -> Unit,
    onAction: () -> Unit
) {
    WorkspaceCard(
        hasImage = hasImage,
        onImport = onImport,
        onRemoveImage = onRemoveImage
    ) {
        ImagePreview(
            imageBitmap = imageBitmap,
            onEmptyClick = onImport,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("深度图", fontWeight = FontWeight.SemiBold)
                Text(
                    "未生成",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "待接入",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Button(
            onClick = onAction,
            enabled = imageBitmap != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("生成深度信息")
        }
    }
}

@Composable
private fun ThreeDWorkspace(
    hasImage: Boolean,
    onImport: () -> Unit,
    onRemoveImage: () -> Unit,
    positionX: Float,
    rotationX: Float,
    rotationY: Float,
    scaleFactor: Float
) {
    WorkspaceCard(
        hasImage = hasImage,
        onImport = onImport,
        onRemoveImage = onRemoveImage
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Scene3DViewport(
                modifier = Modifier.fillMaxSize(),
                positionX = positionX,
                rotationX = rotationX,
                rotationY = rotationY,
                scaleFactor = scaleFactor
            )
        }
    }
}

@Composable
private fun ParameterPanel(
    modifier: Modifier = Modifier,
    positionX: Float,
    rotationX: Float,
    rotationY: Float,
    scaleFactor: Float,
    onPositionChange: (Float) -> Unit,
    onRotationXChange: (Float) -> Unit,
    onRotationYChange: (Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onResetTransforms: () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "参数调整",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = rememberLazyListState(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item(key = "position-x") {
                    TransformSlider("位置 X", positionX, -1f..1f, onPositionChange)
                }
                item(key = "rotation-x") {
                    TransformSlider("旋转 X", rotationX, 0f..360f, onRotationXChange)
                }
                item(key = "rotation-y") {
                    TransformSlider("旋转 Y", rotationY, 0f..360f, onRotationYChange)
                }
                item(key = "scale") {
                    TransformSlider("缩放", scaleFactor, 0.35f..1.8f, onScaleChange)
                }
                item(key = "transform-actions") {
                    OutlinedButton(
                        onClick = onResetTransforms,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("重置变换")
                    }
                }
            }
        }
    }
}

@Composable
private fun TransformSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Text(
                "${"%.2f".format(value)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun ImagePreview(
    imageBitmap: ImageBitmap?,
    onEmptyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = imageBitmap == null, onClick = onEmptyClick),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "导入的图片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "＋",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("尚未导入图片", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun WorkspaceCard(
    hasImage: Boolean,
    onImport: () -> Unit,
    onRemoveImage: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
            WorkspaceImageMenu(
                hasImage = hasImage,
                onImport = onImport,
                onRemoveImage = onRemoveImage,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
            )
        }
    }
}

@Composable
private fun WorkspaceImageMenu(
    hasImage: Boolean,
    onImport: () -> Unit,
    onRemoveImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
        ) {
            IconButton(onClick = { expanded = true }) {
                Text("⋮", style = MaterialTheme.typography.titleLarge)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (hasImage) {
                DropdownMenuItem(
                    text = { Text("更换图片") },
                    onClick = {
                        expanded = false
                        onImport()
                    }
                )
                DropdownMenuItem(
                    text = { Text("移除图片") },
                    onClick = {
                        expanded = false
                        onRemoveImage()
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("导入图片") },
                    onClick = {
                        expanded = false
                        onImport()
                    }
                )
            }
        }
    }
}
